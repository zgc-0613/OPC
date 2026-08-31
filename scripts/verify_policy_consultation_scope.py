import importlib.util
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
IDS = (8, 26, 30, 32, 33, 51, 61, 63, 76, 79, 104, 110, 112, 116, 117)

if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

spec = importlib.util.spec_from_file_location('opc_deploy', ROOT / '.codex_deploy_opc.py')
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)
module.load_local_deploy_secrets(os.environ, module.LOCAL_DEPLOY_SECRET_FILE)
client = module.connect()
try:
    id_list = ','.join(map(str, IDS))
    sql = f"""
SELECT CONCAT('consultation_marked=',COUNT(*))
FROM policies WHERE id IN ({id_list}) AND status='consultation';
SELECT CONCAT('published_marked=',COUNT(*))
FROM policies WHERE id IN ({id_list}) AND status='published';
SELECT status,COUNT(*) FROM policies GROUP BY status ORDER BY status;
SELECT id,status FROM policies WHERE id IN ({id_list}) ORDER BY id;
"""
    _, output, _ = module.database_command(client, sql)
    print(output)
finally:
    client.close()
