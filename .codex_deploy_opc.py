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
import urllib.parse
import urllib.request
from dataclasses import dataclass, replace
from pathlib import Path

import bcrypt
import paramiko

from scripts.deployment_hardening import (
    candidate_probe_failure_message,
    ensure_stable_cursor_hmac_secret,
    is_loopback_listener,
    load_local_deploy_secrets,
    require_initial_admin_credentials,
    require_cursor_hmac_secret_environment,
    require_secret_environment,
    validate_agent_evidence_probe,
    validate_candidate_agent_probe_record,
    validate_agent_probe_record,
    validate_agent_runtime_postcheck,
    validate_assistant_history_revision_postcheck,
    validate_assistant_workspace_postcheck,
    validate_atomic_start_replay,
    validate_cursor_second_page,
    validate_history_cursor_stale_response,
    validate_purge_barrier_record,
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
ASSISTANT_WORKSPACE_STABILIZATION_MIGRATION = ROOT / "deploy" / "sql" / "20260725_assistant_workspace_stabilization.sql"
ASSISTANT_WORKSPACE_POSTCHECK = ROOT / "deploy" / "sql" / "20260725_assistant_workspace_postcheck.sql"
ASSISTANT_HISTORY_REVISION_PRECHECK = ROOT / "deploy" / "sql" / "20260726_assistant_history_revision_precheck.sql"
ASSISTANT_HISTORY_REVISION_MIGRATION = ROOT / "deploy" / "sql" / "20260726_assistant_history_revision.sql"
ASSISTANT_HISTORY_REVISION_POSTCHECK = ROOT / "deploy" / "sql" / "20260726_assistant_history_revision_postcheck.sql"
NGINX = ROOT / "deploy" / "nginx" / "opc.conf"
SYSTEMD = ROOT / "deploy" / "systemd" / "opc-backend.service"
LOCAL_DEPLOY_SECRET_FILE = ROOT / ".local-secrets" / "opc-deploy.env"


@dataclass(frozen=True, repr=False)
class TemporaryProbeAdmin:
    record_id: int | None
    username: str
    token: str
    password_hash: str

    @property
    def headers(self):
        return {"X-Admin-Token": self.token}


@dataclass(frozen=True, repr=False)
class CandidateDatabase:
    name: str
    username: str
    password: str
    environment_file: str


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


def reconnect_ssh_client(client, attempts=3):
    try:
        client.close()
    except Exception:
        pass

    last_error = None
    for attempt in range(attempts):
        try:
            return connect()
        except Exception as error:
            last_error = error
            if attempt + 1 < attempts:
                time.sleep(1)
    raise RuntimeError("SSH reconnect failed") from last_error


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


def run_rollback_preserving_primary(client, command, primary_error, timeout=120):
    rollback_failed = False
    try:
        code, _, _ = run(client, command, check=False, timeout=timeout)
        rollback_failed = code != 0
    except Exception:
        rollback_failed = True
    if rollback_failed:
        primary_error.add_note(
            "Deployment rollback failed after the original deployment error; "
            "inspect the remote release state before retrying"
        )
    return primary_error


def run_recovery_step_preserving_primary(primary_error, failure_note, action):
    try:
        return True, action()
    except Exception:
        primary_error.add_note(failure_note)
        return False, None


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


def candidate_database_command(client, database_name, sql):
    if not re.fullmatch(r"opc_candidate_[0-9]{14}", database_name or ""):
        raise RuntimeError("Candidate database identity is invalid")
    return run(client, f"mysql -uroot '{database_name}'", stdin_text=sql, timeout=180)


def scoped_database_command(client, sql, database_name=None):
    return candidate_database_command(client, database_name, sql) \
        if database_name is not None else database_command(client, sql)


def select_existing_admin_count(client, database_name=None):
    _, table_output, _ = scoped_database_command(
        client,
        "SELECT COUNT(*) FROM information_schema.tables "
        "WHERE table_schema=DATABASE() AND table_name='admin_accounts';\n",
        database_name,
    )
    if int(table_output.splitlines()[-1]) == 0:
        return 0
    _, count_output, _ = scoped_database_command(
        client, "SELECT COUNT(*) FROM admin_accounts;\n", database_name)
    return int(count_output.splitlines()[-1])


def prepare_temporary_probe_admin(stamp):
    suffix = stamp.replace("-", "")[-8:]
    username = f"qa_admin_{suffix}_{secrets.token_hex(3)}"
    password = secrets.token_urlsafe(24)
    password_hash = bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=12)).decode("ascii")
    token = secrets.token_hex(32)
    return TemporaryProbeAdmin(None, username, token, password_hash)


def create_temporary_probe_admin(client, identity, database_name=None):
    if isinstance(identity, str):
        identity = prepare_temporary_probe_admin(identity)
    scoped_database_command(
        client,
        "INSERT INTO admin_accounts (username,password_hash,status) VALUES "
        f"('{identity.username}','{identity.password_hash}','active');\n"
        "INSERT INTO admin_sessions (admin_id,token,expires_at) "
        f"SELECT id,'{identity.token}',DATE_ADD(NOW(),INTERVAL 30 MINUTE) FROM admin_accounts "
        f"WHERE username='{identity.username}' AND password_hash='{identity.password_hash}' LIMIT 1;\n",
        database_name,
    )
    _, record_output, _ = scoped_database_command(
        client,
        f"SELECT id FROM admin_accounts WHERE username='{identity.username}' "
        f"AND password_hash='{identity.password_hash}' LIMIT 1;\n",
        database_name,
    )
    return replace(identity, record_id=int(record_output.splitlines()[-1]))


def cleanup_temporary_probe_admin(client, temporary_admin, database_name=None):
    record_predicate = (
        f"a.id={int(temporary_admin.record_id)} AND "
        if temporary_admin.record_id is not None else ""
    )
    try:
        scoped_database_command(
            client,
            "DELETE s FROM admin_sessions s INNER JOIN admin_accounts a ON a.id=s.admin_id "
            f"WHERE {record_predicate}a.username='{temporary_admin.username}' "
            f"AND a.password_hash='{temporary_admin.password_hash}' AND s.token='{temporary_admin.token}';\n"
            "DELETE a FROM admin_accounts a "
            f"WHERE {record_predicate}a.username='{temporary_admin.username}' "
            f"AND a.password_hash='{temporary_admin.password_hash}';\n",
            database_name,
        )
    except Exception:
        record_label = temporary_admin.record_id if temporary_admin.record_id is not None else "unresolved"
        raise RuntimeError(
            f"Temporary administrator cleanup failed for record ID {record_label}") from None


def assert_probe_admin_count_restored(client, expected_count, database_name=None):
    if select_existing_admin_count(client, database_name) != expected_count:
        raise RuntimeError("Temporary administrator count was not restored after the production probe")


def raise_probe_cleanup_failure_if_needed(primary_error, cleanup_error):
    if cleanup_error is None:
        return
    if primary_error is not None:
        primary_error.add_note(
            "Temporary administrator cleanup also failed after the original deployment error"
        )
        return
    raise cleanup_error


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


def remote_request_json(client, url, method="GET", payload=None, headers=None, expected_code=200, timeout=20):
    config = [
        "silent",
        "show-error",
        f"url = {json.dumps(url)}",
        f"request = {json.dumps(method)}",
        "header = \"Accept: application/json\"",
        "write-out = \"\\n%{http_code}\"",
        f"max-time = {int(timeout)}",
    ]
    if payload is not None:
        config.append("header = \"Content-Type: application/json\"")
        config.append(f"data = {json.dumps(json.dumps(payload, ensure_ascii=False))}")
    for name, value in (headers or {}).items():
        if not re.fullmatch(r"[A-Za-z0-9-]+", name) or "\r" in value or "\n" in value:
            raise RuntimeError("Candidate request header is invalid")
        config.append(f"header = {json.dumps(f'{name}: {value}')}")
    try:
        _, output, _ = run(
            client,
            "curl --config -",
            stdin_text="\n".join(config) + "\n",
            timeout=max(30, timeout + 10),
        )
    except Exception:
        raise RuntimeError("Candidate HTTP request failed") from None
    lines = output.splitlines()
    if len(lines) < 2 or not lines[-1].isdigit():
        raise RuntimeError("Candidate endpoint returned an invalid HTTP response")
    status = int(lines[-1])
    if status != expected_code:
        raise RuntimeError(f"Candidate endpoint returned unexpected HTTP status {status}")
    try:
        return status, json.loads("\n".join(lines[:-1]))
    except json.JSONDecodeError:
        raise RuntimeError("Candidate endpoint returned invalid JSON") from None


def prepare_candidate_database(client, stamp):
    compact = re.sub(r"[^0-9]", "", stamp)
    if len(compact) != 14:
        raise RuntimeError("Candidate release stamp is invalid")
    name = f"opc_candidate_{compact}"
    username = name
    password = secrets.token_hex(24)
    environment_file = f"/run/opc-candidate-{compact}.env"
    command = f"""set -euo pipefail
IFS= read -r candidate_password
case "$candidate_password" in *[!A-Za-z0-9]*) exit 41 ;; esac
cleanup_candidate_snapshot() {{
  rm -f '{environment_file}'
  mysql -uroot <<'SQL' >/dev/null 2>&1 || true
DROP DATABASE IF EXISTS `{name}`;
DROP USER IF EXISTS '{username}'@'127.0.0.1';
SQL
}}
trap cleanup_candidate_snapshot ERR
mysql -uroot <<SQL
DROP DATABASE IF EXISTS {name};
DROP USER IF EXISTS '{username}'@'127.0.0.1';
CREATE DATABASE {name} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER '{username}'@'127.0.0.1' IDENTIFIED BY '${{candidate_password}}';
GRANT ALL PRIVILEGES ON {name}.* TO '{username}'@'127.0.0.1';
SQL
mysqldump --single-transaction --routines --triggers opc_platform | mysql -uroot '{name}'
mysql -uroot '{name}' <<'SQL'
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM ai_agent_provider_calls;
DELETE FROM ai_agent_tool_calls;
DELETE FROM ai_analysis_runs;
DELETE FROM ai_agent_messages;
DELETE FROM ai_agent_content_purge_audits;
DELETE FROM ai_agent_sessions;
DELETE FROM user_sessions;
DELETE FROM platform_users;
DELETE FROM admin_sessions;
DELETE FROM admin_accounts;
SET FOREIGN_KEY_CHECKS=1;
UPDATE ai_model_settings SET agent_enabled=1 WHERE id=1 AND enabled=1;
SQL
umask 077
printf 'SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/{name}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai\nSPRING_DATASOURCE_USERNAME={username}\nSPRING_DATASOURCE_PASSWORD=%s\n' "$candidate_password" > '{environment_file}'
chown root:opc '{environment_file}'
chmod 0640 '{environment_file}'
trap - ERR
"""
    run(client, command, stdin_text=password + "\n", timeout=300)
    return CandidateDatabase(name, username, password, environment_file)


def cleanup_candidate_database(client, candidate):
    if candidate is None:
        return
    if not re.fullmatch(r"opc_candidate_[0-9]{14}", candidate.name or "") \
            or candidate.username != candidate.name \
            or not re.fullmatch(r"/run/opc-candidate-[0-9]{14}\.env", candidate.environment_file or ""):
        raise RuntimeError("Candidate database cleanup identity is invalid")
    code, _, _ = run(
        client,
        f"""set -euo pipefail
rm -f '{candidate.environment_file}'
mysql -uroot <<'SQL'
DROP DATABASE IF EXISTS `{candidate.name}`;
DROP USER IF EXISTS '{candidate.username}'@'127.0.0.1';
SQL
""",
        check=False,
        timeout=120,
    )
    if code != 0:
        raise RuntimeError("Candidate database cleanup failed")


def start_candidate_runtime(client, release, stamp, candidate_database):
    unit = "opc-backend-candidate-" + re.sub(r"[^0-9A-Za-z]", "", stamp)
    run(
        client,
        "set -euo pipefail\n"
        f"systemctl stop '{unit}.service' >/dev/null 2>&1 || true\n"
        f"systemd-run --unit='{unit}' --service-type=simple --uid=opc --gid=opc "
        "--property=WorkingDirectory=/opt/opc --property=EnvironmentFile=/etc/opc-backend.env "
        f"--property=EnvironmentFile='{candidate_database.environment_file}' "
        "--property=NoNewPrivileges=true --property=PrivateTmp=true "
        "/usr/bin/java -Xms128m -Xmx512m "
        f"-jar '{release}/opc-backend.jar' "
        "--spring.config.location=file:/opt/opc/application.yaml "
        "--server.address=127.0.0.1 --server.port=18082 "
        "--opc.ai.agent.worker-initial-delay-ms=0\n"
        "for i in $(seq 1 60); do\n"
        "  curl -fsS http://127.0.0.1:18082/api/health >/dev/null && exit 0\n"
        f"  systemctl is-failed --quiet '{unit}.service' && exit 1\n"
        "  sleep 1\n"
        "done\n"
        "exit 1",
        timeout=90,
    )
    return unit


def stop_candidate_runtime(client, unit):
    if not unit:
        return
    code, _, _ = run(
        client,
        f"systemctl stop '{unit}.service' && systemctl reset-failed '{unit}.service' >/dev/null 2>&1 || true",
        check=False,
        timeout=60,
    )
    if code != 0:
        raise RuntimeError("Candidate runtime cleanup failed")


def test_candidate_provider_connection(client, admin_headers):
    _, settings_body = remote_request_json(
        client, "http://127.0.0.1:18082/api/admin/ai-settings", headers=admin_headers,
    )
    settings = settings_body.get("data") or {}
    if settings_body.get("code") != 200 or not settings.get("provider") or not settings.get("modelId"):
        raise RuntimeError("PROVIDER_CONNECTION_FAILED: candidate Provider settings are incomplete")
    _, connection_body = remote_request_json(
        client,
        "http://127.0.0.1:18082/api/admin/ai-settings/test-connection",
        method="POST",
        headers=admin_headers,
        timeout=90,
    )
    if connection_body.get("code") != 200 or not (connection_body.get("data") or {}).get("success"):
        raise RuntimeError("PROVIDER_CONNECTION_FAILED: candidate Provider connection test failed")
    if not settings.get("agentEnabled"):
        raise RuntimeError("AGENT_DISABLED: candidate Agent v2 probe requires the existing rollout to be enabled")
    return settings


def raise_candidate_cleanup_failure_if_needed(primary_error, cleanup_error):
    if cleanup_error is None:
        return
    if primary_error is not None:
        primary_error.add_note(
            "Candidate probe data cleanup also failed after the original candidate diagnostic"
        )
        return
    raise cleanup_error


def cleanup_candidate_probe_data(client, username, database_name=None):
    if not re.fullmatch(r"candidate_[0-9A-Za-z_]{1,80}", username or ""):
        raise RuntimeError("Candidate cleanup identity is invalid")
    user = f"(SELECT id FROM platform_users WHERE username='{username}' LIMIT 1)"
    scoped_database_command(
        client,
        "START TRANSACTION;\n"
        "DELETE pc FROM ai_agent_provider_calls pc INNER JOIN ai_analysis_runs r "
        "ON r.id=pc.analysis_run_id "
        f"WHERE r.user_id={user};\n"
        "DELETE tc FROM ai_agent_tool_calls tc INNER JOIN ai_analysis_runs r "
        "ON r.id=tc.analysis_run_id "
        f"WHERE r.user_id={user};\n"
        f"DELETE a FROM ai_agent_content_purge_audits a WHERE a.user_id={user};\n"
        f"DELETE r FROM ai_analysis_runs r WHERE r.user_id={user};\n"
        "DELETE m FROM ai_agent_messages m INNER JOIN ai_agent_sessions s "
        f"ON s.id=m.session_id WHERE s.user_id={user};\n"
        f"DELETE s FROM ai_agent_sessions s WHERE s.user_id={user};\n"
        f"DELETE us FROM user_sessions us WHERE us.user_id={user};\n"
        f"DELETE u FROM platform_users u WHERE u.username='{username}';\n"
        "COMMIT;\n",
        database_name,
    )
    _, remaining_output, _ = scoped_database_command(
        client,
        f"SELECT COUNT(*) FROM platform_users WHERE username='{username}';\n",
        database_name,
    )
    lines = [line.strip() for line in remaining_output.splitlines() if line.strip().isdigit()]
    if not lines or int(lines[-1]) != 0:
        raise RuntimeError("Candidate probe data cleanup could not be verified")


def run_candidate_agent_v2_probe(client, stamp, settings, candidate_database):
    username = f"candidate_{stamp.replace('-', '')[-10:]}_{secrets.token_hex(2)}"
    token = secrets.token_hex(32)
    session_id = None
    run_id = None
    primary_error = None
    try:
        candidate_database_command(
            client,
            candidate_database.name,
            "INSERT INTO platform_users (username,email,password_hash,status) VALUES "
            f"('{username}','{username}@example.invalid',NULL,'active');\n"
            "INSERT INTO user_sessions (user_id,token,expires_at) "
            f"SELECT id,'{token}',DATE_ADD(NOW(),INTERVAL 15 MINUTE) FROM platform_users "
            f"WHERE username='{username}' LIMIT 1;\n",
        )
        _, region_output, _ = candidate_database_command(
            client,
            candidate_database.name,
            "SELECT id FROM regions WHERE name IN ('湖北省','湖北') ORDER BY (name='湖北省') DESC,id LIMIT 1;\n"
            "SELECT id FROM tags WHERE is_industry=1 AND name LIKE '%人工智能%' ORDER BY id LIMIT 1;\n",
        )
        ids = [line.strip() for line in region_output.splitlines() if line.strip().isdigit()]
        if len(ids) < 2:
            raise RuntimeError("CANDIDATE_EVIDENCE_CONTEXT_MISSING: region or industry tag is unavailable")
        headers = {"Authorization": f"Bearer {token}"}
        payload = {
            "profile": {
                "ventureType": "solo_company",
                "regionId": int(ids[-2]),
                "industryTagId": int(ids[-1]),
                "industry": "人工智能应用",
                "stage": "validation",
                "budgetRange": "under_100k",
                "goal": "核验本地案例、政策与优先行动",
                "resources": "具备产品开发能力",
            },
            "content": "检索湖北及武汉的人工智能创业案例和政策，并给出十万元内的优先行动建议。",
            "idempotencyKey": f"candidate-agent-{stamp.replace('-', '')}-{secrets.token_hex(3)}",
        }
        _, start_body = remote_request_json(
            client,
            "http://127.0.0.1:18082/api/ai/research/sessions/start",
            method="POST",
            headers=headers,
            expected_code=202,
            payload=payload,
            timeout=30,
        )
        start_data = start_body.get("data") or {}
        if start_body.get("code") != 200:
            raise RuntimeError("CANDIDATE_AGENT_START_FAILED")
        try:
            start_identity = validate_atomic_start_replay(start_data, start_data)
        except (TypeError, ValueError):
            raise RuntimeError("CANDIDATE_AGENT_START_FAILED: run identity is missing") from None
        session_id = start_identity["session_id"]
        run_id = start_identity["run_id"]
        run_data = {}
        for _ in range(100):
            _, run_body = remote_request_json(
                client,
                f"http://127.0.0.1:18082/api/ai/research/runs/{run_id}",
                headers=headers,
                timeout=20,
            )
            run_data = run_body.get("data") or {}
            if run_data.get("status") in {
                "completed", "evidence_insufficient", "failed", "cancelled", "expired"
            }:
                break
            time.sleep(2)
        _, audit_output, _ = candidate_database_command(
            client,
            candidate_database.name,
            "WITH authorized_sources AS ("
            " SELECT DISTINCT source_id FROM ("
            "  SELECT item_source.source_id FROM ai_agent_tool_calls tc"
            "  JOIN JSON_TABLE(COALESCE(tc.result_summary_json,JSON_OBJECT()),'$.items[*]'"
            "   COLUMNS(source_id BIGINT PATH '$.sourceId')) item_source"
            f"  WHERE tc.analysis_run_id={run_id} AND tc.status='completed'"
            "  UNION ALL"
            "  SELECT conclusion_source.source_id FROM ai_agent_tool_calls tc"
            "  JOIN JSON_TABLE(COALESCE(tc.result_summary_json,JSON_OBJECT()),'$.conclusions[*]'"
            "   COLUMNS(source_id BIGINT PATH '$.sourceId')) conclusion_source"
            f"  WHERE tc.analysis_run_id={run_id} AND tc.status='completed'"
            " ) evidence_ids WHERE source_id IS NOT NULL"
            "), cited_sources AS ("
            " SELECT cited.source_id FROM ai_agent_messages message"
            " JOIN JSON_TABLE(COALESCE(message.citations_json,JSON_ARRAY()),'$[*]'"
            "  COLUMNS(source_id BIGINT PATH '$.sourceId')) cited"
            f" WHERE message.run_id={run_id} AND message.role='assistant'"
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
            "   ON allowed.source_id=cited.source_id WHERE allowed.source_id IS NULL),"
            " r.prompt_version,r.settlement_status,r.reserved_tokens"
            " FROM ai_analysis_runs r LEFT JOIN ai_agent_messages m"
            " ON m.run_id=r.id AND m.role='assistant'"
            f" WHERE r.id={run_id} LIMIT 1;\n",
        )
        audit_lines = [line for line in audit_output.splitlines() if "\t" in line]
        audit = audit_lines[-1].split("\t") if audit_lines else []
        if len(audit) != 19:
            raise RuntimeError("CANDIDATE_AGENT_AUDIT_INCOMPLETE")
        probe_record = {
            "status": audit[0],
            "provider": audit[1],
            "model": audit[2],
            "finish_reason": audit[3],
            "provider_request_id": audit[4] or "not_provided",
            "prompt_tokens": int(audit[5]),
            "completion_tokens": int(audit[6]),
            "total_tokens": int(audit[7]),
            "latency_ms": int(audit[8]),
            "model_rounds": int(audit[9]),
            "tool_call_count": int(audit[10]),
            "completed_tool_count": int(audit[11]),
            "citation_count": len(run_data.get("citations") or []),
            "internal_request_id": audit[13],
            "provider_call_count": int(audit[14]),
            "unknown_citation_count": int(audit[15]),
            "prompt_version": audit[16],
            "settlement_status": audit[17],
            "reserved_tokens": int(audit[18]),
            "release_switched": False,
        }
        if probe_record["prompt_version"] != "agent-research-v2" \
                or probe_record["provider"] != settings.get("provider") \
                or probe_record["model"] != settings.get("modelId"):
            raise RuntimeError("CANDIDATE_AGENT_CONTRACT_MISMATCH")
        if probe_record["citation_count"] != int(audit[12]):
            raise RuntimeError("CANDIDATE_AGENT_CITATION_AUDIT_MISMATCH")
        if run_data.get("status") not in {"completed", "evidence_insufficient"}:
            diagnostic = run_data.get("diagnosticCode") or "CANDIDATE_AGENT_FAILED"
            raise RuntimeError(candidate_probe_failure_message(diagnostic, probe_record))

        _, evidence_body = remote_request_json(
            client,
            f"http://127.0.0.1:18082/api/ai/research/runs/{run_id}/evidence",
            headers=headers,
        )
        evidence = evidence_body.get("data") or {}
        if evidence.get("runId") != run_id or evidence.get("status") != run_data.get("status"):
            raise RuntimeError("CANDIDATE_AGENT_EVIDENCE_INVALID")
        evidence_items = evidence.get("items") or []
        if not isinstance(evidence_items, list):
            raise RuntimeError("CANDIDATE_AGENT_EVIDENCE_INVALID")
        available_items = [item for item in evidence_items if isinstance(item, dict) and item.get("available") is True]
        evidence_source_ids = {
            item.get("sourceId") for item in available_items
            if isinstance(item.get("sourceId"), int) and item.get("sourceId") > 0
        }
        probe_record.update({
            "evidence_case_count": sum(item.get("itemType") == "case" for item in available_items),
            "evidence_policy_count": sum(item.get("itemType") == "policy" for item in available_items),
            "evidence_source_count": len(evidence_source_ids),
        })
        coverage = (run_data.get("structuredResult") or {}).get("evidenceCoverage") or {}
        probe_record.update({
            "coverage_status": coverage.get("status"),
            "coverage_case_count": int(coverage.get("caseCount") or 0),
            "coverage_policy_count": int(coverage.get("policyCount") or 0),
            "coverage_source_count": int(coverage.get("sourceCount") or 0),
        })
        if run_data.get("status") == "completed":
            if probe_record["tool_call_count"] < 1 or not (run_data.get("citations") or []):
                raise RuntimeError("CANDIDATE_AGENT_EVIDENCE_INVALID")
            validate_agent_evidence_probe(evidence, expected_run_id=run_id)
        try:
            validate_candidate_agent_probe_record(
                probe_record,
                max_model_rounds=int(settings.get("agentMaxModelRounds") or 4),
                max_tool_calls=int(settings.get("agentMaxToolCalls") or 6),
            )
        except ValueError as error:
            raise RuntimeError(f"CANDIDATE_AGENT_CONTRACT_INVALID: {error}") from None
        return probe_record
    except Exception as error:
        primary_error = error
        raise
    finally:
        cleanup_error = None
        try:
            cleanup_candidate_probe_data(client, username, candidate_database.name)
        except Exception as error:
            cleanup_error = error
        raise_candidate_cleanup_failure_if_needed(primary_error, cleanup_error)


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
    initial_client = client
    cursor_secret = require_cursor_hmac_secret_environment(os.environ)
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
        ASSISTANT_WORKSPACE_STABILIZATION_MIGRATION,
        ASSISTANT_WORKSPACE_POSTCHECK,
        ASSISTANT_HISTORY_REVISION_PRECHECK,
        ASSISTANT_HISTORY_REVISION_MIGRATION,
        ASSISTANT_HISTORY_REVISION_POSTCHECK,
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
    release_switched = False
    database_mutated = False
    service_user_preexisting = False
    assistant_probe = None
    agent_probe = None
    candidate_probe = None
    unclassified_policy_count = None
    admin_headers = None
    agent_disable_payload = None
    agent_rollout_enabled_by_deploy = False
    temporary_probe_admin = None
    probe_admin_count_before = None
    primary_error = None

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
    sftp.put(str(ASSISTANT_WORKSPACE_STABILIZATION_MIGRATION), f"{release}/assistant-workspace-stabilization.sql")
    sftp.put(str(ASSISTANT_WORKSPACE_POSTCHECK), f"{release}/assistant-workspace-postcheck.sql")
    sftp.put(str(ASSISTANT_HISTORY_REVISION_PRECHECK), f"{release}/assistant-history-revision-precheck.sql")
    sftp.put(str(ASSISTANT_HISTORY_REVISION_MIGRATION), f"{release}/assistant-history-revision.sql")
    sftp.put(str(ASSISTANT_HISTORY_REVISION_POSTCHECK), f"{release}/assistant-history-revision-postcheck.sql")
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
        f"{release}/assistant-workspace-stabilization.sql": sha256(ASSISTANT_WORKSPACE_STABILIZATION_MIGRATION),
        f"{release}/assistant-workspace-postcheck.sql": sha256(ASSISTANT_WORKSPACE_POSTCHECK),
        f"{release}/assistant-history-revision-precheck.sql": sha256(ASSISTANT_HISTORY_REVISION_PRECHECK),
        f"{release}/assistant-history-revision.sql": sha256(ASSISTANT_HISTORY_REVISION_MIGRATION),
        f"{release}/assistant-history-revision-postcheck.sql": sha256(ASSISTANT_HISTORY_REVISION_POSTCHECK),
        uploaded_nginx: sha256(NGINX),
        uploaded_systemd: sha256(SYSTEMD),
    }
    for remote_path, local_hash in local_files.items():
        _, remote_hash, _ = run(client, f"sha256sum '{remote_path}' | awk '{{print $1}}'")
        if remote_hash.lower() != local_hash.lower():
            raise RuntimeError(f"Upload checksum mismatch: {remote_path}")

    existing_admin_count = select_existing_admin_count(client)
    initial_admin_credentials = require_initial_admin_credentials(os.environ, existing_admin_count)
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
        assistant_precheck_fields = assistant_precheck_output.splitlines()[-1].split("\t")
        if len(assistant_precheck_fields) != 5 or assistant_precheck_fields[:3] != ["3", "5", "3"]:
            raise RuntimeError("Assistant workspace database precheck failed")
        existing_workspace_columns = int(assistant_precheck_fields[3])
        existing_purge_audit_tables = int(assistant_precheck_fields[4])
        if existing_workspace_columns < 0 or existing_workspace_columns > 6:
            raise RuntimeError("Assistant workspace database precheck returned an invalid workspace column count")
        if existing_purge_audit_tables not in (0, 1):
            raise RuntimeError("Assistant workspace database precheck returned an invalid purge audit table count")
        assistant_rollout_at = (
            "2026-07-25 21:56:34.000000"
            if existing_workspace_columns == 6
            else time.strftime("%Y-%m-%d %H:%M:%S.000000", time.strptime(stamp, "%Y%m%d-%H%M%S"))
        )
        database_command(
            client,
            "INSERT INTO app_settings (setting_key,setting_value,`sensitive`) VALUES "
            f"('migration.assistant_workspace_rollout_at','{assistant_rollout_at}',0) "
            "ON DUPLICATE KEY UPDATE setting_key=VALUES(setting_key);\n",
        )
        run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql -u \"$DB_USER\" opc_platform < '{release}/assistant-workspace.sql'",
        )
        run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql -u \"$DB_USER\" opc_platform < '{release}/assistant-workspace-stabilization.sql'",
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
        _, history_revision_precheck_output, _ = run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql --batch --skip-column-names -u \"$DB_USER\" opc_platform < '{release}/assistant-history-revision-precheck.sql'",
        )
        history_revision_precheck = history_revision_precheck_output.splitlines()[-1].split("\t")
        if len(history_revision_precheck) != 2 or history_revision_precheck[0] != "1" \
                or history_revision_precheck[1] not in ("0", "1"):
            raise RuntimeError("Assistant history revision database precheck failed")
        run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql -u \"$DB_USER\" opc_platform < '{release}/assistant-history-revision.sql'",
        )
        _, history_revision_postcheck_output, _ = run(
            client,
            "set -euo pipefail\n" + DB_ENV
            + f"\nMYSQL_PWD=\"$DB_PASS\" mysql --batch --skip-column-names -u \"$DB_USER\" opc_platform < '{release}/assistant-history-revision-postcheck.sql'",
        )
        try:
            validate_assistant_history_revision_postcheck(history_revision_postcheck_output)
        except ValueError as exception:
            raise RuntimeError(
                f"Assistant history revision database postcheck failed: {exception}"
            ) from exception
        run(
            client,
            "set -euo pipefail\n"
            "if ! grep -q '^OPC_AI_SETTINGS_MASTER_KEY=' /etc/opc-backend.env; then\n"
            "  umask 077\n"
            "  printf 'OPC_AI_SETTINGS_MASTER_KEY=%s\\n' \"$(openssl rand -base64 32 | tr -d '\\n')\" >> /etc/opc-backend.env\n"
            "fi",
        )
        if initial_admin_credentials is not None:
            initial_hash = bcrypt.hashpw(
                initial_admin_credentials.password.encode("utf-8"),
                bcrypt.gensalt(rounds=12),
            ).decode("ascii")
            database_command(
                client,
                "INSERT INTO admin_accounts (username, password_hash, status) VALUES "
                f"('{initial_admin_credentials.username}', '{initial_hash}', 'active');\n",
            )

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
        run(
            client,
            """set -euo pipefail
IFS= read -r cursor_secret
existing_cursor_secret=$(awk -F= '$1=="OPC_ASSISTANT_CURSOR_HMAC_SECRET" {
  print substr($0,index($0,"=")+1); exit
}' /etc/opc-backend.env)
if test -n "$existing_cursor_secret"; then
  case "$existing_cursor_secret" in
    *[!A-Za-z0-9._~+/=-]*) exit 42 ;;
  esac
  test "${#existing_cursor_secret}" -ge 32
  exit 0
fi
umask 077
printf 'OPC_ASSISTANT_CURSOR_HMAC_SECRET=%s\n' "$cursor_secret" >> /etc/opc-backend.env
chown root:opc /etc/opc-backend.env
chmod 0640 /etc/opc-backend.env
""",
            stdin_text=cursor_secret + "\n",
        )
        run(client, f"systemd-analyze verify '{uploaded_systemd}'")
        run(client, f"cp -a '{backup}/opc-backend.jar' '{backup_jar}'")

        candidate_database = None
        candidate_admin = prepare_temporary_probe_admin(stamp)
        candidate_admin_count = 0
        candidate_unit = None
        candidate_error = None
        try:
            candidate_database = prepare_candidate_database(client, stamp)
            candidate_admin_count = select_existing_admin_count(client, candidate_database.name)
            candidate_admin = create_temporary_probe_admin(
                client, candidate_admin, candidate_database.name)
            candidate_unit = start_candidate_runtime(client, release, stamp, candidate_database)
            candidate_settings = test_candidate_provider_connection(client, candidate_admin.headers)
            candidate_probe = run_candidate_agent_v2_probe(
                client, stamp, candidate_settings, candidate_database)
        except Exception as error:
            candidate_error = error
            raise
        finally:
            candidate_cleanup_error = None
            try:
                stop_candidate_runtime(client, candidate_unit)
            except Exception as error:
                candidate_cleanup_error = error
            try:
                if candidate_database is not None:
                    cleanup_temporary_probe_admin(
                        client, candidate_admin, candidate_database.name)
                    assert_probe_admin_count_restored(
                        client, candidate_admin_count, candidate_database.name)
            except Exception as error:
                candidate_cleanup_error = candidate_cleanup_error or error
            try:
                cleanup_candidate_database(client, candidate_database)
            except Exception as error:
                candidate_cleanup_error = candidate_cleanup_error or error
            if candidate_cleanup_error is not None:
                if candidate_error is not None:
                    candidate_error.add_note(
                        "Candidate runtime or temporary identity cleanup also failed"
                    )
                else:
                    raise candidate_cleanup_error

        run(client, f"install -o root -g root -m 0644 '{uploaded_nginx}' '{remote_nginx}'")
        run(client, f"install -o root -g root -m 0644 '{uploaded_systemd}' '{remote_systemd}'")
        run(
            client,
            f"ln -sfn '{release}' '{current_link}.next.{stamp}' && mv -Tf '{current_link}.next.{stamp}' '{current_link}'",
        )
        release_switched = True
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
        client = reconnect_ssh_client(client)
        run(
            client,
            """set -euo pipefail
backend_pid=$(systemctl show -p MainPID --value opc-backend.service)
test "$backend_pid" -gt 1
cursor_length=$(tr '\\0' '\\n' < "/proc/$backend_pid/environ" | awk -F= '
  $1=="OPC_ASSISTANT_CURSOR_HMAC_SECRET" {
    print length(substr($0,index($0,"=")+1)); exit
  }')
test "${cursor_length:-0}" -ge 32
""",
        )
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

        probe_admin_count_before = select_existing_admin_count(client)
        temporary_probe_admin = prepare_temporary_probe_admin(stamp)
        temporary_probe_admin = create_temporary_probe_admin(client, temporary_probe_admin)
        admin_headers = temporary_probe_admin.headers

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
        if candidate_probe is None or ai_settings_data.get("provider") != candidate_probe.get("provider") \
                or ai_settings_data.get("modelId") != candidate_probe.get("model"):
            raise RuntimeError("Production Provider settings changed after the candidate gate")
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
            resolved_industry_tag_id = (readiness_data.get("resolvedIndustryTag") or {}).get("tagId")
            if not isinstance(resolved_industry_tag_id, int) or resolved_industry_tag_id <= 0:
                raise RuntimeError("Production assistant probe did not resolve a canonical industry tag")
            advice_payload = {
                "ventureType": "solo_company",
                "regionId": hubei["id"],
                "industryTagId": resolved_industry_tag_id,
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
                    f"(response_code={advice_body.get('code')}, diagnostic={diagnostic}, "
                    f"finish_reason={finish_reason})"
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
            agent_question = (
                "请检索湖北省及武汉市已核验的人工智能创业案例和适用政策，"
                "比较证据并给出结合10万元以内预算的优先行动建议。"
            )
            agent_start_payload = {
                "profile": {
                    "ventureType": "solo_company",
                    "regionId": hubei["id"],
                    "industryTagId": resolved_industry_tag_id,
                    "industry": "人工智能应用",
                    "stage": "validation",
                    "budgetRange": "under_100k",
                    "goal": "结合本地案例、政策和预算形成优先行动建议",
                    "resources": "具备产品开发能力，尚无稳定渠道",
                },
                "content": agent_question,
                "idempotencyKey": f"deploy-agent-{stamp.replace('-', '')}",
            }
            _, agent_start_body = request_json(
                "https://findopc.online/api/ai/research/sessions/start",
                method="POST",
                headers=user_headers,
                expected_code=202,
                payload=agent_start_payload,
                timeout=30,
            )
            _, agent_replay_body = request_json(
                "https://findopc.online/api/ai/research/sessions/start",
                method="POST",
                headers=user_headers,
                expected_code=202,
                payload=agent_start_payload,
                timeout=30,
            )
            agent_start_data = agent_start_body.get("data") or {}
            agent_replay_data = agent_replay_body.get("data") or {}
            if agent_start_body.get("code") != 200 or agent_replay_body.get("code") != 200:
                raise RuntimeError("Production Agent atomic start failed")
            start_identity = validate_atomic_start_replay(agent_start_data, agent_replay_data)
            agent_session_id = start_identity["session_id"]
            agent_run_id = start_identity["run_id"]
            _, active_session_body = request_json(
                f"https://findopc.online/api/ai/research/sessions/{agent_session_id}",
                headers=user_headers,
            )
            active_detail = active_session_body.get("data") or {}
            observed_run = active_detail.get("activeRun") or active_detail.get("latestRun") or {}
            if active_session_body.get("code") != 200 or int(observed_run.get("runId") or 0) != int(agent_run_id):
                raise RuntimeError("Production Agent atomic start was not restorable as active/latest")
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
                token_usage = agent_run_data.get("tokenUsage") or {}
                raise RuntimeError(
                    "Production Agent probe did not complete "
                    f"(status={agent_run_data.get('status')}, diagnostic={agent_run_data.get('diagnosticCode')}, "
                    f"stage={agent_run_data.get('currentStage')}, rounds={agent_run_data.get('stepCount')}, "
                    f"tools={agent_run_data.get('toolCallCount')}, finish={agent_run_data.get('finishReason')}, "
                    f"tokens={token_usage.get('totalTokens')})"
                )
            if agent_run_data.get("toolCallCount", 0) < 1:
                raise RuntimeError("Production Agent probe completed without a tool call")
            if len(agent_run_data.get("citations") or []) < 1:
                raise RuntimeError("Production Agent probe completed without a legal citation")
            _, agent_evidence_body = request_json(
                f"https://findopc.online/api/ai/research/runs/{agent_run_id}/evidence",
                headers=user_headers,
                timeout=20,
            )
            if agent_evidence_body.get("code") != 200:
                raise RuntimeError("Production Agent evidence endpoint failed")
            agent_evidence_data = agent_evidence_body.get("data") or {}
            validate_agent_evidence_probe(agent_evidence_data, expected_run_id=int(agent_run_id))
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
                "   ON allowed.source_id=cited.source_id WHERE allowed.source_id IS NULL),"
                " r.prompt_version"
                " FROM ai_analysis_runs r LEFT JOIN ai_agent_messages m"
                " ON m.run_id=r.id AND m.role='assistant'"
                f" WHERE r.id={agent_run_id_sql} LIMIT 1;\n",
            )
            agent_audit_lines = agent_audit_output.splitlines()
            agent_audit = agent_audit_lines[-1].split("\t") if len(agent_audit_lines) > 1 else []
            if len(agent_audit) != 17:
                raise RuntimeError("Production Agent database audit is incomplete")
            agent_probe = {
                "question": agent_question,
                "run_id": int(agent_run_id),
                "session_id": int(agent_session_id),
                "status": agent_audit[0],
                "provider": agent_audit[1],
                "model": agent_audit[2],
                "prompt_version": agent_audit[16],
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
                "case_evidence_count": int((agent_evidence_data.get("groups") or {}).get("case") or 0),
                "policy_evidence_count": int((agent_evidence_data.get("groups") or {}).get("policy") or 0),
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

            history_sequence_sql = " UNION ALL ".join(
                f"SELECT {value} AS n" if value == 1 else f"SELECT {value}"
                for value in range(1, 56)
            )
            database_command(
                client,
                "INSERT INTO ai_agent_sessions "
                "(user_id,title,title_mode,status,created_at,last_message_at) "
                "SELECT u.id,CONCAT('Deployment pagination ',seq_rows.n),'manual','active',NOW(6),NOW(6) "
                f"FROM platform_users u JOIN ({history_sequence_sql}) seq_rows ON 1=1 "
                f"WHERE u.username='{ai_qa_username}';\n",
            )
            _, pagination_session_output, _ = database_command(
                client,
                "SELECT sessions.id FROM ai_agent_sessions sessions "
                "JOIN platform_users users ON users.id=sessions.user_id "
                f"WHERE users.username='{ai_qa_username}' "
                "AND sessions.title='Deployment pagination 2' LIMIT 1;\n",
            )
            pagination_session_id = int(pagination_session_output.splitlines()[-1])
            message_sequence_sql = " UNION ALL ".join(
                f"SELECT {value} AS n" if value == 1 else f"SELECT {value}"
                for value in range(1, 56)
            )
            database_command(
                client,
                "INSERT INTO ai_agent_messages (session_id,role,content,status,sequence_no) "
                f"SELECT {pagination_session_id},"
                "IF(MOD(seq_rows.n,2)=1,'user','assistant'),"
                "CONCAT('Deployment message ',seq_rows.n),'completed',seq_rows.n "
                f"FROM ({message_sequence_sql}) seq_rows;\n",
            )

            history_query = urllib.parse.quote("Deployment pagination", safe="")
            _, first_history_body = request_json(
                "https://findopc.online/api/ai/research/sessions/history"
                f"?scope=active&q={history_query}&limit=50",
                headers=user_headers,
            )
            first_history = first_history_body.get("data") or {}
            first_history_cursor = urllib.parse.quote(str(first_history.get("nextCursor") or ""), safe="")
            if first_history_body.get("code") != 200 or not first_history_cursor:
                raise RuntimeError(
                    "Production Agent history first page failed "
                    f"(response_code={first_history_body.get('code')}, "
                    f"item_count={len(first_history.get('items') or [])}, "
                    f"has_more={first_history.get('hasMore')}, "
                    f"cursor_present={bool(first_history.get('nextCursor'))})"
                )
            database_command(
                client,
                "INSERT INTO ai_agent_messages (session_id,role,content,status,sequence_no) "
                "SELECT sessions.id,'user','Concurrent pagination activity','completed',1 "
                "FROM ai_agent_sessions sessions JOIN platform_users users ON users.id=sessions.user_id "
                f"WHERE users.username='{ai_qa_username}' "
                "AND sessions.title='Deployment pagination 1' LIMIT 1;\n"
                "UPDATE ai_agent_sessions sessions JOIN platform_users users ON users.id=sessions.user_id "
                "SET sessions.last_message_at=NOW(6) "
                f"WHERE users.username='{ai_qa_username}' "
                "AND sessions.title='Deployment pagination 1';\n",
            )
            _, second_history_body = request_json(
                "https://findopc.online/api/ai/research/sessions/history"
                f"?scope=active&q={history_query}&limit=50&cursor={first_history_cursor}",
                headers=user_headers,
            )
            if second_history_body.get("code") != 200:
                raise RuntimeError("Production Agent history second page failed")
            validate_cursor_second_page(
                first_history,
                second_history_body.get("data") or {},
                id_field="sessionId",
                cursor_field="nextCursor",
                expected_total=55,
            )

            _, metadata_history_body = request_json(
                "https://findopc.online/api/ai/research/sessions/history"
                f"?scope=active&q={history_query}&limit=50",
                headers=user_headers,
            )
            metadata_history = metadata_history_body.get("data") or {}
            metadata_items = metadata_history.get("items") or []
            metadata_cursor = urllib.parse.quote(
                str(metadata_history.get("nextCursor") or ""), safe=""
            )
            if metadata_history_body.get("code") != 200 or not metadata_items or not metadata_cursor:
                raise RuntimeError("Production Agent metadata history first page failed")
            metadata_session_id = metadata_items[0].get("sessionId")
            _, pin_history_body = request_json(
                f"https://findopc.online/api/ai/research/sessions/{metadata_session_id}",
                method="PATCH",
                payload={"pinned": True},
                headers=user_headers,
            )
            if pin_history_body.get("code") != 200:
                raise RuntimeError("Production Agent metadata mutation failed")
            _, stale_history_body = request_json(
                "https://findopc.online/api/ai/research/sessions/history"
                f"?scope=active&q={history_query}&limit=50&cursor={metadata_cursor}",
                headers=user_headers,
            )
            validate_history_cursor_stale_response(stale_history_body)

            _, first_message_body = request_json(
                f"https://findopc.online/api/ai/research/sessions/{pagination_session_id}/messages?limit=50",
                headers=user_headers,
            )
            first_messages = first_message_body.get("data") or {}
            before_sequence = first_messages.get("nextBeforeSequence")
            if first_message_body.get("code") != 200 or before_sequence in (None, ""):
                raise RuntimeError("Production Agent message first page failed")
            _, second_message_body = request_json(
                f"https://findopc.online/api/ai/research/sessions/{pagination_session_id}/messages"
                f"?limit=50&beforeSequence={int(before_sequence)}",
                headers=user_headers,
            )
            if second_message_body.get("code") != 200:
                raise RuntimeError("Production Agent message second page failed")
            validate_cursor_second_page(
                first_messages,
                second_message_body.get("data") or {},
                id_field="sequenceNo",
                cursor_field="nextBeforeSequence",
                expected_total=55,
            )
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

            _, final_trash_body = request_json(
                f"https://findopc.online/api/ai/research/sessions/{agent_session_id}/trash",
                method="POST",
                headers=user_headers,
            )
            if final_trash_body.get("code") != 200:
                raise RuntimeError("Production Agent purge setup failed")
            _, permanent_delete_body = request_json(
                f"https://findopc.online/api/ai/research/sessions/{agent_session_id}/permanent",
                method="DELETE",
                headers=user_headers,
            )
            if permanent_delete_body.get("code") != 200:
                raise RuntimeError("Production Agent permanent purge failed")
            _, purged_detail_body = request_json(
                f"https://findopc.online/api/ai/research/sessions/{agent_session_id}",
                headers=user_headers,
            )
            _, purged_messages_body = request_json(
                f"https://findopc.online/api/ai/research/sessions/{agent_session_id}/messages?limit=50",
                headers=user_headers,
            )
            if purged_detail_body.get("code") != 404 or purged_messages_body.get("code") != 404:
                raise RuntimeError("Production Agent purged content remained API-readable")
            _, purge_barrier_output, _ = database_command(
                client,
                "SELECT COALESCE(DATE_FORMAT(s.purged_at,'%Y-%m-%d %H:%i:%s.%f'),''),"
                "s.content_generation,r.session_content_generation,"
                "(SELECT COUNT(*) FROM ai_agent_messages m WHERE m.session_id=s.id "
                " AND (m.content<>'[已删除]' OR COALESCE(JSON_LENGTH(m.citations_json),0)<>0)),"
                "(SELECT COUNT(*) FROM ai_agent_tool_calls tc WHERE tc.analysis_run_id=r.id "
                " AND (COALESCE(JSON_LENGTH(tc.arguments_json),0)<>0 "
                "   OR COALESCE(JSON_LENGTH(tc.result_summary_json),0)<>0 OR tc.evidence_hash IS NOT NULL)),"
                "IF(r.result_json IS NULL,0,1),"
                "IF(r.session_content_generation=s.content_generation,1,0) "
                "FROM ai_agent_sessions s JOIN ai_analysis_runs r ON r.session_id=s.id "
                f"WHERE s.id={int(agent_session_id)} AND r.id={int(agent_run_id)} LIMIT 1;\n",
            )
            purge_barrier_fields = purge_barrier_output.splitlines()[-1].split("\t")
            if len(purge_barrier_fields) != 7:
                raise RuntimeError("Production Agent purge barrier audit is incomplete")
            validate_purge_barrier_record({
                "purged_at": purge_barrier_fields[0],
                "session_generation": purge_barrier_fields[1],
                "run_generation": purge_barrier_fields[2],
                "readable_messages": purge_barrier_fields[3],
                "readable_tools": purge_barrier_fields[4],
                "readable_run_results": purge_barrier_fields[5],
                "generation_matches": purge_barrier_fields[6],
            })
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
        cleanup_temporary_probe_admin(client, temporary_probe_admin)
        temporary_probe_admin = None
        assert_probe_admin_count_restored(client, probe_admin_count_before)
    except Exception as error:
        primary_error = error
        if mutated or agent_rollout_enabled_by_deploy or temporary_probe_admin is not None:
            reconnected, recovery_client = run_recovery_step_preserving_primary(
                primary_error,
                "SSH reconnect failed after the original deployment error",
                lambda: reconnect_ssh_client(client),
            )
            if reconnected:
                client = recovery_client
        if agent_rollout_enabled_by_deploy:
            disabled_through_api = False
            if admin_headers and agent_disable_payload:
                disable_request_ok, disable_response = run_recovery_step_preserving_primary(
                    primary_error,
                    "Emergency Agent API disable failed after the original deployment error",
                    lambda: request_json(
                        "https://admin.findopc.online/api/admin/ai-settings",
                        method="PUT",
                        headers=admin_headers,
                        payload=agent_disable_payload,
                    ),
                )
                if disable_request_ok:
                    _, disable_body = disable_response
                    disabled_through_api = disable_body.get("code") == 200
            if not disabled_through_api:
                emergency_sql = (
                    "UPDATE ai_model_settings SET agent_enabled=0,"
                    "agent_rollout_state='explicitly_disabled',agent_rollout_changed_at=NOW(6),"
                    "agent_rollout_changed_by_admin_id=(SELECT id FROM admin_accounts "
                    "WHERE status='active' ORDER BY id LIMIT 1) WHERE id=1;\n"
                    "INSERT INTO ai_settings_audit(admin_id,admin_username,action,change_summary,success) "
                    "SELECT id,username,'agent_rollout_emergency_disabled',"
                    "'Deployment probe failed; emergency rollout disable',1 FROM admin_accounts "
                    "WHERE status='active' ORDER BY id LIMIT 1;\n"
                )
                run_recovery_step_preserving_primary(
                    primary_error,
                    "Emergency Agent database disable failed after the original deployment error",
                    lambda: database_command(client, emergency_sql),
                )
        if release_switched:
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
            run_rollback_preserving_primary(client, rollback, primary_error, timeout=120)
            if not service_user_preexisting:
                run_recovery_step_preserving_primary(
                    primary_error,
                    "System account cleanup failed after the original deployment error",
                    lambda: run(
                        client,
                        "userdel opc >/dev/null 2>&1 || true; groupdel opc >/dev/null 2>&1 || true",
                        check=False,
                    ),
                )
        raise
    finally:
        cleanup_error = None
        if temporary_probe_admin is not None:
            try:
                cleanup_temporary_probe_admin(client, temporary_probe_admin)
            except Exception as error:
                cleanup_error = error
        if client is not initial_client:
            if primary_error is None:
                client.close()
            else:
                run_recovery_step_preserving_primary(
                    primary_error,
                    "SSH client cleanup failed after the original deployment error",
                    client.close,
                )
        raise_probe_cleanup_failure_if_needed(primary_error, cleanup_error)

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
        "candidate_probe": candidate_probe,
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
    load_local_deploy_secrets(os.environ, LOCAL_DEPLOY_SECRET_FILE)
    if mode == "deploy":
        ensure_stable_cursor_hmac_secret(os.environ, LOCAL_DEPLOY_SECRET_FILE)
        require_cursor_hmac_secret_environment(os.environ)
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
