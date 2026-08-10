import contextlib
import io
import json
import sys
import unittest
from unittest import mock

from scripts import run_phase_one_mysql_test as runner


class PhaseOneMySqlRunnerTest(unittest.TestCase):
    def invoke_main(self, maven_result, containers, report):
        calls = {}
        maven_result = {"started_at": 0, **maven_result}

        def fake_maven(timeout_seconds, label, log_path, thread_dump_after_seconds, run_id):
            calls["maven"] = {
                "timeout_seconds": timeout_seconds,
                "label": label,
                "run_id": run_id,
            }
            return maven_result

        with mock.patch.object(runner, "run_checked", side_effect=[(0, "", 0.01), (0, "", 0.01)]), \
                mock.patch.object(runner, "run_maven", side_effect=fake_maven), \
                mock.patch.object(runner, "parse_report", return_value=report), \
                mock.patch.object(runner, "list_owned_containers", side_effect=[list(containers), []]) as listed, \
                mock.patch.object(runner, "cleanup_owned_containers", return_value=list(containers)) as cleaned, \
                mock.patch.object(runner.uuid, "uuid4", return_value="12345678-1234-4234-8234-123456789abc"), \
                mock.patch.object(sys, "argv", ["run_phase_one_mysql_test.py"]):
            stdout = io.StringIO()
            with contextlib.redirect_stdout(stdout):
                code = runner.main()
        output = stdout.getvalue()
        first_end = output.find("\n")
        final_start = output.find("\n{\n", first_end) + 1
        calls["output"] = [json.loads(output[:first_end]), json.loads(output[final_start:])]
        calls["listed"] = listed
        calls["cleaned"] = cleaned
        return code, calls

    def test_generates_run_id_and_passes_it_to_maven(self):
        code, calls = self.invoke_main(
            {"exit_code": 0, "elapsed_seconds": 1, "stages": {}, "log": "target/test.log", "thread_dumps": []},
            [],
            {"report_present": True, "tests": 81, "failures": 0, "errors": 0, "surefire_seconds": 1},
        )

        self.assertEqual(0, code)
        self.assertEqual("12345678-1234-4234-8234-123456789abc", calls["maven"]["run_id"])
        self.assertEqual(1200, calls["maven"]["timeout_seconds"])
        self.assertEqual("12345678-1234-4234-8234-123456789abc", calls["listed"].call_args.args[0])

    def test_maven_command_carries_only_the_validated_run_id_property(self):
        command = runner.build_maven_command("12345678-1234-4234-8234-123456789abc")
        self.assertIn(
            "-Dopc.phase-one.mysql.run-id=12345678-1234-4234-8234-123456789abc",
            command,
        )
        with self.assertRaises(ValueError):
            runner.build_maven_command("not-a-run-id")

    def test_container_query_uses_exact_project_label_and_ignores_other_sessions(self):
        run_id = "12345678-1234-4234-8234-123456789abc"
        calls = [
            mock.Mock(returncode=0, stdout="a1b2c3\nd4e5f6\n"),
            mock.Mock(returncode=0, stdout=f"mysql:8.4|{run_id}\n"),
            mock.Mock(returncode=0, stdout="mysql:8.4|different-run\n"),
        ]
        with mock.patch.object(runner.subprocess, "run", side_effect=calls) as docker_run:
            self.assertEqual(["a1b2c3"], runner.list_owned_containers(run_id))
        query = docker_run.call_args_list[0].args[0]
        self.assertIn(f"label={runner.RUN_ID_LABEL}={run_id}", query)
        self.assertIn("ancestor=mysql:8.4", query)
        self.assertNotIn("org.testcontainers=true", query)

    def test_success_with_current_run_container_is_resource_leak_failure(self):
        code, calls = self.invoke_main(
            {"exit_code": 0, "elapsed_seconds": 1, "stages": {}, "log": "target/test.log", "thread_dumps": []},
            ["current-container"],
            {"report_present": True, "tests": 81, "failures": 0, "errors": 0, "surefire_seconds": 1},
        )

        self.assertNotEqual(0, code)
        self.assertTrue(calls["cleaned"].called)
        result = calls["output"][-1]
        self.assertTrue(result["resource_leak_detected"])
        self.assertEqual(1, result["current_run_container_count"])

    def test_success_with_zero_current_run_containers_is_green(self):
        code, calls = self.invoke_main(
            {"exit_code": 0, "elapsed_seconds": 1, "stages": {}, "log": "target/test.log", "thread_dumps": []},
            [],
            {"report_present": True, "tests": 81, "failures": 0, "errors": 0, "surefire_seconds": 1},
        )

        self.assertEqual(0, code)
        self.assertFalse(calls["cleaned"].called)
        self.assertFalse(calls["output"][-1]["resource_leak_detected"])

    def test_failed_run_cleans_only_current_run_label(self):
        code, calls = self.invoke_main(
            {"exit_code": 1, "elapsed_seconds": 1, "stages": {}, "log": "target/test.log", "thread_dumps": []},
            ["current-container"],
            {"report_present": False},
        )

        self.assertNotEqual(0, code)
        calls["cleaned"].assert_called_once_with("12345678-1234-4234-8234-123456789abc")
        self.assertEqual(1, calls["output"][-1]["current_run_container_count"])


if __name__ == "__main__":
    unittest.main()
