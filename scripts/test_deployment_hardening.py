import ast
import re
import hmac
import contextlib
import importlib.util
import io
import json
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import scripts.deployment_hardening as deployment_hardening
from scripts.deployment_hardening import (
    CandidateReleaseGate,
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

APPLICATION_CONFIG = ROOT / "opc-backend" / "src" / "main" / "resources" / "application.yaml"
NGINX_CONFIG = ROOT / "deploy" / "nginx" / "opc.conf"
SYSTEMD_UNIT = ROOT / "deploy" / "systemd" / "opc-backend.service"
DEPLOY_SCRIPT = ROOT / ".codex_deploy_opc.py"


class DeploymentHardeningTest(unittest.TestCase):
    def test_candidate_failure_leaves_all_production_mutation_counts_at_zero(self):
        gate = CandidateReleaseGate()
        migration_hash = "a" * 64

        with self.assertRaisesRegex(RuntimeError, "before the candidate gate"):
            gate.record_production_migration(migration_hash)
        with self.assertRaisesRegex(RuntimeError, "before the candidate gate"):
            gate.record_release_switch()
        with self.assertRaisesRegex(RuntimeError, "before the candidate gate"):
            gate.record_service_restart()

        self.assertEqual(0, gate.production_migration_calls)
        self.assertEqual(0, gate.release_switch_calls)
        self.assertEqual(0, gate.service_restart_calls)

    def test_candidate_success_allows_one_matching_production_migration_and_switch(self):
        gate = CandidateReleaseGate()
        migration_hash = "b" * 64
        gate.mark_candidate_passed(migration_hash)

        gate.record_production_migration(migration_hash)
        gate.record_release_switch()
        gate.record_service_restart()

        self.assertEqual(1, gate.production_migration_calls)
        self.assertEqual(1, gate.release_switch_calls)
        self.assertEqual(1, gate.service_restart_calls)
        with self.assertRaisesRegex(RuntimeError, "only once"):
            gate.record_production_migration(migration_hash)
        with self.assertRaisesRegex(RuntimeError, "hashes differ"):
            CandidateReleaseGate(
                candidate_passed=True,
                candidate_migration_hash=migration_hash,
            ).record_production_migration("c" * 64)

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

    def test_candidate_contract_validation_reason_is_safe_and_stable(self):
        spec = importlib.util.spec_from_file_location(
            "opc_deploy_contract_reason", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)

        self.assertEqual(
            "coverage_audit",
            deploy_module.candidate_contract_validation_reason(
                ValueError("Candidate Agent evidence coverage differs from the authorized evidence snapshot")
            ),
        )
        self.assertEqual(
            "unknown",
            deploy_module.candidate_contract_validation_reason(
                ValueError("provider body must never appear in a candidate report")
            ),
        )

    def test_candidate_evidence_insufficient_failure_keeps_sanitized_metrics(self):
        message = deployment_hardening.candidate_probe_failure_message(
            "CANDIDATE_CASE_COMPARISON_EVIDENCE_INSUFFICIENT",
            {
                "provider": "deepseek",
                "model_rounds": 3,
                "provider_call_count": 3,
                "tool_call_count": 2,
                "total_tokens": 9000,
                "finish_reason": "stop",
                "latency_ms": 21000,
                "release_switched": False,
            },
        )

        self.assertTrue(message.startswith("CANDIDATE_CASE_COMPARISON_EVIDENCE_INSUFFICIENT:"))
        self.assertIn("model_rounds=3", message)
        self.assertIn("tool_call_count=2", message)
        self.assertIn("total_tokens=9000", message)
        self.assertIn("latency_ms=21000", message)
        self.assertIn("release_switched=false", message)

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
            "ai_agent_sessions.idx_agent_sessions_purge_due,"
            "ai_agent_sessions.idx_agent_sessions_task_context_hash\t0"
        )

    def test_agent_runtime_postcheck_allows_but_does_not_require_phase_three_task_context_index(self):
        postcheck = (ROOT / "deploy" / "sql" / "20260725_agent_runtime_postcheck.sql").read_text(
            encoding="utf-8"
        )
        self.assertNotIn(
            "UNION ALL SELECT 'ai_agent_sessions', 'idx_agent_sessions_task_context_hash'",
            postcheck,
        )

        with self.assertRaisesRegex(ValueError, r"unexpected=.*idx_agent_sessions_unknown"):
            validate_agent_runtime_postcheck(
                "4\t21\t10\t7\t8\t\t"
                "ai_agent_sessions.idx_agent_sessions_task_context_hash,"
                "ai_agent_sessions.idx_agent_sessions_unknown\t0"
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
        cleanup = deploy[
            deploy.index("def cleanup_production_probe_data("):
            deploy.index("def run_phase_three_product_probes(")
        ]

        self.assertIn('if advice_body.get("code") != 200:', body)
        self.assertIn('advice_data.get("summary") or advice_data.get("recommendedDirection")', body)
        self.assertIn("FROM ai_analysis_runs", body)
        self.assertIn('analysis_run_status', body)
        self.assertIn('analysis_run_finish_reason', body)
        self.assertIn('analysis_run_total_tokens', body)
        self.assertIn("response_code={advice_body.get('code')}", body)
        self.assertIn("cleanup_production_probe_data(", body)
        self.assertIn("DELETE run FROM ai_analysis_runs", cleanup)

    def test_phase_three_product_probe_is_a_guarded_production_gate_with_peer_idor_identity(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = source[source.index("def deploy(client):"):source.index("def deploy_frontend(client):")]

        owner_identity = body.index("ai_qa_username =")
        peer_identity = body.index("ai_qa_peer_username =")
        peer_session = body.index("ai_qa_peer_token")
        final_message = body.index("phase_three_final_message =")
        product_probe = body.index("phase_three_probe = run_phase_three_product_probes(")
        permanent_purge = body.index("_, final_trash_body = request_json(")
        cleanup = body.index("cleanup_production_probe_data(")

        self.assertLess(owner_identity, peer_identity)
        self.assertLess(peer_identity, peer_session)
        self.assertLess(peer_session, final_message)
        self.assertLess(final_message, product_probe)
        self.assertLess(product_probe, permanent_purge)
        self.assertLess(permanent_purge, cleanup)
        self.assertIn('item.get("runId") == int(agent_run_id)', body)
        self.assertIn('item.get("role") == "assistant"', body)
        inner_finally = body.rfind("finally:", product_probe, cleanup)
        self.assertGreater(inner_finally, product_probe)
        finally_body = body[inner_finally:cleanup + len("cleanup_production_probe_data")]
        self.assertIn("cleanup_production_probe_data", finally_body)
        self.assertIn('"phase_three_probe": phase_three_probe', body)
        self.assertNotIn("DELETE FROM user_sessions WHERE token =", body)

    def test_real_provider_candidate_gate_runs_before_current_release_switch(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = deploy[deploy.index("def deploy(client):"):deploy.index("def deploy_frontend(client):")]

        candidate_start = body.index("start_candidate_runtime(")
        provider_connection = body.index("test_candidate_provider_connection(")
        agent_probe = body.index("run_candidate_agent_v2_scenarios(")
        release_switch = body.index("ln -sfn '{release}' '{current_link}.next.{stamp}'")

        self.assertLess(candidate_start, provider_connection)
        self.assertLess(provider_connection, agent_probe)
        self.assertLess(agent_probe, release_switch)

    def test_candidate_migrations_and_probe_finish_before_production_backup_or_mutation(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = deploy[deploy.index("def deploy(client):"):deploy.index("def deploy_frontend(client):")]

        candidate_migration = body.index("apply_candidate_release_migrations(")
        candidate_probe = body.index("run_candidate_agent_v2_scenarios(")
        candidate_passed = body.index("release_gate.mark_candidate_passed(")
        production_backup = body.index("run(client, backup_command")
        production_migration = body.index("release_gate.record_production_migration(")
        release_switch = body.index("release_gate.record_release_switch()")

        self.assertLess(candidate_migration, candidate_probe)
        self.assertLess(candidate_probe, candidate_passed)
        self.assertLess(candidate_passed, production_backup)
        self.assertLess(production_backup, production_migration)
        self.assertLess(production_migration, release_switch)

    def test_candidate_evidence_insufficient_path_uses_sanitized_failure_record(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        probe = deploy[
            deploy.index("def run_candidate_agent_v2_probe"):
            deploy.index("def ai_settings_update_payload")
        ]

        self.assertIn(
            'diagnostic = f"CANDIDATE_{scenario.upper()}_EVIDENCE_INSUFFICIENT"',
            probe,
        )
        self.assertIn("CandidateProbeFailure(diagnostic, probe_record)", probe)
        self.assertNotIn(
            'raise RuntimeError(f"CANDIDATE_{scenario.upper()}_EVIDENCE_INSUFFICIENT")',
            probe,
        )

    def test_candidate_scenarios_continue_after_one_failure_and_aggregate_diagnostics(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        attempted = []

        def run_probe(client, stamp, settings, candidate_database, scenario):
            attempted.append(scenario)
            if scenario == "case_comparison":
                raise RuntimeError("CANDIDATE_CASE_COMPARISON_REQUIRED_TOOL_MISSING")
            return {"scenario": scenario, "status": "completed", "diagnostic_code": "OK"}

        with mock.patch.object(deploy_module, "run_candidate_agent_v2_probe", side_effect=run_probe):
            with self.assertRaisesRegex(RuntimeError, "CANDIDATE_SCENARIOS_FAILED") as raised:
                deploy_module.run_candidate_agent_v2_scenarios(
                    object(), "20260727-010000", {"provider": "deepseek"}, object()
                )

        self.assertEqual(
            ["policy", "case_comparison", "source_verification"], attempted
        )
        self.assertIn("case_comparison", str(raised.exception))
        self.assertNotIn("provider", str(raised.exception).lower())

    def test_candidate_tool_sequence_failure_preserves_complete_sanitized_record(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script_sequence", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        record = {
            "model_rounds": 4,
            "tool_call_count": 1,
            "completed_tool_count": 1,
            "total_tokens": 20747,
            "finish_reason": "stop",
            "settlement_status": "settled_actual",
            "reserved_tokens": 0,
            "resolved_intent": "case_comparison",
            "model_intent": "case_analysis",
            "terminal_status": "completed",
            "release_switched": False,
        }

        with self.assertRaises(deploy_module.CandidateProbeFailure) as raised:
            deploy_module.validate_candidate_tool_sequence(
                record,
                "case_comparison",
                ("search_cases", "compare_cases"),
                ["search_cases"],
            )

        failure_record = raised.exception.record
        self.assertEqual(["search_cases", "compare_cases"], failure_record["expected_tools"])
        self.assertEqual(["search_cases"], failure_record["actual_tool_sequence"])
        self.assertEqual(["compare_cases"], failure_record["missing_tools"])
        self.assertEqual(["CASE_SEARCH", "CASE_COMPARISON"], failure_record["execution_requirements"])
        self.assertEqual("CANDIDATE_CASE_COMPARISON_TOOL_SEQUENCE_INVALID",
                         failure_record["diagnostic_code"])
        self.assertEqual(20747, failure_record["total_tokens"])
        self.assertFalse(failure_record["release_switched"])

    def test_candidate_collects_tool_sequence_before_terminal_status_gate(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        probe = source[
            source.index("def run_candidate_agent_v2_probe("):
            source.index("def ai_settings_update_payload(")
        ]

        self.assertLess(
            probe.index("tool_sequence_output"),
            probe.index('if run_data.get("status") not in'),
        )

    def test_candidate_parses_safe_tool_diagnostics_before_terminal_status_gate(self):
        spec = importlib.util.spec_from_file_location(
            "opc_deploy_tool_diagnostics", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        output = (
            "step_no\ttool_name\trequest_id\tscope\tquery_present\tcategory_present\trequested_limit\t"
            "returned_count\tdistinct_authorized_case_count\tdistinct_authorized_source_count\t"
            "depends_on\tstatus\n"
            "1\tsearch_cases\trequiredCaseSearch\tselected\t0\t0\t5\t0\t0\t0\t[]\tcompleted\n"
            "2\tsearch_cases\trequiredCaseBroad\tcross_region_reference\t0\t0\t5\t2\t2\t2\t[]\tcompleted\n"
            "3\tcompare_cases\trequiredCaseCompare\t\t0\t0\t\t2\t2\t2\t"
            "[\"requiredCaseBroad\"]\tcompleted\n"
        )

        diagnostics = deploy_module.parse_candidate_tool_diagnostics(output)

        self.assertEqual(3, len(diagnostics))
        self.assertEqual("selected", diagnostics[0]["scope"])
        self.assertFalse(diagnostics[0]["query_present"])
        self.assertEqual(0, diagnostics[0]["returned_count"])
        self.assertEqual(2, diagnostics[1]["distinct_authorized_case_count"])
        self.assertEqual(["requiredCaseBroad"], diagnostics[2]["depends_on"])
        serialized = json.dumps(diagnostics, ensure_ascii=False)
        self.assertNotIn("query text", serialized)
        self.assertNotIn("category text", serialized)

        probe = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        probe = probe[
            probe.index("def run_candidate_agent_v2_probe("):
            probe.index("def ai_settings_update_payload(")
        ]
        self.assertIn('probe_record["tool_diagnostics"]', probe)
        self.assertLess(
            probe.index('probe_record["tool_diagnostics"]'),
            probe.index('if run_data.get("status") not in'),
        )

    def test_failed_candidate_report_keeps_completed_tool_sequence_and_primary_diagnostic(self):
        spec = importlib.util.spec_from_file_location(
            "opc_deploy_failed_probe_record", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        record = {
            "status": "failed",
            "terminal_status": "failed",
            "diagnostic_code": "REQUIRED_TOOL_CHAIN_UNSATISFIED",
            "completed_tool_count": 2,
            "model_rounds": 3,
            "total_tokens": 15679,
            "latency_ms": 41041,
            "release_switched": False,
        }

        deploy_module.record_candidate_tool_sequence(
            record,
            "policy",
            ("search_policies",),
            ("search_policies", "search_cases"),
        )
        record["diagnostic_code"] = "REQUIRED_TOOL_CHAIN_UNSATISFIED"
        failure = deploy_module.CandidateProbeFailure(
            "REQUIRED_TOOL_CHAIN_UNSATISFIED", record)

        self.assertEqual(
            ["search_policies", "search_cases"],
            failure.record["actual_tool_sequence"],
        )
        self.assertEqual(2, failure.record["completed_tool_count"])
        self.assertEqual(
            "REQUIRED_TOOL_CHAIN_UNSATISFIED",
            failure.record["diagnostic_code"],
        )
        self.assertFalse(failure.record["release_switched"])

    def test_candidate_tool_sequence_parser_treats_sql_null_as_empty(self):
        spec = importlib.util.spec_from_file_location(
            "opc_deploy_tool_sequence_parser", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)

        self.assertEqual([], deploy_module.parse_candidate_tool_sequence("NULL\n"))
        self.assertEqual(
            ["search_cases", "compare_cases"],
            deploy_module.parse_candidate_tool_sequence(
                "GROUP_CONCAT(tool_name)\nsearch_cases,compare_cases\n"
            ),
        )

    def test_candidate_starts_each_scenario_with_an_explicit_requested_intent(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        probe = source[
            source.index("def run_candidate_agent_v2_probe("):
            source.index("def ai_settings_update_payload(")
        ]

        self.assertIn('"requested_intent": "policy_lookup"', probe)
        self.assertIn('"requested_intent": "case_comparison"', probe)
        self.assertIn('"requested_intent": "source_verification"', probe)
        self.assertIn('"requestedIntent": scenario_contract["requested_intent"]', probe)
        self.assertIn("仅完成一次来源核验", probe)
        self.assertIn("本次检索返回的来源", probe)
        self.assertIn("一条简短、带该来源引用的结论", probe)

    def test_failed_candidate_release_cleanup_is_exact_and_protects_current(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script_release_cleanup", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)

        with mock.patch.object(deploy_module, "run", return_value=(0, "", "")) as remote_run:
            deploy_module.cleanup_failed_candidate_release(
                object(),
                "/opt/opc/releases/20260727-191500",
                "20260727-191500",
                "/opt/opc/releases/20260726-213258",
            )

        command = remote_run.call_args.args[1]
        self.assertIn("/opt/opc/releases/20260727-191500", command)
        self.assertIn("readlink -f '/opt/opc/current'", command)
        self.assertIn("rm -rf -- '/opt/opc/releases/20260727-191500'", command)
        self.assertNotIn("/opt/opc/releases/20260727-*", command)

        with self.assertRaises(ValueError):
            deploy_module.cleanup_failed_candidate_release(
                object(),
                "/opt/opc/releases/20260726-213258",
                "20260726-213258",
                "/opt/opc/releases/20260726-213258",
            )
        with self.assertRaises(ValueError):
            deploy_module.cleanup_failed_candidate_release(
                object(),
                "/opt/opc/releases/20260727-191500/child",
                "20260727-191500",
                "/opt/opc/releases/20260726-213258",
            )

    def test_candidate_failure_finally_removes_only_this_unswitched_release(self):
        source = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        candidate = source[
            source.index("candidate_database = None"):
            source.index("if candidate_only:")
        ]

        self.assertIn("cleanup_failed_candidate_release(", candidate)
        self.assertIn("if candidate_error is not None and not release_switched:", candidate)
        self.assertIn("Candidate release cleanup also failed", candidate)

    def test_candidate_case_comparison_context_requires_two_eligible_cases_without_hardcoded_ids(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script_context", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        outputs = iter([
            (None, "310\t19\t武汉市\t人工智能\t2\n", None),
            (None, "420\t23\t湖北省\t软件服务\t1\n", None),
        ])

        with mock.patch.object(deploy_module, "candidate_database_command", side_effect=outputs) as command:
            comparison = deploy_module.candidate_research_context(
                object(), "opc_candidate_20260727023000", "case_comparison")
            policy = deploy_module.candidate_research_context(
                object(), "opc_candidate_20260727023000", "policy")

        self.assertEqual(310, comparison["region_id"])
        self.assertEqual(19, comparison["industry_tag_id"])
        self.assertEqual("武汉市", comparison["region_name"])
        self.assertEqual("人工智能", comparison["industry"])
        self.assertEqual(420, policy["region_id"])
        comparison_sql = command.call_args_list[0].args[2]
        self.assertIn("HAVING COUNT(DISTINCT c.id) >= 2", comparison_sql)
        self.assertIn("c.status='published'", comparison_sql)
        self.assertIn("c.ai_evidence_status='verified'", comparison_sql)
        self.assertNotRegex(comparison_sql, r"c\.id\s+IN\s*\([0-9]")

    def test_multiround_token_budget_migration_runs_on_candidate_before_production(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        candidate_migrations = deploy[
            deploy.index("def apply_candidate_release_migrations"):
            deploy.index("def candidate_runtime_unit")
        ]
        production = deploy[
            deploy.index("def deploy(client):"):
            deploy.index("def deploy_frontend(client):")
        ]

        self.assertIn('source("agent-multiround-budget.sql")', candidate_migrations)
        self.assertIn("agent-multiround-budget.sql", production)
        budget_sql = (ROOT / "deploy" / "sql" / "20260727_agent_multiround_budget.sql").read_text(
            encoding="utf-8")
        self.assertIn("SET agent_max_tokens=28000", budget_sql)
        self.assertIn("agent_max_tokens < 28000", budget_sql)
        self.assertIn("agent_max_model_rounds=5", budget_sql)
        self.assertLess(
            production.index("run_candidate_agent_v2_scenarios("),
            production.index("opc_platform < '{release}/agent-multiround-budget.sql'"),
        )

    def test_multiround_migration_adds_and_verifies_requested_intent(self):
        schema = (ROOT / "opc-backend" / "src" / "main" / "resources" / "db" / "schema.sql").read_text(
            encoding="utf-8")
        precheck = (ROOT / "deploy" / "sql" / "20260727_agent_multiround_budget_precheck.sql").read_text(
            encoding="utf-8")
        migration = (ROOT / "deploy" / "sql" / "20260727_agent_multiround_budget.sql").read_text(
            encoding="utf-8")
        postcheck = (ROOT / "deploy" / "sql" / "20260727_agent_multiround_budget_postcheck.sql").read_text(
            encoding="utf-8")
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("requested_intent VARCHAR(40) NOT NULL DEFAULT 'auto'", schema)
        self.assertIn("column_name='requested_intent'", precheck)
        self.assertIn("ADD COLUMN requested_intent VARCHAR(40) NOT NULL DEFAULT ''auto''", migration)
        self.assertIn("column_name='requested_intent'", postcheck)
        self.assertIn('["1\\t1\\t0"]', deploy)
        self.assertIn('["1\\t1"]', deploy)

    def test_candidate_failure_does_not_roll_back_or_restart_the_current_release(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = deploy[deploy.index("def deploy(client):"):deploy.index("def deploy_frontend(client):")]
        exception_start = body.index("    except Exception as error:\n        primary_error = error")
        recovery = body[exception_start:body.index("    finally:", exception_start)]

        self.assertIn("if release_switched:", recovery)
        self.assertNotIn("if mutated:\n", recovery)
        self.assertLess(body.index("run_candidate_agent_v2_scenarios("), body.index("release_switched = True"))

    def test_candidate_provider_connection_recovers_once_after_a_transient_failed_probe(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_provider_retry", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        settings = {"provider": "deepseek", "modelId": "deepseek-v4-flash", "agentEnabled": True}

        with mock.patch.object(
            deploy_module,
            "remote_request_json",
            side_effect=[
                (200, {"code": 200, "data": settings}),
                (200, {"code": 503, "data": {"success": False, "message": "secret"}}),
                (200, {"code": 200, "data": {"success": True}}),
            ],
        ) as request, mock.patch.object(deploy_module.time, "sleep") as sleep:
            returned = deploy_module.test_candidate_provider_connection(object(), {"X-Admin-Token": "secret"})

        self.assertEqual(settings, returned)
        self.assertEqual(3, request.call_count)
        sleep.assert_called_once_with(5)

    def test_candidate_provider_connection_stops_after_one_retry_with_safe_diagnostics(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_provider_retry_failure", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        settings = {"provider": "deepseek", "modelId": "deepseek-v4-flash", "agentEnabled": True}

        with mock.patch.object(
            deploy_module,
            "remote_request_json",
            side_effect=[
                (200, {"code": 200, "data": settings}),
                (200, {"code": 503, "data": {"success": False, "message": "secret"}}),
                (200, {"code": 503, "data": {"success": False, "rawResponse": "secret"}}),
            ],
        ) as request, mock.patch.object(deploy_module.time, "sleep") as sleep:
            with self.assertRaisesRegex(
                RuntimeError,
                r"connectionCode=503; connectionSuccess=false; providerClass=unknown; attempts=2",
            ) as raised:
                deploy_module.test_candidate_provider_connection(object(), {"X-Admin-Token": "secret"})

        self.assertEqual(3, request.call_count)
        sleep.assert_called_once_with(5)
        self.assertNotIn("secret", str(raised.exception))
        self.assertNotIn("rawResponse", str(raised.exception))

    def test_candidate_provider_connection_reports_only_the_safe_rate_limit_class(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_provider_rate_limit", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        settings = {"provider": "deepseek", "modelId": "deepseek-v4-flash", "agentEnabled": True}

        with mock.patch.object(
            deploy_module,
            "remote_request_json",
            side_effect=[
                (200, {"code": 200, "data": settings}),
                (200, {"code": 503, "message": "AI provider request failed (HTTP 429)", "data": None}),
                (200, {"code": 503, "message": "AI provider request failed (HTTP 429)", "data": None}),
            ],
        ), mock.patch.object(deploy_module.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, r"providerClass=rate_limit") as raised:
                deploy_module.test_candidate_provider_connection(object(), {"X-Admin-Token": "secret"})

        self.assertNotIn("HTTP 429", str(raised.exception))

    def test_candidate_provider_connection_classifies_other_provider_http_errors_without_raw_text(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_provider_http_error", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        settings = {"provider": "deepseek", "modelId": "deepseek-v4-flash", "agentEnabled": True}

        with mock.patch.object(
            deploy_module,
            "remote_request_json",
            side_effect=[
                (200, {"code": 200, "data": settings}),
                (200, {"code": 503, "message": "AI provider request failed (HTTP 402)", "data": None}),
                (200, {"code": 503, "message": "AI provider request failed (HTTP 402)", "data": None}),
            ],
        ), mock.patch.object(deploy_module.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, r"providerClass=provider_4xx") as raised:
                deploy_module.test_candidate_provider_connection(object(), {"X-Admin-Token": "secret"})

        self.assertNotIn("HTTP 402", str(raised.exception))

    def test_candidate_provider_connection_classifies_acknowledgement_shape_without_raw_text(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_provider_ack", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        settings = {"provider": "deepseek", "modelId": "deepseek-v4-flash", "agentEnabled": True}

        with mock.patch.object(
            deploy_module,
            "remote_request_json",
            side_effect=[
                (200, {"code": 200, "data": settings}),
                (200, {"code": 503, "message": "连接测试响应校验失败 [AI_CONNECTION_ACK_MISSING_OK]", "data": None}),
                (200, {"code": 503, "message": "连接测试响应校验失败 [AI_CONNECTION_ACK_MISSING_OK]", "data": None}),
            ],
        ), mock.patch.object(deploy_module.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, r"providerClass=ack_missing_ok") as raised:
                deploy_module.test_candidate_provider_connection(object(), {"X-Admin-Token": "secret"})

        self.assertNotIn("AI_CONNECTION_ACK_MISSING_OK", str(raised.exception))

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

    def test_report_export_request_returns_bounded_text_without_leaking_authentication(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        secret = "report-export-session-secret-never-log"
        response = mock.MagicMock()
        response.status = 200
        response.headers = {
            "Content-Type": "text/markdown;charset=UTF-8",
            "Content-Disposition": "attachment; filename=research-report.md",
        }
        response.read.return_value = b"# Deployment Phase Three report\n\n## Sources\n"
        response.__enter__.return_value = response

        with mock.patch.object(
            deploy_module.urllib.request, "urlopen", return_value=response
        ) as urlopen:
            status, headers, body = deploy_module.request_text(
                "https://findopc.online/api/ai/research/reports/71/export?format=markdown",
                headers={"Authorization": f"Bearer {secret}"},
                maximum_bytes=1024,
            )

        self.assertEqual(200, status)
        self.assertEqual("text/markdown;charset=UTF-8", headers["content-type"])
        self.assertIn("## Sources", body)
        request = urlopen.call_args.args[0]
        self.assertEqual(f"Bearer {secret}", request.headers["Authorization"])

        oversized = mock.MagicMock()
        oversized.status = 200
        oversized.headers = {"Content-Type": "text/markdown"}
        oversized.read.return_value = b"x" * 1025
        oversized.__enter__.return_value = oversized
        with mock.patch.object(deploy_module.urllib.request, "urlopen", return_value=oversized):
            with self.assertRaises(RuntimeError) as raised:
                deploy_module.request_text(
                    "https://findopc.online/api/ai/research/reports/71/export",
                    headers={"Authorization": f"Bearer {secret}"},
                    maximum_bytes=1024,
                )
        self.assertIn("response exceeded", str(raised.exception).lower())
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
        self.assertLess(cleanup_sql.index("DELETE report FROM ai_research_reports"),
                        cleanup_sql.index("DELETE r FROM ai_analysis_runs"))
        self.assertLess(cleanup_sql.index("DELETE feedback FROM ai_agent_run_feedback"),
                        cleanup_sql.index("DELETE r FROM ai_analysis_runs"))
        self.assertLess(cleanup_sql.index("DELETE snapshot FROM ai_analytics_snapshots"),
                        cleanup_sql.index("DELETE r FROM ai_analysis_runs"))
        self.assertLess(cleanup_sql.index("DELETE r FROM ai_analysis_runs"),
                        cleanup_sql.index("DELETE m FROM ai_agent_messages"))
        self.assertLess(cleanup_sql.index("DELETE preference FROM ai_research_preferences"),
                        cleanup_sql.index("DELETE u FROM platform_users"))
        self.assertNotIn("LIKE", cleanup_sql.upper())
        self.assertIn("username='candidate_20260726_ab12'", cleanup_sql)

    def test_production_phase_three_probe_cleanup_is_exact_idempotent_and_verified(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        sql_calls = []

        with mock.patch.object(
            deploy_module,
            "database_command",
            side_effect=lambda _client, sql: sql_calls.append(sql) or (0, "\n".join(["0"] * 11), ""),
        ):
            identities = ("aiqa_2608021200", "aiqapeer_2608021200")
            deploy_module.cleanup_production_probe_data(object(), identities)
            deploy_module.cleanup_production_probe_data(object(), identities)

        self.assertEqual(2, len(sql_calls))
        self.assertEqual(sql_calls[0], sql_calls[1])
        cleanup_sql = sql_calls[0]
        self.assertIn("CREATE TEMPORARY TABLE opc_probe_users", cleanup_sql)
        self.assertIn("CREATE TEMPORARY TABLE opc_probe_runs", cleanup_sql)
        self.assertIn("CREATE TEMPORARY TABLE opc_probe_sessions", cleanup_sql)
        self.assertIn("username IN ('aiqa_2608021200','aiqapeer_2608021200')", cleanup_sql)
        self.assertNotIn("LIKE", cleanup_sql.upper())
        for table in (
            "ai_research_reports",
            "ai_agent_run_feedback",
            "ai_analytics_snapshots",
            "ai_research_preferences",
            "ai_agent_provider_calls",
            "ai_agent_tool_calls",
            "ai_analysis_runs",
            "ai_agent_messages",
            "ai_agent_sessions",
            "user_sessions",
            "platform_users",
        ):
            self.assertIn(table, cleanup_sql)
        self.assertLess(cleanup_sql.index("DELETE report FROM ai_research_reports"),
                        cleanup_sql.index("DELETE run FROM ai_analysis_runs"))
        self.assertLess(cleanup_sql.index("DELETE feedback FROM ai_agent_run_feedback"),
                        cleanup_sql.index("DELETE run FROM ai_analysis_runs"))
        self.assertLess(cleanup_sql.index("DELETE run FROM ai_analysis_runs"),
                        cleanup_sql.index("DELETE message FROM ai_agent_messages"))
        self.assertNotIn("AS remaining_probe_rows", cleanup_sql)
        for remaining_check in (
            "remaining_reports",
            "remaining_feedback",
            "remaining_snapshots",
            "remaining_preferences",
            "remaining_provider_calls",
            "remaining_tool_calls",
            "remaining_runs",
            "remaining_messages",
            "remaining_sessions",
            "remaining_user_sessions",
            "remaining_users",
        ):
            self.assertIn(f"AS {remaining_check}", cleanup_sql)

        with mock.patch.object(deploy_module, "database_command") as database:
            with self.assertRaisesRegex(RuntimeError, "identity is invalid"):
                deploy_module.cleanup_production_probe_data(
                    object(), ("aiqa_2608021200' OR 1=1 --",)
                )
        database.assert_not_called()

    def test_phase_three_product_probe_exercises_owned_contracts_and_returns_only_safe_summary(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)
        owner_headers = {"Authorization": "Bearer owner-probe-secret"}
        peer_headers = {"Authorization": "Bearer peer-probe-secret"}
        admin_headers = {"X-Admin-Token": "admin-probe-secret"}
        calls = []
        preference = {"memoryEnabled": True, "commonRegion": "Hubei", "commonIndustry": "AI"}
        preference_exists = True

        def fake_request(url, method="GET", payload=None, headers=None, expected_code=200, timeout=20):
            nonlocal preference, preference_exists
            calls.append({
                "url": url, "method": method, "payload": payload,
                "headers": headers, "expected_code": expected_code,
            })
            if url.endswith("/sessions/11/reports"):
                return 200, {"code": 200, "data": {
                    "reportId": 71, "userId": 42, "sessionId": 11, "runId": 22,
                    "finalMessageId": 33, "title": "Deployment Phase Three report",
                    "evidenceVersion": "evidence-v1", "dataVersion": None,
                    "status": "active", "revision": 1,
                }}
            if url.endswith("/reports/71"):
                code = 200 if headers == owner_headers else 404
                return 200, {"code": code, "data": {"reportId": 71} if code == 200 else None}
            if url.endswith("/preferences"):
                if method == "PATCH":
                    preference = dict(payload)
                    preference_exists = True
                    return 200, {"code": 200, "data": preference}
                if method == "DELETE":
                    preference_exists = False
                    return 200, {"code": 200, "data": None}
                return 200, {"code": 200, "data": preference if preference_exists else None}
            if url.endswith("/runs/22/feedback"):
                if headers == peer_headers:
                    return 200, {"code": 404, "data": None}
                if method == "GET":
                    return 200, {"code": 200, "data": {
                        "runId": 22, "rating": "helpful", "reason": "good_evidence",
                        "comment": None, "revision": 2,
                    }}
                revision = payload["expectedRevision"]
                if revision == 0:
                    return 200, {"code": 200, "data": {
                        "runId": 22, "rating": "helpful", "reason": "accurate_and_useful",
                        "comment": None, "revision": 1,
                    }}
                if revision == 1 and payload["reason"] == "good_evidence":
                    return 200, {"code": 200, "data": {
                        "runId": 22, "rating": "helpful", "reason": "good_evidence",
                        "comment": None, "revision": 2,
                    }}
                return 200, {"code": 409, "message": "FEEDBACK_REVISION_CONFLICT", "data": None}
            if url.endswith("/api/analytics/overview"):
                return 200, {"code": 200, "data": {
                    "dataVersion": "analytics-v1:test",
                    "cards": [{"metricId": "overview.verified_cases", "value": 3}],
                    "status": "complete",
                }}
            if url.endswith("/api/ai/research/from-analytics"):
                if payload["dataVersion"] != "analytics-v1:test":
                    return 200, {"code": 409, "message": "ANALYTICS_DATA_VERSION_STALE", "data": None}
                return 202, {"code": 200, "data": {
                    "session": {"sessionId": 88}, "messageId": 89, "runId": 90,
                    "status": "received", "analyticsSnapshotId": 80,
                    "metricId": "overview.verified_cases", "dataVersion": "analytics-v1:test",
                }}
            if url.endswith("/runs/90/cancel"):
                return 200, {"code": 200, "data": {"runId": 90, "status": "clarification_needed"}}
            if url.endswith("/runs/90"):
                code = 200 if headers == owner_headers else 404
                return 200, {"code": code, "data": {"runId": 90, "status": "clarification_needed"} if code == 200 else None}
            if url.endswith("/api/admin/ai/research/quality"):
                if headers != admin_headers:
                    return 200, {"code": 401, "data": None}
                return 200, {"code": 200, "data": {
                    "sampleSize": 3, "completedCount": 2, "helpfulCount": 1,
                    "reasonCounts": {"good_evidence": 1}, "failureReasons": {},
                    "latencySummary": {"total": 300, "average": 100},
                    "tokenSummary": {"total": 900, "average": 300},
                    "toolCallSummary": {"total": 6, "average": 2},
                    "granularity": "day",
                }}
            raise AssertionError(f"Unexpected request: {method} {url}")

        def fake_text(url, method="GET", headers=None, expected_code=200, timeout=20,
                      maximum_bytes=2 * 1024 * 1024):
            calls.append({"url": url, "method": method, "headers": headers, "text": True})
            if headers == owner_headers:
                return 200, {"content-type": "text/markdown;charset=UTF-8"}, (
                    "# Deployment Phase Three report\n\n"
                    "> evidence-v1\n\n## Sources\n"
                )
            return 200, {"content-type": "application/json"}, json.dumps({
                "code": 404, "message": "Resource not found", "data": None,
            })

        with mock.patch.object(deploy_module, "request_json", side_effect=fake_request), \
                mock.patch.object(deploy_module, "request_text", side_effect=fake_text), \
                mock.patch.object(
                    deploy_module,
                    "database_command",
                    return_value=(
                        0,
                        "overview.verified_cases\tanalytics-v1:test\t90\t80\tanalytics-v1:test\t1",
                        "",
                    ),
                ):
            summary = deploy_module.run_phase_three_product_probes(
                object(), "20260802-120000", owner_headers, peer_headers,
                admin_headers, session_id=11, run_id=22, final_message_id=33,
            )

        self.assertEqual({
            "report_saved": True,
            "report_export": "markdown",
            "report_owner_isolated": True,
            "preference_consent": True,
            "preference_deleted": True,
            "feedback_revision": 2,
            "feedback_cas": True,
            "analytics_snapshot_id": 80,
            "analytics_run_id": 90,
            "analytics_data_version": "analytics-v1:test",
            "analytics_owner_isolated": True,
            "admin_quality_sample_size": 3,
            "admin_quality_auth": True,
        }, summary)
        serialized_summary = json.dumps(summary)
        for forbidden in ("owner-probe-secret", "peer-probe-secret", "admin-probe-secret", "comment", "question"):
            self.assertNotIn(forbidden, serialized_summary)

        report_calls = [call for call in calls if "/reports" in call["url"]]
        self.assertTrue(any(call.get("headers") == peer_headers for call in report_calls))
        feedback_writes = [
            call for call in calls
            if call["url"].endswith("/runs/22/feedback") and call["method"] == "PUT"
        ]
        self.assertEqual([0, 1, 1], [call["payload"]["expectedRevision"] for call in feedback_writes])
        analytics_writes = [
            call for call in calls if call["url"].endswith("/api/ai/research/from-analytics")
        ]
        self.assertEqual([], analytics_writes[0]["payload"]["selectedBucketIds"])
        self.assertEqual("analytics-v1:test", analytics_writes[0]["payload"]["dataVersion"])
        quality_calls = [call for call in calls if call["url"].endswith("/api/admin/ai/research/quality")]
        self.assertEqual([None, owner_headers, admin_headers], [call["headers"] for call in quality_calls])

    def test_phase_three_cancel_probe_accepts_controlled_state_race_only_after_terminal_read(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)

        self.assertTrue(deploy_module.analytics_cancel_confirmed(
            {"code": 200, "data": {"status": "cancelled"}},
            {"code": 200, "data": {"status": "cancelled"}},
        ))
        self.assertTrue(deploy_module.analytics_cancel_confirmed(
            {"code": 200, "data": {"status": "clarification_needed"}},
            {"code": 200, "data": {"status": "clarification_needed"}},
        ))
        self.assertTrue(deploy_module.analytics_cancel_confirmed(
            {"code": 409, "data": None},
            {"code": 200, "data": {"status": "completed"}},
        ))
        self.assertTrue(deploy_module.analytics_cancel_confirmed(
            {"code": 200, "data": {"status": "clarification_needed"}},
            {"code": 200, "data": {"status": "clarification_needed"}},
        ))
        self.assertFalse(deploy_module.analytics_cancel_confirmed(
            {"code": 409, "data": None},
            {"code": 200, "data": {"status": "running"}},
        ))
        self.assertFalse(deploy_module.analytics_cancel_confirmed(
            {"code": 401, "data": None},
            {"code": 200, "data": {"status": "cancelled"}},
        ))

    def test_phase_three_cancel_diagnostic_keeps_only_safe_business_state_fields(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)

        diagnostic = deploy_module.analytics_cancel_diagnostic(
            200,
            {"code": 200, "data": {"status": "clarification_needed", "message": "secret"}},
            200,
            {"code": 200, "data": {"status": "clarification_needed", "rawResponse": "secret"}},
        )

        self.assertEqual(
            "cancelCode=200; cancelStatus=clarification_needed; "
            "readCode=200; readStatus=clarification_needed",
            diagnostic,
        )
        self.assertNotIn("secret", diagnostic)
        self.assertNotIn("rawResponse", diagnostic)

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
        self.assertIn(
            "UPDATE ai_model_settings SET agent_enabled=1, "
            "agent_rollout_state='explicitly_enabled' WHERE id=1 AND enabled=1;",
            command,
        )
        self.assertIn("SPRING_DATASOURCE_URL", command)
        creation_sql = command.split("mysql -uroot <<SQL", 1)[1].split("\nSQL\n", 1)[0]
        self.assertNotIn("`", creation_sql)
        self.assertNotIn(candidate.password, command)
        self.assertIn(candidate.password, stdin_text)
        self.assertTrue(candidate.environment_file.startswith("/run/opc-candidate-"))

    def test_candidate_database_commands_use_stable_headerless_output(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_candidate_mysql", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)

        with mock.patch.object(
            deploy_module,
            "run",
            return_value=(0, "1\n1", ""),
        ) as remote_run:
            deploy_module.candidate_database_command(
                object(),
                "opc_candidate_20260726200000",
                "SELECT 1; SELECT 1;\n",
            )

        command = remote_run.call_args.args[1]
        self.assertIn("mysql --batch --skip-column-names -uroot", command)

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

    def test_candidate_runtime_stop_failure_is_reported(self):
        spec = importlib.util.spec_from_file_location("opc_deploy_script", DEPLOY_SCRIPT)
        deploy_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(deploy_module)

        with mock.patch.object(deploy_module, "run", return_value=(1, "", "stop failed")):
            with self.assertRaisesRegex(RuntimeError, "Candidate runtime cleanup failed"):
                deploy_module.stop_candidate_runtime(
                    object(), "opc-backend-candidate-20260726200000"
                )

        with mock.patch.object(deploy_module, "run", return_value=(0, "", "")) as remote_run:
            deploy_module.stop_candidate_runtime(
                object(), "opc-backend-candidate-20260726200000"
            )
        command = remote_run.call_args.args[1]
        self.assertIn('exit "$stop_code"', command)
        self.assertNotRegex(command, r"systemctl stop[^\n]+\|\| true")

    def test_candidate_unit_identity_exists_before_runtime_health_check(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = deploy[deploy.index("def deploy(client):"):deploy.index("def deploy_frontend(client):")]

        identity = body.index("candidate_unit = candidate_runtime_unit(stamp)")
        start = body.index("start_candidate_runtime(")
        cleanup = body.index("stop_candidate_runtime(client, candidate_unit)")

        self.assertLess(identity, start)
        self.assertLess(start, cleanup)

    def test_candidate_citation_audit_uses_only_the_authorized_projection(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        probe = deploy[
            deploy.index("def run_candidate_agent_v2_probe"):
            deploy.index("def ai_settings_update_payload")
        ]

        self.assertIn("$._authorized.items", probe)
        self.assertIn("$._authorized.cases", probe)
        self.assertIn("$._authorized.sourceId", probe)
        self.assertNotIn("'$.items[*]'", probe)
        self.assertNotIn("'$.conclusions[*]'", probe)

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
        cleanup = deploy[
            deploy.index("def cleanup_production_probe_data("):
            deploy.index("def run_phase_three_product_probes(")
        ]

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
        self.assertIn("cleanup_production_probe_data(", body)
        self.assertIn("DELETE session FROM ai_agent_sessions", cleanup)
        self.assertLess(
            cleanup.index("DELETE run FROM ai_analysis_runs"),
            cleanup.index("DELETE session FROM ai_agent_sessions"),
        )


if __name__ == "__main__":
    unittest.main()
