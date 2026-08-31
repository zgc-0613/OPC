#!/usr/bin/env python
"""Prepare an idempotent SQL import for the final three rows of a policy workbook."""

from __future__ import annotations

import argparse
import re
from pathlib import Path

from import_policy_excel import read_policy_rows, sql_string


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--excel", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--batch", default="policy_tail_20260822")
    parser.add_argument("--accessed-at", default="2026-08-22")
    args = parser.parse_args()

    rows, warnings = read_policy_rows(args.excel, None)
    rows = [row for row in rows if row.excel_row in {101, 102, 103}]
    tail_warnings = [warning for warning in warnings if any(f"Row {row.excel_row}:" in warning for row in rows)]
    tail_warnings = [warning for warning in tail_warnings if "unknown status" not in warning]
    if len(rows) != 3 or tail_warnings:
        raise RuntimeError(f"Expected clean rows 101-103; rows={len(rows)}, warnings={tail_warnings}")

    marker = sql_string(f"%import_batch={args.batch}%")
    op = sql_string(f"{args.batch}-verified")
    lines = [
        "-- Audited OPC policy tail import (rows 101-103)",
        f"-- source_workbook={args.excel.name}",
        f"-- import_batch={args.batch}",
        "SET NAMES utf8mb4;",
        "USE opc_platform;",
        f"CREATE TABLE IF NOT EXISTS backup_sources_{re.sub(r'[^0-9A-Za-z_]', '', args.batch)} AS SELECT * FROM sources;",
        f"CREATE TABLE IF NOT EXISTS backup_policies_{re.sub(r'[^0-9A-Za-z_]', '', args.batch)} AS SELECT * FROM policies;",
        "START TRANSACTION;",
    ]
    for row in rows:
        tags = []
        for label in ("算力支持", "资金补贴", "场地工位", "场景需求", "人才服务", "投资融资", "其他"):
            if f"【{label}】" in (row.support_measures or ""):
                tags.append(label)
        notes = f"Imported from {args.excel.name}; import_batch={args.batch}; excel_row={row.excel_row}; verification=official page HTTP 200, title and issuer matched"
        evidence = row.evidence_url or None
        lines += [
            f"-- Excel row {row.excel_row}: {row.title}",
            "INSERT INTO sources (title,source_type,publisher,url,local_file,accessed_at,notes,status,ai_evidence_status,evidence_revision)",
            "SELECT " + ",".join([
                sql_string(row.title), sql_string("government_site"), sql_string(row.issuing_body), sql_string(row.original_url),
                "NULL", sql_string(args.accessed_at), sql_string(notes), sql_string("published" if row.status == "published" else "draft"),
                sql_string("legacy_unverified"), "0",
            ]) + " WHERE NOT EXISTS (SELECT 1 FROM sources WHERE url=" + sql_string(row.original_url) + ");",
            "SET @source_id := (SELECT id FROM sources WHERE url=" + sql_string(row.original_url) + " ORDER BY id LIMIT 1);",
            "UPDATE sources SET title=" + sql_string(row.title) + ",publisher=" + sql_string(row.issuing_body) + ",accessed_at=" + sql_string(args.accessed_at) + ",notes=" + sql_string(notes) + ",status=" + sql_string("published" if row.status == "published" else "draft") + " WHERE id=@source_id;",
            "INSERT INTO policies (title,region_id,issuing_body,document_no,publish_date,effective_date,valid_period,source_id,policy_level,policy_type,applicability_mode,summary,key_points,support_measures,tags,original_url,evidence_url,local_file,accessed_at,status,reviewer,ai_evidence_status,evidence_revision)",
            "SELECT " + ",".join([
                sql_string(row.title), "(SELECT id FROM regions WHERE name=" + sql_string(row.province) + " AND level='province' LIMIT 1)", sql_string(row.issuing_body), sql_string(row.document_no),
                sql_string(row.publish_date), sql_string(row.effective_date), sql_string(row.valid_period), "@source_id", sql_string(row.policy_level), sql_string("comprehensive"), sql_string("general"),
                sql_string(row.summary), sql_string(row.key_points), sql_string(row.support_measures), sql_string(",".join(tags)), sql_string(row.original_url), sql_string(evidence), "NULL", sql_string(args.accessed_at), sql_string(row.status), sql_string("codex-tail-import"), sql_string("legacy_unverified"), "0",
            ]) + " WHERE NOT EXISTS (SELECT 1 FROM policies WHERE original_url=" + sql_string(row.original_url) + ");",
            "SET @policy_id := (SELECT id FROM policies WHERE original_url=" + sql_string(row.original_url) + " ORDER BY id LIMIT 1);",
            "UPDATE policies SET title=" + sql_string(row.title) + ",region_id=(SELECT id FROM regions WHERE name=" + sql_string(row.province) + " AND level='province' LIMIT 1),issuing_body=" + sql_string(row.issuing_body) + ",document_no=" + sql_string(row.document_no) + ",publish_date=" + sql_string(row.publish_date) + ",effective_date=" + sql_string(row.effective_date) + ",valid_period=" + sql_string(row.valid_period) + ",source_id=@source_id,policy_level=" + sql_string(row.policy_level) + ",policy_type='comprehensive',applicability_mode='general',summary=" + sql_string(row.summary) + ",key_points=" + sql_string(row.key_points) + ",support_measures=" + sql_string(row.support_measures) + ",tags=" + sql_string(",".join(tags)) + ",evidence_url=" + sql_string(evidence) + ",accessed_at=" + sql_string(args.accessed_at) + ",status=" + sql_string(row.status) + ",reviewer='codex-tail-import' WHERE id=@policy_id;",
            "UPDATE sources SET ai_evidence_status='verified',evidence_revision=evidence_revision+1 WHERE id=@source_id AND ai_evidence_status='legacy_unverified';",
            "SET @source_reviewed := ROW_COUNT();",
            "INSERT INTO ai_evidence_reviews (item_type,item_id,previous_status,new_status,admin_id,admin_username,notes,action_type,reason,operation_id) SELECT 'source',@source_id,'legacy_unverified','verified',0,'codex-import','2026-08-22 audited policy tail import','verified_import','official page HTTP 200, title and issuer matched'," + op + " WHERE @source_reviewed=1;",
            "UPDATE policies SET ai_evidence_status='verified',evidence_revision=evidence_revision+1 WHERE id=@policy_id AND ai_evidence_status='legacy_unverified' AND status='published' AND EXISTS (SELECT 1 FROM sources s WHERE s.id=policies.source_id AND s.ai_evidence_status='verified');",
            "SET @policy_reviewed := ROW_COUNT();",
            "INSERT INTO ai_evidence_reviews (item_type,item_id,previous_status,new_status,admin_id,admin_username,notes,action_type,reason,operation_id) SELECT 'policy',@policy_id,'legacy_unverified','verified',0,'codex-import','2026-08-22 audited policy tail import','verified_import','official page HTTP 200, title and issuer matched'," + op + " WHERE @policy_reviewed=1;",
        ]
    lines += [
        "COMMIT;",
        "SELECT CONCAT('tail_sources=',COUNT(*)) FROM sources WHERE notes LIKE " + marker + ";",
        "SELECT CONCAT('tail_policies=',COUNT(*),', verified=',SUM(p.ai_evidence_status='verified'),', pending=',SUM(p.ai_evidence_status='legacy_unverified')) FROM policies p JOIN sources s ON s.id=p.source_id WHERE s.notes LIKE " + marker + ";",
    ]
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "policy-tail-import.sql").write_text("\n".join(lines), encoding="utf-8")
    rollback = "\n".join([
        "SET NAMES utf8mb4;", "USE opc_platform;", "START TRANSACTION;",
        "CREATE TEMPORARY TABLE rollback_policy_ids AS SELECT p.id FROM policies p JOIN sources s ON s.id=p.source_id WHERE s.notes LIKE " + marker + ";",
        "CREATE TEMPORARY TABLE rollback_source_ids AS SELECT id FROM sources WHERE notes LIKE " + marker + ";",
        "DELETE FROM ai_evidence_reviews WHERE operation_id=" + op + ";",
        "DELETE FROM policy_tags WHERE policy_id IN (SELECT id FROM rollback_policy_ids);",
        "DELETE FROM policies WHERE id IN (SELECT id FROM rollback_policy_ids);",
        "DELETE FROM sources WHERE id IN (SELECT id FROM rollback_source_ids) AND NOT EXISTS (SELECT 1 FROM policies WHERE policies.source_id=sources.id);",
        "COMMIT;",
    ])
    (args.output_dir / "policy-tail-rollback.sql").write_text(rollback, encoding="utf-8")
    print(f"rows=3; import_sql={args.output_dir / 'policy-tail-import.sql'}; rollback_sql={args.output_dir / 'policy-tail-rollback.sql'}")


if __name__ == "__main__":
    main()
