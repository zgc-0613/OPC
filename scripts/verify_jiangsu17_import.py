import importlib.util, os, sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root))
sp=importlib.util.spec_from_file_location('d',root/'.codex_deploy_opc.py'); d=importlib.util.module_from_spec(sp); sys.modules['d']=d; sp.loader.exec_module(d)
d.load_local_deploy_secrets(os.environ,d.LOCAL_DEPLOY_SECRET_FILE); c=d.connect()
sql="""SET NAMES utf8mb4;
SELECT COUNT(*) AS total_policies FROM policies;
SELECT status,COUNT(*) AS count FROM policies GROUP BY status ORDER BY status;
SELECT COUNT(*) AS effective_verified FROM policies p JOIN sources s ON s.id=p.source_id WHERE p.status='published' AND p.ai_evidence_status='verified' AND s.status='published' AND s.ai_evidence_status='verified';
SELECT policy_type,COUNT(*) AS count FROM policies GROUP BY policy_type ORDER BY count DESC;
SELECT t.name,COUNT(DISTINCT pt.policy_id) AS count FROM tags t JOIN policy_tags pt ON pt.tag_id=t.id WHERE t.tag_type='policy' GROUP BY t.name ORDER BY count DESC;
SELECT COUNT(*) AS imported FROM policies WHERE original_url IN (SELECT url FROM sources WHERE notes LIKE '%import_batch=policy-jiangsu17-20260828%');
SELECT p.id,p.title,p.status,p.policy_type,p.original_url,COUNT(pt.tag_id) tags FROM policies p JOIN sources s ON s.id=p.source_id LEFT JOIN policy_tags pt ON pt.policy_id=p.id WHERE s.notes LIKE '%import_batch=policy-jiangsu17-20260828%' GROUP BY p.id,p.title,p.status,p.policy_type,p.original_url ORDER BY p.id;
SELECT COUNT(*) AS merged_title FROM policies WHERE title LIKE '%扬州市人工智能OPC集聚创业地建设行动计划及政务服务改革八项措施%';
"""
try: print(d.database_command(c,sql)[1])
finally: c.close()
