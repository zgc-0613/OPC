from __future__ import annotations
import importlib.util, os, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    if str(ROOT) not in sys.path: sys.path.insert(0,str(ROOT))
    spec=importlib.util.spec_from_file_location('opc_deploy',ROOT/'.codex_deploy_opc.py')
    m=importlib.util.module_from_spec(spec); sys.modules[spec.name]=m; spec.loader.exec_module(m)
    m.load_local_deploy_secrets(os.environ,m.LOCAL_DEPLOY_SECRET_FILE)
    sql="""
SELECT id,title,status,policy_type,tags FROM policies WHERE id BETWEEN 96 AND 105 ORDER BY id;
SELECT COUNT(*) FROM policies WHERE id=102;
SELECT id,title,category,subcategory,status,source_id,original_url FROM case_items WHERE source_id=(SELECT id FROM sources WHERE url LIKE '%779817%' ORDER BY id LIMIT 1) OR original_url LIKE '%779817%';
SELECT id,title,status,ai_evidence_status FROM sources WHERE url LIKE '%779817%' ORDER BY id;
SELECT id,name,level,parent_id FROM regions WHERE name LIKE '%中山%' ORDER BY id;
"""
    c=m.connect()
    try:
        print(m.database_command(c,sql)[1])
    finally: c.close()
if __name__=='__main__': main()
