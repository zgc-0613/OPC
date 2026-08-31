import json
from pathlib import Path
import openpyxl

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(r"e:\个人发展\0-科研\OPC\opc-sci\调研表\高校OPC社区配套表_20260818.xlsx")
OUT = ROOT / "outputs/university-opc-20260826"


def q(v):
    if v in (None, ""):
        return "NULL"
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def main():
    sheet = openpyxl.load_workbook(SOURCE, read_only=True, data_only=True)["高校OPC社区配套表"]
    rows = list(sheet.iter_rows(values_only=True))
    headers = [str(v or "").strip() for v in rows[0]]
    records = [dict(zip(headers, row)) for row in rows[1:] if any(row)]
    duplicate_ids = {"OPC-COMM-JS-0003"}
    records = [row for row in records if row["community_id"] not in duplicate_ids]
    if len(records) != 7:
        raise RuntimeError(f"expected 7 new communities, got {len(records)}")
    codes = [r["community_id"] for r in records]
    lines = [
        "SET NAMES utf8mb4;",
        "USE opc_platform;",
        "START TRANSACTION;",
        "-- import_batch=university-community-20260818",
        "ALTER TABLE university_opc_records MODIFY COLUMN evidence_grade VARCHAR(30) NULL;",
    ]
    for r in records:
        raw = json.dumps(r, ensure_ascii=False, default=str, separators=(",", ":"))
        source_title = r.get("source_title") or r.get("community_name")
        source_url = r.get("source_url")
        source_unit = r.get("source_unit") or r.get("institution_name")
        notes = f"import_batch=university-community-20260818; source_record={r['community_id']}; remains pending administrator verification"
        lines += [
            "",
            f"-- {r['community_id']} {r['community_name']}",
            "INSERT INTO sources (title,source_type,publisher,url,accessed_at,notes,status,ai_evidence_status)",
            f"SELECT {q(source_title)},'government_site',{q(source_unit)},{q(source_url)},'2026-08-26',{q(notes)},'draft','legacy_unverified'",
            f"WHERE NOT EXISTS (SELECT 1 FROM sources WHERE url={q(source_url)});",
            f"SET @source_id := (SELECT id FROM sources WHERE url={q(source_url)} ORDER BY id LIMIT 1);",
            "INSERT INTO university_opc_records (record_type,record_type_label,record_code,record_name,institution_name,province,city,district,verification_status,evidence_grade,date_original,source_title,source_url,summary_text,notes,raw_details)",
            f"SELECT 'communities','高校 OPC 社区',{q(r['community_id'])},{q(r['community_name'])},{q(r['institution_name'])},{q(r['province'])},{q(r['city'])},{q(r['district'])},'pending',{q(r['evidence_grade'])},{q(r['launch_date'])},{q(r['source_title'])},{q(r['source_url'])},{q(r['service_summary'])},{q(r['notes'])},{q(raw)}",
            f"WHERE NOT EXISTS (SELECT 1 FROM university_opc_records WHERE record_type='communities' AND record_code={q(r['community_id'])});",
        ]
    lines += ["", "COMMIT;", ""]
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "community-increment.sql").write_text("\n".join(lines), encoding="utf-8")
    (OUT / "community-increment-rollback.sql").write_text(
        "USE opc_platform;\nSTART TRANSACTION;\n"
        "DELETE FROM university_opc_records WHERE record_type='communities' AND record_code IN ("
        + ",".join(q(c) for c in codes)
        + ");\nDELETE FROM sources WHERE notes LIKE '%import_batch=university-community-20260818%';\nCOMMIT;\n",
        encoding="utf-8",
    )
    (OUT / "community-increment-postcheck.sql").write_text(
        "USE opc_platform;\nSELECT CONCAT('new_communities=',COUNT(*)) FROM university_opc_records "
        "WHERE record_type='communities' AND record_code IN (" + ",".join(q(c) for c in codes) + ");\n"
        "SELECT verification_status,COUNT(*) FROM university_opc_records WHERE record_type='communities' "
        "AND record_code IN (" + ",".join(q(c) for c in codes) + ") GROUP BY verification_status;\n",
        encoding="utf-8",
    )
    (OUT / "community-increment-records.json").write_text(json.dumps(records, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
    print(json.dumps({"newRecords": len(records), "excludedDuplicates": sorted(duplicate_ids), "output": str(OUT)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
