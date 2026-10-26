"""Build the platform-site SQL seed and frontend fallback from the supplied CSV.

Usage: python scripts/build_platform_sites_import_20261025.py <source.csv>
The script validates the input before replacing either generated artifact.
"""

import csv
import sys
from pathlib import Path
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]
SQL_PATH = ROOT / "deploy/sql/20261025_platform_sites_seed.sql"
FIELDS = ("id", "name", "url", "category", "owner", "services", "summary", "domain", "logo_local", "logo_url")


def sql_string(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def read_sites(source):
    with source.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if tuple(reader.fieldnames or ()) != FIELDS:
            raise ValueError(f"Unexpected CSV columns: {reader.fieldnames}")
        rows = list(reader)

    if len(rows) != 37:
        raise ValueError(f"Expected 37 supplied websites, received {len(rows)}")

    seen_ids = set()
    seen_urls = set()
    for row in rows:
        identifier = int(row["id"])
        if identifier in seen_ids:
            raise ValueError(f"Duplicate source id: {identifier}")
        seen_ids.add(identifier)
        if not all(row[field].strip() for field in ("name", "url", "category", "summary", "domain")):
            raise ValueError(f"Missing required field in source id {identifier}")
        parsed = urlparse(row["url"])
        if parsed.scheme not in ("http", "https") or not parsed.hostname:
            raise ValueError(f"Invalid website URL in source id {identifier}: {row['url']}")
        if row["url"] in seen_urls:
            raise ValueError(f"Duplicate website URL: {row['url']}")
        seen_urls.add(row["url"])
    if seen_ids != set(range(1, 38)):
        raise ValueError("Source ids must run from 1 through 37")
    return rows


def build_sql(rows):
    lines = [
        "-- Supplied OPC navigation CSV snapshot, 2026-10-25 (37 records).",
        "-- Run after 20261025_platform_sites.sql. Links, owners, services and icons are not independently verified.",
        "-- The duplicate-key no-op preserves later edits when this seed is rerun.",
        "SET NAMES utf8mb4;",
        "INSERT INTO platform_sites",
        "  (source_key, name, url, category, owner, services, summary, domain, favicon_url, placeholder_logo_path, sort_order, status, verification_status, source_batch)",
        "VALUES",
    ]
    values = []
    for row in rows:
        identifier = int(row["id"])
        cells = [
            f"opc-nav-20261025-{identifier:02d}", row["name"], row["url"], row["category"],
            row["owner"], row["services"], row["summary"], row["domain"],
            row["logo_url"], row["logo_local"],
        ]
        values.append("  (" + ", ".join(sql_string(cell) for cell in cells) +
                      f", {identifier}, 'published', 'pending', 'opc-nav-20261025')")
    lines.append(",\n".join(values) + "\nON DUPLICATE KEY UPDATE source_key=source_key;")
    return "\n".join(lines) + "\n"


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    rows = read_sites(Path(sys.argv[1]))
    SQL_PATH.parent.mkdir(parents=True, exist_ok=True)
    SQL_PATH.write_text(build_sql(rows), encoding="utf-8")
    print(f"Generated {len(rows)} platform sites in {SQL_PATH}")


if __name__ == "__main__":
    main()
