import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "outputs/policy-manual-review-round2-encoding-fixed-20260826.json"
OUT = ROOT / "outputs/policy-classification-20260826"


def quote(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def main() -> None:
    rows = json.loads(SOURCE.read_text(encoding="utf-8"))
    if len(rows) != 90:
        raise RuntimeError(f"expected 90 policies, got {len(rows)}")
    ids = [int(row["id"]) for row in rows]
    if len(set(ids)) != 90:
        raise RuntimeError("duplicate policy ids")

    allowed = {
        "comprehensive": "综合发展政策",
        "computing_support": "算力技术",
        "funding_subsidy": "财政激励",
        "scenario_demand": "场景开放",
        "talent_service": "人才培育",
        "investment": "金融资本",
        "governance_market": "制度治理",
    }
    id_list = ",".join(map(str, ids))
    type_cases = "\n".join(
        f"WHEN {row['id']} THEN {quote(row['primaryType'])}" for row in rows
    )
    tag_cases = "\n".join(
        f"WHEN {row['id']} THEN {quote('，'.join(row['replacementTagLabels']))}"
        for row in rows
    )
    relation_values = []
    for row in rows:
        for label in row["replacementTagLabels"]:
            relation_values.append(f"({row['id']}, {quote(label)})")

    OUT.mkdir(parents=True, exist_ok=True)
    migration = f"""USE opc_platform;
START TRANSACTION;

CREATE TABLE IF NOT EXISTS policy_classification_backup_20260826 (
  policy_id BIGINT PRIMARY KEY,
  policy_type VARCHAR(50),
  tags VARCHAR(500),
  original_url VARCHAR(500),
  status VARCHAR(20),
  ai_evidence_status VARCHAR(30)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS policy_tag_rel_backup_20260826 (
  policy_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (policy_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO policy_classification_backup_20260826
  (policy_id, policy_type, tags, original_url, status, ai_evidence_status)
SELECT id, policy_type, tags, original_url, status, ai_evidence_status
FROM policies WHERE id IN ({id_list});

INSERT IGNORE INTO policy_tag_rel_backup_20260826 (policy_id, tag_id)
SELECT policy_id, tag_id FROM policy_tags WHERE policy_id IN ({id_list});

INSERT INTO tags (name, tag_type, is_industry, sort_order)
VALUES
{','.join(f"({quote(label)}, 'policy', 0, {index})" for index, label in enumerate(allowed.values(), 1))}
ON DUPLICATE KEY UPDATE sort_order=VALUES(sort_order), is_industry=0;

UPDATE policies
SET policy_type = CASE id
{type_cases}
END,
tags = CASE id
{tag_cases}
END
WHERE id IN ({id_list});

DELETE FROM policy_tags WHERE policy_id IN ({id_list});

INSERT IGNORE INTO policy_tags (policy_id, tag_id)
SELECT replacement.policy_id, dictionary.id
FROM (
  SELECT relation.policy_id, relation.tag_name
  FROM (VALUES_PLACEHOLDER) relation
) replacement
JOIN tags dictionary
  ON dictionary.name = replacement.tag_name AND dictionary.tag_type = 'policy';

COMMIT;
"""
    # MySQL has no portable VALUES table constructor in all supported versions.
    union_rows = "\n  UNION ALL ".join(
        f"SELECT {row['id']} AS policy_id, {quote(label)} AS tag_name"
        for row in rows for label in row["replacementTagLabels"]
    )
    migration = migration.replace(
        "SELECT relation.policy_id, relation.tag_name\n  FROM (VALUES_PLACEHOLDER) relation",
        union_rows,
    )
    (OUT / "update.sql").write_text(migration, encoding="utf-8")

    rollback = f"""USE opc_platform;
START TRANSACTION;
UPDATE policies p
JOIN policy_classification_backup_20260826 b ON b.policy_id=p.id
SET p.policy_type=b.policy_type, p.tags=b.tags;
DELETE FROM policy_tags WHERE policy_id IN ({id_list});
INSERT IGNORE INTO policy_tags (policy_id, tag_id)
SELECT policy_id, tag_id FROM policy_tag_rel_backup_20260826;
COMMIT;
"""
    (OUT / "rollback.sql").write_text(rollback, encoding="utf-8")

    checks = f"""SELECT CONCAT('target=',COUNT(*)) FROM policies WHERE id IN ({id_list});
SELECT policy_type,COUNT(*) FROM policies WHERE id IN ({id_list}) GROUP BY policy_type ORDER BY policy_type;
SELECT CONCAT('invalid_types=',COUNT(*)) FROM policies WHERE id IN ({id_list})
AND policy_type NOT IN ({','.join(quote(code) for code in allowed)});
SELECT CONCAT('missing_tags=',COUNT(*)) FROM policies WHERE id IN ({id_list}) AND (tags IS NULL OR tags='');
SELECT CONCAT('url_changed=',COUNT(*)) FROM policies p JOIN policy_classification_backup_20260826 b ON b.policy_id=p.id
WHERE NOT (p.original_url <=> b.original_url);
SELECT CONCAT('status_changed=',COUNT(*)) FROM policies p JOIN policy_classification_backup_20260826 b ON b.policy_id=p.id
WHERE NOT (p.status <=> b.status) OR NOT (p.ai_evidence_status <=> b.ai_evidence_status);
"""
    (OUT / "postcheck.sql").write_text(checks, encoding="utf-8")
    print(OUT)


if __name__ == "__main__":
    main()
