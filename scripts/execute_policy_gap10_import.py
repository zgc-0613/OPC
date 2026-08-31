#!/usr/bin/env python
"""Execute and verify the reviewed policy gap import against production."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
import re
import sys
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def load_deploy_module():
    if str(ROOT) not in sys.path:
        sys.path.insert(0, str(ROOT))
    spec = importlib.util.spec_from_file_location("opc_deploy", ROOT / ".codex_deploy_opc.py")
    if spec is None or spec.loader is None:
        raise RuntimeError("Cannot load deployment helper")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def db_output(deploy, client, sql: str) -> str:
    _, output, _ = deploy.database_command(client, sql)
    return output.strip()


def verification_sql(batch: str) -> str:
    marker = batch.replace("'", "''")
    return f"""
SELECT CONCAT('batch_sources=',COUNT(*))
FROM sources WHERE notes LIKE '%import_batch={marker}%';
SELECT CONCAT('batch_policies=',COUNT(*),', verified=',SUM(p.ai_evidence_status='verified'),', pending=',SUM(p.ai_evidence_status='legacy_unverified'))
FROM policies p JOIN sources s ON s.id=p.source_id
WHERE s.notes LIKE '%import_batch={marker}%';
SELECT CONCAT('effective_verified=',COUNT(*))
FROM policies p JOIN sources s ON s.id=p.source_id
WHERE s.notes LIKE '%import_batch={marker}%'
  AND p.status='published' AND p.ai_evidence_status='verified'
  AND s.status='published' AND s.ai_evidence_status='verified';
SELECT CONCAT('review_audits=',COUNT(*))
FROM ai_evidence_reviews WHERE operation_id='{marker}-verified';
SELECT CONCAT('incomplete_required=',COUNT(*))
FROM policies p JOIN sources s ON s.id=p.source_id
WHERE s.notes LIKE '%import_batch={marker}%'
  AND (p.title='' OR p.summary='' OR p.issuing_body='' OR p.original_url='' OR p.region_id IS NULL OR p.source_id IS NULL
       OR s.title='' OR s.publisher='' OR s.url='');
SELECT CONCAT('invalid_taxonomy=',COUNT(*))
FROM policies p JOIN sources s ON s.id=p.source_id
WHERE s.notes LIKE '%import_batch={marker}%'
  AND (p.policy_type<>'comprehensive' OR p.applicability_mode<>'general'
       OR p.tags REGEXP '(^|,)([^,]*；[^,]*)(,|$)');
SELECT p.id,p.title,r.name,p.policy_level,p.status,p.ai_evidence_status,s.ai_evidence_status,p.tags
FROM policies p JOIN sources s ON s.id=p.source_id JOIN regions r ON r.id=p.region_id
WHERE s.notes LIKE '%import_batch={marker}%'
ORDER BY p.id;
"""


def assert_verified(output: str) -> None:
    expected = {
        "batch_sources=10",
        "batch_policies=10, verified=9, pending=1",
        "effective_verified=9",
        "review_audits=18",
        "incomplete_required=0",
        "invalid_taxonomy=0",
    }
    missing = sorted(item for item in expected if item not in output)
    if missing:
        raise RuntimeError(f"Import verification failed; missing: {missing}\n{output}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sql", type=Path, required=True)
    parser.add_argument("--rollback", type=Path, required=True)
    parser.add_argument("--batch", required=True)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    if not args.apply:
        raise RuntimeError("Refusing production write without --apply")

    sql = args.sql.read_text(encoding="utf-8")
    rollback = args.rollback.read_text(encoding="utf-8")
    if f"import_batch={args.batch}" not in sql:
        raise RuntimeError("SQL batch marker mismatch")
    checks = {
        "source_inserts": sql.count("INSERT INTO sources ("),
        "policy_inserts": sql.count("INSERT INTO policies ("),
        "source_reviews": sql.count("UPDATE sources SET ai_evidence_status='verified'"),
        "policy_reviews": sql.count("UPDATE policies SET ai_evidence_status='verified'"),
    }
    if checks != {"source_inserts": 10, "policy_inserts": 10, "source_reviews": 9, "policy_reviews": 9}:
        raise RuntimeError(f"Unexpected SQL operation counts: {checks}")

    deploy = load_deploy_module()
    deploy.load_local_deploy_secrets(os.environ, deploy.LOCAL_DEPLOY_SECRET_FILE)
    client = deploy.connect()
    rolled_back = False
    candidate_verified = False
    candidate = None
    try:
        existing = db_output(
            deploy,
            client,
            f"SELECT COUNT(*) FROM sources WHERE notes LIKE '%import_batch={args.batch.replace(chr(39), chr(39) * 2)}%';",
        )
        existing_count = int(existing.splitlines()[-1])
        if existing_count not in {0, 10}:
            raise RuntimeError(f"Partial prior import detected: {existing_count} batch sources")
        if existing_count == 0:
            candidate = deploy.prepare_candidate_database(client, datetime.now().strftime("%Y%m%d%H%M%S"))
            candidate_sql = sql.replace("USE opc_platform;", f"USE {candidate.name};", 1)
            deploy.candidate_database_command(client, candidate.name, candidate_sql)
            _, candidate_output, _ = deploy.candidate_database_command(
                client, candidate.name, verification_sql(args.batch)
            )
            assert_verified(candidate_output)
            candidate_verified = True
            deploy.cleanup_candidate_database(client, candidate)
            candidate = None
            db_output(deploy, client, sql)

        verification = db_output(deploy, client, verification_sql(args.batch))
        try:
            assert_verified(verification)
        except Exception:
            if existing_count == 0:
                db_output(deploy, client, rollback)
                rolled_back = True
            raise

        print(json.dumps({
            "applied": existing_count == 0,
            "already_present": existing_count == 10,
            "rolled_back": rolled_back,
            "candidate_verified": candidate_verified,
            "sql_sha256": hashlib.sha256(sql.encode("utf-8")).hexdigest(),
            "verification": verification,
        }, ensure_ascii=False, indent=2))
    finally:
        if candidate is not None:
            deploy.cleanup_candidate_database(client, candidate)
        client.close()


if __name__ == "__main__":
    main()
