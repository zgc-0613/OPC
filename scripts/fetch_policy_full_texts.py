import concurrent.futures
import html
import json
import re
import subprocess
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
records = json.loads((ROOT / 'outputs/policies-production-20260826.json').read_text(encoding='utf-8'))
OUT = ROOT / 'outputs/policy-full-texts-20260826'
OUT.mkdir(parents=True, exist_ok=True)

def field(row, camel, snake):
    return row.get(camel) or row.get(snake)

def clean_html(body):
    body = re.sub(r'<(script|style|noscript|svg|nav|footer)[^>]*>.*?</\1>', ' ', body, flags=re.I | re.S)
    body = re.sub(r'<br\s*/?>', '\n', body, flags=re.I)
    body = re.sub(r'</(p|div|li|tr|h[1-6])>', '\n', body, flags=re.I)
    text = html.unescape(re.sub(r'<[^>]+>', ' ', body))
    lines = [re.sub(r'\s+', ' ', line).strip() for line in text.splitlines()]
    return '\n'.join(line for line in lines if line)

def decode_html(raw, content_type):
    candidates = []
    header_match = re.search(r'charset=([\w-]+)', content_type or '', re.I)
    head = raw[:5000].decode('latin1', errors='ignore')
    meta_match = re.search(r'charset\s*=\s*["\']?([\w-]+)', head, re.I)
    for encoding in [
        header_match.group(1) if header_match else None,
        meta_match.group(1) if meta_match else None,
        'utf-8', 'gb18030', 'gbk',
    ]:
        if encoding and encoding.lower() not in [item[0] for item in candidates]:
            try:
                candidates.append((encoding.lower(), raw.decode(encoding, errors='replace')))
            except (LookupError, UnicodeDecodeError):
                pass

    def score(item):
        _, text = item
        cjk = len(re.findall(r'[\u4e00-\u9fff]', text))
        broken = text.count('\ufffd') + text.count('锟') + text.count('烫')
        return cjk - broken * 50

    return max(candidates, key=score)[1] if candidates else raw.decode('utf-8', errors='replace')

def fetch(row):
    rid = row['id']
    original = field(row, 'originalUrl', 'original_url')
    evidence = field(row, 'evidenceUrl', 'evidence_url')
    urls = [('original', original)]
    if evidence and evidence != original:
        for url in re.split(r'[；;\n]+', evidence):
            if url.strip():
                urls.append(('evidence', url.strip()))
    attempts = []
    selected = None
    for kind, url in urls:
        attempt = {'kind': kind, 'url': url, 'status': None, 'contentType': None, 'error': None, 'textLength': 0}
        try:
            request = urllib.request.Request(url, headers={
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) FindOPC-policy-review/1.0',
                'Accept-Language': 'zh-CN,zh;q=0.9',
            })
            with urllib.request.urlopen(request, timeout=18) as response:
                content_type = response.headers.get('Content-Type', '')
                raw = response.read(2_000_000)
                attempt['status'] = response.status
                attempt['contentType'] = content_type
            if 'pdf' in content_type.lower() or raw[:4] == b'%PDF':
                pdf_path = OUT / f'{rid}.pdf'
                pdf_path.write_bytes(raw)
                attempt['error'] = 'pdf_saved_requires_text_extraction'
            else:
                body = decode_html(raw, content_type)
                text = clean_html(body)
                attempt['textLength'] = len(text)
                if selected is None and len(text) >= 200:
                    selected = {'kind': kind, 'url': url, 'text': text}
        except urllib.error.HTTPError as exc:
            attempt['status'] = exc.code
            attempt['error'] = str(exc)
        except Exception as exc:
            attempt['error'] = type(exc).__name__ + ': ' + str(exc)
        if not selected and kind == 'original':
            try:
                curl = subprocess.run(
                    ['curl.exe', '-L', '--max-time', '30', '-A', 'Mozilla/5.0', '-sS', url],
                    capture_output=True, check=False, timeout=35,
                )
                if curl.returncode == 0 and len(curl.stdout) >= 200:
                    if curl.stdout[:4] == b'%PDF':
                        (OUT / f'{rid}.pdf').write_bytes(curl.stdout)
                    else:
                        body = decode_html(curl.stdout, '')
                        text = clean_html(body)
                        if len(text) >= 200:
                            selected = {'kind': kind + '_curl', 'url': url, 'text': text}
                            attempt['textLength'] = len(text)
                            attempt['error'] = None
            except Exception:
                pass
        attempts.append(attempt)
    if selected:
        (OUT / f'{rid}.txt').write_text(selected['text'], encoding='utf-8')
    return {
        'id': rid,
        'title': row.get('title'),
        'status': row.get('status'),
        'originalUrl': original,
        'selectedUrl': selected['url'] if selected else None,
        'selectedKind': selected['kind'] if selected else None,
        'textFile': str(OUT / f'{rid}.txt') if selected else None,
        'textLength': len(selected['text']) if selected else 0,
        'attempts': attempts,
    }

with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
    manifest = list(pool.map(fetch, records))

target = ROOT / 'outputs/policy-full-text-manifest-20260826.json'
target.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({
    'records': len(manifest),
    'text_ready': sum(bool(row['textFile']) for row in manifest),
    'original_selected': sum(row['selectedKind'] == 'original' for row in manifest),
    'evidence_fallback': sum(row['selectedKind'] == 'evidence' for row in manifest),
    'pdf_saved': sum(any(a.get('error') == 'pdf_saved_requires_text_extraction' for a in row['attempts']) for row in manifest),
    'unavailable': sum(not row['textFile'] for row in manifest),
}, ensure_ascii=False))
