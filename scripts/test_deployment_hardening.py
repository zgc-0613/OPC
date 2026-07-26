import ast
import re
import hmac
import contextlib
import importlib.util
import io
import subprocess
import tempfile
import unittest
from unittest import mock
from pathlib import Path

import scripts.deployment_hardening as deployment_hardening
from scripts.deployment_hardening import (
    is_loopback_listener,
    ensure_stable_cursor_hmac_secret,
    load_local_deploy_secrets,
    require_initial_admin_credentials,
    require_cursor_hmac_secret_environment,
    require_secret_environment,
    validate_agent_evidence_probe,
    validate_agent_probe_record,
    validate_agent_runtime_postcheck,
    validate_assistant_workspace_postcheck,
    validate_assistant_history_revision_postcheck,
    validate_atomic_start_replay,
    validate_cursor_second_page,
    validate_history_cursor_stale_response,
    validate_purge_barrier_record,
)


ROOT = Path(__file__).resolve().parents[1]
APPLICATION_CONFIG = ROOT / "opc-backend" / "src" / "main" / "resources" / "application.yaml"
NGINX_CONFIG = ROOT / "deploy" / "nginx" / "opc.conf"
SYSTEMD_UNIT = ROOT / "deploy" / "systemd" / "opc-backend.service"
DEPLOY_SCRIPT = ROOT / ".codex_deploy_opc.py"


class DeploymentHardeningTest(unittest.TestCase):
    def test_candidate_probe_contract_requires_full_runtime_and_coverage_audit(self):
        record = {
            "status": "completed",
            "provider": "deepseek",
            "model": "configured-model",
            "prompt_version": "agent-research-v2",
            "finish_reason": "stop",
            "provider_request_id": "provider-request",
            "internal_request_id": "internal-request",
            "prompt_tokens": 120,
            "completion_tokens": 80,
            "total_tokens": 200,
            "latency_ms": 450,
            "model_rounds": 2,
            "provider_call_count": 2,
            "tool_call_count": 2,
            "completed_tool_count": 2,
            "citation_count": 2,
            "unknown_citation_count": 0,
            "settlement_status": "settled_actual",
            "reserved_tokens": 0,
            "coverage_status": "sufficient",
            "coverage_case_count": 1,
            "coverage_policy_count": 1,
            "coverage_source_count": 2,
            "evidence_case_count": 1,
            "evidence_policy_count": 1,
            "evidence_source_count": 2,
            "release_switched": False,
        }

        deployment_hardening.validate_candidate_agent_probe_record(
            record, max_model_rounds=4, max_tool_calls=6
        )

        for field, invalid_value in (
            ("coverage_source_count", 3),
            ("provider_call_count", 1),
            ("settlement_status", "reserved"),
            ("reserved_tokens", 1),
            ("release_switched", True),
        ):
            invalid = {**record, field: invalid_value}
            with self.assertRaises(ValueError, msg=field):
                deployment_hardening.validate_candidate_agent_probe_record(
                    invalid, max_model_rounds=4, max_tool_calls=6
                )

    def test_candidate_failure_diagnostic_is_complete_and_does_not_leak_raw_values(self):
        message = deployment_hardening.candidate_probe_failure_message(
            "INVALID_STRUCTURED_RESULT",
            {
                "provider": "deepseek",
                "model_rounds": 2,
                "provider_call_count": 2,
                "tool_call_count": 1,
                "total_tokens": 321,
                "finish_reason": "length",
                "latency_ms": 987,
                "release_switched": False,
            },
        )

        self.assertIn("INVALID_STRUCTURED_RESULT", message)
        self.assertIn("provider_called=true", message)
        self.assertIn("model_rounds=2", message)
        self.assertIn("tool_call_count=1", message)
        self.assertIn("total_tokens=321", message)
        self.assertIn("finish_reason=length", message)
        self.assertIn("latency_ms=987", message)
        self.assertIn("release_switched=false", message)
        self.assertNotIn("raw-model-output", message)

    def test_explicit_environment_wins_over_local_deploy_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "opc-deploy.env"
            path.write_text(
                "OPC_SSH_PASSWORD=file-test-value\n"
                "OPC_ASSISTANT_CURSOR_HMAC_SECRET=cursor-test-secret-0123456789abcdef\n",
                encoding="utf-8",
            )
            environment = {"OPC_SSH_PASSWORD": "explicit-test-value"}

            presence = load_local_deploy_secrets(environment, path)

        self.assertEqual("explicit-test-value", environment["OPC_SSH_PASSWORD"])
        self.assertEqual(
            "cursor-test-secret-0123456789abcdef",
            environment["OPC_ASSISTANT_CURSOR_HMAC_SECRET"],
        )
        self.assertEqual(
            {"OPC_SSH_PASSWORD": True, "OPC_ASSISTANT_CURSOR_HMAC_SECRET": True,
             "OPC_INITIAL_ADMIN_USERNAME": False, "OPC_INITIAL_ADMIN_PASSWORD": False},
            presence,
        )

    def test_local_deploy_file_rejects_unknown_and_duplicate_keys_without_value_leak(self):
        cases = (
            "OPC_SSH_PASSWORD=fake-one\nUNKNOWN_SECRET=do-not-report-this-value\n",
            "OPC_SSH_PASSWORD=fake-one\nOPC_SSH_PASSWORD=do-not-report-this-value\n",
        )
        for content in cases:
            with self.subTest(content_type=content.splitlines()[-1].split("=")[0]):
                with tempfile.TemporaryDirectory() as directory:
                    path = Path(directory) / "opc-deploy.env"
                    path.write_text(content, encoding="utf-8")
                    with self.assertRaises(RuntimeError) as raised:
                        load_local_deploy_secrets({}, path)
                self.assertNotIn("do-not-report-this-value", str(raised.exception))

    def test_local_deploy_file_errors_do_not_write_secret_values_to_output(self):
        secret_value = "test-only-never-log-this-secret"
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "opc-deploy.env"
            path.write_text(f"UNKNOWN_SECRET={secret_value}\n", encoding="utf-8")
            stdout = io.StringIO()
            stderr = io.StringIO()
            with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                with self.assertRaises(RuntimeError) as raised:
                    load_local_deploy_secrets({}, path)

        combined = stdout.getvalue() + stderr.getvalue() + str(raised.exception)
        self.assertNotIn(secret_value, combined)

    def test_local_secrets_directory_is_ignored_and_untracked(self):
        ignored = subprocess.run(
            ["git", "check-ignore", "-q", ".local-secrets/opc-deploy.env"],
            cwd=ROOT,
            check=False,
        )
        tracked = subprocess.run(
            ["git", "ls-files", "--error-unmatch", ".local-secrets/opc-deploy.env"],
            cwd=ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        status = subprocess.run(
            ["git", "status", "--short", "--untracked-files=all", "--", ".local-secrets"],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )

        self.assertEqual(0, ignored.returncode)
        self.assertNotEqual(0, tracked.returncode)
        self.assertEqual("", status.stdout)

    def test_local_deploy_file_rejects_empty_malformed_and_illegal_newlines(self):
        cases = (
            b"OPC_SSH_PASSWORD=\n",
            b" OPC_SSH_PASSWORD=fake-value\n",
            b"OPC_SSH_PASSWORD fake-value\n",
            b"OPC_SSH_PASSWORD=fake-value\rOPC_INITIAL_ADMIN_USERNAME=test-admin\n",
            b"OPC_SSH_PASSWORD=fake-value\n\nOPC_INITIAL_ADMIN_USERNAME=test-admin\n",
        )
        for content in cases:
            with self.subTest(case=cases.index(content)):
                with tempfile.TemporaryDirectory() as directory:
                    path = Path(directory) / "opc-deploy.env"
                    path.write_bytes(content)
                    with self.assertRaises(RuntimeError) as raised:
                        load_local_deploy_secrets({}, path)
                self.assertNotIn("fake-value", str(raised.exception))

    def test_cursor_secret_is_generated_once_and_never_rotated_on_repeat(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "opc-deploy.env"
            path.write_bytes(b"")
            first_environment = {}
            ensure_stable_cursor_hmac_secret(first_environment, path)
            first = first_environment.get("OPC_ASSISTANT_CURSOR_HMAC_SECRET")
            first_bytes = path.read_bytes()

            second_environment = {}
            ensure_stable_cursor_hmac_secret(second_environment, path)
            second = second_environment.get("OPC_ASSISTANT_CURSOR_HMAC_SECRET")
            second_bytes = path.read_bytes()

        self.assertTrue(isinstance(first, str) and len(first) >= 32)
        self.assertTrue(isinstance(second, str) and hmac.compare_digest(first, second))
        self.assertTrue(first_bytes == second_bytes)

    def test_deploy_loads_ignored_local_credentials_before_connecting(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        main = source[source.index("def main():"):source.index('if __name__ == "__main__":')]
        load = main.index("load_local_deploy_secrets(os.environ, LOCAL_DEPLOY_SECRET_FILE)")
        stabilize = main.index("ensure_stable_cursor_hmac_secret(os.environ, LOCAL_DEPLOY_SECRET_FILE)")
        connect = main.index("client = connect()")

        self.assertLess(load, stabilize)
        self.assertLess(stabilize, connect)
        self.assertIn('ROOT / ".local-secrets" / "opc-deploy.env"', source)

    def test_initial_admin_credentials_are_required_only_for_an_empty_database(self):
        self.assertIsNone(require_initial_admin_credentials({}, existing_admin_count=1))
        with self.assertRaisesRegex(RuntimeError, "OPC_INITIAL_ADMIN_USERNAME is not set"):
            require_initial_admin_credentials({}, existing_admin_count=0)

        credentials = require_initial_admin_credentials(
            {
                "OPC_INITIAL_ADMIN_USERNAME": "test-bootstrap-admin",
                "OPC_INITIAL_ADMIN_PASSWORD": "test-bootstrap-password",
            },
            existing_admin_count=0,
        )
        self.assertEqual("test-bootstrap-admin", credentials.username)
        self.assertTrue(credentials.password_present)

    def test_regular_deploy_does_not_require_initial_admin_credentials_before_ssh(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        main = source[source.index("def main():"):source.index('if __name__ == "__main__":')]
        deploy = source[source.index("def deploy(client):"):source.index("def deploy_frontend(client):")]

        self.assertNotIn('require_secret_environment(os.environ, "OPC_INITIAL_ADMIN_USERNAME")', main)
        self.assertNotIn('require_secret_environment(os.environ, "OPC_INITIAL_ADMIN_PASSWORD")', main)
        self.assertIn("require_initial_admin_credentials(os.environ, existing_admin_count)", deploy)
        self.assertIn("if initial_admin_credentials is not None:", deploy)

    def test_temporary_probe_admin_is_cleaned_in_finally_without_owner_login(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        deploy = source[source.index("def deploy(client):"):source.index("def deploy_frontend(client):")]

        prepare = deploy.index("temporary_probe_admin = prepare_temporary_probe_admin(stamp)")
        create = deploy.index(
            "temporary_probe_admin = create_temporary_probe_admin(client, temporary_probe_admin)")
        self.assertLess(prepare, create)
        self.assertIn("finally:", deploy)
        finally_body = deploy[deploy.rindex("finally:"):]
        self.assertIn("cleanup_temporary_probe_admin", finally_body)
        self.assertNotIn('os.environ["OPC_INITIAL_ADMIN_PASSWORD"]', deploy)
        self.assertNotIn("Initial administrator login failed", deploy)

    def test_temporary_probe_admin_cleanup_failure_stays_inside_rollback_guard(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        deploy = source[source.index("def deploy(client):"):source.index("def deploy_frontend(client):")]
        guarded_body = deploy[:deploy.index("    except Exception as error:\n        primary_error = error")]

        self.assertIn("cleanup_temporary_probe_admin(client, temporary_probe_admin)", guarded_body)
        self.assertIn("temporary_probe_admin = None", guarded_body)

    def test_temporary_probe_admin_username_respects_database_column_limit(self):
        schema = (ROOT / "opc-backend" / "src" / "main" / "resources" / "db" / "schema.sql").read_text(
            encoding="utf-8"
        )
        username_limit = int(re.search(
            r"CREATE TABLE IF NOT EXISTS admin_accounts[\s\S]*?username VARCHAR\((\d+)\)",
            schema,
        ).group(1))
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)

        with mock.patch.object(
            deploy_module,
            "database_command",
            side_effect=[(0, "", ""), (0, "123", "")],
        ):
            temporary_admin = deploy_module.create_temporary_probe_admin(
                object(), "20260726-011500"
            )

        self.assertLessEqual(len(temporary_admin.username), username_limit)

    def test_partial_probe_admin_creation_can_be_cleaned_by_exact_identity(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        identity = deploy_module.prepare_temporary_probe_admin("20260726-021500")
        sql_calls = []

        def fake_database_command(_client, sql):
            sql_calls.append(sql)
            if len(sql_calls) == 1:
                return 0, "", ""
            if len(sql_calls) == 2:
                raise RuntimeError("record lookup failed")
            return 0, "", ""

        with mock.patch.object(deploy_module, "database_command", side_effect=fake_database_command):
            with self.assertRaisesRegex(RuntimeError, "record lookup failed"):
                deploy_module.create_temporary_probe_admin(object(), identity)
            deploy_module.cleanup_temporary_probe_admin(object(), identity)

        cleanup_sql = sql_calls[-1]
        self.assertIn(f"username='{identity.username}'", cleanup_sql)
        self.assertNotIn("LIKE", cleanup_sql.upper())
        self.assertNotIn("qa_admin_%", cleanup_sql)

    def test_probe_admin_session_creation_failure_still_cleans_exact_account(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        identity = deploy_module.prepare_temporary_probe_admin("20260726-022000")
        sql_calls = []

        def fake_database_command(_client, sql):
            sql_calls.append(sql)
            if len(sql_calls) == 1:
                raise RuntimeError("probe session creation failed")
            return 0, "", ""

        with mock.patch.object(deploy_module, "database_command", side_effect=fake_database_command):
            with self.assertRaisesRegex(RuntimeError, "probe session creation failed"):
                deploy_module.create_temporary_probe_admin(object(), identity)
            deploy_module.cleanup_temporary_probe_admin(object(), identity)

        cleanup_sql = sql_calls[-1]
        self.assertIn(f"username='{identity.username}'", cleanup_sql)
        self.assertIn(f"password_hash='{identity.password_hash}'", cleanup_sql)
        self.assertNotIn("LIKE", cleanup_sql.upper())

    def test_probe_admin_cleanup_is_idempotent_and_uses_exact_identity(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        identity = deploy_module.prepare_temporary_probe_admin("20260726-022500")
        sql_calls = []

        with mock.patch.object(
            deploy_module,
            "database_command",
            side_effect=lambda _client, sql: sql_calls.append(sql) or (0, "", ""),
        ):
            deploy_module.cleanup_temporary_probe_admin(object(), identity)
            deploy_module.cleanup_temporary_probe_admin(object(), identity)

        self.assertEqual(2, len(sql_calls))
        self.assertEqual(sql_calls[0], sql_calls[1])
        self.assertNotIn("LIKE", sql_calls[0].upper())
        self.assertNotIn("qa_admin_%", sql_calls[0])

    def test_probe_cleanup_failure_preserves_original_deployment_error(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        primary = RuntimeError("original deployment failure")
        cleanup = RuntimeError("sanitized cleanup failure")

        deploy_module.raise_probe_cleanup_failure_if_needed(primary, cleanup)

        self.assertEqual("original deployment failure", str(primary))
        self.assertTrue(any("cleanup" in note.lower() for note in primary.__notes__))
        self.assertNotIn("sanitized cleanup failure", " ".join(primary.__notes__))

    def test_probe_cleanup_failure_is_reported_after_success(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)

        with self.assertRaisesRegex(RuntimeError, "sanitized cleanup failure"):
            deploy_module.raise_probe_cleanup_failure_if_needed(
                None,
                RuntimeError("sanitized cleanup failure"),
            )

    def test_successful_probe_cleanup_restores_admin_count(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)

        with mock.patch.object(
            deploy_module,
            "select_existing_admin_count",
            side_effect=(7, 7),
        ):
            before = deploy_module.select_existing_admin_count(object())
            deploy_module.assert_probe_admin_count_restored(object(), before)

        with mock.patch.object(
            deploy_module,
            "select_existing_admin_count",
            return_value=8,
        ):
            with self.assertRaisesRegex(RuntimeError, "administrator count was not restored"):
                deploy_module.assert_probe_admin_count_restored(object(), 7)

    def test_secret_environment_requirement_fails_closed_without_echoing_value(self):
        with self.assertRaisesRegex(RuntimeError, "OPC_SSH_PASSWORD is not set"):
            require_secret_environment({}, "OPC_SSH_PASSWORD")
        with self.assertRaisesRegex(RuntimeError, "OPC_SSH_PASSWORD is not set"):
            require_secret_environment({"OPC_SSH_PASSWORD": "   "}, "OPC_SSH_PASSWORD")

        secret = "test-only-secret-value"
        self.assertEqual(
            secret,
            require_secret_environment({"OPC_SSH_PASSWORD": secret}, "OPC_SSH_PASSWORD"),
        )

    def test_cursor_hmac_secret_is_required_strong_and_environment_file_safe(self):
        name = "OPC_ASSISTANT_CURSOR_HMAC_SECRET"
        with self.assertRaisesRegex(RuntimeError, f"{name} is not set"):
            require_cursor_hmac_secret_environment({})
        with self.assertRaisesRegex(RuntimeError, "at least 32 characters"):
            require_cursor_hmac_secret_environment({name: "short"})
        with self.assertRaisesRegex(RuntimeError, "environment-file-safe"):
            require_cursor_hmac_secret_environment({name: "x" * 31 + "\n"})

        secret = "cursor-test-secret-0123456789abcdef"
        self.assertEqual(secret, require_cursor_hmac_secret_environment({name: secret}))

    def test_deployment_installs_cursor_secret_without_command_interpolation(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        self.assertIn("require_cursor_hmac_secret_environment(os.environ)", source)
        self.assertIn("stdin_text=cursor_secret + \"\\n\"", source)
        self.assertIn("existing_cursor_secret=$(awk -F=", source)
        self.assertIn('if test -n "$existing_cursor_secret"', source)
        self.assertNotIn("!/^OPC_ASSISTANT_CURSOR_HMAC_SECRET=/", source)
        self.assertIn("printf 'OPC_ASSISTANT_CURSOR_HMAC_SECRET=%s\\n'", source)
        self.assertIn("exit 0", source)
        self.assertIn("chown root:opc /etc/opc-backend.env", source)
        self.assertIn("chmod 0640 /etc/opc-backend.env", source)
        self.assertIn('test "${#existing_cursor_secret}" -ge 32', source)
        self.assertIn("/proc/$backend_pid/environ", source)

    def test_deployment_reconnects_after_restart_and_before_rollback(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        deploy = source[source.index("def deploy(client):"):source.index("def deploy_frontend(client):")]
        restart = deploy.index('run(client, "systemctl restart opc-backend.service")')
        runtime_check = deploy.index('backend_pid=$(systemctl show -p MainPID', restart)
        exception_guard = deploy.index("    except Exception", runtime_check)
        rollback = deploy.index("            run_rollback_preserving_primary(", exception_guard)

        self.assertIn("client = reconnect_ssh_client(client)", deploy[restart:runtime_check])
        recovery = deploy[exception_guard:rollback]
        self.assertIn("lambda: reconnect_ssh_client(client)", recovery)
        self.assertIn("if reconnected:", recovery)
        self.assertIn("client = recovery_client", recovery)
        self.assertIn("if client is not initial_client:", deploy[rollback:])

    def test_rollback_failure_is_attached_without_replacing_or_leaking_primary_error(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        primary_error = RuntimeError("original deployment failure")
        secret_output = "rollback stderr with password-test-secret"

        with mock.patch.object(
            deploy_module,
            "run",
            return_value=(23, "", secret_output),
        ):
            returned_error = deploy_module.run_rollback_preserving_primary(
                object(),
                "rollback command with session-token-test-secret",
                primary_error,
            )

        self.assertIs(primary_error, returned_error)
        self.assertEqual("original deployment failure", str(returned_error))
        notes = " ".join(getattr(returned_error, "__notes__", ()))
        self.assertIn("rollback failed", notes.lower())
        self.assertNotIn("password-test-secret", notes)
        self.assertNotIn("session-token-test-secret", notes)

        transport_error = RuntimeError("transport leaked-cookie-test-secret")
        second_primary = ValueError("second original deployment failure")
        with mock.patch.object(deploy_module, "run", side_effect=transport_error):
            returned_second = deploy_module.run_rollback_preserving_primary(
                object(), "rollback command", second_primary
            )

        self.assertIs(second_primary, returned_second)
        second_notes = " ".join(getattr(returned_second, "__notes__", ()))
        self.assertIn("rollback failed", second_notes.lower())
        self.assertNotIn("leaked-cookie-test-secret", second_notes)

    def test_recovery_stage_failures_keep_the_original_deployment_error(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        primary = ValueError("original deployment failure")

        for stage in (
            "SSH reconnect",
            "emergency Agent disable",
            "release rollback",
            "system account cleanup",
            "temporary administrator cleanup",
        ):
            def fail(stage_name=stage):
                raise RuntimeError(f"{stage_name} leaked-session-token-test-secret")

            succeeded, result = deploy_module.run_recovery_step_preserving_primary(
                primary,
                f"{stage} failed after the original deployment error",
                fail,
            )
            self.assertFalse(succeeded)
            self.assertIsNone(result)

        self.assertIsInstance(primary, ValueError)
        self.assertEqual("original deployment failure", str(primary))
        notes = " ".join(primary.__notes__)
        for stage in (
            "SSH reconnect",
            "emergency Agent disable",
            "release rollback",
            "system account cleanup",
            "temporary administrator cleanup",
        ):
            self.assertIn(stage, notes)
        self.assertNotIn("leaked-session-token-test-secret", notes)

    def test_cursor_runtime_check_does_not_send_control_characters_over_ssh(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        tree = ast.parse(source)
        commands = [
            node.value
            for node in ast.walk(tree)
            if isinstance(node, ast.Constant)
            and isinstance(node.value, str)
            and "/proc/$backend_pid/environ" in node.value
        ]

        self.assertEqual(1, len(commands))
        self.assertNotIn("\0", commands[0])
        self.assertIn(r"tr '\0' '\n'", commands[0])

    def test_agent_probe_record_requires_real_metadata_and_authorized_citations(self):
        valid = {
            "status": "completed",
            "provider": "deepseek",
            "model": "deepseek-chat",
            "prompt_version": "agent-research-v2",
            "finish_reason": "stop",
            "internal_request_id": "internal-123",
            "provider_request_id": "not_provided",
            "prompt_tokens": 120,
            "completion_tokens": 30,
            "total_tokens": 150,
            "latency_ms": 450,
            "model_rounds": 2,
            "provider_call_count": 2,
            "tool_call_count": 1,
            "completed_tool_count": 1,
            "citation_count": 1,
            "unknown_citation_count": 0,
        }
        validate_agent_probe_record(valid, max_model_rounds=4, max_tool_calls=6)

        invalid_records = [
            {**valid, "internal_request_id": ""},
            {**valid, "prompt_tokens": 0},
            {**valid, "total_tokens": 149},
            {**valid, "model_rounds": 5},
            {**valid, "provider_call_count": 1},
            {**valid, "unknown_citation_count": 1},
            {**valid, "prompt_version": "agent-research-v1"},
            {**valid, "api_key": "sk-should-never-appear"},
        ]
        for record in invalid_records:
            with self.subTest(record=record), self.assertRaises(ValueError):
                validate_agent_probe_record(record, max_model_rounds=4, max_tool_calls=6)

    def test_agent_evidence_probe_requires_available_case_and_policy(self):
        valid = {
            "runId": 30,
            "status": "completed",
            "items": [
                {
                    "itemType": "case", "itemId": 11, "sourceId": 101,
                    "title": "Wuhan AI case", "available": True,
                },
                {
                    "itemType": "policy", "itemId": 12, "sourceId": 102,
                    "title": "Hubei AI policy", "available": True,
                },
            ],
            "groups": {"case": 1, "policy": 1, "source": 0},
        }
        validate_agent_evidence_probe(valid, expected_run_id=30)

        for invalid in (
            {**valid, "runId": 31},
            {**valid, "items": valid["items"][1:], "groups": {"case": 0, "policy": 1}},
            {**valid, "items": [{**valid["items"][0], "available": False}, valid["items"][1]]},
        ):
            with self.subTest(invalid=invalid), self.assertRaisesRegex(ValueError, "evidence probe"):
                validate_agent_evidence_probe(invalid, expected_run_id=30)

    def test_production_agent_probe_requests_mixed_research_and_reads_evidence_api(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        deploy = source[source.index("def deploy(client):"):source.index("def deploy_frontend(client):")]
        agent_start = deploy[deploy.index("agent_start_payload = {"):deploy.index("_, agent_start_body = request_json(")]

        self.assertIn("湖北省及武汉市", deploy)
        self.assertIn("人工智能创业案例和适用政策", deploy)
        self.assertIn("优先行动建议", deploy)
        self.assertIn('"industryTagId": resolved_industry_tag_id', agent_start)
        self.assertIn("/runs/{agent_run_id}/evidence", deploy)
        self.assertIn("validate_agent_evidence_probe", deploy)

    def test_agent_runtime_postcheck_reports_missing_and_unexpected_indexes(self):
        validate_agent_runtime_postcheck("4\t21\t10\t7\t8\t\t\t0")

        with self.assertRaisesRegex(
            ValueError,
            r"missing=.*idx_agent_messages_run.*unexpected=.*idx_agent_messages_extra",
        ):
            validate_agent_runtime_postcheck(
                "4\t21\t10\t7\t7\tai_agent_messages.idx_agent_messages_run\t"
                "ai_agent_messages.idx_agent_messages_extra\t0"
            )

    def test_agent_runtime_postcheck_allows_known_forward_workspace_indexes(self):
        validate_agent_runtime_postcheck(
            "4\t21\t10\t7\t8\t\t"
            "ai_agent_sessions.idx_agent_sessions_history_active,"
            "ai_agent_sessions.idx_agent_sessions_history_archived,"
            "ai_agent_sessions.idx_agent_sessions_purge_due\t0"
        )

    def test_assistant_workspace_postcheck_requires_columns_indexes_and_clean_backfills(self):
        validate_assistant_workspace_postcheck("6\t5\t3\t2\t1\t9\t2\t0\t1\t0\t0\t\t\t0")

        with self.assertRaisesRegex(ValueError, "assistant workspace"):
            validate_assistant_workspace_postcheck(
                "6\t4\t2\t2\t1\t9\t2\t0\t0\t1\t1\tidx_agent_sessions_history_active\t\t0"
            )

    def test_assistant_workspace_postcheck_rejects_invalid_index_definitions(self):
        invalid = (
            "ai_agent_messages.uk_agent_message_sequence"
            "(non_unique=1;columns=sequence_no,session_id)"
        )

        with self.assertRaisesRegex(ValueError, r"invalid_index_definitions=.*uk_agent_message_sequence"):
            validate_assistant_workspace_postcheck(
                f"6\t5\t3\t2\t1\t9\t2\t0\t1\t0\t0\t\t{invalid}\t0"
            )

    def test_assistant_workspace_postcheck_rejects_unrecovered_historic_auto_titles(self):
        with self.assertRaisesRegex(ValueError, "historic_auto_titles=1"):
            validate_assistant_workspace_postcheck("6\t5\t3\t2\t1\t9\t2\t0\t1\t0\t0\t\t\t1")

    def test_assistant_workspace_postcheck_rejects_purge_audit_foreign_keys(self):
        with self.assertRaisesRegex(ValueError, "purge_audit_foreign_keys=1"):
            validate_assistant_workspace_postcheck("6\t5\t3\t2\t1\t9\t2\t1\t1\t0\t0\t\t\t0")

    def test_assistant_history_revision_postcheck_requires_exact_safe_definition(self):
        validate_assistant_history_revision_postcheck("1\t1\t0")

        for output in ("0\t0\t0", "1\t0\t0", "1\t1\t1"):
            with self.subTest(output=output), self.assertRaisesRegex(
                ValueError, "assistant history revision"
            ):
                validate_assistant_history_revision_postcheck(output)

    def test_atomic_start_replay_requires_the_same_session_message_and_run(self):
        first = {
            "session": {"sessionId": 10},
            "messageId": 20,
            "runId": 30,
            "status": "received",
        }
        validate_atomic_start_replay(first, dict(first))

        for replay in (
            {**first, "session": {"sessionId": 11}},
            {**first, "messageId": 21},
            {**first, "runId": 31},
        ):
            with self.subTest(replay=replay), self.assertRaisesRegex(ValueError, "atomic start replay"):
                validate_atomic_start_replay(first, replay)

    def test_cursor_second_page_requires_complete_disjoint_pages(self):
        first = {
            "items": [{"sessionId": value} for value in range(55, 5, -1)],
            "nextCursor": "cursor-50",
            "hasMore": True,
        }
        second = {
            "items": [{"sessionId": value} for value in range(5, 0, -1)],
            "nextCursor": None,
            "hasMore": False,
        }
        validate_cursor_second_page(
            first, second, id_field="sessionId", cursor_field="nextCursor", expected_total=55
        )

        with self.assertRaisesRegex(ValueError, "cursor pagination"):
            validate_cursor_second_page(
                first,
                {**second, "items": [{"sessionId": 55}] + second["items"]},
                id_field="sessionId",
                cursor_field="nextCursor",
                expected_total=56,
            )

    def test_history_cursor_stale_probe_requires_controlled_diagnostic(self):
        validate_history_cursor_stale_response({
            "code": 409,
            "message": "history changed",
            "data": {"diagnosticCode": "HISTORY_CURSOR_STALE"},
        })

        for response in (
            {"code": 500, "data": {}},
            {"code": 409, "data": {}},
            {"code": 409, "data": {"diagnosticCode": "OTHER"}},
        ):
            with self.subTest(response=response), self.assertRaisesRegex(
                ValueError, "history cursor stale"
            ):
                validate_history_cursor_stale_response(response)

    def test_production_history_probe_reports_only_safe_pagination_diagnostics(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = source[source.index("def deploy(client):"):source.index("def deploy_frontend(client):")]

        self.assertIn("response_code={first_history_body.get('code')}", body)
        self.assertIn("item_count={len(first_history.get('items') or [])}", body)
        self.assertIn("has_more={first_history.get('hasMore')}", body)
        self.assertIn("cursor_present={bool(first_history.get('nextCursor'))}", body)

    def test_purge_barrier_requires_scrubbed_content_and_advanced_generation(self):
        valid = {
            "purged_at": "2026-07-25 22:30:00.000000",
            "session_generation": 1,
            "run_generation": 0,
            "readable_messages": 0,
            "readable_tools": 0,
            "readable_run_results": 0,
            "generation_matches": 0,
        }
        validate_purge_barrier_record(valid)

        for invalid in (
            {**valid, "purged_at": ""},
            {**valid, "session_generation": 0},
            {**valid, "readable_messages": 1},
            {**valid, "generation_matches": 1},
        ):
            with self.subTest(invalid=invalid), self.assertRaisesRegex(ValueError, "purge barrier"):
                validate_purge_barrier_record(invalid)

    def test_agent_runtime_stabilization_migration_runs_before_postcheck(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = deploy[deploy.index("def deploy(client):"):deploy.index("def deploy_frontend(client):")]

        self.assertIn("20260725_agent_runtime_stabilization.sql", deploy)
        self.assertIn("agent-runtime-stabilization.sql", body)
        self.assertLess(
            body.index("agent-runtime-stabilization.sql'"),
            body.index("agent-runtime-postcheck.sql'"),
        )

    def test_listener_check_accepts_native_and_ipv4_mapped_loopback_addresses(self):
        for listener in (
            "127.0.0.1:8082",
            "[::1]:8082",
            "[::ffff:127.0.0.1]:8082",
        ):
            with self.subTest(listener=listener):
                self.assertTrue(is_loopback_listener(listener, expected_port=8082))

    def test_listener_check_rejects_wildcard_and_external_addresses(self):
        for listener in (
            "*:8082",
            "0.0.0.0:8082",
            "[::]:8082",
            "39.105.25.189:8082",
            "127.0.0.1:18082",
            "",
        ):
            with self.subTest(listener=listener):
                self.assertFalse(is_loopback_listener(listener, expected_port=8082))

    def test_backend_address_can_be_restricted_by_production_environment(self):
        config = APPLICATION_CONFIG.read_text(encoding="utf-8")

        self.assertRegex(
            config,
            r"(?m)^server:\s*\n\s+address:\s+\$\{SERVER_ADDRESS:0\.0\.0\.0\}\s*$",
        )

    def test_case_analysis_has_a_bounded_exact_match_proxy_on_both_hosts(self):
        config = NGINX_CONFIG.read_text(encoding="utf-8")
        locations = re.findall(
            r"location = /api/ai/case-analysis \{(?P<body>.*?)\n\s*\}",
            config,
            flags=re.DOTALL,
        )

        self.assertIn(
            "limit_req_zone $binary_remote_addr zone=opc_ai_case:10m rate=20r/m;",
            config,
        )
        self.assertEqual(2, len(locations))
        for location in locations:
            self.assertIn("limit_req zone=opc_ai_case burst=5 nodelay;", location)
            self.assertIn("client_max_body_size 64k;", location)
            self.assertIn("proxy_connect_timeout 5s;", location)
            self.assertIn("proxy_send_timeout 15s;", location)
            self.assertIn("proxy_read_timeout 190s;", location)
            self.assertIn("proxy_cache off;", location)
            self.assertNotIn("proxy_buffering off", location)

    def test_entrepreneurship_advisor_has_the_same_bounded_proxy_on_both_hosts(self):
        config = NGINX_CONFIG.read_text(encoding="utf-8")
        locations = re.findall(
            r"location = /api/ai/entrepreneurship-advice \{(?P<body>.*?)\n\s*\}",
            config,
            flags=re.DOTALL,
        )

        self.assertEqual(2, len(locations))
        for location in locations:
            self.assertIn("limit_req zone=opc_ai_case burst=5 nodelay;", location)
            self.assertIn("client_max_body_size 64k;", location)
            self.assertIn("proxy_connect_timeout 5s;", location)
            self.assertIn("proxy_send_timeout 15s;", location)
            self.assertIn("proxy_read_timeout 190s;", location)
            self.assertIn("proxy_cache off;", location)
            self.assertNotIn("proxy_buffering off", location)

    def test_paid_industry_resolution_has_a_rate_limited_exact_proxy_on_both_hosts(self):
        config = NGINX_CONFIG.read_text(encoding="utf-8")
        locations = re.findall(
            r"location = /api/ai/industry-resolution \{(?P<body>.*?)\n\s*\}",
            config,
            flags=re.DOTALL,
        )

        self.assertEqual(2, len(locations))
        for location in locations:
            self.assertIn("limit_req zone=opc_ai_case burst=3 nodelay;", location)
            self.assertIn("client_max_body_size 16k;", location)
            self.assertIn("proxy_connect_timeout 5s;", location)
            self.assertIn("proxy_read_timeout 60s;", location)

    def test_backend_systemd_unit_runs_as_a_restricted_service_account(self):
        unit = SYSTEMD_UNIT.read_text(encoding="utf-8")

        for directive in (
            "User=opc",
            "Group=opc",
            "WorkingDirectory=/opt/opc",
            "NoNewPrivileges=true",
            "PrivateTmp=true",
            "ProtectSystem=strict",
            "ProtectHome=true",
            "PrivateDevices=true",
            "RestrictSUIDSGID=true",
            "LockPersonality=true",
            "CapabilityBoundingSet=",
            "AmbientCapabilities=",
            "RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6",
        ):
            self.assertIn(directive, unit)
        self.assertNotIn("MemoryDenyWriteExecute", unit)

    def test_frontend_and_backend_are_served_from_one_atomic_release_link(self):
        nginx = NGINX_CONFIG.read_text(encoding="utf-8")
        unit = SYSTEMD_UNIT.read_text(encoding="utf-8")
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")

        self.assertEqual(3, nginx.count("root /opt/opc/current/frontend;"))
        self.assertIn("-jar /opt/opc/current/opc-backend.jar", unit)
        self.assertIn("mv -Tf", deploy)
        self.assertNotIn("mv /var/www/opc", deploy)
        self.assertIn("test -L", deploy)

    def test_database_mutations_are_inside_the_guarded_deployment_boundary(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = deploy[deploy.index("def deploy(client):"):deploy.index("def deploy_frontend(client):")]

        backup_position = body.index("mysqldump --single-transaction")
        guarded_position = body.index("    try:")
        first_migration_position = body.index("admin-registration.sql'")
        self.assertLess(backup_position, guarded_position)
        self.assertGreater(first_migration_position, guarded_position)
        self.assertIn("opc_platform.sql.gz", body)

        for migration_name in (
            "20260719_admin_registration.sql",
            "20260724_ai_phase_one.sql",
            "20260724_ai_model_catalog.sql",
            "20260724_ai_stabilization.sql",
        ):
            migration = (ROOT / "deploy" / "sql" / migration_name).read_text(encoding="utf-8")
            self.assertNotRegex(migration, r"(?im)^\s*(DROP|TRUNCATE|DELETE)\b")

    def test_ssh_fingerprint_is_checked_before_password_authentication(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        self.assertNotIn("paramiko.AutoAddPolicy", deploy)
        self.assertLess(
            deploy.index("actual = hashlib.sha256(key.asbytes()).hexdigest()"),
            deploy.index("client.connect("),
        )
        self.assertIn("phase-one-finalization.sql", deploy)

    def test_real_assistant_probe_is_a_hard_release_gate(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = deploy[deploy.index("def deploy(client):"):deploy.index("def deploy_frontend(client):")]

        self.assertIn('if advice_body.get("code") != 200:', body)
        self.assertIn('advice_data.get("summary") or advice_data.get("recommendedDirection")', body)
        self.assertIn("FROM ai_analysis_runs", body)
        self.assertIn('analysis_run_status', body)
        self.assertIn('analysis_run_finish_reason', body)
        self.assertIn('analysis_run_total_tokens', body)
        self.assertIn("response_code={advice_body.get('code')}", body)
        self.assertIn("DELETE FROM ai_analysis_runs", body)

    def test_real_provider_candidate_gate_runs_before_current_release_switch(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = deploy[deploy.index("def deploy(client):"):deploy.index("def deploy_frontend(client):")]

        candidate_start = body.index("start_candidate_runtime(")
        provider_connection = body.index("test_candidate_provider_connection(")
        agent_probe = body.index("run_candidate_agent_v2_probe(")
        release_switch = body.index("ln -sfn '{release}' '{current_link}.next.{stamp}'")

        self.assertLess(candidate_start, provider_connection)
        self.assertLess(provider_connection, agent_probe)
        self.assertLess(agent_probe, release_switch)

    def test_candidate_failure_does_not_roll_back_or_restart_the_current_release(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = deploy[deploy.index("def deploy(client):"):deploy.index("def deploy_frontend(client):")]
        exception_start = body.index("    except Exception as error:\n        primary_error = error")
        recovery = body[exception_start:body.index("    finally:", exception_start)]

        self.assertIn("if release_switched:", recovery)
        self.assertNotIn("if mutated:\n", recovery)
        self.assertLess(body.index("run_candidate_agent_v2_probe("), body.index("release_switched = True"))

    def test_provider_connection_is_not_repeated_after_release_switch(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = deploy[deploy.index("def deploy(client):"):deploy.index("def deploy_frontend(client):")]
        release_switch = body.index("release_switched = True")

        self.assertIn("test_candidate_provider_connection(", body[:release_switch])
        self.assertNotIn("test-connection", body[release_switch:])

    def test_candidate_request_uses_stdin_for_unicode_payload_and_secret_headers(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        secret = "candidate-session-secret-never-log"

        with mock.patch.object(
            deploy_module,
            "run",
            return_value=(0, '{"code":200,"data":{"status":"ok"}}\n200', ""),
        ) as remote_run:
            status, body = deploy_module.remote_request_json(
                object(),
                "http://127.0.0.1:18082/api/ai/research/sessions/start",
                method="POST",
                payload={"content": "武汉人工智能研究"},
                headers={"Authorization": f"Bearer {secret}"},
                expected_code=200,
            )

        self.assertEqual(200, status)
        self.assertEqual("ok", body["data"]["status"])
        command = remote_run.call_args.args[1]
        stdin_text = remote_run.call_args.kwargs["stdin_text"]
        self.assertEqual("curl --config -", command)
        self.assertNotIn(secret, command)
        self.assertNotIn("武汉人工智能研究", command)
        self.assertIn(secret, stdin_text)
        self.assertIn("\\u6b66\\u6c49", stdin_text)

    def test_candidate_request_failure_does_not_leak_secret_transport_output(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        secret = "candidate-secret-from-remote-stderr"

        with mock.patch.object(
            deploy_module,
            "run",
            side_effect=RuntimeError(f"curl failed with {secret}"),
        ):
            with self.assertRaises(RuntimeError) as raised:
                deploy_module.remote_request_json(
                    object(),
                    "http://127.0.0.1:18082/api/health",
                    headers={"Authorization": f"Bearer {secret}"},
                )

        self.assertNotIn(secret, str(raised.exception))

    def test_candidate_probe_data_cleanup_is_idempotent_and_follows_fk_order(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        sql_calls = []

        with mock.patch.object(
            deploy_module,
            "database_command",
            side_effect=lambda _client, sql: sql_calls.append(sql) or (0, "0", ""),
        ):
            deploy_module.cleanup_candidate_probe_data(object(), "candidate_20260726_ab12")
            deploy_module.cleanup_candidate_probe_data(object(), "candidate_20260726_ab12")

        self.assertEqual(4, len(sql_calls))
        self.assertEqual(sql_calls[0], sql_calls[2])
        cleanup_sql = sql_calls[0]
        self.assertLess(cleanup_sql.index("DELETE pc FROM ai_agent_provider_calls"),
                        cleanup_sql.index("DELETE r FROM ai_analysis_runs"))
        self.assertLess(cleanup_sql.index("DELETE tc FROM ai_agent_tool_calls"),
                        cleanup_sql.index("DELETE r FROM ai_analysis_runs"))
        self.assertLess(cleanup_sql.index("DELETE r FROM ai_analysis_runs"),
                        cleanup_sql.index("DELETE m FROM ai_agent_messages"))
        self.assertNotIn("LIKE", cleanup_sql.upper())
        self.assertIn("username='candidate_20260726_ab12'", cleanup_sql)

    def test_candidate_probe_reads_the_atomic_start_receipt_shape(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = source[
            source.index("def run_candidate_agent_v2_probe("):
            source.index("def ai_settings_update_payload(")
        ]

        self.assertIn("validate_atomic_start_replay(start_data, start_data)", body)
        self.assertNotIn('start_data.get("sessionId")', body)

    def test_candidate_cleanup_failure_preserves_primary_probe_diagnostic(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        primary = RuntimeError("INVALID_STRUCTURED_RESULT: candidate probe failed")
        cleanup = RuntimeError("cleanup leaked candidate-token-secret")

        deploy_module.raise_candidate_cleanup_failure_if_needed(primary, cleanup)

        self.assertEqual("INVALID_STRUCTURED_RESULT: candidate probe failed", str(primary))
        notes = " ".join(primary.__notes__)
        self.assertIn("cleanup", notes.lower())
        self.assertNotIn("candidate-token-secret", notes)

    def test_candidate_database_snapshot_is_isolated_and_keeps_secret_off_command_line(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)

        with mock.patch.object(
            deploy_module,
            "run",
            return_value=(0, "", ""),
        ) as remote_run:
            candidate = deploy_module.prepare_candidate_database(object(), "20260726-200000")

        command = remote_run.call_args.args[1]
        stdin_text = remote_run.call_args.kwargs["stdin_text"]
        self.assertIn("mysqldump --single-transaction", command)
        self.assertIn("opc_candidate_20260726200000", command)
        self.assertIn("DELETE FROM ai_analysis_runs", command)
        self.assertIn("SPRING_DATASOURCE_URL", command)
        creation_sql = command.split("mysql -uroot <<SQL", 1)[1].split("\nSQL\n", 1)[0]
        self.assertNotIn("`", creation_sql)
        self.assertNotIn(candidate.password, command)
        self.assertIn(candidate.password, stdin_text)
        self.assertTrue(candidate.environment_file.startswith("/run/opc-candidate-"))

    def test_candidate_runtime_uses_the_isolated_environment_file(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        candidate = deploy_module.CandidateDatabase(
            name="opc_candidate_20260726200000",
            username="opc_candidate_20260726200000",
            password="test-only-password",
            environment_file="/run/opc-candidate-20260726200000.env",
        )

        with mock.patch.object(deploy_module, "run", return_value=(0, "", "")) as remote_run:
            deploy_module.start_candidate_runtime(
                object(), "/opt/opc/releases/20260726-200000", "20260726-200000", candidate
            )

        command = remote_run.call_args.args[1]
        self.assertIn("EnvironmentFile=", command)
        self.assertIn(candidate.environment_file, command)
        self.assertIn("--property=WorkingDirectory=/opt/opc", command)
        self.assertNotIn("--working-directory", command)
        self.assertNotIn(candidate.password, command)

    def test_candidate_database_cleanup_is_idempotent_and_exact(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        candidate = deploy_module.CandidateDatabase(
            name="opc_candidate_20260726200000",
            username="opc_candidate_20260726200000",
            password="test-only-password",
            environment_file="/run/opc-candidate-20260726200000.env",
        )

        with mock.patch.object(deploy_module, "run", return_value=(0, "", "")) as remote_run:
            deploy_module.cleanup_candidate_database(object(), candidate)
            deploy_module.cleanup_candidate_database(object(), candidate)

        self.assertEqual(2, remote_run.call_count)
        for call in remote_run.call_args_list:
            command = call.args[1]
            self.assertIn("DROP DATABASE IF EXISTS `opc_candidate_20260726200000`", command)
            self.assertIn("DROP USER IF EXISTS 'opc_candidate_20260726200000'@'127.0.0.1'", command)
            self.assertNotIn("LIKE", command.upper())
            self.assertNotIn(candidate.password, command)

    def test_agent_runtime_migration_is_prechecked_verified_and_uploaded(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = deploy[deploy.index("def deploy(client):"):deploy.index("def deploy_frontend(client):")]

        for name in (
            "20260725_agent_runtime_precheck.sql",
            "20260725_agent_runtime.sql",
            "20260725_agent_runtime_postcheck.sql",
        ):
            self.assertIn(name, deploy)
        execution = body[body.index("agent_precheck_output"):body.index("if ! grep -q '^OPC_AI_SETTINGS_MASTER_KEY='")]
        self.assertLess(execution.index("agent-runtime-precheck.sql"), execution.index("agent-runtime.sql'"))
        self.assertLess(execution.index("agent-runtime.sql'"), execution.index("agent-runtime-postcheck.sql"))
        self.assertIn("Agent Runtime database postcheck failed", body)

    def test_assistant_workspace_migration_runs_after_agent_runtime_and_before_service_restart(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = deploy[deploy.index("def deploy(client):"):deploy.index("def deploy_frontend(client):")]

        for name in (
            "20260725_assistant_workspace_precheck.sql",
            "20260725_assistant_workspace.sql",
            "20260725_assistant_workspace_stabilization.sql",
            "20260725_assistant_workspace_postcheck.sql",
        ):
            self.assertIn(name, deploy)
        self.assertLess(body.index("agent-runtime-postcheck.sql'"), body.index("assistant-workspace-precheck.sql'"))
        self.assertLess(body.index("assistant-workspace-precheck.sql'"), body.index("assistant-workspace.sql'"))
        self.assertLess(body.index("assistant-workspace.sql'"), body.index("assistant-workspace-stabilization.sql'"))
        self.assertLess(
            body.index("assistant-workspace-stabilization.sql'"),
            body.index("assistant-workspace-postcheck.sql'"),
        )
        marker_position = body.index("migration.assistant_workspace_rollout_at")
        self.assertLess(marker_position, body.index("assistant-workspace.sql'"))
        self.assertIn("2026-07-25 21:56:34.000000", body)
        self.assertIn("len(assistant_precheck_fields) != 5", body)
        self.assertIn("existing_purge_audit_tables = int(assistant_precheck_fields[4])", body)
        self.assertIn("Assistant workspace database postcheck failed", body)

    def test_assistant_history_revision_migration_is_prechecked_and_postchecked(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = source[source.index("def deploy(client):"):source.index("def deploy_frontend(client):")]

        for name in (
            "20260726_assistant_history_revision_precheck.sql",
            "20260726_assistant_history_revision.sql",
            "20260726_assistant_history_revision_postcheck.sql",
        ):
            self.assertIn(name, source)
        precheck = body.index("assistant-history-revision-precheck.sql'")
        migration = body.index("assistant-history-revision.sql'")
        postcheck = body.index("assistant-history-revision-postcheck.sql'")
        self.assertLess(precheck, migration)
        self.assertLess(migration, postcheck)
        self.assertIn("validate_assistant_history_revision_postcheck", body)

    def test_agent_probe_requires_async_completion_tool_citation_and_database_audit(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = deploy[deploy.index("def deploy(client):"):deploy.index("def deploy_frontend(client):")]

        self.assertIn("/api/ai/research/sessions", body)
        self.assertIn("/api/ai/research/sessions/start", body)
        self.assertIn("validate_atomic_start_replay(", body)
        self.assertIn("validate_cursor_second_page(", body)
        self.assertIn("validate_purge_barrier_record(", body)
        self.assertIn("range(1, 56)", body)
        self.assertIn("beforeSequence=", body)
        self.assertIn("/permanent", body)
        self.assertIn("/messages", body)
        self.assertIn("expected_code=202", body)
        self.assertIn("/api/ai/research/runs/", body)
        self.assertIn("/sessions/history?scope=active", body)
        self.assertIn("/messages?limit=50", body)
        self.assertIn("/api/ai/research/usage", body)
        self.assertIn('method="PATCH"', body)
        self.assertIn('("archive", "archived")', body)
        self.assertIn('("trash", "trash")', body)
        self.assertIn('detail_session.get("titleMode") != "auto"', body)
        self.assertIn('latest_run.get("runId")', body)
        self.assertIn('agent_run_data.get("status") != "completed"', body)
        self.assertIn('agent_run_data.get("toolCallCount", 0) < 1', body)
        self.assertIn('len(agent_run_data.get("citations") or []) < 1', body)
        self.assertIn("FROM ai_agent_tool_calls", body)
        self.assertIn("FROM ai_agent_provider_calls", body)
        self.assertIn("unknown_citation_count", body)
        self.assertIn("validate_agent_probe_record", body)
        self.assertIn("/api/admin/ai-agent-runs/", body)
        self.assertIn("Disabled QA user reached Agent Runtime", body)
        self.assertIn("Ordinary user reached administrator Agent audit", body)
        self.assertIn("DELETE FROM ai_agent_sessions", body)


if __name__ == "__main__":
    unittest.main()
