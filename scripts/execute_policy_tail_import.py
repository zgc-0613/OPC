#!/usr/bin/env python
"""Validate and optionally apply the audited three-row policy tail import."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import os
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def deploy_module():
    sys.path.insert(0, str(ROOT))
    spec = importlib.util.spec_from_file_location("opc_deploy", ROOT / ".codex_deploy_opc.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def checks(batch: str) -> str:
    marker = batch.replace("'", "''")
    return f"""
SELECT CONCAT('tail_sources=',COUNT(*)) FROM sources WHERE notes LIKE '%import_batch={marker}%';
SELECT CONCAT('tail_policies=',COUNT(*),', verified=',SUM(p.ai_evidence_status='verified'),', pending=',SUM(p.ai_evidence_status='legacy_unverified'))
FROM policies p JOIN sources s ON s.id=p.source_id WHERE s.notes LIKE '%import_batch={marker}%';
SELECT CONCAT('incomplete=',COUNT(*)) FROM policies p JOIN sources s ON s.id=p.source_id
WHERE s.notes LIKE '%import_batch={marker}%' AND (p.title='' OR p.summary='' OR p.issuing_body='' OR p.original_url='' OR p.region_id IS NULL OR s.url='');
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sql", type=Path, required=True)
    parser.add_argument("--rollback", type=Path, required=True)
    parser.add_argument("--batch", required=True)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    sql = args.sql.read_text(encoding="utf-8")
    rollback = args.rollback.read_text(encoding="utf-8")
    if f"import_batch={args.batch}" not in sql:
        raise RuntimeError("batch marker mismatch")
    deploy = deploy_module()
    deploy.load_local_deploy_secrets(os.environ, deploy.LOCAL_DEPLOY_SECRET_FILE)
    client = deploy.connect()
    candidate = None
    try:
        existing = deploy.database_command(client, f"SELECT COUNT(*) FROM sources WHERE notes LIKE '%import_batch={args.batch}%';")[1].strip()
        existing_count = int(existing.splitlines()[-1])
        if existing_count not in (0, 3):
            raise RuntimeError(f"partial prior import: {existing_count}")
        if existing_count == 0:
            candidate = deploy.prepare_candidate_database(client, datetime.now().strftime('%Y%m%d%H%M%S'))
            candidate_sql = sql.replace("USE opc_platform;", f"USE {candidate.name};", 1)
            deploy.candidate_database_command(client, candidate.name, candidate_sql)
            verification = deploy.candidate_database_command(client, candidate.name, checks(args.batch))[1]
            if "tail_sources=3" not in verification or "tail_policies=3" not in verification or "incomplete=0" not in verification:
                raise RuntimeError(f"candidate verification failed:\n{verification}")
            print("candidate_verified=true")
            deploy.cleanup_candidate_database(client, candidate)
            candidate = None
        if args.apply and existing_count == 0:
            deploy.database_command(client, sql)
            print("applied=true")
        else:
            print(f"applied=false; apply_requested={args.apply}")
        print(f"sha256={hashlib.sha256(sql.encode('utf-8')).hexdigest()}")
        print(deploy.database_command(client, checks(args.batch))[1])
    finally:
        if candidate is not None:
            deploy.cleanup_candidate_database(client, candidate)
        client.close()


if __name__ == "__main__":
    main()
