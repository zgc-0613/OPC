import re
import unittest
from pathlib import Path

from scripts.deployment_hardening import (
    is_loopback_listener,
    require_secret_environment,
    validate_agent_probe_record,
    validate_agent_runtime_postcheck,
)


ROOT = Path(__file__).resolve().parents[1]
APPLICATION_CONFIG = ROOT / "opc-backend" / "src" / "main" / "resources" / "application.yaml"
NGINX_CONFIG = ROOT / "deploy" / "nginx" / "opc.conf"
SYSTEMD_UNIT = ROOT / "deploy" / "systemd" / "opc-backend.service"
DEPLOY_SCRIPT = ROOT / ".codex_deploy_opc.py"


class DeploymentHardeningTest(unittest.TestCase):
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

    def test_agent_probe_record_requires_real_metadata_and_authorized_citations(self):
        valid = {
            "status": "completed",
            "provider": "deepseek",
            "model": "deepseek-chat",
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
            {**valid, "api_key": "sk-should-never-appear"},
        ]
        for record in invalid_records:
            with self.subTest(record=record), self.assertRaises(ValueError):
                validate_agent_probe_record(record, max_model_rounds=4, max_tool_calls=6)

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
        self.assertIn("DELETE FROM ai_analysis_runs", body)

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

    def test_agent_probe_requires_async_completion_tool_citation_and_database_audit(self):
        deploy = DEPLOY_SCRIPT.read_text(encoding="utf-8")
        body = deploy[deploy.index("def deploy(client):"):deploy.index("def deploy_frontend(client):")]

        self.assertIn("/api/ai/research/sessions", body)
        self.assertIn("/messages", body)
        self.assertIn("expected_code=202", body)
        self.assertIn("/api/ai/research/runs/", body)
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
