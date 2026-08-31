import json
import re
from collections import Counter
from pathlib import Path

src = Path('outputs/policies-production-20260826.json')
out = Path('outputs/policy-classification-candidates-20260826.json')
records = json.loads(src.read_text(encoding='utf-8'))
evidence_path = Path('outputs/policy-source-evidence-sentences-20260826.json')
evidence = {r['id']: r for r in json.loads(evidence_path.read_text(encoding='utf-8'))} if evidence_path.exists() else {}

# Closed seven-category taxonomy. Legacy tags are evidence inputs only.
rules = {
    'computing_support': ['算力', '智算', '计算资源', '数据中心', '模型服务', '模型调用', 'token'],
    'funding_subsidy': ['补贴', '奖励', '资助', '专项资金', '资金支持', '扶持资金', '财政贴息', '算力券', '数据券'],
    'scenario_demand': ['应用场景', '场景需求', '揭榜', '试点示范', '场景开放', '示范应用', '需求清单'],
    'talent_service': ['人才', '培训', '高校', '导师', '就业', '人才公寓'],
    'investment': ['投资', '融资', '贷款', '创投', '风投', '基金', '金融服务', '质押'],
    'governance_market': ['知识产权', '标准', '登记', '合规', '认定', '评价', '指引', '规范', '申报', '监管', '公平竞争'],
}

def value(record, camel, snake=None):
    return record.get(camel) or record.get(snake or '') or ''

def full_text(record):
    fields = [str(value(record, camel, snake)) for camel, snake in (
        ('title', None), ('summary', None), ('keyPoints', 'key_points'),
        ('supportMeasures', 'support_measures'),
    )]
    # Remove legacy taxonomy headings so they cannot influence the new coding.
    cleaned = []
    for text in fields:
        text = re.sub(r'政策要点：[^\n]*', ' ', text)
        text = re.sub(r'【[^】]+】', ' ', text)
        cleaned.append(text)
    cleaned.extend(evidence.get(record.get('id'), {}).get('sentences', []))
    return ' '.join(cleaned).lower()

def primary_type(record):
    title = str(record.get('title') or '').lower()
    if any(word in title for word in ('算力券', '数据券', '补贴', '奖励', '资助', '专项资金')):
        return 'funding_subsidy', '标题明确以财政补贴、奖励或券类政策工具为核心'
    if any(word in title for word in ('人才认定', '人才申报', '高校', '人才支持', '人才计划', '培训')):
        return 'talent_service', '标题明确以人才、高校或培训支持为核心'
    if any(word in title for word in ('融资', '贷款', '投资基金', '金融服务', '创业担保贷')):
        return 'investment', '标题明确以投融资或金融服务为核心'
    if any(word in title for word in ('场景开放', '应用场景', '场景清单', '揭榜挂帅', '示范应用')):
        return 'scenario_demand', '标题明确以场景开放或应用推广为核心'
    if any(word in title for word in ('算力', '智算', '计算资源', '数据中心')):
        return 'computing_support', '标题明确以算力或技术基础设施为核心'
    if any(word in title for word in (
        '知识产权', '团体标准', '术语', '评价规范', '服务指南', '工作指引',
        '合规指引', '登记便利', '开办', '认定工作', '认定通知', '申报指南',
    )):
        return 'governance_market', '标题明确以标准、指引、认定、登记或合规治理为核心'
    return 'comprehensive', '文件统筹多类政策工具，或标题未指向单一专项工具'

result = []
for record in records:
    text = full_text(record)
    primary, reason = primary_type(record)
    involved = [key for key, words in rules.items() if any(word in text for word in words)]
    if primary != 'comprehensive' and primary not in involved:
        involved.insert(0, primary)
    secondary = [key for key in involved if key != primary]
    result.append({
        'id': record.get('id'),
        'title': record.get('title'),
        'regionName': value(record, 'regionName', 'region_name'),
        'currentPolicyType': value(record, 'policyType', 'policy_type'),
        'candidatePrimaryType': primary,
        'candidateSecondaryTypes': secondary,
        'involvedTypes': involved,
        'replacementTags': involved,
        'classificationReason': reason,
        'sourceUrl': value(record, 'originalUrl', 'original_url'),
        'needsManualReview': True,
    })

out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
valid = {'comprehensive','computing_support','funding_subsidy','scenario_demand','talent_service','investment','governance_market'}
print('records', len(result))
print('primary', Counter(r['candidatePrimaryType'] for r in result))
print('invalid_primary', sum(r['candidatePrimaryType'] not in valid for r in result))
