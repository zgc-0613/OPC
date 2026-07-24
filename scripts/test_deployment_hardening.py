import re
import unittest
from pathlib import Path

from scripts.deployment_hardening import is_loopback_listener


ROOT = Path(__file__).resolve().parents[1]
APPLICATION_CONFIG = ROOT / "opc-backend" / "src" / "main" / "resources" / "application.yaml"
NGINX_CONFIG = ROOT / "deploy" / "nginx" / "opc.conf"
SYSTEMD_UNIT = ROOT / "deploy" / "systemd" / "opc-backend.service"
DEPLOY_SCRIPT = ROOT / ".codex_deploy_opc.py"


class DeploymentHardeningTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
