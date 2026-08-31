import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLASSIFICATION = ROOT / 'outputs/policy-reclassification-20260828/reclassification-v2.json'
SNAPSHOT = ROOT / 'outputs/policies-production-20260826.json'
OUT = ROOT / 'outputs/policy-reclassification-20260828'


def link(value):
    value = str(value or '').strip()
    return '' if value in {'无', '暂无', '未提供', 'nan', 'None'} else value


def main():
    classified = json.loads(CLASSIFICATION.read_text(encoding='utf-8'))
    production = {int(row['id']): row for row in json.loads(SNAPSHOT.read_text(encoding='utf-8'))}
    rows = [
        row for row in classified
        if row.get('scope') == 'specialized'
        and row.get('includeInPolicyStats')
        and row.get('recordNature') == 'policy'
    ]
    rows.sort(key=lambda row: int(row['id']))

    result = []
    for row in rows:
        source = production.get(int(row['id']), {})
        result.append({
            'id': int(row['id']),
            'title': row.get('title') or source.get('title') or '',
            'status': source.get('status') or '',
            'policyType': row.get('primary') or '',
            'themes': row.get('themeLabels') or [],
            'originalUrl': link(source.get('originalUrl')),
            'evidenceUrl': link(source.get('evidenceUrl')),
            'sourceTitle': source.get('sourceTitle') or '',
            'issuingBody': source.get('issuingBody') or '',
            'policyLevel': source.get('policyLevel') or '',
            'oldPrimary': row.get('oldPrimary') or '',
            'confidence': row.get('confidence') or '',
            'scores': row.get('scores') or {},
            'reviewQuestion': f"核对是否以“{row.get('primary') or '当前主分类'}”作为唯一主导支持机制；若有多个同等核心机制，应改为综合型。",
        })

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'specialized-policy-review-list.json').write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8'
    )

    lines = [
        '# 专项型政策人工核验清单',
        '',
        '范围：正式政策中当前候选为专项型的记录。征求意见稿、标准规范资料、官方报道/非政策资料已排除。',
        '核验要求：必须阅读原文或明确的官方解读，确认政策最主要的支持机制；如果多个机制同等重要，改为综合型。涉及主题只能依据原文具体措施确认。',
        '',
        f'共 {len(result)} 条。',
        '',
        '| ID | 政策标题 | 当前候选主分类 | 候选涉及主题 | 状态 | 原文链接 | 辅证链接 | 核验意见 |',
        '|---:|---|---|---|---|---|---|---|',
    ]
    for row in result:
        original = f"[原文]({row['originalUrl']})" if row['originalUrl'] else '未提供'
        evidence = f"[辅证]({row['evidenceUrl']})" if row['evidenceUrl'] else '未提供'
        themes = '、'.join(row['themes']) or '待确认'
        lines.append(
            f"| {row['id']} | {row['title']} | {row['policyType']} | {themes} | "
            f"{row['status'] or '未标注'} | {original} | {evidence} | 待人工核验 |"
        )

    lines.extend(['', '## 逐条核验提示', ''])
    for row in result:
        extra = '；该记录当前状态为 expired，请同时确认是否仅保留历史资料' if row['status'] == 'expired' else ''
        lines.extend([
            f"### ID{row['id']} {row['title']}",
            f"- 当前候选主分类：{row['policyType']}",
            f"- 原主分类：{row['oldPrimary'] or '未记录'}；候选置信度：{row['confidence'] or '未记录'}",
            f"- 当前候选涉及主题：{'、'.join(row['themes']) or '待确认'}",
            f"- 发文单位：{row['issuingBody'] or '原文未明确'}；政策层级：{row['policyLevel'] or '原文未明确'}",
            f"- 原文链接：{row['originalUrl'] or '未提供'}",
            f"- 辅证链接：{row['evidenceUrl'] or '未提供'}",
            f"- 核验问题：{row['reviewQuestion']}{extra}",
            '',
        ])
    (OUT / 'specialized-policy-review-list.md').write_text('\n'.join(lines), encoding='utf-8')
    print(json.dumps({'count': len(result), 'ids': [row['id'] for row in result], 'output': str(OUT)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
