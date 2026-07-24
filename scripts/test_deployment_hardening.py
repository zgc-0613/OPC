import re
import unittest
from pathlib import Path

from scripts.deployment_hardening import is_loopback_listener


ROOT = Path(__file__).resolve().parents[1]
APPLICATION_CONFIG = ROOT / "opc-backend" / "src" / "main" / "resources" / "application.yaml"
NGINX_CONFIG = ROOT / "deploy" / "nginx" / "opc.conf"
SYSTEMD_UNIT = ROOT / "deploy" / "systemd" / "opc-backend.service"


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


if __name__ == "__main__":
    unittest.main()
