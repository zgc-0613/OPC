import concurrent.futures
import html
import json
import re
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
snapshot = ROOT / 'outputs/policies-production-20260826.json'
records = json.loads(snapshot.read_text(encoding='utf-8'))
url_snapshot = ROOT / 'outputs/policy-original-urls-production-20260826.json'
if url_snapshot.exists():
    urls_by_id = {r['id']: r for r in json.loads(url_snapshot.read_text(encoding='utf-8'))}
    for record in records:
        db_urls = urls_by_id.get(record.get('id'), {})
        record['originalUrl'] = db_urls.get('originalUrl') or record.get('originalUrl') or record.get('original_url')
        record['evidenceUrl'] = db_urls.get('evidenceUrl') or record.get('evidenceUrl') or record.get('evidence_url')
rules = {
    'computing_support': ['算力', '智算', '计算资源', '数据中心', '模型券', '模型服务'],
    'funding_subsidy': ['补贴', '奖励', '资助', '专项资金', '资金支持', '扶持资金', '券'],
    'investment': ['投资', '融资', '贷款', '创投', '风投', '基金', '金融支持'],
    'scenario_demand': ['应用场景', '场景需求', '揭榜', '试点示范', '场景开放', '示范应用'],
    'talent_service': ['人才', '培训', '高校', '创业服务', '服务机构', '导师', '就业'],
    'other': ['知识产权', '标准', '登记', '合规', '认证', '指引', '规范'],
}
terms = [w for words in rules.values() for w in words]

def fetch(row):
    raw_url = row.get('originalUrl') or row.get('original_url') or row.get('evidenceUrl') or row.get('evidence_url')
    urls = [u.strip() for u in re.split(r'[；;\n]+', str(raw_url or '')) if u.strip()]
    base = {'id': row.get('id'), 'url': raw_url, 'sourceAttempts': [], 'http_status': None, 'sentences': [], 'error': ''}
    if not urls:
        base['error'] = 'no_url'
        return base
    for url in urls:
        attempt = {'url': url, 'http_status': None, 'error': ''}
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) FindOPC-audit/1.0', 'Accept-Language': 'zh-CN,zh;q=0.9', 'Accept': 'text/html,application/xhtml+xml'})
            with urllib.request.urlopen(req, timeout=12) as resp:
                body = resp.read(700_000).decode('utf-8', errors='replace')
                attempt['http_status'] = resp.status
            if base['http_status'] is None or attempt['http_status'] == 200:
                base['http_status'] = attempt['http_status']
            body = re.sub(r'<(script|style|noscript|svg)[^>]*>.*?</\1>', ' ', body, flags=re.I | re.S)
            text = html.unescape(re.sub(r'<[^>]+>', ' ', body))
            text = re.sub(r'\s+', ' ', text).strip()
            parts = [p.strip() for p in re.split(r'(?<=[。！？；])', text) if len(p.strip()) >= 12]
            for sentence in [p[:500] for p in parts if any(t in p for t in terms)]:
                if sentence not in base['sentences']:
                    base['sentences'].append(sentence)
        except urllib.error.HTTPError as exc:
            attempt['http_status'] = exc.code
            attempt['error'] = str(exc)
        except Exception as exc:
            attempt['error'] = type(exc).__name__ + ': ' + str(exc)
        base['sourceAttempts'].append(attempt)
    base['sentences'] = base['sentences'][:30]
    if not base['sentences'] and base['sourceAttempts']:
        base['error'] = '; '.join(a.get('error', '') for a in base['sourceAttempts'] if a.get('error'))
    return base

with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    evidence = list(pool.map(fetch, records))
out = ROOT / 'outputs/policy-source-evidence-sentences-20260826.json'
out.write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'records': len(evidence), 'http200': sum(x['http_status'] == 200 for x in evidence), 'with_sentences': sum(bool(x['sentences']) for x in evidence)}, ensure_ascii=False))
