import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SNAPSHOT = ROOT / 'outputs/policies-production-20260826.json'
OUT = ROOT / 'outputs/policy-reclassification-20260828'


def quote(value: str) -> str:
    return "'" + value.replace('\\', '\\\\').replace("'", "''") + "'"


def main() -> None:
    rows = json.loads(SNAPSHOT.read_text(encoding='utf-8'))
    consultation = [
        row for row in rows
        if str(row.get('status') or '').lower() == 'consultation'
        or '征求意见稿' in str(row.get('title') or '')
    ]
    ids = sorted({int(row['id']) for row in consultation})
    title_status_mismatch = [
        row for row in consultation
        if str(row.get('status') or '').lower() != 'consultation'
    ]
    id_list = ','.join(str(i) for i in ids)
    sql = f"""USE opc_platform;
START TRANSACTION;

-- Consultation drafts remain visible in the database but are excluded from
-- the seven-category classification, statistics, and subsequent review.
UPDATE policies
SET status='consultation', reviewer='consultation-scope-20260829'
WHERE id IN ({id_list});

SELECT COUNT(*) AS marked_consultation
FROM policies WHERE id IN ({id_list}) AND status='consultation';
SELECT id,title,status
FROM policies WHERE id IN ({id_list})
ORDER BY id;
COMMIT;
"""
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'consultation-scope-update.sql').write_text(sql, encoding='utf-8')
    report = [
        '# 征求意见稿范围标注',
        '',
        '处理规则：征求意见稿保留在数据库中，但不纳入七类分类、统计和后续人工核对。',
        '',
        f'- 共识别：{len(ids)} 条',
        f'- 原状态已为 consultation：{len(ids) - len(title_status_mismatch)} 条',
        f'- 需要状态统一为 consultation：{len(title_status_mismatch)} 条（标题含“征求意见稿”但原状态不是 consultation）',
        f'- ID 列表：{", ".join(map(str, ids))}',
        '',
        '| ID | 标题 | 原状态 | 统一后状态 |',
        '|---:|---|---|---|',
    ]
    for row in consultation:
        report.append(f"| {row['id']} | {row.get('title') or ''} | {row.get('status') or '未标注'} | consultation（征求意见稿） |")
    (OUT / 'consultation-scope-report.md').write_text('\n'.join(report), encoding='utf-8')
    print(json.dumps({
        'consultationCount': len(ids),
        'alreadyConsultation': len(ids) - len(title_status_mismatch),
        'statusUpdatesNeeded': len(title_status_mismatch),
        'ids': ids,
        'sql': str(OUT / 'consultation-scope-update.sql'),
    }, ensure_ascii=False))


if __name__ == '__main__':
    main()
