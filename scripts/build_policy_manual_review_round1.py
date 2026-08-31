import csv
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
records = json.loads((ROOT / 'outputs/policies-production-20260826.json').read_text(encoding='utf-8'))
manifest = {r['id']: r for r in json.loads((ROOT / 'outputs/policy-full-text-manifest-20260826.json').read_text(encoding='utf-8'))}

labels = {
    'comprehensive': '综合发展政策',
    'computing_support': '算力技术',
    'funding_subsidy': '财政激励',
    'scenario_demand': '场景开放',
    'talent_service': '人才培育',
    'investment': '金融资本',
    'governance_market': '制度治理',
}

# These assignments are based on the document's primary function after reading
# the available source text. All unlisted records are broad multi-tool plans or
# measures and therefore use comprehensive as their unique primary category.
primary_overrides = {
    9: 'computing_support',
    11: 'governance_market',
    19: 'governance_market',
    24: 'governance_market',
    44: 'computing_support',
    56: 'funding_subsidy',
    58: 'governance_market',
    66: 'talent_service',
    67: 'scenario_demand',
    69: 'governance_market',
    72: 'governance_market',
    73: 'governance_market',
    75: 'governance_market',
    82: 'governance_market',
    92: 'governance_market',
    96: 'governance_market',
    97: 'governance_market',
    98: 'governance_market',
}

topic_terms = {
    'computing_support': ['算力', '智算', '计算资源', '模型调用', 'token', '数据中心'],
    'funding_subsidy': ['补贴', '奖励', '资助', '财政', '贴息', '算力券', '数据券', '资金支持'],
    'scenario_demand': ['场景开放', '应用场景', '场景需求', '揭榜', '示范应用', '需求清单'],
    'talent_service': ['人才', '高校', '培训', '导师', '就业', '人才公寓'],
    'investment': ['投融资', '融资', '贷款', '基金', '创投', '质押', '金融服务'],
    'governance_market': ['登记', '合规', '知识产权', '标准', '规范', '认定', '评价', '监管', '公平竞争'],
}

def get(row, camel, snake):
    return str(row.get(camel) or row.get(snake) or '')

def clean_structured_text(row):
    text = '\n'.join([
        get(row, 'title', 'title'), get(row, 'summary', 'summary'),
        get(row, 'keyPoints', 'key_points'), get(row, 'supportMeasures', 'support_measures'),
    ])
    text = re.sub(r'政策要点：[^\n]*', ' ', text)
    text = re.sub(r'【[^】]+】', ' ', text)
    return text

def source_text(row):
    item = manifest[row['id']]
    path = Path(item['textFile']) if item.get('textFile') else ROOT / 'outputs/policy-full-texts-20260826' / f"{row['id']}.txt"
    if path.exists():
        return path.read_text(encoding='utf-8', errors='replace'), item.get('selectedKind') or 'original_powershell'
    return clean_structured_text(row), 'structured_record_only'

def sentences(text):
    compact = re.sub(r'[ \t]+', ' ', text)
    return [s.strip() for s in re.split(r'(?<=[。！？；])|\n+', compact) if 18 <= len(s.strip()) <= 500]

def excerpts(text, primary, involved):
    parts = sentences(text)
    chosen = []
    for topic in ([primary] if primary != 'comprehensive' else []) + involved:
        terms = topic_terms.get(topic, [])
        match = next((s for s in parts if any(term.lower() in s.lower() for term in terms)), None)
        if match and match not in chosen:
            chosen.append(match)
        if len(chosen) >= 6:
            break
    if not chosen:
        chosen = parts[:3]
    return chosen[:6]

result = []
for row in records:
    text, selected_kind = source_text(row)
    primary = primary_overrides.get(row['id'], 'comprehensive')
    measure_text = clean_structured_text(row)
    involved_basis = measure_text if len(measure_text.strip()) >= 100 else text
    involved = [topic for topic, terms in topic_terms.items() if any(term.lower() in involved_basis.lower() for term in terms)]
    if primary != 'comprehensive' and primary not in involved:
        involved.insert(0, primary)
    tags = ([primary] if primary == 'comprehensive' else []) + involved
    item = manifest[row['id']]
    local_text = ROOT / 'outputs/policy-full-texts-20260826' / f"{row['id']}.txt"
    read_status = 'source_text_read' if item.get('textFile') or local_text.exists() else 'source_access_limited_structured_record_review'
    result.append({
        'id': row['id'],
        'title': row['title'],
        'status': row['status'],
        'originalUrl': get(row, 'originalUrl', 'original_url'),
        'primaryType': primary,
        'primaryTypeLabel': labels[primary],
        'replacementTags': tags,
        'replacementTagLabels': [labels[t] for t in tags],
        'sourceReadStatus': read_status,
        'selectedSourceKind': selected_kind,
        'sourceTextLength': item.get('textLength', 0) or (local_text.stat().st_size if local_text.exists() else 0),
        'classificationEvidence': excerpts(text, primary, involved),
        'needsAdminReview': True,
    })

json_path = ROOT / 'outputs/policy-manual-review-round2-encoding-fixed-20260826.json'
json_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
csv_path = ROOT / 'outputs/policy-manual-review-round2-encoding-fixed-20260826.csv'
fields = ['id','title','status','primaryTypeLabel','replacementTagLabels','sourceReadStatus','selectedSourceKind','sourceTextLength','classificationEvidence','originalUrl','needsAdminReview']
with csv_path.open('w', encoding='utf-8-sig', newline='') as handle:
    writer = csv.DictWriter(handle, fieldnames=fields)
    writer.writeheader()
    for row in result:
        flat = dict(row)
        flat['replacementTagLabels'] = '、'.join(row['replacementTagLabels'])
        flat['classificationEvidence'] = '｜'.join(row['classificationEvidence'])
        writer.writerow({key: flat.get(key) for key in fields})

print(json.dumps({
    'records': len(result),
    'primaryCounts': Counter(row['primaryTypeLabel'] for row in result),
    'sourceTextRead': sum(row['sourceReadStatus'] == 'source_text_read' for row in result),
    'sourceAccessLimited': sum(row['sourceReadStatus'] != 'source_text_read' for row in result),
}, ensure_ascii=False, default=dict))
