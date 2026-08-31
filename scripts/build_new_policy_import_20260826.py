import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "outputs/policy-new-gd-zj-xj-20260826"
ROWS = json.loads((BASE / "classification-review.json").read_text(encoding="utf-8"))
BATCH = "policy-gd-zj-xj-20260826"
ACCESS_DATE = "2026-08-26"

ISSUERS = {
    97: "梅州市科学技术局",
    98: "广州市白云区人民政府办公室",
    99: "广州市天河区人民政府",
    100: "中山市人工智能产业办公室",
    101: "广州市南沙区人民政府",
    102: "深圳市龙华区科技创新局",
    103: "广州市市场监督管理局",
    104: "佛山市南海区桂城街道办事处",
    105: "汕头华侨试验区管理委员会",
    106: "佛山市南海区人民政府",
    107: "杭州市上城区人民政府",
    108: "杭州市萧山区人民政府",
    109: "台州市市场监督管理局",
    110: "苍南县人工智能专班办公室",
    111: "杭州高新技术产业开发区管理委员会、杭州市滨江区人民政府",
    112: "湖州市市场监督管理局",
    113: "宁波市市场监督管理局",
    114: "乌鲁木齐高新技术产业开发区（新市区）管理委员会",
    115: "哈密市伊州区人民政府",
    116: "乌鲁木齐经济技术开发区（头屯河区）管理委员会",
}

BEST_URLS = {
    104: "https://news.southcn.com/node_ac2b0b62a4/cff475d96c.shtml",
    108: "https://hz.bendibao.com/live/2026312/169216.shtm",
    114: "https://app.xinhuanet.com/news/article.html?articleId=572bfed2529491bc40be559661a4a73f",
}

PUBLISH_DATES = {
    100: "2026-07-28",
    107: "2026-03-11",
    109: "2026-04-10",
    113: "2026-07-23",
    116: "2026-05-06",
}

DRAFT_IDS = {102, 108, 110, 114, 115}
SOURCE_TYPES = {
    100: "news",
    104: "news",
    108: "news",
    114: "news",
    115: "news",
}

LEVELS = {
    "省级": "provincial",
    "市级": "city",
    "市级部门": "city",
    "区级": "district",
    "县级": "district",
    "镇街级": "district",
}

MEASURE_FIELDS = [
    ("具体政策·算力支持", "算力技术"),
    ("具体政策·资金补贴", "财政激励"),
    ("具体政策·场地工位", "创业生态"),
    ("具体政策·场景需求", "场景开放"),
    ("具体政策·人才服务", "人才培育"),
    ("具体政策·投资融资", "金融资本"),
    ("具体政策·其他", "制度治理"),
]


def q(value):
    if value in (None, ""):
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def build_measures(source):
    sections = []
    for field, label in MEASURE_FIELDS:
        value = source.get(field)
        if value not in (None, ""):
            sections.append(f"【{label}】\n{value}")
    return "\n\n".join(sections)


def main():
    if len(ROWS) != 20:
        raise RuntimeError("expected 20 rows")
    target_ids = [row["id"] + 2 for row in ROWS]
    id_list = ",".join(map(str, target_ids))
    lines = [
        "SET NAMES utf8mb4;",
        "USE opc_platform;",
        "START TRANSACTION;",
        "",
        f"-- import_batch={BATCH}",
    ]
    mapping = []
    for row in ROWS:
        old_id = row["id"]
        new_id = old_id + 2
        source = row["sourceRow"]
        title = row["title"]
        issuer = ISSUERS[old_id]
        original_input = row.get("originalUrl")
        original_url = BEST_URLS.get(old_id, original_input)
        alternatives = [value for value in [original_input, row.get("evidenceUrl")] if value and value != original_url]
        evidence_url = " | ".join(alternatives) or None
        publish_date = PUBLISH_DATES.get(old_id, source.get("发布日期"))
        policy_status = "draft" if old_id in DRAFT_IDS else "pending"
        source_type = SOURCE_TYPES.get(old_id, "government_site")
        policy_level = LEVELS.get(source.get("policy_level政策等级"), "district")
        tags = "，".join(row["topicTagLabels"])
        key_points = (
            f"主分类：{row['primaryTypeLabel']}\n"
            f"涉及主题：{tags}\n"
            + "\n".join(row.get("classificationEvidence") or [])
        )
        notes = (
            f"import_batch={BATCH}; workbook_id={old_id}; assigned_policy_id={new_id}; "
            "source reviewed 2026-08-26; remains pending administrator evidence approval"
        )
        lines.extend([
            "",
            f"-- workbook {old_id} -> policy {new_id}: {title}",
            "INSERT INTO sources (title, source_type, publisher, url, local_file, accessed_at, notes, status, ai_evidence_status)",
            f"SELECT {q(title)}, {q(source_type)}, {q(issuer)}, {q(original_url)}, NULL, {q(ACCESS_DATE)}, {q(notes)}, 'draft', 'legacy_unverified'",
            f"WHERE NOT EXISTS (SELECT 1 FROM sources WHERE url={q(original_url)} OR (title={q(title)} AND publisher={q(issuer)}));",
            f"SET @source_id := (SELECT id FROM sources WHERE url={q(original_url)} OR (title={q(title)} AND publisher={q(issuer)}) ORDER BY id LIMIT 1);",
            f"SET @region_id := (SELECT id FROM regions WHERE name={q(row['province'])} ORDER BY id LIMIT 1);",
            "INSERT INTO policies (id,title,region_id,issuing_body,document_no,publish_date,effective_date,valid_period,source_id,policy_level,policy_type,summary,key_points,support_measures,tags,original_url,evidence_url,local_file,accessed_at,status,reviewer,ai_evidence_status)",
            f"SELECT {new_id},{q(title)},@region_id,{q(issuer)},{q(source.get('文号'))},{q(publish_date)},{q(source.get('开始实施时间'))},{q(source.get('政策有效时长'))},@source_id,{q(policy_level)},{q(row['primaryType'])},{q(source.get('summary摘要（100字左右）'))},{q(key_points)},{q(build_measures(source))},{q(tags)},{q(original_url)},{q(evidence_url)},NULL,{q(ACCESS_DATE)},{q(policy_status)},'codex-source-review-pending','legacy_unverified'",
            f"WHERE @region_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM policies WHERE id={new_id} OR original_url={q(original_url)} OR title={q(title)});",
            f"SET @policy_id := (SELECT id FROM policies WHERE id={new_id} OR original_url={q(original_url)} OR title={q(title)} ORDER BY id LIMIT 1);",
        ])
        for order, label in enumerate(row["topicTagLabels"], 1):
            lines.extend([
                f"INSERT INTO tags (name,tag_type,is_industry,sort_order) VALUES ({q(label)},'policy',0,{order}) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id);",
                "SET @tag_id := LAST_INSERT_ID();",
                "INSERT IGNORE INTO policy_tags (policy_id,tag_id) SELECT @policy_id,@tag_id WHERE @policy_id IS NOT NULL;",
            ])
        mapping.append({
            "workbookId": old_id,
            "policyId": new_id,
            "title": title,
            "status": policy_status,
            "primaryType": row["primaryType"],
            "primaryTypeLabel": row["primaryTypeLabel"],
            "originalUrl": original_url,
            "evidenceUrl": evidence_url,
        })
    lines.extend(["", "COMMIT;", ""])
    (BASE / "update.sql").write_text("\n".join(lines), encoding="utf-8")

    rollback = f"""SET NAMES utf8mb4;
USE opc_platform;
START TRANSACTION;
DELETE FROM policy_tags WHERE policy_id IN ({id_list});
DELETE FROM policies WHERE id IN ({id_list});
DELETE FROM sources WHERE notes LIKE '%import_batch={BATCH}%';
COMMIT;
"""
    (BASE / "rollback.sql").write_text(rollback, encoding="utf-8")

    postcheck = f"""SELECT CONCAT('imported=',COUNT(*)) FROM policies WHERE id IN ({id_list});
SELECT CONCAT('sources=',COUNT(*)) FROM sources WHERE notes LIKE '%import_batch={BATCH}%';
SELECT CONCAT('pending=',SUM(status='pending'),',draft=',SUM(status='draft'),',published=',SUM(status='published')) FROM policies WHERE id IN ({id_list});
SELECT policy_type,COUNT(*) FROM policies WHERE id IN ({id_list}) GROUP BY policy_type ORDER BY policy_type;
SELECT CONCAT('verified=',SUM(ai_evidence_status='verified'),',unverified=',SUM(ai_evidence_status='legacy_unverified')) FROM policies WHERE id IN ({id_list});
SELECT CONCAT('missing=',COUNT(*)) FROM policies WHERE id IN ({id_list}) AND (title='' OR summary='' OR issuing_body='' OR original_url='' OR region_id IS NULL OR tags='');
SELECT CONCAT('tagless=',COUNT(*)) FROM policies p WHERE p.id IN ({id_list}) AND NOT EXISTS (SELECT 1 FROM policy_tags pt WHERE pt.policy_id=p.id);
"""
    (BASE / "postcheck.sql").write_text(postcheck, encoding="utf-8")
    (BASE / "id-mapping.json").write_text(json.dumps(mapping, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({
        "records": len(mapping),
        "ids": [min(target_ids), max(target_ids)],
        "pending": sum(item["status"] == "pending" for item in mapping),
        "draft": sum(item["status"] == "draft" for item in mapping),
        "output": str(BASE),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
