from __future__ import annotations

import argparse
import importlib.util
import json
import os
import sys
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "outputs" / "data-import-20260929"
REVIEWER = "excel-import-20260929"
BATCH = "data-import-20260929"


def deploy_module():
    if str(ROOT) not in sys.path:
        sys.path.insert(0, str(ROOT))
    spec = importlib.util.spec_from_file_location("opc_deploy", ROOT / ".codex_deploy_opc.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def load_plan():
    return json.loads((BASE / "import-plan.json").read_text(encoding="utf-8"))


def expected_university(plan):
    return {(row["record_type"], row["record_code"]) for row in plan["university_rows"]}


def precheck(deploy, client, plan):
    columns = "'article_title','subcategory','ai_evidence_status','evidence_revision'"
    sql = f"""
SELECT CONCAT('case_total=',COUNT(*)) FROM case_items;
SELECT CONCAT('case_batch_existing=',COUNT(*)) FROM case_items WHERE reviewer='{REVIEWER}';
SELECT CONCAT('source_batch_existing=',COUNT(*)) FROM sources WHERE notes LIKE '%import_batch={BATCH}%';
SELECT CONCAT('university_table=',COUNT(*)) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='university_opc_records';
SELECT CONCAT('case_required_columns=',COUNT(*)) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='case_items' AND column_name IN ({columns});
SELECT CONCAT('region_country=',COUNT(*)) FROM regions WHERE level='country' AND name='中国';
"""
    return deploy.database_command(client, sql)[1]


def assert_precheck(output):
    required = [
        "case_batch_existing=0",
        "source_batch_existing=0",
        "case_required_columns=4",
        "region_country=1",
    ]
    missing = [value for value in required if value not in output]
    if missing:
        raise RuntimeError(f"production precheck failed, missing {missing}:\n{output}")


def assert_postcheck(output, plan):
    expected_activity = sum(row["record_type"] == "activity" for row in plan["university_rows"])
    expected_case = sum(row["record_type"] == "case" for row in plan["university_rows"])
    required = [
        f"case_batch={len(plan['case_rows'])}",
        "case_missing_region=0",
        "case_missing_tags=0",
        f"activity\t{expected_activity}",
        f"case\t{expected_case}",
        "university_missing_required=0",
    ]
    missing = [value for value in required if value not in output]
    if missing:
        raise RuntimeError(f"postcheck failed, missing {missing}:\n{output}")


def backup_database(deploy, client):
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = f"/opt/opc/backups/data-import-20260929-{stamp}"
    command = (
        "set -euo pipefail\n" + deploy.DB_ENV + f"\nmkdir -p '{backup}'\n"
        + f"MYSQL_PWD=\"$DB_PASS\" mysqldump --single-transaction --quick --no-tablespaces -u \"$DB_USER\" opc_platform | gzip -c > '{backup}/opc_platform.sql.gz.tmp'\n"
        + f"gzip -t '{backup}/opc_platform.sql.gz.tmp'\n"
        + f"mv '{backup}/opc_platform.sql.gz.tmp' '{backup}/opc_platform.sql.gz'\n"
        + f"sha256sum '{backup}/opc_platform.sql.gz'"
    )
    return backup, deploy.run(client, command, timeout=240)[1]


def internal_json(deploy, client, url):
    _, output, _ = deploy.run(client, f"curl -fsS -H 'User-Agent: FindOPC data import verification' '{url}'", timeout=30)
    return json.loads(output)


def verify_api(deploy, client, plan):
    cases = internal_json(deploy, client, "http://127.0.0.1:8082/api/public/cases")
    if cases.get("code") != 200:
        raise RuntimeError(f"case API failed: {cases}")
    case_titles = {row["title"] for row in plan["case_rows"]}
    visible = {item.get("title") for item in (cases.get("data") or [])}
    missing = sorted(case_titles - visible)
    if missing:
        raise RuntimeError(f"new cases missing from public API: {missing}")
    university = internal_json(deploy, client, "http://127.0.0.1:8082/api/public/university-opc")
    if university.get("code") != 200:
        raise RuntimeError(f"university OPC API failed: {university}")
    records = university.get("data") or []
    codes = {row.get("id") for row in records}
    expected_codes = {row["record_code"] for row in plan["university_rows"]}
    if not expected_codes.issubset(codes):
        raise RuntimeError(f"university records missing from public API: {sorted(expected_codes - codes)}")
    return {"publicCaseRecords": len(visible), "publicUniversityRecords": len(records)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    plan = load_plan()
    update_sql = (BASE / "update.sql").read_text(encoding="utf-8")
    postcheck_sql = (BASE / "postcheck.sql").read_text(encoding="utf-8")
    deploy = deploy_module()
    deploy.load_local_deploy_secrets(os.environ, deploy.LOCAL_DEPLOY_SECRET_FILE)
    client = deploy.connect()
    candidate = None
    result = {"applied": False, "caseRows": len(plan["case_rows"]), "universityRows": len(plan["university_rows"])}
    try:
        if args.verify_only:
            output = deploy.database_command(client, postcheck_sql)[1]
            assert_postcheck(output, plan)
            result["postcheck"] = output
            result["api"] = verify_api(deploy, client, plan)
            print(json.dumps(result, ensure_ascii=False, indent=2))
            return

        pre = precheck(deploy, client, plan)
        assert_precheck(pre)
        result["precheck"] = pre
        candidate = deploy.prepare_candidate_database(client, datetime.now().strftime("%Y%m%d%H%M%S"))
        candidate_update = update_sql.replace("USE opc_platform;", f"USE {candidate.name};", 1)
        deploy.candidate_database_command(client, candidate.name, candidate_update)
        candidate_postcheck_sql = postcheck_sql.replace("USE opc_platform;", f"USE {candidate.name};", 1)
        candidate_postcheck = deploy.candidate_database_command(client, candidate.name, candidate_postcheck_sql)[1]
        candidate_debug = deploy.candidate_database_command(
            client,
            candidate.name,
            "SELECT CONCAT('debug_sources=',COUNT(*)) FROM sources WHERE notes LIKE '%import_batch=data-import-20260929%';\n"
            "SELECT CONCAT('debug_cases_reviewer=',COUNT(*)) FROM case_items WHERE reviewer='excel-import-20260929';\n"
            "SELECT CONCAT('debug_case_original_url=',COUNT(*)) FROM case_items WHERE original_url LIKE '%adimg.ce.cn%';\n"
            "SELECT CONCAT('debug_country=',COUNT(*)) FROM regions WHERE level='country' AND name='中国';\n",
        )[1]
        result["candidateDebug"] = candidate_debug
        print("CANDIDATE_DEBUG\n" + candidate_debug)
        assert_postcheck(candidate_postcheck, plan)
        result["candidateVerified"] = True
        result["candidatePostcheck"] = candidate_postcheck
        deploy.cleanup_candidate_database(client, candidate)
        candidate = None

        if args.apply:
            backup, backup_hash = backup_database(deploy, client)
            result["backup"] = backup
            result["backupHash"] = backup_hash
            deploy.database_command(client, update_sql)
            production_postcheck = deploy.database_command(client, postcheck_sql)[1]
            assert_postcheck(production_postcheck, plan)
            result["postcheck"] = production_postcheck
            result["api"] = verify_api(deploy, client, plan)
            result["applied"] = True
        print(json.dumps(result, ensure_ascii=False, indent=2))
    finally:
        if candidate is not None:
            deploy.cleanup_candidate_database(client, candidate)
        client.close()


if __name__ == "__main__":
    main()
