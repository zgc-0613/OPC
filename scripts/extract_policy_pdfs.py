import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.release-tools'))
from pypdf import PdfReader

folder = ROOT / 'outputs/policy-full-texts-20260826'
manifest_path = ROOT / 'outputs/policy-full-text-manifest-20260826.json'
manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
by_id = {row['id']: row for row in manifest}
results = []

for pdf_path in sorted(folder.glob('*.pdf'), key=lambda p: int(p.stem)):
    rid = int(pdf_path.stem)
    reader = PdfReader(str(pdf_path))
    pages = [(page.extract_text() or '').strip() for page in reader.pages]
    text = '\n\n'.join(page for page in pages if page)
    text_path = folder / f'{rid}.txt'
    if len(text) >= 200:
        text_path.write_text(text, encoding='utf-8')
        row = by_id[rid]
        row['textFile'] = str(text_path)
        row['textLength'] = len(text)
        row['selectedUrl'] = row['originalUrl']
        row['selectedKind'] = 'original_pdf'
    results.append({'id': rid, 'pages': len(reader.pages), 'textLength': len(text)})

manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(results, ensure_ascii=False))
