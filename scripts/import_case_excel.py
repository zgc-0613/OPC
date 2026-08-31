#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Import helper for OPC case Excel data.

Default behavior is dry-run only. Use --write-sql to generate a MySQL script
that can be reviewed and executed manually.
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
DEFAULT_BATCH = "case_excel_20260708"
SOURCE_TYPE = "news"
STATUS = "published"
REVIEWER = "case-excel-import"
CASE_TAG_TYPE = "case"

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

HEADER_ROW = 2
DATA_START_ROW = 3

REQUIRED_HEADERS = [
    "文章名",
    "案例来源（链接）",
    "关联地区-省",
    "关联地区-市",
    "关联地区-区",
    "主体名",
    "是否为大学生创新创业",
    "案例类型",
    "发布日期",
    "100-300字摘要",
    "用到的AI工具或能力",
    "结果（效果、成果、数据）",
]


@dataclass
class CaseRow:
    excel_row: int
    title: str
    original_url: str | None
    province: str
    city: str | None
    district: str | None
    actor_name: str | None
    is_student_startup: str | None
    category: str
    publish_date: str | None
    summary: str
    ai_tools: str | None
    outcome: str | None
    business_model: str
    tags: str | None
    tag_names: list[str]


def main() -> None:
    parser = argparse.ArgumentParser(description="Read OPC case Excel and optionally generate MySQL import SQL.")
    parser.add_argument("--excel", type=Path, default=None, help="Path to case .xlsx file.")
    parser.add_argument("--limit", type=int, default=None, help="Only process the first N valid case rows.")
    parser.add_argument("--batch", default=DEFAULT_BATCH, help="Import batch marker.")
    parser.add_argument("--accessed-at", default=date.today().isoformat(), help="Access/import date, YYYY-MM-DD.")
    parser.add_argument("--write-sql", action="store_true", help="Generate SQL file under outputs/.")
    parser.add_argument("--write-check-sql", action="store_true", help="Generate SQL that checks which Excel cases are missing from DB.")
    parser.add_argument("--output", type=Path, default=None, help="Custom SQL output path.")
    args = parser.parse_args()

    excel_path = args.excel or find_default_excel()
    rows, warnings = read_case_rows(excel_path, args.limit)
    report = build_report(rows, warnings)
    print_report(excel_path, rows, report, args.write_sql)

    if args.write_sql:
        output_path = args.output or DEFAULT_OUTPUT_DIR / f"import_cases_{args.batch}.sql"
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(generate_sql(rows, excel_path.name, args.batch, args.accessed_at), encoding="utf-8")
        print(f"\nSQL file generated: {output_path}")
        print("Review it first, then execute it in MySQL against the opc_platform database.")

    if args.write_check_sql:
        output_path = args.output or DEFAULT_OUTPUT_DIR / f"check_case_import_{args.batch}.sql"
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(generate_check_sql(rows), encoding="utf-8")
        print(f"\nCheck SQL file generated: {output_path}")
        print("Execute it in MySQL to list Excel rows that are not present in case_items.")


def find_default_excel() -> Path:
    files = [
        path for path in DEFAULT_DATA_DIR.glob("*.xlsx")
        if "案例" in path.name and "V2" in path.name and not path.name.startswith("~$")
    ]
    if not files:
        raise FileNotFoundError(f"No case V2 .xlsx file found under {DEFAULT_DATA_DIR}")
    if len(files) > 1:
        names = ", ".join(path.name for path in files)
        raise RuntimeError(f"Multiple case .xlsx files found. Use --excel to choose one: {names}")
    return files[0]


def read_case_rows(excel_path: Path, limit: int | None) -> tuple[list[CaseRow], list[str]]:
    workbook = openpyxl.load_workbook(excel_path, data_only=True)
    sheet = workbook.active
    headers = [normalize_text(cell.value) for cell in sheet[HEADER_ROW]]
    header_index = {header: index for index, header in enumerate(headers) if header}
    missing_headers = [header for header in REQUIRED_HEADERS if header not in header_index]
    if missing_headers:
        raise RuntimeError(f"Missing required headers: {', '.join(missing_headers)}")

    rows: list[CaseRow] = []
    warnings: list[str] = []
    seen_keys: set[tuple[str, str, str]] = set()

    for excel_row, values in enumerate(sheet.iter_rows(min_row=DATA_START_ROW, values_only=True), start=DATA_START_ROW):
        raw = {header: values[index] if index < len(values) else None for header, index in header_index.items()}
        title = normalize_text(raw.get("文章名"))
        if not title:
            continue

        province = normalize_text(raw.get("关联地区-省"))
        city = normalize_text(raw.get("关联地区-市")) or None
        district = normalize_text(raw.get("关联地区-区")) or None
        actor_name = normalize_text(raw.get("主体名")) or None
        category = normalize_text(raw.get("案例类型")) or "其他"
        summary = normalize_text(raw.get("100-300字摘要"))
        original_url = normalize_text(raw.get("案例来源（链接）")) or None
        is_student_startup = normalize_text(raw.get("是否为大学生创新创业")) or None
        ai_tools = normalize_text(raw.get("用到的AI工具或能力")) or None
        outcome = normalize_text(raw.get("结果（效果、成果、数据）")) or None
        publish_date = parse_date(raw.get("发布日期"))
        province, city, district = infer_case_region(title, actor_name, province, city, district)

        if province not in PROVINCE_NAMES:
            warnings.append(f"Row {excel_row}: unknown province '{province}'")
        if not original_url:
            warnings.append(f"Row {excel_row}: missing source URL")
        if not summary:
            warnings.append(f"Row {excel_row}: missing summary")

        duplicate_key = (title, actor_name or "", province)
        if duplicate_key in seen_keys:
            warnings.append(f"Row {excel_row}: duplicate title + actor + province in Excel")
        seen_keys.add(duplicate_key)

        tag_names = build_tag_names(category, is_student_startup, ai_tools)

        rows.append(CaseRow(
            excel_row=excel_row,
            title=title,
            original_url=original_url,
            province=province,
            city=city,
            district=district,
            actor_name=actor_name,
            is_student_startup=is_student_startup,
            category=category,
            publish_date=publish_date,
            summary=summary,
            ai_tools=ai_tools,
            outcome=outcome,
            business_model=build_business_model(province, city, district, category, is_student_startup, publish_date),
            tags=",".join(tag_names) if tag_names else None,
            tag_names=tag_names,
        ))

        if limit is not None and len(rows) >= limit:
            break

    return rows, warnings


def infer_case_region(
    title: str,
    actor_name: str | None,
    province: str,
    city: str | None,
    district: str | None,
) -> tuple[str, str | None, str | None]:
    if province:
        return province, city, district
    if title == "一人公司，只是开始" and actor_name == "王钰博":
        return "北京市", city or "北京市", district or "海淀区"
    return province, city, district


def build_report(rows: list[CaseRow], warnings: list[str]) -> dict[str, Any]:
    province_counter = Counter(row.province for row in rows)
    city_counter = Counter(row.city or "未标注城市" for row in rows)
    category_counter = Counter(row.category for row in rows)
    tag_counter = Counter(tag for row in rows for tag in row.tag_names)
    return {
        "warnings": warnings,
        "province_counter": province_counter,
        "city_counter": city_counter,
        "category_counter": category_counter,
        "tag_counter": tag_counter,
        "source_count": len({row.original_url or f"excel-row-{row.excel_row}" for row in rows}),
        "case_count": len(rows),
        "tag_count": len(tag_counter),
        "case_tag_dictionary_count": sum(len(row.tag_names) for row in rows),
    }


def print_report(excel_path: Path, rows: list[CaseRow], report: dict[str, Any], write_sql: bool) -> None:
    print(f"Excel: {excel_path}")
    print(f"Valid case rows: {len(rows)}")
    print(f"Mode: {'generate SQL' if write_sql else 'dry-run only'}")
    print()
    print("Planned rows:")
    print(f"  sources: up to {report['source_count']} unique source URLs")
    print(f"  case_items: {report['case_count']}")
    print(f"  tags dictionary: {report['tag_count']}")
    print()
    print_counter("Top provinces", report["province_counter"], limit=34)
    print_counter("Top cities", report["city_counter"], limit=20)
    print_counter("Case categories", report["category_counter"])
    print_counter("Case tags", report["tag_counter"], limit=30)
    if report["warnings"]:
        print("\nWarnings:")
        for warning in report["warnings"][:80]:
            print(f"  - {warning}")
        if len(report["warnings"]) > 80:
            print(f"  ... {len(report['warnings']) - 80} more warnings")
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


def generate_sql(rows: list[CaseRow], excel_filename: str, batch: str, accessed_at: str) -> str:
    lines: list[str] = [
        "-- Generated by scripts/import_case_excel.py",
        "-- Review before executing.",
        "SET NAMES utf8mb4;",
        "USE opc_platform;",
        "START TRANSACTION;",
        "",
    ]

    for index, row in enumerate(rows, start=1):
        notes = "; ".join(part for part in [
            f"Imported from {excel_filename}",
            f"import_batch={batch}",
            f"excel_row={row.excel_row}",
            f"publish_date={row.publish_date}" if row.publish_date else "",
            f"city={row.city}" if row.city else "",
            f"district={row.district}" if row.district else "",
        ] if part)

        lines.extend([
            f"-- {index}. Excel row {row.excel_row}: {row.title}",
            "INSERT INTO sources (title, source_type, publisher, url, local_file, accessed_at, notes, status)",
            "SELECT "
            f"{sql_string(row.title)}, {sql_string(SOURCE_TYPE)}, NULL, "
            f"{sql_string(row.original_url)}, NULL, {sql_string(accessed_at)}, {sql_string(notes)}, {sql_string(STATUS)}",
            "WHERE NOT EXISTS (",
            "    SELECT 1 FROM sources",
            f"    WHERE ({sql_string(row.original_url)} IS NOT NULL AND url = {sql_string(row.original_url)})",
            f"       OR (title = {sql_string(row.title)} AND notes LIKE {sql_string('%import_batch=' + batch + '%')})",
            ");",
            "SET @source_id := (",
            "    SELECT id FROM sources",
            f"    WHERE ({sql_string(row.original_url)} IS NOT NULL AND url = {sql_string(row.original_url)})",
            f"       OR (title = {sql_string(row.title)} AND notes LIKE {sql_string('%import_batch=' + batch + '%')})",
            "    ORDER BY id LIMIT 1",
            ");",
            "SET @region_id := (",
            "    SELECT id FROM regions",
            f"    WHERE name = {sql_string(row.province)}",
            "    ORDER BY id LIMIT 1",
            ");",
            "INSERT INTO case_items (",
            "    title, region_id, category, actor_name, source_id, summary, business_model, ai_tools, outcome,",
            "    tags, original_url, local_file, accessed_at, status, reviewer",
            ")",
            "SELECT ",
            f"    {sql_string(row.title)}, @region_id, {sql_string(row.category)}, {sql_string(row.actor_name)}, @source_id,",
            f"    {sql_string(row.summary)}, {sql_string(row.business_model)}, {sql_string(row.ai_tools)}, {sql_string(row.outcome)},",
            f"    {sql_string(row.tags)}, {sql_string(row.original_url)}, NULL, {sql_string(accessed_at)}, {sql_string(STATUS)}, {sql_string(REVIEWER)}",
            "WHERE @region_id IS NOT NULL",
            "  AND @source_id IS NOT NULL",
            "  AND NOT EXISTS (",
            "      SELECT 1 FROM case_items",
            f"      WHERE title = {sql_string(row.title)}",
            f"        AND COALESCE(actor_name, '') = {sql_string(row.actor_name or '')}",
            "        AND region_id = @region_id",
            "  );",
        ])

        for tag_name in row.tag_names:
            lines.extend([
                "INSERT INTO tags (name, tag_type, sort_order)",
                f"VALUES ({sql_string(tag_name)}, {sql_string(CASE_TAG_TYPE)}, 0)",
                "ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);",
            ])
        lines.append("")

    lines.extend(["COMMIT;", ""])
    return "\n".join(lines)


def generate_check_sql(rows: list[CaseRow]) -> str:
    selects: list[str] = []
    for row in rows:
        selects.append(
            "SELECT "
            f"{row.excel_row} AS excel_row, "
            f"{sql_string(row.title)} AS title, "
            f"{sql_string(row.actor_name or '')} AS actor_name, "
            f"{sql_string(row.original_url)} AS original_url, "
            f"{sql_string(row.province)} AS province"
        )

    expected_rows_sql = "\nUNION ALL\n".join(selects)
    return "\n".join([
        "-- Generated by scripts/import_case_excel.py --write-check-sql",
        "SET NAMES utf8mb4;",
        "USE opc_platform;",
        "",
        "SELECT e.excel_row, e.title, e.actor_name, e.original_url, e.province",
        "FROM (",
        expected_rows_sql,
        ") e",
        "LEFT JOIN regions r ON r.name = e.province",
        "LEFT JOIN case_items c",
        "  ON c.title = e.title",
        " AND COALESCE(c.actor_name, '') = e.actor_name",
        " AND c.region_id = r.id",
        "WHERE c.id IS NULL",
        "ORDER BY e.excel_row;",
        "",
        "SELECT",
        "  COUNT(*) AS expected_case_count,",
        "  SUM(CASE WHEN c.id IS NULL THEN 1 ELSE 0 END) AS missing_case_count,",
        "  SUM(CASE WHEN c.id IS NOT NULL THEN 1 ELSE 0 END) AS matched_case_count",
        "FROM (",
        expected_rows_sql,
        ") e",
        "LEFT JOIN regions r ON r.name = e.province",
        "LEFT JOIN case_items c",
        "  ON c.title = e.title",
        " AND COALESCE(c.actor_name, '') = e.actor_name",
        " AND c.region_id = r.id;",
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


def build_tag_names(category: str, is_student_startup: str | None, ai_tools: str | None) -> list[str]:
    result: list[str] = []
    append_unique(result, category)
    if is_student_startup == "是":
        append_unique(result, "大学生创新创业")
    if ai_tools:
        for part in re.split(r"[;；、,，]", ai_tools):
            clean = part.strip()
            if 1 < len(clean) <= 20:
                append_unique(result, clean)
            if len(result) >= 8:
                break
    return result


def build_business_model(
    province: str,
    city: str | None,
    district: str | None,
    category: str,
    is_student_startup: str | None,
    publish_date: str | None,
) -> str:
    region_path = " / ".join(part for part in [province, city, district] if part)
    lines = [
        f"地区：{region_path}",
        f"案例类型：{category}",
    ]
    if is_student_startup:
        lines.append(f"是否为大学生创新创业：{is_student_startup}")
    if publish_date:
        lines.append(f"发布日期：{publish_date}")
    return "\n".join(lines)


def append_unique(items: list[str], value: str | None) -> None:
    if value and value not in items:
        items.append(value)


def sql_string(value: Any) -> str:
    if value is None or value == "":
        return "NULL"
    text = str(value)
    return "'" + text.replace("\\", "\\\\").replace("'", "''") + "'"


if __name__ == "__main__":
    main()
