"""Deploy the current OPC build over SCP to the existing opc_platform server.

Run after building opc-frontend/dist and opc-backend/target/*.jar:
    python scripts/deploy_current_scp.py
SSH credentials come from .local-secrets/opc-deploy.env and are never printed.
"""

import hashlib
import json
import os
import runpy
import shlex
import sys
import tarfile
import tempfile
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
DEPLOY = runpy.run_path(str(ROOT / ".codex_deploy_opc.py"))
FRONTEND = ROOT / "opc-frontend" / "dist"
BACKEND = ROOT / "opc-backend" / "target" / "opc-backend-0.0.1-SNAPSHOT.jar"
MIGRATIONS = [
    ROOT / "deploy" / "sql" / "20261025_platform_sites_precheck.sql",
    ROOT / "deploy" / "sql" / "20261025_platform_sites.sql",
    ROOT / "deploy" / "sql" / "20261025_platform_sites_seed.sql",
    ROOT / "deploy" / "sql" / "20261025_platform_sites_postcheck.sql",
]


def remote(client, command, check=True):
    return DEPLOY["run"](client, command, check=check, timeout=180)


def database(client, sql):
    return DEPLOY["database_command"](client, sql)[1]


def scp_file(client, local_path, remote_dir):
    """Send one regular file using the SCP sink protocol over the pinned SSH session."""
    channel = client.get_transport().open_session()
    channel.settimeout(180)
    channel.exec_command(f"scp -t {shlex.quote(remote_dir)}")

    def acknowledge():
        status = channel.recv(1)
        if status != b"\x00":
            detail = channel.recv(4096).decode("utf-8", errors="replace") if status else "connection closed"
            raise RuntimeError(f"SCP upload failed for {local_path.name}: {detail.strip()}")

    try:
        acknowledge()
        channel.sendall(f"C0644 {local_path.stat().st_size} {local_path.name}\n".encode("utf-8"))
        acknowledge()
        with local_path.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                channel.sendall(chunk)
        channel.sendall(b"\x00")
        acknowledge()
    finally:
        channel.close()


def file_hash(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def wait_for_backend(client):
    for _ in range(30):
        code, _, _ = remote(client, "curl -fsS --max-time 2 http://127.0.0.1:8082/api/health >/dev/null", check=False)
        if code == 0:
            return
        time.sleep(2)
    raise RuntimeError("Backend health check did not recover within 60 seconds")


def main():
    if not (FRONTEND / "index.html").is_file() or not BACKEND.is_file():
        raise RuntimeError("Build the frontend and backend before deployment")
    DEPLOY["load_local_deploy_secrets"](os.environ, DEPLOY["LOCAL_DEPLOY_SECRET_FILE"])
    client = DEPLOY["connect"]()
    switched = False
    stamp = time.strftime("%Y%m%d-%H%M%S")
    release = f"/opt/opc/releases/{stamp}"
    current = "/opt/opc/current"
    try:
        _, previous, _ = remote(client, f"readlink -f {current}")
        if not previous.startswith("/opt/opc/releases/"):
            raise RuntimeError("Unexpected active release; deployment stopped")
        target = database(client, "SELECT DATABASE();").strip().splitlines()[-1]
        if target != "opc_platform":
            raise RuntimeError(f"Unexpected production database: {target}")
        baseline = database(client,
            "SELECT 'policies',COUNT(*) FROM policies UNION ALL "
            "SELECT 'cases',COUNT(*) FROM case_items UNION ALL "
            "SELECT 'universities',COUNT(*) FROM university_opc_records;")
        baseline_rows = baseline.strip().splitlines()[1:]
        remote(client, f"mkdir -p {shlex.quote(release)}")

        with tempfile.TemporaryDirectory(prefix="opc-scp-") as temporary:
            archive = Path(temporary) / "frontend.tar.gz"
            with tarfile.open(archive, "w:gz") as output:
                for path in FRONTEND.rglob("*"):
                    output.add(path, arcname=path.relative_to(FRONTEND).as_posix())
            upload_paths = [archive, BACKEND, *MIGRATIONS]
            for path in upload_paths:
                scp_file(client, path, release)
                _, actual, _ = remote(client, f"sha256sum {shlex.quote(release + '/' + path.name)} | cut -d' ' -f1")
                if actual.lower() != file_hash(path):
                    raise RuntimeError(f"Upload checksum mismatch: {path.name}")

        remote(client, f"mkdir -p {shlex.quote(release + '/frontend')} && "
                       f"tar -xzf {shlex.quote(release + '/frontend.tar.gz')} -C {shlex.quote(release + '/frontend')} && "
                       f"mv {shlex.quote(release + '/' + BACKEND.name)} {shlex.quote(release + '/opc-backend.jar')} && "
                       f"chmod -R a+rX {shlex.quote(release)}")

        sql_base = DEPLOY["DB_ENV"] + "\nMYSQL_PWD=\"$DB_PASS\" mysql -u \"$DB_USER\" opc_platform"
        for path in MIGRATIONS[:3]:
            _, output, _ = remote(client, "set -euo pipefail\n" + sql_base + f" < {shlex.quote(release + '/' + path.name)}")
            if path.name.endswith("precheck.sql") and "opc_platform" not in output:
                raise RuntimeError("Platform migration precheck did not confirm opc_platform")
        _, postcheck, _ = remote(client, "set -euo pipefail\n" + sql_base +
                                 f" < {shlex.quote(release + '/' + MIGRATIONS[3].name)}")
        site_count = database(client, "SELECT COUNT(*) FROM platform_sites WHERE source_batch='opc-nav-20261025';").strip().splitlines()[-1]
        if site_count != "37":
            raise RuntimeError(f"Unexpected platform seed count: {site_count}")
        after_rows = database(client,
            "SELECT 'policies',COUNT(*) FROM policies UNION ALL "
            "SELECT 'cases',COUNT(*) FROM case_items UNION ALL "
            "SELECT 'universities',COUNT(*) FROM university_opc_records;").strip().splitlines()[1:]
        if after_rows != baseline_rows:
            raise RuntimeError("Existing policy, case or university counts changed during migration")

        remote(client, f"ln -sfn {shlex.quote(release)} {shlex.quote(current + '.next.' + stamp)} && "
                       f"mv -Tf {shlex.quote(current + '.next.' + stamp)} {shlex.quote(current)}")
        switched = True
        remote(client, "systemctl restart opc-backend.service && nginx -t && systemctl reload nginx")
        wait_for_backend(client)
        code, _, _ = remote(client,
            "curl -fsS --max-time 10 http://127.0.0.1:8082/api/public/platform-sites >/dev/null", check=False)
        if code != 0:
            raise RuntimeError("Platform API check failed after deployment")
        print(json.dumps({"release": release, "previous_release": previous,
                          "database": target, "platform_sites": int(site_count),
                          "preserved_counts": baseline_rows, "status": "deployed"},
                         ensure_ascii=False, indent=2))
    except Exception:
        if switched:
            remote(client, f"ln -sfn {shlex.quote(previous)} {shlex.quote(current + '.rollback.' + stamp)} && "
                           f"mv -Tf {shlex.quote(current + '.rollback.' + stamp)} {shlex.quote(current)} && "
                           "systemctl restart opc-backend.service", check=False)
        raise
    finally:
        client.close()


if __name__ == "__main__":
    main()
