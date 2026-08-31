import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "opc-frontend/public/university-opc-data.json"
OUT = ROOT / "outputs/university-opc-20260826"


def q(value):
    if value in (None, ""):
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def main():
    payload = json.loads(DATA.read_text(encoding="utf-8"))
    records = payload["records"]
    lines = [
        "SET NAMES utf8mb4;",
        "USE opc_platform;",
        "START TRANSACTION;",
        """CREATE TABLE IF NOT EXISTS university_opc_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  record_type VARCHAR(30) NOT NULL,
  record_type_label VARCHAR(100) NOT NULL,
  record_code VARCHAR(50) NOT NULL,
  record_name VARCHAR(500) NOT NULL,
  institution_name VARCHAR(1000) NULL,
  province VARCHAR(100) NULL,
  city VARCHAR(100) NULL,
  district VARCHAR(200) NULL,
  verification_status VARCHAR(30) NOT NULL DEFAULT 'pending',
  evidence_grade VARCHAR(10) NULL,
  date_original VARCHAR(100) NULL,
  source_title VARCHAR(1000) NULL,
  source_url TEXT NULL,
  summary_text TEXT NULL,
  notes TEXT NULL,
  raw_details LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_university_opc_type_code (record_type, record_code),
  INDEX idx_university_opc_type (record_type),
  INDEX idx_university_opc_province (province),
  INDEX idx_university_opc_status (verification_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;""",
        "",
    ]
    for record in records:
        raw = json.dumps(record, ensure_ascii=False, separators=(",", ":"))
        columns = [
            record["type"], record["typeLabel"], record["id"], record["name"],
            record.get("institution"), record.get("province"), record.get("city"),
            record.get("district"), record.get("status") or "pending", record.get("grade"),
            record.get("date"), record.get("sourceTitle"), record.get("sourceUrl"),
            record.get("summary"), record.get("notes"), raw,
        ]
        lines.append(
            "INSERT INTO university_opc_records "
            "(record_type,record_type_label,record_code,record_name,institution_name,province,city,district,"
            "verification_status,evidence_grade,date_original,source_title,source_url,summary_text,notes,raw_details) "
            f"VALUES ({','.join(q(value) for value in columns)}) "
            "ON DUPLICATE KEY UPDATE record_type_label=VALUES(record_type_label),record_name=VALUES(record_name),"
            "institution_name=VALUES(institution_name),province=VALUES(province),city=VALUES(city),district=VALUES(district),"
            "verification_status=VALUES(verification_status),evidence_grade=VALUES(evidence_grade),date_original=VALUES(date_original),"
            "source_title=VALUES(source_title),source_url=VALUES(source_url),summary_text=VALUES(summary_text),notes=VALUES(notes),"
            "raw_details=VALUES(raw_details);"
        )
    lines.extend([
        "",
        "COMMIT;",
        "",
    ])
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "update.sql").write_text("\n".join(lines), encoding="utf-8")
    (OUT / "rollback.sql").write_text(
        "USE opc_platform;\nDROP TABLE IF EXISTS university_opc_records;\n",
        encoding="utf-8",
    )
    (OUT / "postcheck.sql").write_text(
        "USE opc_platform;\n"
        "SELECT CONCAT('records=',COUNT(*)) FROM university_opc_records;\n"
        "SELECT record_type,COUNT(*) FROM university_opc_records GROUP BY record_type ORDER BY record_type;\n"
        "SELECT CONCAT('missing_required=',COUNT(*)) FROM university_opc_records "
        "WHERE record_code='' OR record_name='' OR source_url IS NULL OR source_url='';\n"
        "SELECT verification_status,COUNT(*) FROM university_opc_records GROUP BY verification_status;\n",
        encoding="utf-8",
    )
    print({"records": len(records), "output": str(OUT)})


if __name__ == "__main__":
    main()
