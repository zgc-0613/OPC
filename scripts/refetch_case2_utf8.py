import re
import requests
from bs4 import BeautifulSoup

url = 'https://jsnews.jschina.com.cn/jsyw/202608/t20260823_s6a8a4b10e4b027d1d0a3a13d.shtml'
r = requests.get(url, headers={'User-Agent': 'Mozilla/5.0'}, timeout=20)
for enc in ('utf-8', 'gb18030'):
    soup = BeautifulSoup(r.content, 'html.parser', from_encoding=enc)
    for node in soup(['script', 'style', 'noscript']):
        node.decompose()
    text = re.sub(r'\s+', ' ', soup.get_text(' ', strip=True))
    open(f'outputs/case2-{enc}.txt', 'w', encoding='utf8').write(text)
    print(enc, text[:2000])
