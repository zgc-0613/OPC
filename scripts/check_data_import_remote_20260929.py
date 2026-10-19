from __future__ import annotations

import importlib.util
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
spec = importlib.util.spec_from_file_location("opc_deploy", ROOT / ".codex_deploy_opc.py")
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)
module.load_local_deploy_secrets(os.environ, module.LOCAL_DEPLOY_SECRET_FILE)
client = module.connect()
try:
    sql = """
SELECT CONCAT('case_total=',COUNT(*)) FROM case_items;
SELECT CONCAT('case_batch_existing=',COUNT(*)) FROM case_items WHERE reviewer='excel-import-20260929';
SELECT CONCAT('source_batch_existing=',COUNT(*)) FROM sources WHERE notes LIKE '%import_batch=data-import-20260929%';
SELECT CONCAT('university_table=',COUNT(*)) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='university_opc_records';
SELECT CONCAT('case_required_columns=',COUNT(*)) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='case_items' AND column_name IN ('article_title','subcategory','ai_evidence_status','evidence_revision');
SELECT CONCAT('region_country=',COUNT(*)) FROM regions WHERE level='country' AND name='中国';
SELECT CONCAT('region_provinces=',COUNT(*)) FROM regions WHERE level='province';
"""
    print(module.database_command(client, sql)[1])
finally:
    client.close()
