import base64
import importlib.util
import json
import os
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('deploy', root / '.codex_deploy_opc.py')
deploy = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = deploy
spec.loader.exec_module(deploy)
deploy.load_local_deploy_secrets(os.environ, deploy.LOCAL_DEPLOY_SECRET_FILE)

sql = """
SET NAMES utf8mb4;
SELECT p.id,
       REPLACE(TO_BASE64(COALESCE(p.original_url,'')), '\n', ''),
       REPLACE(TO_BASE64(COALESCE(p.evidence_url,'')), '\n', ''),
       REPLACE(TO_BASE64(COALESCE(s.url,'')), '\n', '')
FROM policies p
JOIN sources s ON s.id=p.source_id
ORDER BY p.id;
"""

client = deploy.connect()
try:
    _, output, _ = deploy.database_command(client, sql)
finally:
    client.close()

def decode(value):
    return base64.b64decode(value).decode('utf-8') if value else ''

rows = []
for line in output.splitlines():
    parts = line.split('\t')
    if len(parts) != 4 or not parts[0].isdigit():
        continue
    rows.append({
        'id': int(parts[0]),
        'originalUrl': decode(parts[1]) or None,
        'evidenceUrl': decode(parts[2]) or None,
        'sourceUrl': decode(parts[3]) or None,
    })

target = root / 'outputs/policy-original-urls-production-20260826.json'
target.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({
    'records': len(rows),
    'original_url_present': sum(bool(r['originalUrl']) for r in rows),
    'source_url_present': sum(bool(r['sourceUrl']) for r in rows),
    'original_source_mismatch': sum(r['originalUrl'] != r['sourceUrl'] for r in rows),
    'output': str(target),
}, ensure_ascii=False))
