import argparse, importlib.util, os, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser(); p.add_argument('--sql',type=Path,required=True); p.add_argument('--apply',action='store_true'); a=p.parse_args()
 sys.path.insert(0,str(ROOT)); spec=importlib.util.spec_from_file_location('d',ROOT/'.codex_deploy_opc.py'); m=importlib.util.module_from_spec(spec); sys.modules['d']=m; spec.loader.exec_module(m); m.load_local_deploy_secrets(os.environ,m.LOCAL_DEPLOY_SECRET_FILE); c=m.connect(); sql=a.sql.read_text(encoding='utf8'); cand=None
 try:
  cand=m.prepare_candidate_database(c,'20260824010000'); m.candidate_database_command(c,cand.name,sql.replace('USE opc_platform;',f'USE {cand.name};',1)); print('candidate_verified=true'); m.cleanup_candidate_database(c,cand); cand=None
  if a.apply: m.database_command(c,sql); print('applied=true')
  else: print('applied=false')
 finally:
  if cand: m.cleanup_candidate_database(c,cand)
  c.close()
if __name__=='__main__': main()
