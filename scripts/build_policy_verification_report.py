import csv
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
records = json.loads((ROOT / 'outputs/policies-production-20260826.json').read_text(encoding='utf-8'))
candidates = {r['id']: r for r in json.loads((ROOT / 'outputs/policy-classification-candidates-20260826.json').read_text(encoding='utf-8'))}
sources = {r['id']: r for r in json.loads((ROOT / 'outputs/policy-source-batch-1-88.json').read_text(encoding='utf-8'))}
evidence = {r['id']: r for r in json.loads((ROOT / 'outputs/policy-source-evidence-sentences-20260826.json').read_text(encoding='utf-8'))}
labels = {
    'comprehensive': '综合发展政策',
    'computing_support': '算力技术',
    'funding_subsidy': '财政激励',
    'scenario_demand': '场景开放',
    'talent_service': '人才培育',
    'investment': '金融资本',
    'governance_market': '制度治理',
}

def concise_source_sentences(items):
    selected = []
    for item in items or []:
        text = ' '.join(str(item).split())
        if len(text) < 20 or '首页' in text or '导航' in text or '更多' in text:
            continue
        if text not in selected:
            selected.append(text[:350])
        if len(selected) >= 6:
            break
    return selected

def evidence_level(source):
    urls = [a.get('url', '') for a in source.get('sourceAttempts', [])]
    official_domains = ('.gov.cn', '.gov', 'gov.cn')
    if any(a.get('http_status') == 200 and any(d in str(a.get('url', '')).lower() for d in official_domains) for a in source.get('sourceAttempts', [])):
        return 'official_or_government_domain'
    if any(a.get('http_status') == 200 for a in source.get('sourceAttempts', [])):
        return 'reprint_or_nonofficial_page'
    if urls:
        return 'link_present_but_unverified'
    return 'missing_source_link'

out = []
for record in records:
    rid = record.get('id')
    candidate = candidates[rid]
    source = sources.get(rid, {})
    source_evidence = evidence.get(rid, {})
    primary = candidate['candidatePrimaryType']
    secondary = list(candidate.get('candidateSecondaryTypes') or [])
    # This is deliberately labelled as a platform summary, not an official quote.
    basis = ' '.join(str(record.get(k) or record.get({'keyPoints':'key_points','supportMeasures':'support_measures'}.get(k, '')) or '').strip() for k in ('summary', 'keyPoints', 'supportMeasures')).strip()
    tags = [labels[t] for t in candidate.get('replacementTags', []) if t in labels]
    involved = [primary] if primary in labels else []
    for topic in secondary:
        if topic in labels and topic not in involved:
            involved.append(topic)
    record_url = record.get('originalUrl') or record.get('original_url') or record.get('evidenceUrl') or record.get('evidence_url')
    state = 'official_page_http_200' if source_evidence.get('http_status') == 200 else ('no_url' if not record_url else 'source_not_fetchable_or_non200')
    out.append({
        'id': rid,
        'title': record.get('title'),
        'regionName': record.get('regionName') or record.get('region_name'),
        'primaryType': primary,
        'primaryTypeLabel': labels.get(primary, '待人工复核'),
        'involvedTypes': involved,
        'involvedTypeLabels': [labels[t] for t in involved],
        'classificationRationale': candidate.get('classificationReason'),
        'detailKeySentence': basis or '原有结构化字段未提供可直接引用的分类依据，需人工阅读来源正文。',
        'detailKeySentenceType': 'platform_summary' if basis else 'missing',
        'sourceEvidenceSentences': source_evidence.get('sentences', []),
        'detailOfficialKeySentences': concise_source_sentences(source_evidence.get('sentences', [])),
        'sourceEvidenceSentenceType': 'official_source_excerpt' if source_evidence.get('sentences') else 'not_found',
        'evidenceLevel': evidence_level(source_evidence),
        'adminReviewAction': 'approve_after_source_check' if source_evidence.get('sentences') else ('find_or_attach_source' if not source_evidence.get('url') or source_evidence.get('error') == 'no_url' else 'open_link_and_verify'),
        'sourceAttempts': source_evidence.get('sourceAttempts', []),
        'sourceUrl': record_url,
        'sourceState': state,
        'sourcePageTitle': source.get('page_title') or None,
        'tags': tags,
        'needsManualReview': True,
    })

target = ROOT / 'outputs/policy-verification-detail-candidates-20260826.json'
target.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding='utf-8')
csv_path = ROOT / 'outputs/policy-verification-detail-candidates-20260826.csv'
with csv_path.open('w', encoding='utf-8-sig', newline='') as f:
    fields = ['id','title','regionName','primaryTypeLabel','involvedTypeLabels','detailKeySentence','detailKeySentenceType','detailOfficialKeySentences','sourceEvidenceSentenceType','evidenceLevel','adminReviewAction','sourceUrl','sourceState','sourcePageTitle','tags','needsManualReview']
    writer = csv.DictWriter(f, fieldnames=fields)
    writer.writeheader()
    for row in out:
        row = dict(row)
        row['involvedTypeLabels'] = '、'.join(row['involvedTypeLabels'])
        row['tags'] = '、'.join(row['tags'])
        row['detailOfficialKeySentences'] = '｜'.join(row['detailOfficialKeySentences'])
        writer.writerow({k: row.get(k) for k in fields})
print(f'wrote {target} and {csv_path}; records={len(out)}')
