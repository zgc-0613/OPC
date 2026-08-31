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

text_fields = [
    'title', 'region_name', 'issuing_body', 'document_no', 'valid_period',
    'source_title', 'policy_level', 'policy_type', 'applicability_mode',
    'summary', 'key_points', 'support_measures', 'tags', 'original_url',
    'evidence_url', 'status', 'ai_evidence_status',
]
field_names = {
    'title': 'title', 'region_name': 'regionName', 'issuing_body': 'issuingBody',
    'document_no': 'documentNo', 'valid_period': 'validPeriod',
    'source_title': 'sourceTitle', 'policy_level': 'policyLevel',
    'policy_type': 'policyType', 'applicability_mode': 'applicabilityMode',
    'summary': 'summary', 'key_points': 'keyPoints',
    'support_measures': 'supportMeasures', 'tags': 'tags',
    'original_url': 'originalUrl', 'evidence_url': 'evidenceUrl',
    'status': 'status', 'ai_evidence_status': 'aiEvidenceStatus',
}
encoded = [
    f"REPLACE(TO_BASE64(COALESCE({field},'')), '\\n', '')"
    for field in text_fields
]
sql = f"""
SET NAMES utf8mb4;
SELECT id, region_id, source_id,
       DATE_FORMAT(publish_date,'%Y-%m-%d'),
       DATE_FORMAT(effective_date,'%Y-%m-%d'),
       DATE_FORMAT(accessed_at,'%Y-%m-%d'),
       evidence_revision,
       {', '.join(encoded)}
FROM (
    SELECT p.*, r.name AS region_name, s.title AS source_title
    FROM policies p
    JOIN regions r ON r.id=p.region_id
    JOIN sources s ON s.id=p.source_id
) snapshot
ORDER BY id;
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
    if len(parts) != 7 + len(text_fields) or not parts[0].isdigit():
        continue
    plain = parts[:7]
    values = dict(zip(text_fields, map(decode, parts[7:])))
    rows.append({
        'id': int(plain[0]),
        'regionId': int(plain[1]),
        'sourceId': int(plain[2]),
        'publishDate': None if plain[3] == 'NULL' else plain[3],
        'effectiveDate': None if plain[4] == 'NULL' else plain[4],
        'accessedAt': None if plain[5] == 'NULL' else plain[5],
        'evidenceRevision': int(plain[6]),
        **{field_names[k]: (v or None) for k, v in values.items()},
    })

target = root / 'outputs/policies-production-20260826.json'
target.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({
    'records': len(rows),
    'statuses': {s: sum(r['status'] == s for r in rows) for s in sorted({r['status'] for r in rows})},
    'evidence_statuses': {s: sum(r['aiEvidenceStatus'] == s for r in rows) for s in sorted({r['aiEvidenceStatus'] for r in rows})},
    'output': str(target),
}, ensure_ascii=False))
