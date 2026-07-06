#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Import helper for OPC policy Excel data.

Default behavior is dry-run only. Use --write-sql to generate an idempotent-ish
MySQL script that can be reviewed and executed manually.
"""

from __future__ import annotations

import argparse
import re
from collections import Counter
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Any

import openpyxl


ROOT_DIR = Path(__file__).resolve().parents[1]
DEFAULT_DATA_DIR = ROOT_DIR / "data"
DEFAULT_OUTPUT_DIR = ROOT_DIR / "outputs"
DEFAULT_BATCH = "policy_excel_20260704"
SOURCE_TYPE = "government_site"
POLICY_TYPE = "comprehensive"
REVIEWER = "excel-import"
POLICY_TAG_TYPE = "policy"

LEVEL_MAP = {
    "国家级": "national",
    "省级": "provincial",
    "市级": "city",
    "区级": "district",
}

STATUS_MAP = {
    "现行有效": "published",
    "征求意见稿": "draft",
}

PROVINCE_NAMES = {
    "北京市", "天津市", "上海市", "重庆市",
    "河北省", "山西省", "辽宁省", "吉林省", "黑龙江省",
    "江苏省", "浙江省", "安徽省", "福建省", "江西省", "山东省",
    "河南省", "湖北省", "湖南省", "广东省", "海南省",
    "四川省", "贵州省", "云南省", "陕西省", "甘肃省", "青海省",
    "台湾省",
    "内蒙古自治区", "广西壮族自治区", "西藏自治区", "宁夏回族自治区", "新疆维吾尔自治区",
    "香港特别行政区", "澳门特别行政区",
}

REQUIRED_HEADERS = [
    "title政策标题",
    "发布日期",
    "url政策原文网页链接",
    "政策状态",
    "辅证链接",
    "policy_level政策等级",
    "region省",
    "region市",
    "region区",
    "文号",
    "发文单位",
    "summary摘要（100字左右）",
    "开始实施时间",
    "政策有效时长",
    "政策要点(多值)",
    "具体政策·算力支持",
    "具体政策·资金补贴",
    "具体政策·场地工位",
    "具体政策·场景需求",
    "具体政策·人才服务",
    "具体政策·投资融资",
    "具体政策·其他",
]

MEASURE_FIELDS = [
    ("具体政策·算力支持", "算力支持"),
    ("具体政策·资金补贴", "资金补贴"),
    ("具体政策·场地工位", "场地工位"),
    ("具体政策·场景需求", "场景需求"),
    ("具体政策·人才服务", "人才服务"),
    ("具体政策·投资融资", "投资融资"),
    ("具体政策·其他", "其他"),
]


@dataclass
class PolicyRow:
    excel_row: int
    title: str
    publish_date: str | None
    original_url: str | None
    status: str
    evidence_url: str | None
    policy_level: str
    province: str
    city: str | None
    district: str | None
    document_no: str | None
    issuing_body: str
    summary: str
    effective_date: str | None
    valid_period: str | None
    key_points: str
    support_measures: str | None
    tags: str | None
    tag_names: list[str]


def main() -> None:
    parser = argparse.ArgumentParser(description="Read OPC policy Excel and optionally generate MySQL import SQL.")
    parser.add_argument("--excel", type=Path, default=None, help="Path to policy .xlsx file.")
    parser.add_argument("--limit", type=int, default=None, help="Only process the first N valid policy rows.")
    parser.add_argument("--batch", default=DEFAULT_BATCH, help="Import batch marker.")
    parser.add_argument("--accessed-at", default=date.today().isoformat(), help="Access/import date, YYYY-MM-DD.")
    parser.add_argument("--write-sql", action="store_true", help="Generate SQL file under outputs/.")
    parser.add_argument("--write-check-sql", action="store_true", help="Generate SQL that checks which Excel policies are missing from DB.")
    parser.add_argument("--output", type=Path, default=None, help="Custom SQL output path.")
    args = parser.parse_args()

    excel_path = args.excel or find_default_excel()
    rows, warnings = read_policy_rows(excel_path, args.limit)
    report = build_report(rows, warnings)
    print_report(excel_path, rows, report, args.write_sql)

    if args.write_sql:
        output_path = args.output or DEFAULT_OUTPUT_DIR / f"import_policies_{args.batch}.sql"
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(generate_sql(rows, excel_path.name, args.batch, args.accessed_at), encoding="utf-8")
        print(f"\nSQL file generated: {output_path}")
        print("Review it first, then execute it in MySQL against the opc_platform database.")

    if args.write_check_sql:
        output_path = args.output or DEFAULT_OUTPUT_DIR / f"check_policy_import_{args.batch}.sql"
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(generate_check_sql(rows), encoding="utf-8")
        print(f"\nCheck SQL file generated: {output_path}")
        print("Execute it in MySQL to list Excel rows that are not present in policies.")


def find_default_excel() -> Path:
    files = [p for p in DEFAULT_DATA_DIR.glob("*.xlsx") if not p.name.startswith("~$")]
    if not files:
        raise FileNotFoundError(f"No .xlsx file found under {DEFAULT_DATA_DIR}")
    if len(files) > 1:
        names = ", ".join(p.name for p in files)
        raise RuntimeError(f"Multiple .xlsx files found. Use --excel to choose one: {names}")
    return files[0]


def read_policy_rows(excel_path: Path, limit: int | None) -> tuple[list[PolicyRow], list[str]]:
    workbook = openpyxl.load_workbook(excel_path, data_only=True)
    sheet = workbook.active
    headers = [normalize_text(cell.value) for cell in sheet[1]]
    header_index = {header: index for index, header in enumerate(headers) if header}
    missing_headers = [header for header in REQUIRED_HEADERS if header not in header_index]
    if missing_headers:
        raise RuntimeError(f"Missing required headers: {', '.join(missing_headers)}")

    rows: list[PolicyRow] = []
    warnings: list[str] = []
    seen_urls: set[str] = set()
    seen_title_publishers: set[tuple[str, str]] = set()

    for excel_row, values in enumerate(sheet.iter_rows(min_row=2, values_only=True), start=2):
        raw = {header: values[index] if index < len(values) else None for header, index in header_index.items()}
        title = normalize_text(raw.get("title政策标题"))
        if not title:
            continue

        province = normalize_text(raw.get("region省"))
        issuing_body = normalize_text(raw.get("发文单位"))
        summary = normalize_text(raw.get("summary摘要（100字左右）"))
        level_text = normalize_text(raw.get("policy_level政策等级"))
        status_text = normalize_text(raw.get("政策状态"))
        original_url = normalize_text(raw.get("url政策原文网页链接")) or None

        if province not in PROVINCE_NAMES:
            warnings.append(f"Row {excel_row}: unknown province '{province}'")
        if not issuing_body:
            warnings.append(f"Row {excel_row}: missing issuing body")
        if not summary:
            warnings.append(f"Row {excel_row}: missing summary")
        if level_text not in LEVEL_MAP:
            warnings.append(f"Row {excel_row}: unknown policy level '{level_text}'")
        if status_text and status_text not in STATUS_MAP:
            warnings.append(f"Row {excel_row}: unknown status '{status_text}', will use draft")

        duplicate_key = (title, issuing_body)
        if original_url and original_url in seen_urls:
            warnings.append(f"Row {excel_row}: duplicate original URL in Excel")
        if duplicate_key in seen_title_publishers:
            warnings.append(f"Row {excel_row}: duplicate title + issuing body in Excel")
        if original_url:
            seen_urls.add(original_url)
        seen_title_publishers.add(duplicate_key)

        tag_names = parse_tags(raw.get("政策要点(多值)"))
        tags = ",".join(tag_names) if tag_names else None
        city = normalize_text(raw.get("region市")) or None
        district = normalize_text(raw.get("region区")) or None

        rows.append(PolicyRow(
            excel_row=excel_row,
            title=title,
            publish_date=parse_date(raw.get("发布日期")),
            original_url=original_url,
            status=STATUS_MAP.get(status_text, "draft"),
            evidence_url=normalize_text(raw.get("辅证链接")) or None,
            policy_level=LEVEL_MAP.get(level_text, "district"),
            province=province,
            city=city,
            district=district,
            document_no=normalize_text(raw.get("文号")) or None,
            issuing_body=issuing_body,
            summary=summary,
            effective_date=parse_date(raw.get("开始实施时间")),
            valid_period=normalize_text(raw.get("政策有效时长")) or None,
            key_points=build_key_points(tag_names, province, city, district),
            support_measures=build_support_measures(raw),
            tags=tags,
            tag_names=tag_names,
        ))

        if limit is not None and len(rows) >= limit:
            break

    return rows, warnings


def build_report(rows: list[PolicyRow], warnings: list[str]) -> dict[str, Any]:
    tag_counter = Counter(tag for row in rows for tag in row.tag_names)
    province_counter = Counter(row.province for row in rows)
    level_counter = Counter(row.policy_level for row in rows)
    status_counter = Counter(row.status for row in rows)
    return {
        "warnings": warnings,
        "tag_counter": tag_counter,
        "province_counter": province_counter,
        "level_counter": level_counter,
        "status_counter": status_counter,
        "source_count": len(rows),
        "policy_count": len(rows),
        "tag_count": len(tag_counter),
        "policy_tag_count": sum(len(row.tag_names) for row in rows),
    }


def print_report(excel_path: Path, rows: list[PolicyRow], report: dict[str, Any], write_sql: bool) -> None:
    print(f"Excel: {excel_path}")
    print(f"Valid policy rows: {len(rows)}")
    print(f"Mode: {'generate SQL' if write_sql else 'dry-run only'}")
    print()
    print("Planned rows:")
    print(f"  sources: {report['source_count']}")
    print(f"  policies: {report['policy_count']}")
    print(f"  tags: {report['tag_count']}")
    print(f"  policy_tags: {report['policy_tag_count']}")
    print()
    print_counter("Policy levels", report["level_counter"])
    print_counter("Statuses", report["status_counter"])
    print_counter("Top provinces", report["province_counter"], limit=10)
    print_counter("Tags", report["tag_counter"])
    if report["warnings"]:
        print("\nWarnings:")
        for warning in report["warnings"][:50]:
            print(f"  - {warning}")
        if len(report["warnings"]) > 50:
            print(f"  ... {len(report['warnings']) - 50} more warnings")
    else:
        print("\nWarnings: none")


def print_counter(title: str, counter: Counter, limit: int | None = None) -> None:
    print(f"{title}:")
    items = counter.most_common(limit)
    if not items:
        print("  none")
        return
    for key, count in items:
        print(f"  {key}: {count}")
    print()


def generate_sql(rows: list[PolicyRow], excel_filename: str, batch: str, accessed_at: str) -> str:
    lines: list[str] = [
        "-- Generated by scripts/import_policy_excel.py",
        "-- Review before executing.",
        "SET NAMES utf8mb4;",
        "USE opc_platform;",
        "START TRANSACTION;",
        "",
    ]

    for index, row in enumerate(rows, start=1):
        notes = f"Imported from {excel_filename}; import_batch={batch}; excel_row={row.excel_row}"
        lines.extend([
            f"-- {index}. Excel row {row.excel_row}: {row.title}",
            "INSERT INTO sources (title, source_type, publisher, url, local_file, accessed_at, notes, status)",
            "SELECT "
            f"{sql_string(row.title)}, {sql_string(SOURCE_TYPE)}, {sql_string(row.issuing_body)}, "
            f"{sql_string(row.original_url)}, NULL, {sql_string(accessed_at)}, {sql_string(notes)}, {sql_string(row.status)}",
            "WHERE NOT EXISTS (",
            "    SELECT 1 FROM sources",
            f"    WHERE ({sql_string(row.original_url)} IS NOT NULL AND url = {sql_string(row.original_url)})",
            f"       OR (title = {sql_string(row.title)} AND publisher = {sql_string(row.issuing_body)})",
            ");",
            "SET @source_id := (",
            "    SELECT id FROM sources",
            f"    WHERE ({sql_string(row.original_url)} IS NOT NULL AND url = {sql_string(row.original_url)})",
            f"       OR (title = {sql_string(row.title)} AND publisher = {sql_string(row.issuing_body)})",
            "    ORDER BY id LIMIT 1",
            ");",
            "SET @region_id := (",
            "    SELECT id FROM regions",
            f"    WHERE name = {sql_string(row.province)}",
            "    ORDER BY id LIMIT 1",
            ");",
            "INSERT INTO policies (",
            "    title, region_id, issuing_body, document_no, publish_date, effective_date, valid_period, source_id,",
            "    policy_level, policy_type, summary, key_points, support_measures, tags, original_url, evidence_url,",
            "    local_file, accessed_at, status, reviewer",
            ")",
            "SELECT ",
            f"    {sql_string(row.title)}, @region_id, {sql_string(row.issuing_body)}, {sql_string(row.document_no)},",
            f"    {sql_string(row.publish_date)}, {sql_string(row.effective_date)}, {sql_string(row.valid_period)}, @source_id,",
            f"    {sql_string(row.policy_level)}, {sql_string(POLICY_TYPE)}, {sql_string(row.summary)},",
            f"    {sql_string(row.key_points)}, {sql_string(row.support_measures)}, {sql_string(row.tags)},",
            f"    {sql_string(row.original_url)}, {sql_string(row.evidence_url)}, NULL, {sql_string(accessed_at)},",
            f"    {sql_string(row.status)}, {sql_string(REVIEWER)}",
            "WHERE @region_id IS NOT NULL",
            "  AND NOT EXISTS (",
            "      SELECT 1 FROM policies",
            f"      WHERE ({sql_string(row.original_url)} IS NOT NULL AND original_url = {sql_string(row.original_url)})",
            f"         OR (title = {sql_string(row.title)} AND issuing_body = {sql_string(row.issuing_body)})",
            "  );",
            "SET @policy_id := (",
            "    SELECT id FROM policies",
            f"    WHERE ({sql_string(row.original_url)} IS NOT NULL AND original_url = {sql_string(row.original_url)})",
            f"       OR (title = {sql_string(row.title)} AND issuing_body = {sql_string(row.issuing_body)})",
            "    ORDER BY id LIMIT 1",
            ");",
        ])

        for tag_name in row.tag_names:
            lines.extend([
                "INSERT INTO tags (name, tag_type, sort_order)",
                f"VALUES ({sql_string(tag_name)}, {sql_string(POLICY_TAG_TYPE)}, 0)",
                "ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);",
                "SET @tag_id := LAST_INSERT_ID();",
                "INSERT IGNORE INTO policy_tags (policy_id, tag_id)",
                "SELECT @policy_id, @tag_id",
                "WHERE @policy_id IS NOT NULL AND @tag_id IS NOT NULL;",
            ])
        lines.append("")

    lines.extend(["COMMIT;", ""])
    return "\n".join(lines)


def generate_check_sql(rows: list[PolicyRow]) -> str:
    selects: list[str] = []
    for row in rows:
        selects.append(
            "SELECT "
            f"{row.excel_row} AS excel_row, "
            f"{sql_string(row.title)} AS title, "
            f"{sql_string(row.issuing_body)} AS issuing_body, "
            f"{sql_string(row.original_url)} AS original_url, "
            f"{sql_string(row.province)} AS province"
        )

    expected_rows_sql = "\nUNION ALL\n".join(selects)
    return "\n".join([
        "-- Generated by scripts/import_policy_excel.py --write-check-sql",
        "SET NAMES utf8mb4;",
        "USE opc_platform;",
        "",
        "SELECT e.excel_row, e.title, e.issuing_body, e.original_url, e.province",
        "FROM (",
        expected_rows_sql,
        ") e",
        "LEFT JOIN policies p",
        "  ON (e.original_url IS NOT NULL AND p.original_url = e.original_url)",
        "  OR (p.title = e.title AND p.issuing_body = e.issuing_body)",
        "WHERE p.id IS NULL",
        "ORDER BY e.excel_row;",
        "",
        "SELECT",
        "  COUNT(*) AS expected_policy_count,",
        "  SUM(CASE WHEN p.id IS NULL THEN 1 ELSE 0 END) AS missing_policy_count,",
        "  SUM(CASE WHEN p.id IS NOT NULL THEN 1 ELSE 0 END) AS matched_policy_count",
        "FROM (",
        expected_rows_sql,
        ") e",
        "LEFT JOIN policies p",
        "  ON (e.original_url IS NOT NULL AND p.original_url = e.original_url)",
        "  OR (p.title = e.title AND p.issuing_body = e.issuing_body);",
        "",
    ])


def normalize_text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, datetime):
        return value.date().isoformat()
    if isinstance(value, date):
        return value.isoformat()
    text = str(value).strip()
    return re.sub(r"\s+", " ", text)


def parse_date(value: Any) -> str | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.date().isoformat()
    if isinstance(value, date):
        return value.isoformat()
    text = normalize_text(value)
    if not text:
        return None
    match = re.search(r"(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})", text)
    if not match:
        return None
    year, month, day = match.groups()
    try:
        return date(int(year), int(month), int(day)).isoformat()
    except ValueError:
        return None


def parse_tags(value: Any) -> list[str]:
    text = normalize_text(value)
    if not text:
        return []
    parts = [item.strip() for item in re.split(r"[,，]", text)]
    result: list[str] = []
    seen: set[str] = set()
    for part in parts:
        if part and part not in seen:
            result.append(part)
            seen.add(part)
    return result


def build_key_points(tag_names: list[str], province: str, city: str | None, district: str | None) -> str:
    region_path = " / ".join(part for part in [province, city, district] if part)
    lines = [
        f"政策要点：{','.join(tag_names)}" if tag_names else "政策要点：",
        f"地区：{region_path}",
    ]
    return "\n".join(lines)


def build_support_measures(raw: dict[str, Any]) -> str | None:
    sections: list[str] = []
    for field, label in MEASURE_FIELDS:
        value = normalize_text(raw.get(field))
        if value:
            sections.append(f"【{label}】\n{value}")
    return "\n\n".join(sections) if sections else None


def sql_string(value: Any) -> str:
    if value is None or value == "":
        return "NULL"
    text = str(value)
    return "'" + text.replace("\\", "\\\\").replace("'", "''") + "'"


if __name__ == "__main__":
    main()
