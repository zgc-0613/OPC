#!/usr/bin/env python
"""Prepare the audited August 2026 gap-policy import and rollback SQL."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

from import_policy_excel import MEASURE_FIELDS, read_policy_rows, sql_string


VERIFIED_EXCEL_ROWS = {2, 3, 4, 5, 6, 7, 8, 9, 11}
PENDING_EXCEL_ROWS = {10}
REVIEWER_VERIFIED = "codex-verified-import"
REVIEWER_PENDING = "codex-pending-import"
APPLICABILITY_MODE = "general"
POLICY_TYPE = "comprehensive"
SOURCE_TYPE = "government_site"


def split_urls(value: str | None) -> list[str]:
    return [part for part in re.split(r"[,\s]+", value or "") if part.startswith(("http://", "https://"))]


def standardized_tags(support_measures: str | None) -> list[str]:
    content = support_measures or ""
    return [label for _, label in MEASURE_FIELDS if f"【{label}】" in content]


def verification_note(audit_row: dict) -> str:
    main = audit_row["main"]
    if audit_row["excel_row"] == 8:
        return "官方政府页面 HTTP 200；页面标题包含沈阳市推出支持OPC企业培育发展16项工作举措，人工复核通过"
    if main.get("pdf_signature"):
        return "官方政府 PDF HTTP 200，PDF 文件签名与大小校验通过"
    return "官方政府页面 HTTP 200，政策标题或发布主体匹配"


def build_import_sql(rows, audit_by_row: dict[int, dict], filename: str, batch: str, stamp: str) -> str:
    backup_suffix = re.sub(r"[^0-9A-Za-z_]", "", stamp)
    operation_id = f"{batch}-verified"
    lines = [
        "-- Audited OPC policy gap import",
        f"-- source_workbook={filename}",
        f"-- import_batch={batch}",
        "SET NAMES utf8mb4;",
        "USE opc_platform;",
        f"CREATE TABLE IF NOT EXISTS backup_sources_{backup_suffix} AS SELECT * FROM sources;",
        f"CREATE TABLE IF NOT EXISTS backup_policies_{backup_suffix} AS SELECT * FROM policies;",
        f"CREATE TABLE IF NOT EXISTS backup_policy_tags_{backup_suffix} AS SELECT * FROM policy_tags;",
        f"CREATE TABLE IF NOT EXISTS backup_tags_{backup_suffix} AS SELECT * FROM tags;",
        f"CREATE TABLE IF NOT EXISTS backup_ai_evidence_reviews_{backup_suffix} AS SELECT * FROM ai_evidence_reviews;",
        "START TRANSACTION;",
        "",
    ]

    for row in rows:
        audit = audit_by_row[row.excel_row]
        evidence_urls = split_urls(row.evidence_url)
        primary_evidence = evidence_urls[0] if evidence_urls else None
        verified = row.excel_row in VERIFIED_EXCEL_ROWS
        reviewer = REVIEWER_VERIFIED if verified else REVIEWER_PENDING
        tags = standardized_tags(row.support_measures)
        notes = "; ".join(filter(None, [
            f"Imported from {filename}",
            f"import_batch={batch}",
            f"excel_row={row.excel_row}",
            f"verification={verification_note(audit) if verified else '官方主页面返回 HTTP 412，保留为待审核'}",
            f"evidence_urls={' | '.join(evidence_urls)}" if evidence_urls else None,
        ]))

        lines.extend([
            f"-- Excel row {row.excel_row}: {row.title}",
            "INSERT INTO sources (",
            "  title,source_type,publisher,url,local_file,accessed_at,notes,status,ai_evidence_status,evidence_revision",
            ")",
            "SELECT "
            f"{sql_string(row.title)},{sql_string(SOURCE_TYPE)},{sql_string(row.issuing_body)},"
            f"{sql_string(row.original_url)},NULL,'2026-08-18',{sql_string(notes)},'published','legacy_unverified',0",
            "WHERE NOT EXISTS (",
            "  SELECT 1 FROM sources",
            f"  WHERE (url={sql_string(row.original_url)})",
            f"     OR (title={sql_string(row.title)} AND publisher={sql_string(row.issuing_body)})",
            ");",
            "SET @source_id := (",
            "  SELECT id FROM sources",
            f"  WHERE (url={sql_string(row.original_url)})",
            f"     OR (title={sql_string(row.title)} AND publisher={sql_string(row.issuing_body)})",
            "  ORDER BY id LIMIT 1",
            ");",
            "SET @region_id := (",
            f"  SELECT id FROM regions WHERE name={sql_string(row.province)} AND level='province' LIMIT 1",
            ");",
            "INSERT INTO policies (",
            "  title,region_id,issuing_body,document_no,publish_date,effective_date,valid_period,source_id,",
            "  policy_level,policy_type,applicability_mode,summary,key_points,support_measures,tags,",
            "  original_url,evidence_url,local_file,accessed_at,status,reviewer,ai_evidence_status,evidence_revision",
            ")",
            "SELECT ",
            f"  {sql_string(row.title)},@region_id,{sql_string(row.issuing_body)},{sql_string(row.document_no)},",
            f"  {sql_string(row.publish_date)},{sql_string(row.effective_date)},{sql_string(row.valid_period)},@source_id,",
            f"  {sql_string(row.policy_level)},{sql_string(POLICY_TYPE)},{sql_string(APPLICABILITY_MODE)},",
            f"  {sql_string(row.summary)},{sql_string(row.key_points)},{sql_string(row.support_measures)},",
            f"  {sql_string(','.join(tags))},{sql_string(row.original_url)},{sql_string(primary_evidence)},NULL,",
            f"  '2026-08-18','published',{sql_string(reviewer)},'legacy_unverified',0",
            "WHERE @region_id IS NOT NULL AND @source_id IS NOT NULL",
            "  AND NOT EXISTS (",
            "    SELECT 1 FROM policies",
            f"    WHERE original_url={sql_string(row.original_url)}",
            f"       OR (title={sql_string(row.title)} AND issuing_body={sql_string(row.issuing_body)})",
            "  );",
            "SET @policy_id := (",
            "  SELECT id FROM policies",
            f"  WHERE original_url={sql_string(row.original_url)}",
            f"     OR (title={sql_string(row.title)} AND issuing_body={sql_string(row.issuing_body)})",
            "  ORDER BY id LIMIT 1",
            ");",
        ])

        for tag in tags:
            lines.extend([
                "INSERT INTO tags (name,tag_type,sort_order)",
                f"VALUES ({sql_string(tag)},'policy',0)",
                "ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id);",
                "SET @tag_id := LAST_INSERT_ID();",
                "INSERT IGNORE INTO policy_tags (policy_id,tag_id)",
                "SELECT @policy_id,@tag_id WHERE @policy_id IS NOT NULL AND @tag_id IS NOT NULL;",
            ])

        if verified:
            reason = verification_note(audit)
            lines.extend([
                "UPDATE sources SET ai_evidence_status='verified',evidence_revision=evidence_revision+1",
                "WHERE id=@source_id AND ai_evidence_status='legacy_unverified';",
                "SET @source_reviewed := ROW_COUNT();",
                "INSERT INTO ai_evidence_reviews (",
                "  item_type,item_id,previous_status,new_status,admin_id,admin_username,notes,action_type,reason,operation_id",
                ") SELECT 'source',@source_id,'legacy_unverified','verified',0,'codex-import',",
                f"  {sql_string('2026-08-18 audited policy import')},'verified_import',{sql_string(reason)},{sql_string(operation_id)}",
                "WHERE @source_reviewed=1;",
                "UPDATE policies SET ai_evidence_status='verified',evidence_revision=evidence_revision+1",
                "WHERE id=@policy_id AND ai_evidence_status='legacy_unverified'",
                "  AND status='published'",
                "  AND EXISTS (SELECT 1 FROM sources s WHERE s.id=policies.source_id AND s.status='published' AND s.ai_evidence_status='verified');",
                "SET @policy_reviewed := ROW_COUNT();",
                "INSERT INTO ai_evidence_reviews (",
                "  item_type,item_id,previous_status,new_status,admin_id,admin_username,notes,action_type,reason,operation_id",
                ") SELECT 'policy',@policy_id,'legacy_unverified','verified',0,'codex-import',",
                f"  {sql_string('2026-08-18 audited policy import')},'verified_import',{sql_string(reason)},{sql_string(operation_id)}",
                "WHERE @policy_reviewed=1;",
            ])
        lines.append("")

    lines.extend([
        "COMMIT;",
        "",
        "-- Post-import verification",
        f"SELECT CONCAT('batch_sources=',COUNT(*)) FROM sources WHERE notes LIKE {sql_string('%import_batch=' + batch + '%')};",
        f"SELECT CONCAT('batch_policies=',COUNT(*),', verified=',SUM(p.ai_evidence_status='verified'),', pending=',SUM(p.ai_evidence_status='legacy_unverified')) FROM policies p JOIN sources s ON s.id=p.source_id WHERE s.notes LIKE {sql_string('%import_batch=' + batch + '%')};",
        f"SELECT p.id,p.title,r.name AS province,p.policy_level,p.applicability_mode,p.status,p.ai_evidence_status,s.ai_evidence_status AS source_evidence_status,p.tags,p.original_url,p.evidence_url FROM policies p JOIN regions r ON r.id=p.region_id JOIN sources s ON s.id=p.source_id WHERE s.notes LIKE {sql_string('%import_batch=' + batch + '%')} ORDER BY p.id;",
        "",
    ])
    return "\n".join(lines)


def build_rollback_sql(batch: str) -> str:
    return "\n".join([
        "SET NAMES utf8mb4;",
        "USE opc_platform;",
        "START TRANSACTION;",
        "CREATE TEMPORARY TABLE rollback_policy_ids AS",
        "SELECT p.id FROM policies p JOIN sources s ON s.id=p.source_id",
        f"WHERE s.notes LIKE {sql_string('%import_batch=' + batch + '%')};",
        "CREATE TEMPORARY TABLE rollback_source_ids AS",
        f"SELECT id FROM sources WHERE notes LIKE {sql_string('%import_batch=' + batch + '%')};",
        "DELETE FROM ai_evidence_reviews WHERE operation_id=" + sql_string(f"{batch}-verified") + ";",
        "DELETE FROM policy_industry_tags WHERE policy_id IN (SELECT id FROM rollback_policy_ids);",
        "DELETE FROM policy_tags WHERE policy_id IN (SELECT id FROM rollback_policy_ids);",
        "DELETE FROM policies WHERE id IN (SELECT id FROM rollback_policy_ids);",
        "DELETE FROM sources WHERE id IN (SELECT id FROM rollback_source_ids)",
        "  AND NOT EXISTS (SELECT 1 FROM policies WHERE policies.source_id=sources.id)",
        "  AND NOT EXISTS (SELECT 1 FROM case_items WHERE case_items.source_id=sources.id);",
        "COMMIT;",
        "",
    ])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--excel", type=Path, required=True)
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--batch", default="policy_gap10_20260818")
    parser.add_argument("--stamp", default="policygap10_20260818")
    args = parser.parse_args()

    rows, warnings = read_policy_rows(args.excel, None)
    audit = json.loads(args.audit.read_text(encoding="utf-8"))
    audit_by_row = {int(item["excel_row"]): item for item in audit["rows"]}
    if len(rows) != 10 or warnings:
        raise RuntimeError(f"Expected 10 clean rows, got rows={len(rows)}, warnings={warnings}")
    if {row.excel_row for row in rows} != VERIFIED_EXCEL_ROWS | PENDING_EXCEL_ROWS:
        raise RuntimeError("Workbook row set does not match the reviewed import contract")
    if any(not audit_by_row[row].get("main_verified") for row in VERIFIED_EXCEL_ROWS - {8}):
        raise RuntimeError("A row marked for verification failed automated main-source audit")
    if audit_by_row[8]["main"].get("status") != 200:
        raise RuntimeError("The manually reviewed Shenyang source is not HTTP 200")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    import_path = args.output_dir / "policy-gap10-import.sql"
    rollback_path = args.output_dir / "policy-gap10-rollback.sql"
    import_path.write_text(
        build_import_sql(rows, audit_by_row, args.excel.name, args.batch, args.stamp),
        encoding="utf-8",
    )
    rollback_path.write_text(build_rollback_sql(args.batch), encoding="utf-8")
    print(json.dumps({
        "rows": len(rows),
        "verified": len(VERIFIED_EXCEL_ROWS),
        "pending": len(PENDING_EXCEL_ROWS),
        "standardized_policy_tags": sorted({tag for row in rows for tag in standardized_tags(row.support_measures)}),
        "import_sql": str(import_path),
        "rollback_sql": str(rollback_path),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
