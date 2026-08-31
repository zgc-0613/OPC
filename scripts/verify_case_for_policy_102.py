"""Read-only search for a possible case corresponding to policy record 102."""
from __future__ import annotations
import importlib.util, os, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
def main():
    if str(ROOT) not in sys.path: sys.path.insert(0, str(ROOT))
    spec=importlib.util.spec_from_file_location('opc_deploy', ROOT/'.codex_deploy_opc.py')
    module=importlib.util.module_from_spec(spec); sys.modules[spec.name]=module; spec.loader.exec_module(module)
    module.load_local_deploy_secrets(os.environ, module.LOCAL_DEPLOY_SECRET_FILE)
    sql="""
SELECT id,title,article_title,original_url,status,category,subcategory FROM case_items
WHERE title LIKE '%中山%' OR article_title LIKE '%中山%' OR title LIKE '%人工智能应用大会%' OR article_title LIKE '%人工智能应用大会%'
   OR original_url LIKE '%779817%' OR original_url LIKE '%772708%';
SELECT id,title,url,status FROM sources
WHERE title LIKE '%中山%' OR title LIKE '%人工智能应用大会%' OR url LIKE '%779817%' OR url LIKE '%772708%';
"""
    client=module.connect()
    try:
        _, output, _=module.database_command(client, sql); print(output)
    finally: client.close()
if __name__=='__main__': main()
