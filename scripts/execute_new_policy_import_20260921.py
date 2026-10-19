import argparse
import importlib.util
import json
import os
import sys
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "outputs" / "policy-new-20260921"
REVIEWER = "manual-workbook-20260921"
BATCH = "policy-new-20260921"


def deploy_module():
    if str(ROOT) not in sys.path:
        sys.path.insert(0, str(ROOT))
    spec = importlib.util.spec_from_file_location("opc_deploy", ROOT / ".codex_deploy_opc.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def assert_checks(output):
    expected = [
        "batch_policies=7",
        "batch_sources=7",
        "formal=3,consultation=2,standard=1,other=1",
        "published=5,consultation_status=2",
        "verified_policies=7,verified_sources=7",
        "missing=0",
        "tagless=0",
        "topic_relations=40,topic_names=7",
        "duplicate_urls=0",
        "total_policies=110,formal_published=74,consultations=18,standards=6",
    ]
    missing = [value for value in expected if value not in output]
    if missing:
        raise RuntimeError(f"verification failed, missing {missing}:\n{output}")


def production_precheck(deploy, client):
    sql = f"""
SELECT CONCAT('total=',COUNT(*),',max_id=',MAX(id)) FROM policies;
SELECT CONCAT('existing_batch=',COUNT(*)) FROM policies WHERE reviewer='{REVIEWER}';
SELECT CONCAT('existing_sources=',COUNT(*)) FROM sources WHERE notes LIKE '%import_batch={BATCH}%';
SELECT CONCAT('target_regions=',COUNT(*)) FROM regions WHERE level='province' AND name IN ('湖北省','浙江省','天津市','河南省','贵州省');
SELECT CONCAT('duplicate_urls=',COUNT(*)) FROM policies WHERE original_url IN (
'https://www.jiangan.gov.cn/jaxxw/jazx/bmdt/202609/t20260914_2847174.shtml',
'https://www.qz.gov.cn/col/col1229470839/art/2026/art_2dc52c0a3d70447ab7c1bc59b1241033.html',
'http://tj.xinhuanet.com/20260911/19237095f448460f98de16a176e64fa1/c.html',
'https://m.zzhkgq.gov.cn/2026/09-11/3659523.html',
'https://hrss.tj.gov.cn/zhengwugongkai/zhengcezhinan/zxwjnew/202609/t20260901_7363547.html',
'http://www.jiashan.gov.cn/col/col1229824795/art/2026/art_bd7ed426856447e19a135927afe0abc5.html',
'https://amr.guiyang.gov.cn/xwdt/tzgg/202609/t20260914_90870483.html');
"""
    output = deploy.database_command(client, sql)[1]
    expected = ["total=103,max_id=134", "existing_batch=0", "existing_sources=0", "target_regions=5", "duplicate_urls=0"]
    missing = [value for value in expected if value not in output]
    if missing:
        raise RuntimeError(f"production precheck failed, missing {missing}:\n{output}")
    return output


def backup_database(deploy, client):
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = f"/opt/opc/backups/policy-import-20260921-{stamp}"
    command = (
        "set -euo pipefail\n"
        + deploy.DB_ENV
        + f"\nmkdir -p '{backup}'\n"
        + f"MYSQL_PWD=\"$DB_PASS\" mysqldump --single-transaction --quick --no-tablespaces -u \"$DB_USER\" opc_platform | gzip -c > '{backup}/opc_platform.sql.gz.tmp'\n"
        + f"gzip -t '{backup}/opc_platform.sql.gz.tmp'\n"
        + f"mv '{backup}/opc_platform.sql.gz.tmp' '{backup}/opc_platform.sql.gz'\n"
        + f"sha256sum '{backup}/opc_platform.sql.gz'"
    )
    _, output, _ = deploy.run(client, command, timeout=180)
    return backup, output.strip()


def internal_request_json(deploy, client, url):
    _, output, _ = deploy.run(
        client,
        f"curl -fsS -H 'User-Agent: SoloFirm policy import verification' '{url}'",
        timeout=30,
    )
    return json.loads(output)


def verify_public_api(deploy, client, policy_rows):
    published_titles = {row[1] for row in policy_rows if row[3] == "published"}
    list_body = internal_request_json(deploy, client, "http://127.0.0.1:8082/api/public/policies")
    if list_body.get("code") != 200:
        raise RuntimeError(f"public policy list failed: {list_body}")
    visible_titles = {item.get("title") for item in (list_body.get("data") or [])}
    missing_visible = sorted(published_titles - visible_titles)
    if missing_visible:
        raise RuntimeError(f"published records missing from public list: {missing_visible}")
    for policy_id, title, _, _ in policy_rows:
        detail_body = internal_request_json(
            deploy, client, f"http://127.0.0.1:8082/api/public/policies/{policy_id}"
        )
        if detail_body.get("code") != 200 or (detail_body.get("data") or {}).get("title") != title:
            raise RuntimeError(f"public detail verification failed for {policy_id}: {detail_body}")
    dashboard_body = internal_request_json(
        deploy, client, "http://127.0.0.1:8082/api/public/dashboard/summary"
    )
    dashboard_count = (dashboard_body.get("data") or {}).get("policyCount")
    if dashboard_body.get("code") != 200 or dashboard_count != 90:
        raise RuntimeError(f"public dashboard verification failed: {dashboard_body}")
    return {
        "publicListMatches": len(published_titles),
        "detailPages": len(policy_rows),
        "dashboardPublishedPolicyCount": dashboard_count,
    }


def parse_policy_rows(output):
    rows = []
    for line in output.splitlines():
        parts = line.split("\t")
        if len(parts) >= 6 and parts[0].isdigit():
            rows.append((int(parts[0]), parts[1], parts[2], parts[3]))
    if len(rows) != 7:
        raise RuntimeError(f"unable to parse seven imported policy rows:\n{output}")
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    update_sql = (BASE / "update.sql").read_text(encoding="utf-8")
    postcheck_sql = (BASE / "postcheck.sql").read_text(encoding="utf-8")

    deploy = deploy_module()
    deploy.load_local_deploy_secrets(os.environ, deploy.LOCAL_DEPLOY_SECRET_FILE)
    client = deploy.connect()
    candidate = None
    result = {"applied": False}
    try:
        if args.verify_only:
            production_verification = deploy.database_command(client, postcheck_sql)[1]
            assert_checks(production_verification)
            policy_rows = parse_policy_rows(production_verification)
            result["api"] = verify_public_api(deploy, client, policy_rows)
            result["policyIds"] = [row[0] for row in policy_rows]
            result["alreadyApplied"] = True
            _, backups, _ = deploy.run(
                client,
                "ls -1dt /opt/opc/backups/policy-import-20260921-* 2>/dev/null | head -n 3",
                timeout=30,
            )
            result["backups"] = backups.splitlines()
            print(json.dumps(result, ensure_ascii=False, indent=2))
            return

        result["precheck"] = production_precheck(deploy, client)
        candidate = deploy.prepare_candidate_database(client, datetime.now().strftime("%Y%m%d%H%M%S"))
        candidate_update = update_sql.replace("USE opc_platform;", f"USE {candidate.name};", 1)
        deploy.candidate_database_command(client, candidate.name, candidate_update)
        candidate_verification = deploy.candidate_database_command(client, candidate.name, postcheck_sql)[1]
        assert_checks(candidate_verification)
        result["candidateVerified"] = True
        deploy.cleanup_candidate_database(client, candidate)
        candidate = None

        if args.apply:
            backup, backup_hash = backup_database(deploy, client)
            result["backup"] = backup
            result["backupHash"] = backup_hash
            deploy.database_command(client, update_sql)
            production_verification = deploy.database_command(client, postcheck_sql)[1]
            assert_checks(production_verification)
            policy_rows = parse_policy_rows(production_verification)
            result["api"] = verify_public_api(deploy, client, policy_rows)
            result["policyIds"] = [row[0] for row in policy_rows]
            result["applied"] = True
        print(json.dumps(result, ensure_ascii=False, indent=2))
    finally:
        if candidate is not None:
            deploy.cleanup_candidate_database(client, candidate)
        client.close()


if __name__ == "__main__":
    main()
