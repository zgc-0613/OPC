import json
import requests

urls = [
    'https://hnxjxq.rednet.cn/content/646042/68/16201808.html',
    'https://jsnews.jschina.com.cn/jsyw/202608/t20260823_s6a8a4b10e4b027d1d0a3a13d.shtml',
    'https://cn.chinadaily.com.cn/a/202608/06/WS6a744ec7a310d709c2fc1f0d.html',
    'https://m.sohu.com/a/1058490974_121106832/',
]
out = []
for url in urls:
    try:
        response = requests.get(url, headers={'User-Agent': 'Mozilla/5.0'}, timeout=20)
        out.append({'url': url, 'status': response.status_code, 'final': response.url, 'bytes': len(response.content), 'text': response.text})
    except Exception as exc:
        out.append({'url': url, 'error': f'{type(exc).__name__}: {exc}'})
open('outputs/case4-pages.json', 'w', encoding='utf-8').write(json.dumps(out, ensure_ascii=False, indent=2))
print(json.dumps([{k: item.get(k) for k in ('url', 'status', 'final', 'bytes', 'error')} for item in out], ensure_ascii=False))
