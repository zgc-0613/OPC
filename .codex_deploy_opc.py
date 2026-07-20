import gzip
import hashlib
import json
import os
import secrets
import shutil
import ssl
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

import bcrypt
import paramiko


HOST = "39.105.25.189"
PORT = 22
USER = "root"
EXPECTED_FINGERPRINT = "119ae50f7f0bdb545996a90b49521db3e1404aeae327c13d64c7e67af8195672"
ROOT = Path(__file__).resolve().parent
FRONTEND = ROOT / "opc-frontend" / "dist"
BACKEND = ROOT / "opc-backend" / "target" / "opc-backend-0.0.1-SNAPSHOT.jar"
MIGRATION = ROOT / "deploy" / "sql" / "20260719_admin_registration.sql"
NGINX = ROOT / "deploy" / "nginx" / "opc.conf"


def connect():
    password = os.environ["OPC_SSH_PASSWORD"]
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
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
    key = client.get_transport().get_remote_server_key()
    actual = hashlib.sha256(key.asbytes()).hexdigest()
    if actual != EXPECTED_FINGERPRINT:
        client.close()
        raise RuntimeError("SSH host fingerprint mismatch")
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


def request_json(url, method="GET", payload=None, headers=None, expected_code=200):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request_headers = {"Accept": "application/json"}
    if body is not None:
        request_headers["Content-Type"] = "application/json"
    if headers:
        request_headers.update(headers)
    request = urllib.request.Request(url, data=body, method=method, headers=request_headers)
    context = ssl.create_default_context()
    try:
        with urllib.request.urlopen(request, timeout=20, context=context) as response:
            raw = response.read().decode("utf-8")
            status = response.status
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        status = error.code
    if status != expected_code:
        raise RuntimeError(f"Unexpected HTTP status for {url}: {status}")
    data = json.loads(raw)
    return status, data


def preflight(client):
    commands = {
        "time": "date -Iseconds",
        "services": "systemctl is-active nginx mysqld opc-backend.service",
        "nginx": "nginx -t 2>&1",
        "processes": "ps -eo pid=,args= | awk '/[j]ava .*opc-backend/{count++} END{print count+0}'",
        "disk": "df -P /opt /var/www | tail -n +2 | awk '{print $6 \" \" $5}'",
        "frontend": "test -f /var/www/opc/index.html && sha256sum /var/www/opc/index.html | awk '{print $1}'",
        "backend": "test -f /opt/opc-backend.jar && sha256sum /opt/opc-backend.jar | awk '{print $1}'",
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
    return result


def deploy(client):
    required = [FRONTEND / "index.html", BACKEND, MIGRATION, NGINX]
    for path in required:
        if not path.exists():
            raise RuntimeError(f"Missing deployment artifact: {path}")

    stamp = time.strftime("%Y%m%d-%H%M%S")
    release = f"/opt/opc/releases/{stamp}"
    backup = f"/opt/opc/backups/{stamp}"
    rollback_frontend = f"/var/www/opc.rollback.{stamp}"
    backup_jar = f"/opt/opc-backend.rollback.{stamp}"
    remote_nginx = "/etc/nginx/conf.d/opc.conf"
    uploaded_nginx = f"{release}/opc.conf"
    mutated = False

    run(client, f"mkdir -p '{release}' '{backup}'")
    backup_command = f"""set -euo pipefail
cp -a /var/www/opc '{backup}/frontend'
cp -a /opt/opc-backend.jar '{backup}/opc-backend.jar'
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
    sftp.put(str(NGINX), uploaded_nginx)
    sftp.close()

    local_files = {
        f"{release}/frontend/index.html": sha256(FRONTEND / "index.html"),
        f"{release}/opc-backend.jar": sha256(BACKEND),
        f"{release}/admin-registration.sql": sha256(MIGRATION),
        uploaded_nginx: sha256(NGINX),
    }
    for remote_path, local_hash in local_files.items():
        _, remote_hash, _ = run(client, f"sha256sum '{remote_path}' | awk '{{print $1}}'")
        if remote_hash.lower() != local_hash.lower():
            raise RuntimeError(f"Upload checksum mismatch: {remote_path}")

    run(client, "set -euo pipefail\n" + DB_ENV + f"\nMYSQL_PWD=\"$DB_PASS\" mysql -u \"$DB_USER\" opc_platform < '{release}/admin-registration.sql'")
    initial_hash = bcrypt.hashpw(
        os.environ["OPC_INITIAL_ADMIN_PASSWORD"].encode("utf-8"), bcrypt.gensalt(rounds=12)
    ).decode("ascii")
    initial_username = os.environ["OPC_INITIAL_ADMIN_USERNAME"]
    seed_sql = (
        "INSERT INTO admin_accounts (username, password_hash, status) VALUES "
        f"('{initial_username}', '{initial_hash}', 'active') "
        "ON DUPLICATE KEY UPDATE username = VALUES(username);\n"
    )
    database_command(client, seed_sql)

    try:
        run(client, f"cp -a /opt/opc-backend.jar '{backup_jar}'")
        run(client, f"install -m 0644 '{release}/opc-backend.jar' /opt/opc-backend.jar")
        run(client, f"install -m 0644 '{uploaded_nginx}' '{remote_nginx}'")
        run(client, "nginx -t")
        run(client, "systemctl restart opc-backend.service")
        mutated = True
        health_command = """set -e
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
        run(client, f"mv /var/www/opc '{rollback_frontend}' && cp -a '{release}/frontend' /var/www/opc")
        run(client, "nginx -t && systemctl reload nginx")

        routes = [
            "https://findopc.online/",
            "https://findopc.online/regions",
            "https://findopc.online/policies",
            "https://findopc.online/cases",
            "https://findopc.online/sources",
            "https://findopc.online/login",
            "https://admin.findopc.online/admin/login",
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
        audit_command = """set -e
systemctl is-active nginx mysqld opc-backend.service
test "$(ps -eo pid=,args= | awk '/[j]ava .*opc-backend/{count++} END{print count+0}')" -eq 1
nginx -t
curl -fsS http://127.0.0.1:8082/api/health >/dev/null
"""
        run(client, audit_command)
    except Exception:
        if mutated:
            rollback = f"""set +e
if test -d '{rollback_frontend}'; then
  mv /var/www/opc /var/www/opc.failed.{stamp}
  mv '{rollback_frontend}' /var/www/opc
fi
cp -a '{backup_jar}' /opt/opc-backend.jar
cp -a '{backup}/opc.conf' '{remote_nginx}'
cp -a '{backup}/opc-backend.env' /etc/opc-backend.env
systemctl restart opc-backend.service
nginx -t && systemctl reload nginx
"""
            run(client, rollback, check=False, timeout=120)
        raise

    return {
        "stamp": stamp,
        "release": release,
        "backup": backup,
        "rollback_frontend": rollback_frontend,
        "rollback_backend": backup_jar,
        "frontend_hash": sha256(FRONTEND / "index.html"),
        "backend_hash": sha256(BACKEND),
    }


def deploy_frontend(client):
    if not (FRONTEND / "index.html").exists():
        raise RuntimeError("Missing frontend build")
    stamp = time.strftime("%Y%m%d-%H%M%S")
    release = f"/opt/opc/releases/{stamp}/frontend"
    rollback = f"/var/www/opc.rollback.{stamp}"
    run(client, f"mkdir -p '{release}'")
    sftp = client.open_sftp()
    upload_tree(sftp, FRONTEND, release)
    sftp.close()
    local_hash = sha256(FRONTEND / "index.html")
    _, remote_hash, _ = run(client, f"sha256sum '{release}/index.html' | awk '{{print $1}}'")
    if remote_hash.lower() != local_hash.lower():
        raise RuntimeError("Frontend upload checksum mismatch")
    switched = False
    try:
        run(client, f"mv /var/www/opc '{rollback}' && cp -a '{release}' /var/www/opc")
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
            run(
                client,
                f"mv /var/www/opc /var/www/opc.failed.{stamp} && mv '{rollback}' /var/www/opc && nginx -t && systemctl reload nginx",
                check=False,
            )
        raise
    return {"stamp": stamp, "release": release, "rollback_frontend": rollback, "frontend_hash": local_hash}


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
