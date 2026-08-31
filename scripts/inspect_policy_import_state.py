#!/usr/bin/env python
"""Read-only production preflight for a policy workbook import."""

from __future__ import annotations

import argparse
import importlib.util
import os
import sys
from pathlib import Path

from import_policy_excel import read_policy_rows, sql_string


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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--excel", type=Path, required=True)
    args = parser.parse_args()

    rows, warnings = read_policy_rows(args.excel, None)
    expected = "\nUNION ALL\n".join(
        "SELECT "
        f"{row.excel_row} excel_row, {sql_string(row.title)} title, "
        f"{sql_string(row.issuing_body)} issuing_body, {sql_string(row.original_url)} original_url, "
        f"{sql_string(row.province)} province, {sql_string(row.city)} city, {sql_string(row.district)} district"
        for row in rows
    )
    sql = f"""
SELECT CONCAT('policy_count=',COUNT(*),', policy_max_id=',COALESCE(MAX(id),0)) FROM policies;
SELECT CONCAT('source_count=',COUNT(*),', source_max_id=',COALESCE(MAX(id),0)) FROM sources;
SELECT level,COUNT(*) region_count FROM regions GROUP BY level ORDER BY level;
SELECT id,name,level,parent_id FROM regions
WHERE name IN ({','.join(sql_string(name) for name in sorted({name for row in rows for name in (row.province, row.city, row.district) if name}))})
ORDER BY level,id;
SELECT e.excel_row,e.title,
       COALESCE(p.id,0) policy_id,COALESCE(s.id,0) source_id,
       COALESCE(province.id,0) province_id,COALESCE(city.id,0) city_id,COALESCE(district.id,0) district_id
FROM ({expected}) e
LEFT JOIN policies p ON (e.original_url IS NOT NULL AND p.original_url=e.original_url)
  OR (p.title=e.title AND p.issuing_body=e.issuing_body)
LEFT JOIN sources s ON (e.original_url IS NOT NULL AND s.url=e.original_url)
  OR (s.title=e.title AND s.publisher=e.issuing_body)
LEFT JOIN regions province ON province.name=e.province AND province.level='province'
LEFT JOIN regions city ON city.name=e.city AND city.parent_id=province.id
LEFT JOIN regions district ON district.name=e.district AND district.parent_id=city.id
ORDER BY e.excel_row;
"""

    deploy = load_deploy_module()
    deploy.load_local_deploy_secrets(os.environ, deploy.LOCAL_DEPLOY_SECRET_FILE)
    client = deploy.connect()
    try:
        _, output, _ = deploy.database_command(client, sql)
        print(output)
        if warnings:
            print("Workbook warnings:")
            for warning in warnings:
                print(f"- {warning}")
    finally:
        client.close()


if __name__ == "__main__":
    main()
