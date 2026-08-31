from bs4 import BeautifulSoup
import json
import re
import sys

sys.stdout.reconfigure(encoding='utf-8')

pages = json.load(open('outputs/case4-pages.json', encoding='utf8'))
for index, page in enumerate(pages, 1):
    soup = BeautifulSoup(page.get('text', ''), 'html.parser')
    for node in soup(['script', 'style', 'noscript']):
        node.decompose()
    text = re.sub(r'\s+', ' ', soup.get_text(' ', strip=True))
    open(f'outputs/case{index}-text.txt', 'w', encoding='utf8').write(text)
    print(index, len(text), text[:1800])
