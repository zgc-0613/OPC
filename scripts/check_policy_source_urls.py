import importlib.util
import os
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('deploy', root / '.codex_deploy_opc.py')
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)
module.load_local_deploy_secrets(os.environ, module.LOCAL_DEPLOY_SECRET_FILE)
client = module.connect()
sql = """
SELECT p.id, p.title, p.source_id,
       COALESCE(NULLIF(p.original_url,''),'(empty)') AS policy_original_url,
       COALESCE(NULLIF(p.evidence_url,''),'(empty)') AS policy_evidence_url,
       COALESCE(NULLIF(s.url,''),'(empty)') AS source_url,
       s.title AS source_title, s.status, s.ai_evidence_status
FROM policies p JOIN sources s ON s.id=p.source_id
ORDER BY p.id;
"""
try:
    _, output, _ = module.database_command(client, sql)
    print(output)
finally:
    client.close()
