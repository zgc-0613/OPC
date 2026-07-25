import gzip
import hashlib
import json
import os
import re
import secrets
import shutil
import socket
import ssl
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

import bcrypt
import paramiko

from scripts.deployment_hardening import (
    is_loopback_listener,
    require_secret_environment,
    validate_agent_probe_record,
    validate_agent_runtime_postcheck,
    validate_assistant_workspace_postcheck,
)


HOST = "39.105.25.189"
PORT = 22
USER = "root"
EXPECTED_FINGERPRINT = "119ae50f7f0bdb545996a90b49521db3e1404aeae327c13d64c7e67af8195672"
ROOT = Path(__file__).resolve().parent
FRONTEND = ROOT / "opc-frontend" / "dist"
BACKEND = ROOT / "opc-backend" / "target" / "opc-backend-0.0.1-SNAPSHOT.jar"
MIGRATION = ROOT / "deploy" / "sql" / "20260719_admin_registration.sql"
AI_MIGRATION = ROOT / "deploy" / "sql" / "20260724_ai_phase_one.sql"
AI_CATALOG_MIGRATION = ROOT / "deploy" / "sql" / "20260724_ai_model_catalog.sql"
AI_STABILIZATION_MIGRATION = ROOT / "deploy" / "sql" / "20260724_ai_stabilization.sql"
AI_STABILIZATION_PRECHECK = ROOT / "deploy" / "sql" / "20260724_ai_stabilization_precheck.sql"
AI_STABILIZATION_POSTCHECK = ROOT / "deploy" / "sql" / "20260724_ai_stabilization_postcheck.sql"
EVIDENCE_WORKBENCH_MIGRATION = ROOT / "deploy" / "sql" / "20260725_evidence_workbench.sql"
PHASE_ONE_FINALIZATION_MIGRATION = ROOT / "deploy" / "sql" / "20260725_phase_one_finalization.sql"
POLICY_APPLICABILITY_MIGRATION = ROOT / "deploy" / "sql" / "20260725_policy_applicability.sql"
AI_RESPONSE_DIAGNOSTICS_MIGRATION = ROOT / "deploy" / "sql" / "20260725_ai_response_diagnostics.sql"
AGENT_RUNTIME_PRECHECK = ROOT / "deploy" / "sql" / "20260725_agent_runtime_precheck.sql"
AGENT_RUNTIME_MIGRATION = ROOT / "deploy" / "sql" / "20260725_agent_runtime.sql"
AGENT_RUNTIME_STABILIZATION_MIGRATION = ROOT / "deploy" / "sql" / "20260725_agent_runtime_stabilization.sql"
AGENT_RUNTIME_POSTCHECK = ROOT / "deploy" / "sql" / "20260725_agent_runtime_postcheck.sql"
ASSISTANT_WORKSPACE_PRECHECK = ROOT / "deploy" / "sql" / "20260725_assistant_workspace_precheck.sql"
ASSISTANT_WORKSPACE_MIGRATION = ROOT / "deploy" / "sql" / "20260725_assistant_workspace.sql"
ASSISTANT_WORKSPACE_POSTCHECK = ROOT / "deploy" / "sql" / "20260725_assistant_workspace_postcheck.sql"
NGINX = ROOT / "deploy" / "nginx" / "opc.conf"
SYSTEMD = ROOT / "deploy" / "systemd" / "opc-backend.service"


def connect():
    password = require_secret_environment(os.environ, "OPC_SSH_PASSWORD")

    class PinnedFingerprintPolicy(paramiko.MissingHostKeyPolicy):
        def missing_host_key(self, client, hostname, key):
            actual = hashlib.sha256(key.asbytes()).hexdigest()
            if actual != EXPECTED_FINGERPRINT:
                raise paramiko.SSHException("SSH host fingerprint mismatch")
            client.get_host_keys().add(hostname, key.get_name(), key)

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(PinnedFingerprintPolicy())
    client.connect(
        HOST,
        port=PORT,
        username=USER,
        password=password,
        timeout=15,
        banner_timeout=15,
        auth_timeout=15,
        look_for_keys=False,
        allow_agent=False,
    )
    return client


def run(client, command, stdin_text=None, check=True, timeout=180):
    stdin, stdout, stderr = client.exec_command(command, timeout=timeout)
    if stdin_text is not None:
        stdin.write(stdin_text)
        stdin.channel.shutdown_write()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    code = stdout.channel.recv_exit_status()
    if check and code != 0:
        raise RuntimeError(f"Remote command failed ({code}): {err.strip() or out.strip()}")
    return code, out.strip(), err.strip()


def mkdirs_sftp(sftp, remote_dir):
    parts = []
    current = remote_dir
    while current not in ("", "/"):
        parts.append(current)
        current = current.rsplit("/", 1)[0] or "/"
    for path in reversed(parts):
        try:
            sftp.stat(path)
        except FileNotFoundError:
            sftp.mkdir(path)


def upload_tree(sftp, local_dir, remote_dir):
    mkdirs_sftp(sftp, remote_dir)
    for local_path in sorted(local_dir.rglob("*")):
        relative = local_path.relative_to(local_dir).as_posix()
        remote_path = f"{remote_dir}/{relative}"
        if local_path.is_dir():
            mkdirs_sftp(sftp, remote_path)
        else:
            mkdirs_sftp(sftp, remote_path.rsplit("/", 1)[0])
            sftp.put(str(local_path), remote_path)


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


DB_ENV = r"""
DB_USER=$(awk '/^[[:space:]]+username:/{print $2; exit}' /opt/opc/application.yaml | tr -d '\"')
DB_PASS=$(awk '/^[[:space:]]+password:/{print $2; exit}' /opt/opc/application.yaml | tr -d '\"')
test -n "$DB_USER"
"""


def database_command(client, sql):
    command = "set -euo pipefail\n" + DB_ENV + "\nMYSQL_PWD=\"$DB_PASS\" mysql -u \"$DB_USER\" opc_platform"
    return run(client, command, stdin_text=sql, timeout=180)


def request_json(url, method="GET", payload=None, headers=None, expected_code=200, timeout=20):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request_headers = {"Accept": "application/json"}
    if body is not None:
        request_headers["Content-Type"] = "application/json"
    if headers:
        request_headers.update(headers)
    request = urllib.request.Request(url, data=body, method=method, headers=request_headers)
    context = ssl.create_default_context()
    try:
        with urllib.request.urlopen(request, timeout=timeout, context=context) as response:
            raw = response.read().decode("utf-8")
            status = response.status
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        status = error.code
    if status != expected_code:
        raise RuntimeError(f"Unexpected HTTP status for {url}: {status}")
    data = json.loads(raw)
    return status, data


def ai_settings_update_payload(settings, agent_enabled):
    return {
        "provider": settings.get("provider"),
        "apiFormat": settings.get("apiFormat"),
        "apiBaseUrl": settings.get("apiBaseUrl"),
        "modelId": settings.get("modelId"),
        "models": settings.get("models") or [],
        "temperature": settings.get("temperature"),
        "maxOutputTokens": settings.get("maxOutputTokens"),
        "timeoutSeconds": settings.get("timeoutSeconds"),
        "retryCount": settings.get("retryCount"),
        "dailyTokenQuota": settings.get("dailyTokenQuota"),
        "enabled": bool(settings.get("enabled")),
        "agentEnabled": bool(agent_enabled),
        "agentMaxModelRounds": settings.get("agentMaxModelRounds") or 4,
        "agentMaxToolCalls": settings.get("agentMaxToolCalls") or 6,
        "agentMaxTokens": settings.get("agentMaxTokens") or 8000,
        "agentHistoryWindow": settings.get("agentHistoryWindow") or 12,
        "agentTimeoutSeconds": settings.get("agentTimeoutSeconds") or 120,
        "agentToolMode": settings.get("agentToolMode") or "json_plan",
    }


def assert_external_backend_closed():
    try:
        connection = socket.create_connection((HOST, 8082), timeout=3)
    except OSError:
        return
    connection.close()
    raise RuntimeError("Backend port 8082 is reachable outside the Nginx reverse proxy")


def assert_backend_runtime_hardened(client):
    _, listener, _ = run(client, "ss -lntH 'sport = :8082' | awk '{print $4}'")
    if not is_loopback_listener(listener, expected_port=8082):
        raise RuntimeError(f"Backend listener is not loopback-only: {listener or 'missing'}")

    _, backend_user, _ = run(
        client,
        'pid=$(systemctl show -p MainPID --value opc-backend.service); ps -o user= -p "$pid" | xargs',
    )
    if backend_user != "opc":
        raise RuntimeError(f"Backend process is running as unexpected user: {backend_user or 'missing'}")
    return {"listener": listener, "user": backend_user}


def preflight(client):
    commands = {
        "time": "date -Iseconds",
        "services": "systemctl is-active nginx mysqld opc-backend.service",
        "nginx": "nginx -t 2>&1",
        "processes": "ps -eo pid=,args= | awk '/[j]ava .*opc-backend/{count++} END{print count+0}'",
        "disk": "df -P /opt /var/www | tail -n +2 | awk '{print $6 \" \" $5}'",
        "frontend": "p=/var/www/opc/index.html; test -f /opt/opc/current/frontend/index.html && p=/opt/opc/current/frontend/index.html; sha256sum \"$p\" | awk '{print $1}'",
        "backend": "p=/opt/opc-backend.jar; test -f /opt/opc/current/opc-backend.jar && p=/opt/opc/current/opc-backend.jar; sha256sum \"$p\" | awk '{print $1}'",
        "current_release": "if test -L /opt/opc/current; then readlink -f /opt/opc/current; elif test -e /opt/opc/current; then echo non-symlink; else echo absent; fi",
        "backend_listener": "ss -lntH 'sport = :8082' | awk '{print $4}'",
        "backend_user": "pid=$(systemctl show -p MainPID --value opc-backend.service); ps -o user= -p \"$pid\" | xargs",
        "legacy_admin_secret": "if grep -q '^OPC_ADMIN_PASSWORD=' /etc/opc-backend.env; then echo present; else echo absent; fi",
    }
    result = {}
    for name, command in commands.items():
        _, out, _ = run(client, command)
        result[name] = out
    _, admin_counts, _ = database_command(
        client,
        "SELECT CONCAT((SELECT COUNT(*) FROM admin_accounts), ' accounts, ', "
        "(SELECT COUNT(*) FROM admin_registration_requests), ' requests, ', "
        "(SELECT COUNT(*) FROM admin_sessions), ' sessions');\n"
        "SELECT COUNT(*) FROM admin_accounts WHERE username = 'ACha_' AND status = 'active';\n",
    )
    result["admin_counts"] = admin_counts
    _, evidence_counts, _ = database_command(
        client,
        "SELECT CONCAT('verified_sources=', SUM(ai_evidence_status='verified')) FROM sources;\n"
        "SELECT CONCAT('effective_verified_policies=', COUNT(*)) FROM policies p "
        "JOIN sources s ON s.id=p.source_id WHERE p.ai_evidence_status='verified' AND p.status='published' "
        "AND s.ai_evidence_status='verified' AND s.status='published';\n"
        "SELECT CONCAT('effective_verified_cases=', COUNT(*)) FROM case_items c "
        "JOIN sources s ON s.id=c.source_id WHERE c.ai_evidence_status='verified' AND c.status='published' "
        "AND s.ai_evidence_status='verified' AND s.status='published';\n",
    )
    result["evidence_counts"] = evidence_counts
    return result


def deploy(client):
    required = [
        FRONTEND / "index.html",
        BACKEND,
        MIGRATION,
        AI_MIGRATION,
        AI_CATALOG_MIGRATION,
        AI_STABILIZATION_MIGRATION,
        AI_STABILIZATION_PRECHECK,
        AI_STABILIZATION_POSTCHECK,
        NGINX,
        SYSTEMD,
        EVIDENCE_WORKBENCH_MIGRATION,
        PHASE_ONE_FINALIZATION_MIGRATION,
        POLICY_APPLICABILITY_MIGRATION,
        AI_RESPONSE_DIAGNOSTICS_MIGRATION,
        AGENT_RUNTIME_PRECHECK,
        AGENT_RUNTIME_MIGRATION,
        AGENT_RUNTIME_STABILIZATION_MIGRATION,
        AGENT_RUNTIME_POSTCHECK,
        ASSISTANT_WORKSPACE_PRECHECK,
        ASSISTANT_WORKSPACE_MIGRATION,
        ASSISTANT_WORKSPACE_POSTCHECK,
    ]
    for path in required:
        if not path.exists():
            raise RuntimeError(f"Missing deployment artifact: {path}")

    stamp = time.strftime("%Y%m%d-%H%M%S")
    release = f"/opt/opc/releases/{stamp}"
    backup = f"/opt/opc/backups/{stamp}"
    backup_jar = f"/opt/opc-backend.rollback.{stamp}"
    current_link = "/opt/opc/current"
    remote_nginx = "/etc/nginx/conf.d/opc.conf"
    remote_systemd = "/etc/systemd/system/opc-backend.service"
    uploaded_nginx = f"{release}/opc.conf"
    uploaded_systemd = f"{release}/opc-backend.service"
    mutated = False
    database_mutated = False
    service_user_preexisting = False
    assistant_probe = None
    agent_probe = None
    unclassified_policy_count = None
    admin_headers = None
    agent_disable_payload = None
    agent_rollout_enabled_by_deploy = False

    _, previous_current, _ = run(
        client,
        f"if test -L '{current_link}'; then readlink -f '{current_link}'; fi",
    )

    run(client, f"mkdir -p '{release}' '{backup}'")
    backup_command = f"""set -euo pipefail
FRONTEND_SOURCE=/var/www/opc
BACKEND_SOURCE=/opt/opc-backend.jar
test -f /opt/opc/current/frontend/index.html && FRONTEND_SOURCE=/opt/opc/current/frontend
test -f /opt/opc/current/opc-backend.jar && BACKEND_SOURCE=/opt/opc/current/opc-backend.jar
cp -a "$FRONTEND_SOURCE" '{backup}/frontend'
cp -a "$BACKEND_SOURCE" '{backup}/opc-backend.jar'
cp -a /opt/opc/application.yaml '{backup}/application.yaml'
cp -a /etc/opc-backend.env '{backup}/opc-backend.env'
cp -a /etc/systemd/system/opc-backend.service '{backup}/opc-backend.service'
cp -a '{remote_nginx}' '{backup}/opc.conf'
{DB_ENV}
MYSQL_PWD="$DB_PASS" mysqldump --single-transaction --quick --no-tablespaces -u "$DB_USER" opc_platform | gzip -c > '{backup}/opc_platform.sql.gz.tmp'
gzip -t '{backup}/opc_platform.sql.gz.tmp'
mv '{backup}/opc_platform.sql.gz.tmp' '{backup}/opc_platform.sql.gz'
"""
    run(client, backup_command, timeout=300)

    sftp = client.open_sftp()
    upload_tree(sftp, FRONTEND, f"{release}/frontend")
    sftp.put(str(BACKEND), f"{release}/opc-backend.jar")
    sftp.put(str(MIGRATION), f"{release}/admin-registration.sql")
    sftp.put(str(AI_MIGRATION), f"{release}/ai-phase-one.sql")
    sftp.put(str(AI_CATALOG_MIGRATION), f"{release}/ai-model-catalog.sql")
    sftp.put(str(AI_STABILIZATION_MIGRATION), f"{release}/ai-stabilization.sql")
    sftp.put(str(AI_STABILIZATION_PRECHECK), f"{release}/ai-stabilization-precheck.sql")
    sftp.put(str(AI_STABILIZATION_POSTCHECK), f"{release}/ai-stabilization-postcheck.sql")
    sftp.put(str(EVIDENCE_WORKBENCH_MIGRATION), f"{release}/evidence-workbench.sql")
    sftp.put(str(PHASE_ONE_FINALIZATION_MIGRATION), f"{release}/phase-one-finalization.sql")
    sftp.put(str(POLICY_APPLICABILITY_MIGRATION), f"{release}/policy-applicability.sql")
    sftp.put(str(AI_RESPONSE_DIAGNOSTICS_MIGRATION), f"{release}/ai-response-diagnostics.sql")
    sftp.put(str(AGENT_RUNTIME_PRECHECK), f"{release}/agent-runtime-precheck.sql")
    sftp.put(str(AGENT_RUNTIME_MIGRATION), f"{release}/agent-runtime.sql")
    sftp.put(str(AGENT_RUNTIME_STABILIZATION_MIGRATION), f"{release}/agent-runtime-stabilization.sql")
    sftp.put(str(AGENT_RUNTIME_POSTCHECK), f"{release}/agent-runtime-postcheck.sql")
    sftp.put(str(ASSISTANT_WORKSPACE_PRECHECK), f"{release}/assistant-workspace-precheck.sql")
    sftp.put(str(ASSISTANT_WORKSPACE_MIGRATION), f"{release}/assistant-workspace.sql")
    sftp.put(str(ASSISTANT_WORKSPACE_POSTCHECK), f"{release}/assistant-workspace-postcheck.sql")
    sftp.put(str(NGINX), uploaded_nginx)
    sftp.put(str(SYSTEMD), uploaded_systemd)
    sftp.close()

    local_files = {
        f"{release}/frontend/index.html": sha256(FRONTEND / "index.html"),
        f"{release}/opc-backend.jar": sha256(BACKEND),
        f"{release}/admin-registration.sql": sha256(MIGRATION),
        f"{release}/ai-phase-one.sql": sha256(AI_MIGRATION),
        f"{release}/ai-model-catalog.sql": sha256(AI_CATALOG_MIGRATION),
        f"{release}/ai-stabilization.sql": sha256(AI_STABILIZATION_MIGRATION),
        f"{release}/ai-stabilization-precheck.sql": sha256(AI_STABILIZATION_PRECHECK),
        f"{release}/ai-stabilization-postcheck.sql": sha256(AI_STABILIZATION_POSTCHECK),
        f"{release}/evidence-workbench.sql": sha256(EVIDENCE_WORKBENCH_MIGRATION),
        f"{release}/phase-one-finalization.sql": sha256(PHASE_ONE_FINALIZATION_MIGRATION),
        f"{release}/policy-applicability.sql": sha256(POLICY_APPLICABILITY_MIGRATION),
        f"{release}/ai-response-diagnostics.sql": sha256(AI_RESPONSE_DIAGNOSTICS_MIGRATION),
        f"{release}/agent-runtime-precheck.sql": sha256(AGENT_RUNTIME_PRECHECK),
        f"{release}/agent-runtime.sql": sha256(AGENT_RUNTIME_MIGRATION),
        f"{release}/agent-runtime-stabilization.sql": sha256(AGENT_RUNTIME_STABILIZATION_MIGRATION),
        f"{release}/agent-runtime-postcheck.sql": sha256(AGENT_RUNTIME_POSTCHECK),
        f"{release}/assistant-workspace-precheck.sql": sha256(ASSISTANT_WORKSPACE_PRECHECK),
        f"{release}/assistant-workspace.sql": sha256(ASSISTANT_WORKSPACE_MIGRATION),
        f"{release}/assistant-workspace-postcheck.sql": sha256(ASSISTANT_WORKSPACE_POSTCHECK),
        uploaded_nginx: sha256(NGINX),
        uploaded_systemd: sha256(SYSTEMD),
    }
    for remote_path, local_hash in local_files.items():
        _, remote_hash, _ = run(client, f"sha256sum '{remote_path}' | awk '{{print $1}}'")
        if remote_hash.lower() != local_hash.lower():
            raise RuntimeError(f"Upload checksum mismatch: {remote_path}")

    initial_hash = bcrypt.hashpw(
        os.environ["OPC_INITIAL_ADMIN_PASSWORD"].encode("utf-8"), bcrypt.gensalt(rounds=12)
    ).decode("ascii")
    initial_username = os.environ["OPC_INITIAL_ADMIN_USERNAME"]
    seed_sql = (
        "INSERT INTO admin_accounts (username, password_hash, status) VALUES "
        f"('{initial_username}', '{initial_hash}', 'active') "
        "ON DUPLICATE KEY UPDATE username = VALUES(username);\n"
    )
    try:
        mutated = True
        run(client, "set -euo pipefail\n" + DB_ENV + f"\nMYSQL_PWD=\"$DB_PASS\" mysql -u \"$DB_USER\" opc_platform < '{release}/admin-registration.sql'")
        run(client, "set -euo pipefail\n" + DB_ENV + f"\nMYSQL_PWD=\"$DB_PASS\" mysql -u \"$DB_USER\" opc_platform < '{release}/ai-phase-one.sql'")
        run(client, "set -euo pipefail\n" + DB_ENV + f"\nMYSQL_PWD=\"$DB_PASS\" mysql -u \"$DB_USER\" opc_platform < '{release}/ai-model-catalog.sql'")
        run(client, "set -euo pipefail\n" + DB_ENV + f"\nMYSQL_PWD=\"$DB_PASS\" mysql -u \"$DB_USER\" opc_platform < '{release}/ai-stabilization-precheck.sql'")
        database_mutated = True
        run(client, "set -euo pipefail\n" + DB_ENV + f"\nMYSQL_PWD=\"$DB_PASS\" mysql -u \"$DB_USER\" opc_platform < '{release}/ai-stabilization.sql'")
        _, postcheck_output, _ = run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql --batch --skip-column-names -u \"$DB_USER\" opc_platform < '{release}/ai-stabilization-postcheck.sql'",
        )
        if postcheck_output.splitlines()[-1:] != ["0"]:
            raise RuntimeError("AI stabilization database postcheck failed")
        _, workbench_migration_output, _ = run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql --batch --skip-column-names -u \"$DB_USER\" opc_platform < '{release}/evidence-workbench.sql'",
        )
        if workbench_migration_output.splitlines()[-1:] != ["3\t3\t1"]:
            raise RuntimeError("Evidence workbench database migration verification failed")
        _, finalization_output, _ = run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql --batch --skip-column-names -u \"$DB_USER\" opc_platform < '{release}/phase-one-finalization.sql'",
        )
        _, foreign_key_count, _ = database_command(
            client,
            "SELECT COUNT(*) FROM information_schema.referential_constraints "
            "WHERE constraint_schema = DATABASE() "
            "AND constraint_name IN ('fk_case_items_source', 'fk_policies_source');\n",
        )
        if foreign_key_count.splitlines()[-1:] != ["2"]:
            raise RuntimeError("Phase-one source foreign-key migration verification failed")
        _, applicability_output, _ = run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql --batch --skip-column-names -u \"$DB_USER\" opc_platform < '{release}/policy-applicability.sql'",
        )
        applicability_result = applicability_output.splitlines()[-1].split("\t")
        if len(applicability_result) != 3 or applicability_result[:2] != ["1", "2"]:
            raise RuntimeError("Policy applicability database migration verification failed")
        unclassified_policy_count = int(applicability_result[2])
        _, diagnostics_output, _ = run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql --batch --skip-column-names -u \"$DB_USER\" opc_platform < '{release}/ai-response-diagnostics.sql'",
        )
        if diagnostics_output.splitlines()[-1:] != ["3"]:
            raise RuntimeError("AI response diagnostics database migration verification failed")
        _, agent_precheck_output, _ = run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql --batch --skip-column-names -u \"$DB_USER\" opc_platform < '{release}/agent-runtime-precheck.sql'",
        )
        if agent_precheck_output.splitlines()[-1:] != ["3"]:
            raise RuntimeError("Agent Runtime database precheck failed")
        run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql -u \"$DB_USER\" opc_platform < '{release}/agent-runtime.sql'",
        )
        run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql -u \"$DB_USER\" opc_platform < '{release}/agent-runtime-stabilization.sql'",
        )
        _, agent_postcheck_output, _ = run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql --batch --skip-column-names -u \"$DB_USER\" opc_platform < '{release}/agent-runtime-postcheck.sql'",
        )
        try:
            validate_agent_runtime_postcheck(agent_postcheck_output)
        except ValueError as exception:
            raise RuntimeError(f"Agent Runtime database postcheck failed: {exception}") from exception
        _, assistant_precheck_output, _ = run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql --batch --skip-column-names -u \"$DB_USER\" opc_platform < '{release}/assistant-workspace-precheck.sql'",
        )
        if assistant_precheck_output.splitlines()[-1:] != ["3\t5\t3"]:
            raise RuntimeError("Assistant workspace database precheck failed")
        run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql -u \"$DB_USER\" opc_platform < '{release}/assistant-workspace.sql'",
        )
        _, assistant_postcheck_output, _ = run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql --batch --skip-column-names -u \"$DB_USER\" opc_platform < '{release}/assistant-workspace-postcheck.sql'",
        )
        try:
            validate_assistant_workspace_postcheck(assistant_postcheck_output)
        except ValueError as exception:
            raise RuntimeError(f"Assistant workspace database postcheck failed: {exception}") from exception
        run(
            client,
            "set -euo pipefail\n"
            "if ! grep -q '^OPC_AI_SETTINGS_MASTER_KEY=' /etc/opc-backend.env; then\n"
            "  umask 077\n"
            "  printf 'OPC_AI_SETTINGS_MASTER_KEY=%s\\n' \"$(openssl rand -base64 32 | tr -d '\\n')\" >> /etc/opc-backend.env\n"
            "fi",
        )
        database_command(client, seed_sql)

        _, service_user_state, _ = run(
            client,
            "if id -u opc >/dev/null 2>&1; then echo present; else echo absent; fi",
        )
        service_user_preexisting = service_user_state == "present"
        run(
            client,
            "set -euo pipefail\n"
            "getent group opc >/dev/null 2>&1 || groupadd --system opc\n"
            "id -u opc >/dev/null 2>&1 || useradd --system --gid opc --home-dir /nonexistent --shell /sbin/nologin opc\n"
            "usermod --gid opc --home /nonexistent --shell /sbin/nologin opc\n"
            "install -d -o root -g root -m 0755 /opt/opc\n"
            "if grep -q '^SERVER_ADDRESS=' /etc/opc-backend.env; then\n"
            "  sed -i 's/^SERVER_ADDRESS=.*/SERVER_ADDRESS=127.0.0.1/' /etc/opc-backend.env\n"
            "else\n"
            "  printf 'SERVER_ADDRESS=127.0.0.1\\n' >> /etc/opc-backend.env\n"
            "fi\n"
            "chown root:opc /etc/opc-backend.env /opt/opc/application.yaml\n"
            "chmod 0640 /etc/opc-backend.env /opt/opc/application.yaml",
        )
        run(client, f"systemd-analyze verify '{uploaded_systemd}'")
        run(client, f"cp -a '{backup}/opc-backend.jar' '{backup_jar}'")
        run(client, f"install -o root -g root -m 0644 '{uploaded_nginx}' '{remote_nginx}'")
        run(client, f"install -o root -g root -m 0644 '{uploaded_systemd}' '{remote_systemd}'")
        run(
            client,
            f"ln -sfn '{release}' '{current_link}.next.{stamp}' && mv -Tf '{current_link}.next.{stamp}' '{current_link}'",
        )
        run(client, "nginx -t")
        run(client, "systemctl daemon-reload")
        run(client, "systemctl restart opc-backend.service")
        health_command = """set -euo pipefail
for i in $(seq 1 40); do
  if systemctl is-active --quiet opc-backend.service && curl -fsS http://127.0.0.1:8082/api/health >/dev/null; then
    exit 0
  fi
  sleep 1
done
systemctl status opc-backend.service --no-pager
exit 1
"""
        run(client, health_command, timeout=60)
        backend_runtime = assert_backend_runtime_hardened(client)
        assert_external_backend_closed()
        run(client, "nginx -t && systemctl reload nginx")

        routes = [
            "https://findopc.online/",
            "https://findopc.online/regions",
            "https://findopc.online/policies",
            "https://findopc.online/cases",
            "https://findopc.online/sources",
            "https://findopc.online/login",
            "https://admin.findopc.online/admin/login",
            "https://admin.findopc.online/admin/evidence-reviews",
        ]
        for url in routes:
            request = urllib.request.Request(url, headers={"User-Agent": "SoloFirm deployment check"})
            with urllib.request.urlopen(request, timeout=20, context=ssl.create_default_context()) as response:
                if response.status != 200:
                    raise RuntimeError(f"Route check failed: {url} -> {response.status}")

        initial_password = os.environ["OPC_INITIAL_ADMIN_PASSWORD"]
        _, login_body = request_json(
            "https://admin.findopc.online/api/admin/auth/login",
            method="POST",
            payload={"username": initial_username, "password": initial_password},
        )
        if login_body.get("code") != 200:
            raise RuntimeError("Initial administrator login failed")
        token = login_body["data"]["token"]
        admin_headers = {"X-Admin-Token": token}

        _, anonymous_evidence_queue = request_json(
            "https://admin.findopc.online/api/admin/evidence-reviews?status=legacy_unverified",
        )
        if anonymous_evidence_queue.get("code") != 401:
            raise RuntimeError("Anonymous evidence review queue request was not rejected")
        _, evidence_queue_body = request_json(
            "https://admin.findopc.online/api/admin/evidence-reviews?evidenceStatus=legacy_unverified&page=1&size=5",
            headers=admin_headers,
        )
        if evidence_queue_body.get("code") != 200:
            raise RuntimeError("Evidence review queue check failed")
        evidence_items = (evidence_queue_body.get("data") or {}).get("items") or []
        if evidence_items:
            evidence_probe = evidence_items[0]
            item_type = evidence_probe["itemType"]
            item_id = evidence_probe["itemId"]
            _, evidence_detail_body = request_json(
                f"https://admin.findopc.online/api/admin/evidence-reviews/{item_type}/{item_id}",
                headers=admin_headers,
            )
            evidence_detail = evidence_detail_body.get("data") or {}
            if evidence_detail_body.get("code") != 200 or "checks" not in evidence_detail:
                raise RuntimeError("Evidence review detail check failed")
            _, evidence_preflight_body = request_json(
                "https://admin.findopc.online/api/admin/evidence-reviews/batch/preflight",
                method="POST",
                headers=admin_headers,
                payload={
                    "items": [{
                        "itemType": item_type,
                        "itemId": item_id,
                        "expectedEvidenceStatus": evidence_detail.get("evidenceStatus"),
                        "expectedUpdatedAt": evidence_detail.get("updatedAt"),
                        "expectedVersion": evidence_detail.get("version"),
                    }],
                    "evidenceStatus": "verified",
                },
            )
            if evidence_preflight_body.get("code") != 200:
                raise RuntimeError("Evidence review batch preflight check failed")

        _, anonymous_ai_settings = request_json(
            "https://admin.findopc.online/api/admin/ai-settings",
        )
        if anonymous_ai_settings.get("code") != 401:
            raise RuntimeError("Anonymous AI settings request was not rejected")
        _, ai_settings_body = request_json(
            "https://admin.findopc.online/api/admin/ai-settings",
            headers=admin_headers,
        )
        ai_settings_data = ai_settings_body.get("data") or {}
        if ai_settings_body.get("code") != 200 or "apiKey" in ai_settings_data:
            raise RuntimeError("AI settings endpoint failed or exposed API key")
        if ai_settings_data.get("enabled") and not (
            ai_settings_data.get("apiKeyConfigured")
            and ai_settings_data.get("apiBaseUrl")
            and ai_settings_data.get("modelId")
        ):
            raise RuntimeError("Enabled AI provider is missing required production configuration")
        agent_disable_payload = ai_settings_update_payload(ai_settings_data, False)
        _, disabled_agent_body = request_json(
            "https://admin.findopc.online/api/admin/ai-settings",
            method="PUT",
            headers=admin_headers,
            payload=agent_disable_payload,
        )
        disabled_agent_data = disabled_agent_body.get("data") or {}
        if (
            disabled_agent_body.get("code") != 200
            or disabled_agent_data.get("agentEnabled")
            or disabled_agent_data.get("agentRolloutState") != "explicitly_disabled"
        ):
            raise RuntimeError("Agent Runtime could not be placed in explicit disabled rollout state")
        ai_settings_data = disabled_agent_data

        _, anonymous_analysis = request_json(
            "https://findopc.online/api/ai/case-analysis",
            method="POST",
            payload={"caseId": 0},
        )
        if anonymous_analysis.get("code") != 401:
            raise RuntimeError("Anonymous case analysis request was not rejected")

        _, anonymous_advice = request_json(
            "https://findopc.online/api/ai/entrepreneurship-advice",
            method="POST",
            payload={
                "ventureType": "solo_company",
                "regionId": 1,
                "industry": "人工智能应用",
                "stage": "validation",
                "budgetRange": "under_100k",
                "goal": "验证付费需求",
            },
        )
        if anonymous_advice.get("code") != 401:
            raise RuntimeError("Anonymous entrepreneurship advice request was not rejected")

        _, anonymous_industry_resolution = request_json(
            "https://findopc.online/api/ai/industry-resolution",
            method="POST",
            payload={"industry": "deployment-probe"},
        )
        if anonymous_industry_resolution.get("code") != 401:
            raise RuntimeError("Anonymous industry resolution request was not rejected")
        _, anonymous_agent_sessions = request_json(
            "https://findopc.online/api/ai/research/sessions",
        )
        if anonymous_agent_sessions.get("code") != 401:
            raise RuntimeError("Anonymous user reached Agent Runtime sessions")

        ai_qa_username = f"aiqa_{stamp.replace('-', '')[-10:]}"
        ai_qa_email = f"{ai_qa_username}@example.invalid"
        ai_qa_token = secrets.token_hex(32)
        ai_qa_sql = f"""
INSERT INTO platform_users (username, email, password_hash, status)
VALUES ('{ai_qa_username}', '{ai_qa_email}', NULL, 'active');
INSERT INTO user_sessions (user_id, token, expires_at)
SELECT id, '{ai_qa_token}', DATE_ADD(NOW(), INTERVAL 10 MINUTE)
FROM platform_users WHERE username = '{ai_qa_username}' LIMIT 1;
"""
        database_command(client, ai_qa_sql)
        try:
            user_headers = {"Authorization": f"Bearer {ai_qa_token}"}
            _, ordinary_admin_audit = request_json(
                "https://admin.findopc.online/api/admin/ai-agent-runs?limit=1",
                headers=user_headers,
            )
            if ordinary_admin_audit.get("code") != 401:
                raise RuntimeError("Ordinary user reached administrator Agent audit")
            database_command(
                client,
                f"UPDATE platform_users SET status='disabled' WHERE username='{ai_qa_username}';\n",
            )
            _, disabled_agent_sessions = request_json(
                "https://findopc.online/api/ai/research/sessions",
                method="POST",
                headers=user_headers,
                payload={"title": "Disabled probe", "profile": {}},
            )
            if disabled_agent_sessions.get("code") not in {401, 403}:
                raise RuntimeError("Disabled QA user reached Agent Runtime")
            database_command(
                client,
                f"UPDATE platform_users SET status='active' WHERE username='{ai_qa_username}';\n",
            )
            _, capabilities_body = request_json(
                "https://findopc.online/api/ai/capabilities",
                headers=user_headers,
            )
            provider_state = (capabilities_body.get("data") or {}).get("provider") or {}
            expected_provider_available = bool(ai_settings_data.get("enabled"))
            if (
                capabilities_body.get("code") != 200
                or provider_state.get("available") is not expected_provider_available
            ):
                raise RuntimeError("AI provider capabilities do not match the saved administrator configuration")

            _, regions_body = request_json("https://findopc.online/api/public/regions")
            hubei = next(
                (region for region in (regions_body.get("data") or []) if region.get("name") == "湖北省"),
                None,
            )
            if regions_body.get("code") != 200 or hubei is None:
                raise RuntimeError("Production assistant probe cannot resolve the Hubei region")
            readiness_payload = {
                "regionId": hubei["id"],
                "industry": "人工智能应用",
            }
            _, readiness_body = request_json(
                "https://findopc.online/api/ai/entrepreneurship-readiness",
                method="POST",
                payload=readiness_payload,
                headers=user_headers,
                timeout=60,
            )
            if readiness_body.get("code") != 200:
                raise RuntimeError("Authenticated entrepreneurship readiness probe failed")
            readiness_data = readiness_body.get("data") or {}
            advice_payload = {
                "ventureType": "solo_company",
                "regionId": hubei["id"],
                "industryTagId": (readiness_data.get("resolvedIndustryTag") or {}).get("tagId"),
                "industry": "人工智能应用",
                "stage": "validation",
                "budgetRange": "100k_500k",
                "goal": "评估在湖北省开展人工智能应用一人公司的创业可行性",
                "existingResources": "预算 10-50 万元，计划由一人公司起步",
                "userQuestion": "请结合本地案例和政策给出优先行动建议",
            }
            _, advice_body = request_json(
                "https://findopc.online/api/ai/entrepreneurship-advice",
                method="POST",
                payload=advice_payload,
                headers=user_headers,
                timeout=200,
            )
            advice_data = advice_body.get("data") or {}
            _, analysis_run_output, _ = database_command(
                client,
                "SELECT r.status, COALESCE(r.finish_reason, ''), r.total_tokens, "
                "COALESCE(r.diagnostic_code, ''), COALESCE(r.provider_request_id, '') "
                "FROM ai_analysis_runs r JOIN platform_users u ON u.id = r.user_id "
                f"WHERE u.username = '{ai_qa_username}' AND r.task_type = 'entrepreneurship_advice' "
                "ORDER BY r.id DESC LIMIT 1;\n",
            )
            analysis_run_lines = analysis_run_output.splitlines()
            analysis_run = analysis_run_lines[-1].split("\t") if len(analysis_run_lines) > 1 else []
            if advice_body.get("code") != 200:
                diagnostic = analysis_run[3] if len(analysis_run) == 5 and analysis_run[3] else "none"
                finish_reason = analysis_run[1] if len(analysis_run) == 5 and analysis_run[1] else "none"
                raise RuntimeError(
                    "Authenticated entrepreneurship advice probe did not complete "
                    f"(diagnostic={diagnostic}, finish_reason={finish_reason})"
                )
            if not (advice_data.get("summary") or advice_data.get("recommendedDirection")):
                raise RuntimeError("Authenticated entrepreneurship advice probe returned no visible result")
            if len(analysis_run) != 5 or analysis_run[0] != "completed":
                raise RuntimeError("Authenticated entrepreneurship advice analysis record is not completed")
            assistant_probe = {
                "readiness_code": readiness_body.get("code"),
                "readiness_status": readiness_data.get("readinessStatus"),
                "readiness_reason_count": len(readiness_data.get("reasons") or []),
                "advice_code": advice_body.get("code"),
                "advice_evidence_status": advice_data.get("evidenceStatus"),
                "advice_has_visible_result": bool(
                    advice_data.get("summary") or advice_data.get("recommendedDirection")
                ),
                "advice_message": advice_body.get("message"),
                "analysis_run_status": analysis_run[0],
                "analysis_run_finish_reason": analysis_run[1],
                "analysis_run_total_tokens": int(analysis_run[2]),
                "analysis_run_diagnostic_code": analysis_run[3] or None,
                "analysis_run_request_id": analysis_run[4] or None,
            }
            _, connection_test_body = request_json(
                "https://admin.findopc.online/api/admin/ai-settings/test-connection",
                method="POST",
                headers=admin_headers,
                timeout=90,
            )
            if connection_test_body.get("code") != 200 or not (connection_test_body.get("data") or {}).get("success"):
                raise RuntimeError("Production AI Provider connection test failed before Agent rollout")
            agent_enable_payload = ai_settings_update_payload(ai_settings_data, True)
            _, enabled_agent_body = request_json(
                "https://admin.findopc.online/api/admin/ai-settings",
                method="PUT",
                headers=admin_headers,
                payload=agent_enable_payload,
            )
            enabled_agent_data = enabled_agent_body.get("data") or {}
            if (
                enabled_agent_body.get("code") != 200
                or not enabled_agent_data.get("agentEnabled")
                or enabled_agent_data.get("agentRolloutState") != "explicitly_enabled"
            ):
                raise RuntimeError("Production Agent Runtime explicit rollout failed")
            ai_settings_data = enabled_agent_data
            agent_rollout_enabled_by_deploy = True
            _, agent_session_body = request_json(
                "https://findopc.online/api/ai/research/sessions",
                method="POST",
                headers=user_headers,
                payload={
                    "profile": {
                        "ventureType": "solo_company",
                        "regionId": hubei["id"],
                        "industry": "人工智能应用",
                        "stage": "validation",
                        "budgetRange": "under_100k",
                        "goal": "核验本地政策证据",
                    },
                },
            )
            agent_session_data = agent_session_body.get("data") or {}
            if agent_session_body.get("code") != 200 or not agent_session_data.get("sessionId"):
                raise RuntimeError("Production Agent session creation failed")
            agent_session_id = agent_session_data["sessionId"]
            _, agent_submit_body = request_json(
                f"https://findopc.online/api/ai/research/sessions/{agent_session_id}/messages",
                method="POST",
                headers=user_headers,
                expected_code=202,
                payload={
                    "content": "检索湖北省已核验的人工智能相关政策，并引用证据概括一项可用支持。",
                    "idempotencyKey": f"deploy-agent-{stamp.replace('-', '')}",
                },
                timeout=30,
            )
            agent_receipt = agent_submit_body.get("data") or {}
            agent_run_id = agent_receipt.get("runId")
            if agent_submit_body.get("code") != 200 or not agent_run_id:
                raise RuntimeError("Production Agent message submission failed")
            agent_run_data = {}
            for _ in range(100):
                _, agent_run_body = request_json(
                    f"https://findopc.online/api/ai/research/runs/{agent_run_id}",
                    headers=user_headers,
                    timeout=20,
                )
                if agent_run_body.get("code") != 200:
                    raise RuntimeError("Production Agent run polling failed")
                agent_run_data = agent_run_body.get("data") or {}
                if agent_run_data.get("status") in {
                    "completed", "evidence_insufficient", "failed", "cancelled", "expired"
                }:
                    break
                time.sleep(2)
            if agent_run_data.get("status") != "completed":
                raise RuntimeError(
                    "Production Agent probe did not complete "
                    f"(status={agent_run_data.get('status')}, diagnostic={agent_run_data.get('diagnosticCode')})"
                )
            if agent_run_data.get("toolCallCount", 0) < 1:
                raise RuntimeError("Production Agent probe completed without a tool call")
            if len(agent_run_data.get("citations") or []) < 1:
                raise RuntimeError("Production Agent probe completed without a legal citation")
            agent_run_id_sql = int(agent_run_id)
            _, agent_audit_output, _ = database_command(
                client,
                "WITH authorized_sources AS ("
                " SELECT DISTINCT source_id FROM ("
                "  SELECT item_source.source_id FROM ai_agent_tool_calls tc"
                "  JOIN JSON_TABLE(COALESCE(tc.result_summary_json,JSON_OBJECT()),'$.items[*]'"
                "   COLUMNS(source_id BIGINT PATH '$.sourceId')) item_source"
                f"  WHERE tc.analysis_run_id={agent_run_id_sql} AND tc.status='completed'"
                "  UNION ALL"
                "  SELECT conclusion_source.source_id FROM ai_agent_tool_calls tc"
                "  JOIN JSON_TABLE(COALESCE(tc.result_summary_json,JSON_OBJECT()),'$.conclusions[*]'"
                "   COLUMNS(source_id BIGINT PATH '$.sourceId')) conclusion_source"
                f"  WHERE tc.analysis_run_id={agent_run_id_sql} AND tc.status='completed'"
                " ) evidence_ids WHERE source_id IS NOT NULL"
                "), cited_sources AS ("
                " SELECT cited.source_id FROM ai_agent_messages message"
                " JOIN JSON_TABLE(COALESCE(message.citations_json,JSON_ARRAY()),'$[*]'"
                "  COLUMNS(source_id BIGINT PATH '$.sourceId')) cited"
                f" WHERE message.run_id={agent_run_id_sql} AND message.role='assistant'"
                ")"
                " SELECT r.status,r.provider,r.model_id,COALESCE(r.finish_reason,''),"
                " COALESCE((SELECT pc.provider_request_id FROM ai_agent_provider_calls pc"
                "   WHERE pc.analysis_run_id=r.id ORDER BY pc.round_no DESC LIMIT 1),'not_provided'),"
                " r.prompt_tokens,r.completion_tokens,r.total_tokens,r.latency_ms,r.step_count,r.tool_call_count,"
                " (SELECT COUNT(*) FROM ai_agent_tool_calls tc"
                "   WHERE tc.analysis_run_id=r.id AND tc.status='completed'),"
                " COALESCE(JSON_LENGTH(m.citations_json),0),"
                " COALESCE((SELECT pc.internal_request_id FROM ai_agent_provider_calls pc"
                "   WHERE pc.analysis_run_id=r.id ORDER BY pc.round_no DESC LIMIT 1),''),"
                " (SELECT COUNT(*) FROM ai_agent_provider_calls pc WHERE pc.analysis_run_id=r.id),"
                " (SELECT COUNT(*) FROM cited_sources cited LEFT JOIN authorized_sources allowed"
                "   ON allowed.source_id=cited.source_id WHERE allowed.source_id IS NULL)"
                " FROM ai_analysis_runs r LEFT JOIN ai_agent_messages m"
                " ON m.run_id=r.id AND m.role='assistant'"
                f" WHERE r.id={agent_run_id_sql} LIMIT 1;\n",
            )
            agent_audit_lines = agent_audit_output.splitlines()
            agent_audit = agent_audit_lines[-1].split("\t") if len(agent_audit_lines) > 1 else []
            if len(agent_audit) != 16:
                raise RuntimeError("Production Agent database audit is incomplete")
            agent_probe = {
                "question": "检索湖北省已核验的人工智能相关政策，并引用证据概括一项可用支持。",
                "run_id": int(agent_run_id),
                "session_id": int(agent_session_id),
                "status": agent_audit[0],
                "provider": agent_audit[1],
                "model": agent_audit[2],
                "finish_reason": agent_audit[3],
                "provider_request_id": agent_audit[4] or "not_provided",
                "prompt_tokens": int(agent_audit[5]),
                "completion_tokens": int(agent_audit[6]),
                "total_tokens": int(agent_audit[7]),
                "latency_ms": int(agent_audit[8]),
                "model_rounds": int(agent_audit[9]),
                "tool_call_count": int(agent_audit[10]),
                "completed_tool_count": int(agent_audit[11]),
                "citation_count": len(agent_run_data.get("citations") or []),
                "internal_request_id": agent_audit[13],
                "provider_call_count": int(agent_audit[14]),
                "unknown_citation_count": int(agent_audit[15]),
            }
            if agent_probe["citation_count"] != int(agent_audit[12]):
                raise RuntimeError("Production Agent API and database citation counts differ")
            validate_agent_probe_record(
                agent_probe,
                max_model_rounds=int(ai_settings_data.get("agentMaxModelRounds") or 4),
                max_tool_calls=int(ai_settings_data.get("agentMaxToolCalls") or 6),
            )
            _, agent_session_detail = request_json(
                f"https://findopc.online/api/ai/research/sessions/{agent_session_id}",
                headers=user_headers,
            )
            agent_detail_data = agent_session_detail.get("data") or {}
            detail_session = agent_detail_data.get("session") or {}
            latest_run = agent_detail_data.get("latestRun") or {}
            if (
                agent_session_detail.get("code") != 200
                or detail_session.get("titleMode") != "auto"
                or detail_session.get("title") in (None, "", "新研究")
                or int(latest_run.get("runId") or 0) != int(agent_run_id)
            ):
                raise RuntimeError("Production Agent session detail did not restore automatic title or latestRun")
            _, message_page = request_json(
                f"https://findopc.online/api/ai/research/sessions/{agent_session_id}/messages?limit=50",
                headers=user_headers,
            )
            if message_page.get("code") != 200 or len((message_page.get("data") or {}).get("items") or []) < 2:
                raise RuntimeError("Production Agent message pagination failed")
            _, usage_body = request_json(
                "https://findopc.online/api/ai/research/usage",
                headers=user_headers,
            )
            if usage_body.get("code") != 200 or int((usage_body.get("data") or {}).get("usedTokens") or 0) < 1:
                raise RuntimeError("Production Agent usage projection failed")
            _, updated_session = request_json(
                f"https://findopc.online/api/ai/research/sessions/{agent_session_id}",
                method="PATCH",
                headers=user_headers,
                payload={"title": "Deployment Agent probe", "pinned": True},
            )
            updated_data = updated_session.get("data") or {}
            if updated_session.get("code") != 200 or updated_data.get("titleMode") != "manual" or not updated_data.get("pinned"):
                raise RuntimeError("Production Agent rename or pin failed")
            _, history_body = request_json(
                "https://findopc.online/api/ai/research/sessions/history?scope=active&q=Deployment%20Agent%20probe&limit=10",
                headers=user_headers,
            )
            history_ids = [int(row.get("sessionId")) for row in ((history_body.get("data") or {}).get("items") or [])]
            if history_body.get("code") != 200 or int(agent_session_id) not in history_ids:
                raise RuntimeError("Production Agent server history search failed")
            lifecycle_probes = (
                ("archive", "archived"),
                ("unarchive", "active"),
                ("trash", "trash"),
                ("restore", "active"),
            )
            for action, expected_state in lifecycle_probes:
                _, lifecycle_body = request_json(
                    f"https://findopc.online/api/ai/research/sessions/{agent_session_id}/{action}",
                    method="POST",
                    headers=user_headers,
                )
                lifecycle_data = lifecycle_body.get("data") or {}
                actual_state = "trash" if lifecycle_data.get("deletedAt") else lifecycle_data.get("status")
                if lifecycle_body.get("code") != 200 or actual_state != expected_state:
                    raise RuntimeError(f"Production Agent session {action} probe failed")
            _, admin_agent_audit = request_json(
                f"https://admin.findopc.online/api/admin/ai-agent-runs/{agent_run_id}",
                headers=admin_headers,
            )
            if admin_agent_audit.get("code") != 200:
                raise RuntimeError("Administrator Agent audit detail failed")
            admin_agent_audit_text = json.dumps(admin_agent_audit, ensure_ascii=False)
            if (
                agent_probe["question"] in admin_agent_audit_text
                or re.search(r'(?i)\bsk-[a-z0-9._-]{8,}', admin_agent_audit_text)
                or re.search(r'(?i)"(?:api_?key|authorization|chain_of_thought)"\s*:', admin_agent_audit_text)
            ):
                raise RuntimeError("Administrator Agent audit exposed private or secret content")
            _, authorized_analysis = request_json(
                "https://findopc.online/api/ai/case-analysis",
                method="POST",
                payload={"caseId": 0},
                headers=user_headers,
            )
            if authorized_analysis.get("code") != 404:
                raise RuntimeError("Authenticated case analysis route did not reach case validation")
            if not expected_provider_available:
                _, disabled_industry_resolution = request_json(
                    "https://findopc.online/api/ai/industry-resolution",
                    method="POST",
                    payload={"industry": "deployment-probe"},
                    headers=user_headers,
                )
                if disabled_industry_resolution.get("code") != 503:
                    raise RuntimeError("Disabled provider did not return a controlled industry resolution state")
        finally:
            database_command(
                client,
                f"DELETE FROM user_sessions WHERE token = '{ai_qa_token}';\n"
                f"DELETE FROM ai_analysis_runs WHERE user_id IN "
                f"(SELECT id FROM platform_users WHERE username = '{ai_qa_username}');\n"
                f"DELETE FROM ai_agent_sessions WHERE user_id IN "
                f"(SELECT id FROM platform_users WHERE username = '{ai_qa_username}');\n"
                f"DELETE FROM platform_users WHERE username = '{ai_qa_username}';\n",
            )

        qa_suffix = stamp.replace("-", "")[-10:]
        approve_username = f"qaapprove_{qa_suffix}"
        reject_username = f"qareject_{qa_suffix}"
        qa_password = secrets.token_urlsafe(18)
        cleanup_sql = ""
        try:
            _, submit_body = request_json(
                "https://admin.findopc.online/api/admin/auth/register-request",
                method="POST",
                payload={"username": approve_username, "password": qa_password},
            )
            if submit_body.get("code") != 200 or submit_body["data"].get("status") != "pending":
                raise RuntimeError("Administrator registration request failed")
            approve_id = submit_body["data"]["id"]
            _, approve_body = request_json(
                f"https://admin.findopc.online/api/admin/registration-requests/{approve_id}/approve",
                method="POST",
                headers=admin_headers,
            )
            if approve_body.get("code") != 200 or approve_body["data"].get("status") != "approved":
                raise RuntimeError("Administrator approval failed")
            _, qa_login = request_json(
                "https://admin.findopc.online/api/admin/auth/login",
                method="POST",
                payload={"username": approve_username, "password": qa_password},
            )
            if qa_login.get("code") != 200:
                raise RuntimeError("Approved administrator cannot login")
            request_json(
                "https://admin.findopc.online/api/admin/auth/logout",
                method="POST",
                headers={"X-Admin-Token": qa_login["data"]["token"]},
            )

            _, reject_submit = request_json(
                "https://admin.findopc.online/api/admin/auth/register-request",
                method="POST",
                payload={"username": reject_username, "password": qa_password},
            )
            reject_id = reject_submit["data"]["id"]
            _, reject_body = request_json(
                f"https://admin.findopc.online/api/admin/registration-requests/{reject_id}/reject",
                method="POST",
                headers=admin_headers,
            )
            if reject_body.get("code") != 200 or reject_body["data"].get("status") != "rejected":
                raise RuntimeError("Administrator rejection failed")
            _, rejected_login = request_json(
                "https://admin.findopc.online/api/admin/auth/login",
                method="POST",
                payload={"username": reject_username, "password": qa_password},
            )
            if rejected_login.get("code") == 200:
                raise RuntimeError("Rejected administrator unexpectedly logged in")

            for days in (7, 30, 180):
                _, trend = request_json(f"https://admin.findopc.online/api/public/visits/trend?days={days}")
                if trend.get("code") != 200:
                    raise RuntimeError(f"Visit trend failed for {days} days")

            _, requests_body = request_json(
                "https://admin.findopc.online/api/admin/registration-requests?status=all",
                headers=admin_headers,
            )
            _, accounts_body = request_json(
                "https://admin.findopc.online/api/admin/accounts",
                headers=admin_headers,
            )
            if requests_body.get("code") != 200 or accounts_body.get("code") != 200:
                raise RuntimeError("Administrator approval list failed")
        finally:
            cleanup_sql = f"""
DELETE s FROM admin_sessions s JOIN admin_accounts a ON a.id = s.admin_id WHERE a.username IN ('{approve_username}', '{reject_username}');
DELETE FROM admin_accounts WHERE username IN ('{approve_username}', '{reject_username}');
DELETE FROM admin_registration_requests WHERE username IN ('{approve_username}', '{reject_username}');
"""
            database_command(client, cleanup_sql)
            request_json(
                "https://admin.findopc.online/api/admin/auth/logout",
                method="POST",
                headers=admin_headers,
            )

        run(client, "sed -i '/^OPC_ADMIN_PASSWORD=/d' /etc/opc-backend.env")
        audit_command = """set -euo pipefail
systemctl is-active nginx mysqld opc-backend.service
test "$(ps -eo pid=,args= | awk '/[j]ava .*opc-backend/{count++} END{print count+0}')" -eq 1
nginx -t
curl -fsS http://127.0.0.1:8082/api/health >/dev/null
test "$(stat -c '%U:%G %a' /etc/opc-backend.env)" = "root:opc 640"
test "$(stat -c '%U:%G %a' /opt/opc/application.yaml)" = "root:opc 640"
"""
        run(client, audit_command)
        backend_runtime = assert_backend_runtime_hardened(client)
        assert_external_backend_closed()
    except Exception:
        if agent_rollout_enabled_by_deploy:
            disabled_through_api = False
            if admin_headers and agent_disable_payload:
                try:
                    _, disable_body = request_json(
                        "https://admin.findopc.online/api/admin/ai-settings",
                        method="PUT",
                        headers=admin_headers,
                        payload=agent_disable_payload,
                    )
                    disabled_through_api = disable_body.get("code") == 200
                except Exception:
                    disabled_through_api = False
            if not disabled_through_api:
                database_command(
                    client,
                    "UPDATE ai_model_settings SET agent_enabled=0,"
                    "agent_rollout_state='explicitly_disabled',agent_rollout_changed_at=NOW(6),"
                    "agent_rollout_changed_by_admin_id=(SELECT id FROM admin_accounts "
                    "WHERE status='active' ORDER BY id LIMIT 1) WHERE id=1;\n"
                    "INSERT INTO ai_settings_audit(admin_id,admin_username,action,change_summary,success) "
                    "SELECT id,username,'agent_rollout_emergency_disabled',"
                    "'Deployment probe failed; emergency rollout disable',1 FROM admin_accounts "
                    "WHERE status='active' ORDER BY id LIMIT 1;\n",
                )
        if mutated:
            if previous_current:
                current_rollback = f"ln -sfn '{previous_current}' '{current_link}.rollback.{stamp}' && mv -Tf '{current_link}.rollback.{stamp}' '{current_link}'"
            else:
                current_rollback = f"rm -f '{current_link}'"
            rollback = f"""set +e
{current_rollback}
cp -a '{backup}/opc-backend.jar' /opt/opc-backend.jar
cp -a '{backup}/opc.conf' '{remote_nginx}'
cp -a '{backup}/opc-backend.env' /etc/opc-backend.env
cp -a '{backup}/application.yaml' /opt/opc/application.yaml
cp -a '{backup}/opc-backend.service' '{remote_systemd}'
systemctl daemon-reload
systemctl restart opc-backend.service
nginx -t && systemctl reload nginx
"""
            run(client, rollback, check=False, timeout=120)
            if not service_user_preexisting:
                run(
                    client,
                    "userdel opc >/dev/null 2>&1 || true; groupdel opc >/dev/null 2>&1 || true",
                    check=False,
                )
        raise

    return {
        "stamp": stamp,
        "release": release,
        "backup": backup,
        "previous_current": previous_current,
        "current": current_link,
        "current_target": release,
        "rollback_backend": backup_jar,
        "database_backup": f"{backup}/opc_platform.sql.gz",
        "database_migrations_are_additive_and_resumable": database_mutated,
        "frontend_hash": sha256(FRONTEND / "index.html"),
        "backend_hash": sha256(BACKEND),
        "backend_listener": backend_runtime["listener"],
        "backend_user": backend_runtime["user"],
        "assistant_probe": assistant_probe,
        "agent_probe": agent_probe,
        "unclassified_policy_count": unclassified_policy_count,
    }


def deploy_frontend(client):
    if not (FRONTEND / "index.html").exists():
        raise RuntimeError("Missing frontend build")
    stamp = time.strftime("%Y%m%d-%H%M%S")
    release_root = f"/opt/opc/releases/{stamp}"
    release = f"{release_root}/frontend"
    current_link = "/opt/opc/current"
    run(client, f"mkdir -p '{release}'")
    sftp = client.open_sftp()
    upload_tree(sftp, FRONTEND, release)
    sftp.close()
    local_hash = sha256(FRONTEND / "index.html")
    _, remote_hash, _ = run(client, f"sha256sum '{release}/index.html' | awk '{{print $1}}'")
    if remote_hash.lower() != local_hash.lower():
        raise RuntimeError("Frontend upload checksum mismatch")
    _, previous_current, _ = run(
        client,
        f"if test -L '{current_link}'; then readlink -f '{current_link}'; fi",
    )
    backend_source = f"{previous_current}/opc-backend.jar" if previous_current else "/opt/opc-backend.jar"
    run(client, f"cp -a '{backend_source}' '{release_root}/opc-backend.jar'")
    switched = False
    try:
        run(client, f"ln -sfn '{release_root}' '{current_link}.next.{stamp}' && mv -Tf '{current_link}.next.{stamp}' '{current_link}'")
        switched = True
        run(client, "nginx -t && systemctl reload nginx")
        for url in (
            "https://findopc.online/",
            "https://findopc.online/login",
            "https://admin.findopc.online/admin/login",
            "https://admin.findopc.online/admin/settings",
        ):
            request = urllib.request.Request(url, headers={"User-Agent": "SoloFirm frontend deployment check"})
            with urllib.request.urlopen(request, timeout=20, context=ssl.create_default_context()) as response:
                if response.status != 200:
                    raise RuntimeError(f"Frontend route check failed: {url}")
    except Exception:
        if switched:
            rollback = (
                f"ln -sfn '{previous_current}' '{current_link}.rollback.{stamp}' && "
                f"mv -Tf '{current_link}.rollback.{stamp}' '{current_link}'"
                if previous_current else f"rm -f '{current_link}'"
            )
            run(client, rollback + " && nginx -t && systemctl reload nginx", check=False)
        raise
    return {
        "stamp": stamp,
        "release": release_root,
        "previous_current": previous_current,
        "current": current_link,
        "current_target": release_root,
        "frontend_hash": local_hash,
    }


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "preflight"
    client = connect()
    try:
        if mode == "preflight":
            print(json.dumps(preflight(client), ensure_ascii=False, indent=2))
        elif mode == "deploy":
            print(json.dumps(deploy(client), ensure_ascii=False, indent=2))
        elif mode == "frontend":
            print(json.dumps(deploy_frontend(client), ensure_ascii=False, indent=2))
        else:
            raise RuntimeError(f"Unknown mode: {mode}")
    finally:
        client.close()


if __name__ == "__main__":
    main()
