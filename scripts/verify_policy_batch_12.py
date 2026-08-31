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
SELECT COUNT(*) AS id107_policy_count FROM policies WHERE id=107;
SELECT COUNT(*) AS id107_tag_count FROM policy_tags WHERE policy_id=107;
SELECT id,title,status,policy_type,tags FROM policies WHERE id BETWEEN 116 AND 118 ORDER BY id;
"""
    c=m.connect()
    try: print(m.database_command(c,sql)[1])
    finally: c.close()
if __name__=='__main__': main()
