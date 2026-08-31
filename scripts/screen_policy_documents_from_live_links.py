import concurrent.futures
import html
import importlib.util
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'outputs/policy-live-link-screening-20260829'
TEXT_OUT = OUT / 'texts'
OUT.mkdir(parents=True, exist_ok=True)
TEXT_OUT.mkdir(parents=True, exist_ok=True)

if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


def load_deploy():
    spec = importlib.util.spec_from_file_location('opc_deploy', ROOT / '.codex_deploy_opc.py')
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def query_records():
    module = load_deploy()
    module.load_local_deploy_secrets(os.environ, module.LOCAL_DEPLOY_SECRET_FILE)
    client = module.connect()
    sql = """
SELECT p.id,p.title,p.status,p.original_url,p.evidence_url,
       s.url AS source_url,s.title AS source_title,s.publisher AS source_publisher
FROM policies p LEFT JOIN sources s ON s.id=p.source_id
ORDER BY p.id;
"""
    try:
        _, output, _ = module.database_command(client, sql)
    finally:
        client.close()
    rows = []
    for line in output.splitlines():
        parts = line.split('\t')
        if len(parts) < 8 or not parts[0].isdigit():
            continue
        rows.append({
            'id': int(parts[0]), 'dbTitle': parts[1], 'dbStatus': parts[2],
            'originalUrl': parts[3] if parts[3] != 'NULL' else '',
            'evidenceUrl': parts[4] if parts[4] != 'NULL' else '',
            'sourceUrl': parts[5] if parts[5] != 'NULL' else '',
            'sourceTitle': parts[6] if parts[6] != 'NULL' else '',
            'sourcePublisher': parts[7] if parts[7] != 'NULL' else '',
        })
    return rows


def clean_html(raw):
    raw = re.sub(r'<(script|style|noscript|svg|nav|footer)[^>]*>.*?</\1>', ' ', raw, flags=re.I | re.S)
    raw = re.sub(r'<br\s*/?>', '\n', raw, flags=re.I)
    raw = re.sub(r'</(p|div|li|tr|h[1-6])>', '\n', raw, flags=re.I)
    text = html.unescape(re.sub(r'<[^>]+>', ' ', raw))
    return '\n'.join(re.sub(r'\s+', ' ', line).strip() for line in text.splitlines() if line.strip())


def decode(raw, content_type=''):
    head = raw[:5000].decode('latin1', errors='ignore')
    encodings = []
    match = re.search(r'charset=([\w-]+)', content_type or '', re.I)
    meta = re.search(r'charset\s*=\s*["\']?([\w-]+)', head, re.I)
    for enc in [match.group(1) if match else None, meta.group(1) if meta else None, 'utf-8', 'gb18030', 'gbk']:
        if enc and enc.lower() not in encodings:
            encodings.append(enc.lower())
    candidates = [(enc, raw.decode(enc, errors='replace')) for enc in encodings]
    return max(candidates, key=lambda item: len(re.findall(r'[\u4e00-\u9fff]', item[1])) - 50 * item[1].count('\ufffd'))[1]


def fetch_one(url):
    if not url:
        return {'url': '', 'status': None, 'contentType': '', 'pageTitle': '', 'text': '', 'error': 'empty_url'}
    try:
        request = urllib.request.Request(url, headers={
            'User-Agent': 'Mozilla/5.0 FindOPC-policy-document-screen/1.0',
            'Accept-Language': 'zh-CN,zh;q=0.9',
        })
        with urllib.request.urlopen(request, timeout=25) as response:
            raw = response.read(3_000_000)
            content_type = response.headers.get('Content-Type', '')
            status = response.status
        if 'pdf' in content_type.lower() or raw[:4] == b'%PDF':
            return {'url': url, 'status': status, 'contentType': content_type, 'pageTitle': '', 'text': '', 'error': 'pdf_requires_text_review'}
        body = decode(raw, content_type)
        page_title = ''
        title_match = re.search(r'<title[^>]*>(.*?)</title>', body, re.I | re.S)
        if title_match:
            page_title = re.sub(r'\s+', ' ', html.unescape(re.sub(r'<[^>]+>', '', title_match.group(1)))).strip()
        text = clean_html(body)
        return {'url': url, 'status': status, 'contentType': content_type, 'pageTitle': page_title, 'text': text, 'error': ''}
    except urllib.error.HTTPError as exc:
        return {'url': url, 'status': exc.code, 'contentType': '', 'pageTitle': '', 'text': '', 'error': str(exc)}
    except Exception as exc:
        return {'url': url, 'status': None, 'contentType': '', 'pageTitle': '', 'text': '', 'error': type(exc).__name__ + ': ' + str(exc)}


def classify(record, attempts):
    selected = next((item for item in attempts if len(item.get('text', '')) >= 200), None)
    if not selected:
        pdf = next((item for item in attempts if item.get('error') == 'pdf_requires_text_review'), None)
        return {
            'sourceNature': '待人工核验',
            'statisticalScope': '保留待人工核验；暂不纳入统计',
            'basis': '原始链接未取得可读正文' if not pdf else '原始链接为 PDF，尚未完成正文提取',
            'selected': pdf or (attempts[0] if attempts else {}),
        }
    text = selected['text']
    page_title = selected.get('pageTitle', '')
    probe = f'{page_title}\n{text[:12000]}'
    title_probe = page_title
    lines = text.splitlines()
    title_positions = [index for index, line in enumerate(lines) if page_title and page_title in line]
    core_start = title_positions[-1] if title_positions else 0
    opening_probe = '\n'.join(lines[core_start:core_start + 80])
    formal_markers = re.search(r'关于印发|印发《|若干措施|行动方案|实施方案|实施细则|管理办法|工作指引|服务指引|通知|政府公报|若干意见|指导意见|专项措施', title_probe)
    consultation = bool(re.search(r'公开征求|公开征集.*意见|意见公告|意见的公告|征求社会各界意见|拟出台|即将出台|征求意见稿', title_probe + '\n' + opening_probe[:12000]))
    standard = bool(re.search(r'团体标准|行业标准|术语标准|标准规范', probe)) and not bool(re.search(r'补贴|奖励|资助|贷款|基金|场景开放', probe))
    interpretation = bool(re.search(r'政策解读|解读：|媒体聚焦|新闻发布会|报道|转载|答记者问', title_probe))
    platform = bool(re.search(r'服务平台|平台上线|平台启用|服务专区|一站式服务平台|创业大礼包|工作动态|动态资讯|推出新政', page_title))
    netloc = urllib.parse.urlparse(selected['url']).netloc.lower()
    official = bool(re.search(r'\.gov\.cn|\.gov\b', netloc, re.I))
    authoritative_media = bool(re.search(
        r'xinhuanet\.com|news\.cn|people\.com\.cn|chinadaily\.com\.cn|'
        r'chinanews\.com\.cn|stdaily\.com|cctv\.com|thepaper\.cn',
        netloc, re.I))
    url_path = urllib.parse.urlparse(selected['url']).path.lower()
    file_download = bool(re.search(r'\.(pdf|doc|docx|xls|xlsx)$', url_path)) or selected.get('error') == 'pdf_requires_text_review'
    structural_headings = len(re.findall(r'(^|\n)\s*(?:[一二三四五六七八九十]+、|第[一二三四五六七八九十]+条|（[一二三四五六七八九十]+）)', opening_probe))
    body_policy_markers = bool(re.search(r'重点任务|重点举措|支持对象|组织保障|本方案|本措施|本意见|责任单位', opening_probe))
    if consultation:
        nature, scope, basis = '征求意见稿', '保留资料；不计入统计和分类核验', '页面正文或标题明确出现“征求意见稿”'
    elif standard:
        nature, scope, basis = '标准规范资料', '保留规范资料；不计入正式政策统计', '页面内容属于标准、规范或术语资料'
    elif interpretation:
        nature, scope, basis = '官方政策解读/转载', '保留补充资料；关联正式文件后不重复计数', '页面标题显示为政策解读、媒体聚焦、新闻发布会或转载信息'
    elif authoritative_media:
        nature, scope, basis = '权威媒体报道/转载', '保留补充资料；不计入正式政策统计', '页面来源属于新华社、人民日报、中国新闻网、科技日报等权威媒体，但未确认存在独立正式政策文本'
    elif (formal_markers or file_download or (structural_headings >= 3 and body_policy_markers)):
        nature, scope, basis = '正式政策文件（待人工确认）', '纳入正式政策候选；待人工确认后进入综合/专项判断', '页面标题或正文包含政策文件结构性表述'
    elif platform:
        nature, scope, basis = '官方平台/服务信息', '保留补充资料；不计入正式政策统计', '页面主要介绍平台、服务上线或工作动态，未确认存在独立政策文本'
    elif official:
        nature, scope, basis = '官方政策信息（非正式文件）', '保留补充资料；不计入正式政策统计', '政府官网页面有政策相关内容，但未识别到独立规范性文件结构'
    else:
        nature, scope, basis = '普通报道或非权威资料', '原则排除；保留待人工核验', '页面来源或内容未显示正式政策文件性质'
    return {'sourceNature': nature, 'statisticalScope': scope, 'basis': basis, 'selected': selected}


def main():
    records = query_records()
    def process(record):
        urls = []
        for kind, url in [('original', record['originalUrl']), ('source', record['sourceUrl']), ('evidence', record['evidenceUrl'])]:
            for item in re.split(r'[；;\n]+', url or ''):
                item = item.strip()
                if item and item not in [x['url'] for x in urls]:
                    urls.append({'kind': kind, 'url': item})
        attempts = []
        for item in urls:
            fetched = fetch_one(item['url'])
            fetched['kind'] = item['kind']
            attempts.append(fetched)
            if len(fetched.get('text', '')) >= 200 and item['kind'] == 'original':
                break
        decision = classify(record, attempts)
        selected = decision['selected'] or {}
        text_file = ''
        if selected.get('text'):
            text_path = TEXT_OUT / f"{record['id']}.txt"
            text_path.write_text(selected['text'], encoding='utf-8')
            text_file = str(text_path)
        return {
            'id': record['id'], 'dbTitle': record['dbTitle'], 'dbStatus': record['dbStatus'],
            'originalUrl': record['originalUrl'], 'evidenceUrl': record['evidenceUrl'],
            'sourceNature': decision['sourceNature'], 'statisticalScope': decision['statisticalScope'],
            'basis': decision['basis'], 'selectedUrl': selected.get('url', ''),
            'selectedKind': selected.get('kind', ''), 'pageTitle': selected.get('pageTitle', ''),
            'textLength': len(selected.get('text', '')), 'textFile': text_file,
            'attempts': [{k: v for k, v in item.items() if k != 'text'} for item in attempts],
            'needsManualReview': decision['sourceNature'] in {'待人工核验', '正式政策文件（待人工确认）'},
        }
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        results = list(pool.map(process, records))
    results.sort(key=lambda row: row['id'])
    (OUT / 'live-link-screening.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
    counts = {}
    for row in results:
        counts[row['sourceNature']] = counts.get(row['sourceNature'], 0) + 1
    lines = [
        '# 基于原始链接正文的政策资料性质初筛', '',
        '本清单依据原始链接/辅证链接实际读取结果生成。数据库快照中的摘要、旧标签和旧分类未作为判断依据。',
        '本轮只判断资料性质和统计资格，不判断综合型/专项型，也不修改数据库。', '',
        '## 筛选口径', '',
        '1. 正式政策文件：原文能确认通知、方案、措施、意见、办法、指引等政策文本，并具有条款、任务、责任单位或具体支持安排；进入正式政策候选，待人工确认后再判断综合型/专项型。',
        '2. 征求意见稿：原文明确标注征求意见稿、公开征求意见或拟出台；保留在数据库，但不进入正式政策统计、分类统计和专项/综合核验。',
        '3. 官方政策信息、官方平台/服务信息、官方政策解读/转载：来源权威且可作为证据，保留在数据库；如未取得独立正式政策文本，不计入正式政策数量，已有对应正式文件时不重复计数。',
        '4. 权威媒体报道/转载：新华社、人民日报、中国新闻网、科技日报等报道可作为补充证据保留，但报道本身不等同于正式政策文件，不计入正式政策数量。',
        '5. 标准规范资料：团体标准、术语标准、服务指引等单列保留；除非原文同时构成正式政策文件，否则不计入正式政策统计。',
        '6. 普通报道或非权威资料：原则上不作为正式政策依据，保留记录并列入人工核验；未核实前不计入正式政策统计。',
        '7. 原始链接或辅证链接无法取得可读正文：不作排除结论，列入人工核验。', '',
        f'共读取 {len(results)} 条；可取得正文 {sum(row["textLength"] >= 200 for row in results)} 条；需人工复核 {sum(row["needsManualReview"] for row in results)} 条。', '',
        '| ID | 页面标题 | 资料性质 | 统计处理 | 读取状态 | 原始链接 | 判断依据 |',
        '|---:|---|---|---|---|---|---|',
    ]
    for row in results:
        state = f"已读取正文（{row['textLength']}字）" if row['textLength'] >= 200 else '未取得可读正文'
        url = f"[原文]({row['originalUrl']})" if row['originalUrl'] else '未提供'
        lines.append(f"| {row['id']} | {row['pageTitle'] or row['dbTitle']} | {row['sourceNature']} | {row['statisticalScope']} | {state} | {url} | {row['basis']} |")
    lines.extend(['', '## 资料性质数量', ''])
    for key, value in sorted(counts.items()):
        lines.append(f'- {key}：{value} 条')
    lines.extend(['', '## 需要人工复核', ''])
    for row in results:
        if row['needsManualReview']:
            lines.append(f"- ID{row['id']} {row['dbTitle']}：{row['basis']}；读取链接：{row['selectedUrl'] or row['originalUrl'] or '无'}")
    (OUT / 'live-link-screening.md').write_text('\n'.join(lines), encoding='utf-8')
    print(json.dumps({'records': len(results), 'counts': counts, 'manualReview': sum(row['needsManualReview'] for row in results), 'output': str(OUT)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
