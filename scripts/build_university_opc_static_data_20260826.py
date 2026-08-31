import csv
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = Path(r"e:\个人发展\0-科研\OPC\opc-sci\调研表\Kimi_Agent_高校OPC数据补全")
OUTPUT = ROOT / "opc-frontend/public/university-opc-data.json"

FILES = [
    ("communities", "高校 OPC 社区", "工作表1_高校OPC社区_v3.csv", "community_id", "community_name"),
    ("support", "支持措施", "工作表2_高校OPC支持措施_v3.csv", "support_id", "support_name"),
    ("activities", "竞赛与活动", "工作表3_高校OPC竞赛与活动_v3.csv", "activity_id", "activity_name"),
    ("cases", "高校创业案例", "工作表4_高校OPC创业案例_v3.csv", "case_id", "case_name"),
]


def first_url(value):
    return str(value or "").split(";")[0].strip()


def date_value(row):
    for field in ("launch_date", "start_date", "activity_date", "publish_date"):
        if row.get(field):
            return row[field].strip()
    return ""


def make_record(record_type, label, row, id_field, name_field):
    if record_type == "communities":
        institution = row.get("institution_name", "")
        summary = row.get("service_summary", "")
        grade = row.get("evidence_grade", "")
        status = row.get("verification_status", "")
        source_title = row.get("source_title", "")
        source_url = row.get("source_url", "")
        province, city = row.get("province", ""), row.get("city", "")
    elif record_type == "support":
        institution = row.get("institution_name", "")
        summary = row.get("support_content", "")
        grade = row.get("evidence_grade", "")
        status = row.get("verification_status", "")
        source_title = row.get("source_title", "")
        source_url = row.get("source_url", "")
        province, city = row.get("province", ""), row.get("city", "")
    elif record_type == "activities":
        institution = row.get("host_institution", "")
        summary = row.get("result_summary", "") or row.get("support_measures", "")
        grade = row.get("evidence_grade", "")
        status = row.get("verification_status", "")
        source_title = row.get("source_title", "")
        source_url = row.get("source_url", "")
        province, city = row.get("province", ""), row.get("city", "")
    else:
        institution = row.get("institution_name", "")
        summary = row.get("summary_100_300", "")
        grade = row.get("evidence_grade", "")
        status = row.get("verification_status", "")
        source_title = row.get("article_title", "") or row.get("source_title", "")
        source_url = row.get("source_url", "")
        province, city = row.get("province", ""), row.get("city", "")

    return {
        "type": record_type,
        "typeLabel": label,
        "id": row.get(id_field, "").strip(),
        "name": row.get(name_field, "").strip(),
        "institution": institution.strip(),
        "province": province.strip(),
        "city": city.strip(),
        "district": row.get("district", "").strip(),
        "status": status.strip() or "pending",
        "grade": grade.strip(),
        "date": date_value(row),
        "sourceTitle": source_title.strip(),
        "sourceUrl": source_url.strip(),
        "summary": summary.strip(),
        "notes": row.get("notes", "").strip(),
    }


def main():
    records = []
    for record_type, label, filename, id_field, name_field in FILES:
        path = SOURCE_DIR / filename
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            seen = set()
            for row in reader:
                record = make_record(record_type, label, row, id_field, name_field)
                key = (record_type, record["id"] or record["name"], record["sourceUrl"])
                if key in seen:
                    continue
                seen.add(key)
                if not record["id"] or not record["name"]:
                    raise RuntimeError(f"missing id/name in {filename}: {row}")
                records.append(record)

    ids = [f"{record['type']}:{record['id']}" for record in records]
    if len(ids) != len(set(ids)):
        raise RuntimeError("duplicate record ids")
    if len(records) != 85:
        raise RuntimeError(f"expected 85 cumulative v3 records, got {len(records)}")

    payload = {
        "collectedAt": "2026-08-26",
        "previewOnly": True,
        "sourceVersion": "高校 OPC 数据集 v3（三轮累计）",
        "records": records,
    }
    OUTPUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    counts = {}
    for record in records:
        counts[record["type"]] = counts.get(record["type"], 0) + 1
    print(json.dumps({"records": len(records), "counts": counts, "output": str(OUTPUT)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
