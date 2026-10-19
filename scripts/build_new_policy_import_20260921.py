import argparse
import json
from collections import Counter
from datetime import date, datetime
from pathlib import Path

import openpyxl


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = Path(r"D:\qq文件\新增OPC政策整合表_20260921_7条.xlsx")
OUT = ROOT / "outputs" / "policy-new-20260921"
BATCH = "policy-new-20260921"
REVIEWER = "manual-workbook-20260921"
ACCESS_DATE = "2026-09-21"

TOPIC_FIELDS = [
    ("具体政策·算力技术", "算力技术"),
    ("具体政策·财政激励", "财政激励"),
    ("具体政策·场景开放", "场景开放"),
    ("具体政策·创业生态", "创业生态"),
    ("具体政策·金融资本", "金融资本"),
    ("具体政策·制度治理", "制度治理"),
    ("具体政策·人才培育", "人才培育"),
]

MATERIAL_NATURE = {
    "正式文件": ("formal_policy", "published"),
    "征求意见稿": ("consultation_draft", "consultation"),
    "标准规范": ("standard_reference", "published"),
    "官方平台服务信息": ("official_platform_service", "published"),
    "其他": ("nonformal_policy_info", "published"),
}

POLICY_LEVEL = {
    "省级": "provincial",
    "市级": "city",
    "市级部门": "city",
    "区级": "district",
    "县级": "district",
    "功能区级": "district",
}

PRIMARY_TYPE = {
    "OPC20260921-001": "comprehensive",
    "OPC20260921-002": "comprehensive",
    "OPC20260921-003": "comprehensive",
    "OPC20260921-004": "comprehensive",
    "OPC20260921-005": "comprehensive",
    "OPC20260921-006": "talent_service",
    "OPC20260921-007": "comprehensive",
}

EFFECTIVE_DATES = {
    "OPC20260921-002": "2026-09-20",
    "OPC20260921-004": "2026-08-27",
    "OPC20260921-005": "2026-09-01",
}


def serializable(value):
    if isinstance(value, (date, datetime)):
        return value.strftime("%Y-%m-%d")
    return value


def clean(value):
    if value is None:
        return ""
    return str(value).strip()


def q(value):
    if value in (None, ""):
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def issuing_body(row):
    value = clean(row.get("发文单位"))
    if value:
        return value
    if row["政策id"] == "OPC20260921-003":
        return "发文单位未公开（新华社、津云报道）"
    raise RuntimeError(f"missing issuing body: {row['政策id']}")


def source_publisher(row):
    if row["政策id"] == "OPC20260921-003":
        return "新华社客户端、津云"
    return issuing_body(row)


def topic_labels(row):
    declared = [item.strip() for item in clean(row.get("涉及主题")).replace("；", ";").split(";") if item.strip()]
    field_topics = [label for field, label in TOPIC_FIELDS if clean(row.get(field))]
    if declared != field_topics:
        raise RuntimeError(
            f"topic mismatch for {row['政策id']}: declared={declared}, fields={field_topics}"
        )
    return declared


def support_measures(row):
    sections = []
    for field, label in TOPIC_FIELDS:
        value = clean(row.get(field))
        if value:
            sections.append(f"【{label}】\n{value}")
    other = clean(row.get("具体政策·其他"))
    if other:
        sections.append(f"【其他说明】\n{other}")
    return "\n\n".join(sections)


def key_points(row, topics):
    location = " / ".join(filter(None, [clean(row.get("省")), clean(row.get("市")), clean(row.get("区"))]))
    values = [
        f"资料性质：{clean(row.get('资料性质'))}",
        f"OPC相关性：{clean(row.get('OPC相关性'))}",
        f"涉及主题：{'、'.join(topics)}",
        f"是否综合性：{clean(row.get('是否政策综合性'))}",
        f"适用地区：{location}",
        f"政策状态：{clean(row.get('政策状态'))}",
    ]
    formed = clean(row.get("成文日期"))
    repealed = clean(row.get("废止日期"))
    if formed:
        values.append(f"成文日期：{formed}")
    if repealed:
        values.append(f"废止日期：{repealed}")
    return "\n".join(values)


def read_rows(source):
    workbook = openpyxl.load_workbook(source, read_only=True, data_only=True)
    sheet = workbook["新增政策整合"]
    iterator = sheet.iter_rows(values_only=True)
    headers = [clean(value) for value in next(iterator)]
    rows = []
    for values in iterator:
        if not any(value not in (None, "") for value in values):
            continue
        rows.append({headers[index]: serializable(value) for index, value in enumerate(values)})
    return rows


def build(source):
    rows = read_rows(source)
    expected_ids = [f"OPC20260921-{index:03d}" for index in range(1, 8)]
    if [row.get("政策id") for row in rows] != expected_ids:
        raise RuntimeError("expected exactly the seven OPC20260921 records in sequence")

    material_counts = Counter(clean(row.get("资料性质")) for row in rows)
    expected_material_counts = Counter({"正式文件": 3, "征求意见稿": 2, "标准规范": 1, "其他": 1})
    if material_counts != expected_material_counts:
        raise RuntimeError(f"unexpected material counts: {material_counts}")

    prepared = []
    for row in rows:
        record_id = row["政策id"]
        title = clean(row.get("政策标题"))
        original_url = clean(row.get("政策原文网页链接"))
        province = clean(row.get("省"))
        summary = clean(row.get("summary摘要（100字左右）"))
        material_label = clean(row.get("资料性质"))
        if not title or not original_url or not province or not summary:
            raise RuntimeError(f"required field missing: {record_id}")
        if material_label not in MATERIAL_NATURE:
            raise RuntimeError(f"unsupported material nature: {material_label}")
        if clean(row.get("政策等级")) not in POLICY_LEVEL:
            raise RuntimeError(f"unsupported policy level: {row.get('政策等级')}")
        topics = topic_labels(row)
        if not topics:
            raise RuntimeError(f"missing topics: {record_id}")
        material_nature, status = MATERIAL_NATURE[material_label]
        prepared.append({
            "recordId": record_id,
            "title": title,
            "originalUrl": original_url,
            "evidenceUrl": clean(row.get("辅证链接")) or None,
            "province": province,
            "city": clean(row.get("市")) or None,
            "district": clean(row.get("区")) or None,
            "issuingBody": issuing_body(row),
            "sourcePublisher": source_publisher(row),
            "sourceType": "news" if record_id == "OPC20260921-003" else "government_site",
            "documentNo": clean(row.get("文号")) or None,
            "publishDate": clean(row.get("发布日期")) or None,
            "effectiveDate": EFFECTIVE_DATES.get(record_id),
            "validPeriod": clean(row.get("政策有效时长")) or None,
            "policyLevel": POLICY_LEVEL[clean(row.get("政策等级"))],
            "policyType": PRIMARY_TYPE[record_id],
            "materialNature": material_nature,
            "status": status,
            "summary": summary,
            "keyPoints": key_points(row, topics),
            "supportMeasures": support_measures(row),
            "topics": topics,
        })

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "import-review.json").write_text(
        json.dumps(prepared, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    lines = [
        "SET NAMES utf8mb4;",
        "USE opc_platform;",
        "START TRANSACTION;",
        "",
        f"-- import_batch={BATCH}",
    ]
    for item in prepared:
        source_notes = (
            f"import_batch={BATCH}; workbook_id={item['recordId']}; "
            "manually curated workbook supplied by user on 2026-09-21"
        )
        tags = "，".join(item["topics"])
        lines.extend([
            "",
            f"-- {item['recordId']}: {item['title']}",
            "INSERT INTO sources (title,source_type,publisher,url,local_file,accessed_at,notes,status,ai_evidence_status,evidence_revision)",
            f"SELECT {q(item['title'])},{q(item['sourceType'])},{q(item['sourcePublisher'])},{q(item['originalUrl'])},NULL,{q(ACCESS_DATE)},{q(source_notes)},'published','verified',1",
            f"WHERE NOT EXISTS (SELECT 1 FROM sources WHERE url={q(item['originalUrl'])} OR (title={q(item['title'])} AND publisher={q(item['sourcePublisher'])}));",
            f"SET @source_id := (SELECT id FROM sources WHERE url={q(item['originalUrl'])} OR (title={q(item['title'])} AND publisher={q(item['sourcePublisher'])}) ORDER BY id LIMIT 1);",
            f"SET @region_id := (SELECT id FROM regions WHERE name={q(item['province'])} AND level='province' ORDER BY id LIMIT 1);",
            "INSERT INTO policies (title,region_id,issuing_body,document_no,publish_date,effective_date,valid_period,source_id,policy_level,policy_type,material_nature,applicability_mode,summary,key_points,support_measures,tags,original_url,evidence_url,local_file,accessed_at,status,reviewer,ai_evidence_status,evidence_revision)",
            f"SELECT {q(item['title'])},@region_id,{q(item['issuingBody'])},{q(item['documentNo'])},{q(item['publishDate'])},{q(item['effectiveDate'])},{q(item['validPeriod'])},@source_id,{q(item['policyLevel'])},{q(item['policyType'])},{q(item['materialNature'])},'unclassified',{q(item['summary'])},{q(item['keyPoints'])},{q(item['supportMeasures'])},{q(tags)},{q(item['originalUrl'])},{q(item['evidenceUrl'])},NULL,{q(ACCESS_DATE)},{q(item['status'])},{q(REVIEWER)},'verified',1",
            f"WHERE @source_id IS NOT NULL AND @region_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM policies WHERE original_url={q(item['originalUrl'])} OR title={q(item['title'])});",
            f"SET @policy_id := (SELECT id FROM policies WHERE original_url={q(item['originalUrl'])} OR title={q(item['title'])} ORDER BY id LIMIT 1);",
        ])
        for order, label in enumerate(item["topics"], 1):
            lines.extend([
                f"INSERT INTO tags (name,tag_type,is_industry,sort_order) VALUES ({q(label)},'policy',0,{order}) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id),sort_order=VALUES(sort_order),is_industry=0;",
                "SET @tag_id := LAST_INSERT_ID();",
                "INSERT IGNORE INTO policy_tags (policy_id,tag_id) SELECT @policy_id,@tag_id WHERE @policy_id IS NOT NULL;",
            ])
    lines.extend(["", "COMMIT;", ""])
    (OUT / "update.sql").write_text("\n".join(lines), encoding="utf-8")

    rollback = f"""SET NAMES utf8mb4;
USE opc_platform;
START TRANSACTION;
DELETE pt FROM policy_tags pt JOIN policies p ON p.id=pt.policy_id WHERE p.reviewer={q(REVIEWER)};
DELETE pit FROM policy_industry_tags pit JOIN policies p ON p.id=pit.policy_id WHERE p.reviewer={q(REVIEWER)};
DELETE FROM policies WHERE reviewer={q(REVIEWER)};
DELETE FROM sources WHERE notes LIKE {q('%import_batch=' + BATCH + '%')};
COMMIT;
"""
    (OUT / "rollback.sql").write_text(rollback, encoding="utf-8")

    urls = ",".join(q(item["originalUrl"]) for item in prepared)
    postcheck = f"""SET NAMES utf8mb4;
SELECT CONCAT('batch_policies=',COUNT(*)) FROM policies WHERE reviewer={q(REVIEWER)};
SELECT CONCAT('batch_sources=',COUNT(*)) FROM sources WHERE notes LIKE {q('%import_batch=' + BATCH + '%')};
SELECT CONCAT('formal=',SUM(material_nature='formal_policy'),',consultation=',SUM(material_nature='consultation_draft'),',standard=',SUM(material_nature='standard_reference'),',other=',SUM(material_nature='nonformal_policy_info')) FROM policies WHERE reviewer={q(REVIEWER)};
SELECT CONCAT('published=',SUM(status='published'),',consultation_status=',SUM(status='consultation')) FROM policies WHERE reviewer={q(REVIEWER)};
SELECT CONCAT('verified_policies=',SUM(ai_evidence_status='verified'),',verified_sources=',(SELECT SUM(ai_evidence_status='verified') FROM sources WHERE notes LIKE {q('%import_batch=' + BATCH + '%')})) FROM policies WHERE reviewer={q(REVIEWER)};
SELECT CONCAT('missing=',COUNT(*)) FROM policies WHERE reviewer={q(REVIEWER)} AND (title='' OR issuing_body='' OR summary='' OR original_url='' OR region_id IS NULL OR source_id IS NULL OR tags='');
SELECT CONCAT('tagless=',COUNT(*)) FROM policies p WHERE p.reviewer={q(REVIEWER)} AND NOT EXISTS (SELECT 1 FROM policy_tags pt WHERE pt.policy_id=p.id);
SELECT CONCAT('topic_relations=',COUNT(*),',topic_names=',COUNT(DISTINCT t.name)) FROM policy_tags pt JOIN policies p ON p.id=pt.policy_id JOIN tags t ON t.id=pt.tag_id WHERE p.reviewer={q(REVIEWER)};
SELECT CONCAT('duplicate_urls=',COUNT(*)) FROM (SELECT original_url FROM policies WHERE original_url IN ({urls}) GROUP BY original_url HAVING COUNT(*)>1) duplicates;
SELECT CONCAT('total_policies=',COUNT(*),',formal_published=',SUM(material_nature='formal_policy' AND status='published'),',consultations=',SUM(material_nature='consultation_draft'),',standards=',SUM(material_nature='standard_reference')) FROM policies;
SELECT id,title,material_nature,status,tags,original_url FROM policies WHERE reviewer={q(REVIEWER)} ORDER BY id;
"""
    (OUT / "postcheck.sql").write_text(postcheck, encoding="utf-8")
    print(json.dumps({
        "records": len(prepared),
        "materialCounts": material_counts,
        "topicRelations": sum(len(item["topics"]) for item in prepared),
        "output": str(OUT),
    }, ensure_ascii=False, default=dict))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    args = parser.parse_args()
    build(args.source)


if __name__ == "__main__":
    main()
