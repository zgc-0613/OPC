"""Run MySQL 8.4 integration with bounded timing and exact run-owned cleanup."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import threading
import time
import uuid
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from queue import Empty, Queue


ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "opc-backend"
REPORT = BACKEND / "target" / "surefire-reports" / (
    "TEST-com.opc.platform.integration.PhaseOneMySqlIntegrationTest.xml"
)
RUN_ID_LABEL = "com.opc.phase-one.run-id"
RUN_ID_PATTERN = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
)
EXPECTED_TEST_COUNT = 81

STAGES = (
    ("maven_scan", "Scanning for projects"),
    ("container_create", "Creating container for image: mysql:8.4"),
    ("container_wait", "Waiting for database connection"),
    ("container_ready", "Container mysql:8.4 started"),
    ("spring_started", "Started PhaseOneMySqlIntegrationTest"),
    ("tests_complete", f"Tests run: {EXPECTED_TEST_COUNT}, Failures: 0, Errors: 0"),
    ("build_success", "BUILD SUCCESS"),
)


def redact(value: str) -> str:
    value = re.sub(r"(?i)jdbc:mysql://\S+", "jdbc:mysql://<redacted>", value)
    value = re.sub(
        r"(?i)(password|api[-_ ]?key|authorization|cookie|token)\s*[=:]\s*\S+",
        r"\1=<redacted>",
        value,
    )
    return value


def run_checked(command: list[str], cwd: Path) -> tuple[int, str, float]:
    started = time.monotonic()
    completed = subprocess.run(
        command,
        cwd=cwd,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    return completed.returncode, redact(completed.stdout + completed.stderr), time.monotonic() - started


def parse_report(started_at: float) -> dict[str, object]:
    if not REPORT.exists() or REPORT.stat().st_mtime < started_at:
        return {"report": str(REPORT), "report_present": False}
    suite = ET.parse(REPORT).getroot()
    return {
        "report": str(REPORT),
        "report_present": True,
        "surefire_seconds": float(suite.attrib.get("time", "0")),
        "tests": int(suite.attrib.get("tests", "0")),
        "failures": int(suite.attrib.get("failures", "0")),
        "errors": int(suite.attrib.get("errors", "0")),
        "skipped": int(suite.attrib.get("skipped", "0")),
    }


def child_java_processes(parent_pid: int) -> list[int]:
    if os.name != "nt":
        return []
    command = [
        "powershell",
        "-NoProfile",
        "-Command",
        "$processes = Get-CimInstance Win32_Process; "
        f"$descendants = [System.Collections.Generic.HashSet[int]]::new(); $frontier = @({parent_pid}); "
        "while ($frontier.Count -gt 0) { "
        "$next = @(); foreach ($candidate in $processes) { "
        "if ($frontier -contains $candidate.ParentProcessId -and $descendants.Add([int]$candidate.ProcessId)) { "
        "$next += [int]$candidate.ProcessId } }; $frontier = $next }; "
        "$processes | Where-Object { $_.Name -eq 'java.exe' -and $descendants.Contains([int]$_.ProcessId) } | "
        "Select-Object -ExpandProperty ProcessId",
    ]
    completed = subprocess.run(command, capture_output=True, text=True, check=False)
    return [int(value) for value in completed.stdout.split() if value.isdigit()]


def capture_thread_dumps(parent_pid: int, dump_dir: Path, label: str, elapsed: float) -> list[str]:
    dump_dir.mkdir(parents=True, exist_ok=True)
    captured: list[str] = []
    for pid in child_java_processes(parent_pid):
        dump_path = dump_dir / f"phase-one-mysql-{label}-{pid}-{int(elapsed)}s.thread-dump.txt"
        completed = subprocess.run(
            ["jcmd", str(pid), "Thread.print", "-l"],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=False,
        )
        dump_path.write_text(redact(completed.stdout + completed.stderr), encoding="utf-8")
        captured.append(str(dump_path))
    return captured


def validate_run_id(run_id: str) -> str:
    if not isinstance(run_id, str) or not RUN_ID_PATTERN.fullmatch(run_id):
        raise ValueError("run ID must be a UUID v4")
    return run_id.lower()


def list_owned_containers(run_id: str) -> list[str]:
    run_id = validate_run_id(run_id)
    listed = subprocess.run(
        [
            "docker", "ps", "-a",
            "--filter", f"label={RUN_ID_LABEL}={run_id}",
            "--filter", "ancestor=mysql:8.4",
            "--format", "{{.ID}}",
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    if listed.returncode != 0:
        raise RuntimeError("Docker run-owned container query failed")
    owned: list[str] = []
    for container_id in (line.strip() for line in listed.stdout.splitlines()):
        if not re.fullmatch(r"[0-9a-f]+", container_id):
            continue
        inspected = subprocess.run(
            [
                "docker", "inspect", "--format",
                "{{.Config.Image}}|{{index .Config.Labels \"com.opc.phase-one.run-id\"}}",
                container_id,
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        if inspected.returncode == 0 and inspected.stdout.strip() == f"mysql:8.4|{run_id}":
            owned.append(container_id)
    return owned


def cleanup_owned_containers(run_id: str) -> list[str]:
    cleaned: list[str] = []
    for container_id in list_owned_containers(run_id):
        stopped = subprocess.run(["docker", "stop", container_id], capture_output=True, text=True, check=False)
        removed = subprocess.run(["docker", "rm", container_id], capture_output=True, text=True, check=False)
        if stopped.returncode == 0 and removed.returncode == 0:
            cleaned.append(container_id)
    return cleaned


def build_maven_command(run_id: str) -> list[str]:
    run_id = validate_run_id(run_id)
    return [
        "cmd.exe",
        "/d",
        "/c",
        str(BACKEND / "mvnw.cmd"),
        "-Dtest=PhaseOneMySqlIntegrationTest",
        f"-Dopc.phase-one.mysql.run-id={run_id}",
        "test",
        "-DfailIfNoTests=false",
    ]


def stop_process(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    if os.name == "nt":
        subprocess.run(
            ["taskkill", "/PID", str(process.pid), "/T", "/F"],
            capture_output=True,
            text=True,
            check=False,
        )
    else:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()


def run_maven(
    timeout_seconds: int,
    label: str,
    log_path: Path,
    thread_dump_after_seconds: int,
    run_id: str,
) -> dict[str, object]:
    command = build_maven_command(run_id)
    started = time.monotonic()
    started_at = time.time()
    stages: dict[str, float] = {}
    thread_dumps: list[str] = []
    next_thread_dump = thread_dump_after_seconds
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("w", encoding="utf-8", newline="") as log:
        process = subprocess.Popen(
            command,
            cwd=BACKEND,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )
        assert process.stdout is not None
        lines: Queue[str | None] = Queue()

        def read_output() -> None:
            for output_line in process.stdout:
                lines.put(output_line)
            lines.put(None)

        reader = threading.Thread(target=read_output, name="phase-one-mysql-output", daemon=True)
        reader.start()
        try:
            output_closed = False
            while not output_closed:
                elapsed = time.monotonic() - started
                if elapsed >= timeout_seconds:
                    stop_process(process)
                    stages["external_timeout"] = round(elapsed, 3)
                    break
                if elapsed >= next_thread_dump:
                    thread_dumps.extend(capture_thread_dumps(process.pid, log_path.parent, label, elapsed))
                    next_thread_dump += thread_dump_after_seconds
                try:
                    raw_line = lines.get(timeout=0.2)
                except Empty:
                    continue
                if raw_line is None:
                    output_closed = True
                    continue
                line = redact(raw_line)
                log.write(line)
                log.flush()
                print(line, end="", flush=True)
                for stage, marker in STAGES:
                    if stage not in stages and marker in line:
                        stages[stage] = round(time.monotonic() - started, 3)
            return_code = process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            stop_process(process)
            return_code = 124
            stages["external_timeout"] = round(time.monotonic() - started, 3)
    return {
        "command": " ".join(command[3:]),
        "exit_code": return_code,
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "stages": stages,
        "log": str(log_path),
        "thread_dumps": thread_dumps,
        "started_at": started_at,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--timeout-seconds", type=int, default=1200)
    parser.add_argument("--thread-dump-after-seconds", type=int, default=180)
    parser.add_argument("--label", default="run")
    args = parser.parse_args()
    if args.timeout_seconds <= 0:
        parser.error("--timeout-seconds must be positive")
    if args.thread_dump_after_seconds <= 0:
        parser.error("--thread-dump-after-seconds must be positive")
    run_id = str(uuid.uuid4())
    validate_run_id(run_id)

    docker_code, docker_output, docker_seconds = run_checked(["docker", "info"], ROOT)
    image_code, image_output, image_seconds = run_checked(
        ["docker", "image", "inspect", "mysql:8.4"], ROOT
    )
    print(json.dumps({
        "docker_available": docker_code == 0,
        "docker_seconds": round(docker_seconds, 3),
        "mysql_image_cached": image_code == 0,
        "image_inspect_seconds": round(image_seconds, 3),
    }, ensure_ascii=False))
    if docker_code != 0:
        print(redact(docker_output), file=sys.stderr)
        return docker_code or 1
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    log_path = BACKEND / "target" / f"phase-one-mysql-{args.label}-{stamp}.log"
    result = run_maven(
        args.timeout_seconds,
        args.label,
        log_path,
        args.thread_dump_after_seconds,
        run_id,
    )
    result.update(parse_report(float(result.pop("started_at", time.time()))))
    result["run_id"] = run_id
    try:
        owned_containers = list_owned_containers(run_id)
        result["current_run_container_count"] = len(owned_containers)
        result["resource_leak_detected"] = bool(owned_containers)
        result["cleaned_containers"] = []
        if owned_containers:
            result["cleaned_containers"] = cleanup_owned_containers(run_id)
            remaining = list_owned_containers(run_id)
            result["remaining_current_run_container_count"] = len(remaining)
    except (RuntimeError, ValueError) as error:
        result["resource_query_error"] = str(error)
        result["current_run_container_count"] = None
        result["resource_leak_detected"] = True
        result["cleaned_containers"] = []
    result["label"] = args.label
    result["timestamp_utc"] = stamp
    print(json.dumps(result, ensure_ascii=False, indent=2))
    if result["exit_code"] != 0:
        return int(result["exit_code"])
    if result.get("resource_query_error"):
        return 3
    if result.get("tests") != EXPECTED_TEST_COUNT \
            or result.get("failures") != 0 or result.get("errors") != 0:
        return 2
    if result.get("current_run_container_count") != 0:
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
