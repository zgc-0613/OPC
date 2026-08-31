import argparse
import importlib.util
import os
import sys
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "outputs/policy-new-gd-zj-xj-20260826"


def deploy_module():
    sys.path.insert(0, str(ROOT))
    spec = importlib.util.spec_from_file_location("opc_deploy", ROOT / ".codex_deploy_opc.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def assert_checks(output):
    expected = [
        "imported=20",
        "sources=20",
        "pending=15,draft=5,published=0",
        "verified=0,unverified=20",
        "missing=0",
        "tagless=0",
        "comprehensive\t12",
        "funding_subsidy\t1",
        "governance_market\t5",
        "investment\t1",
        "scenario_demand\t1",
    ]
    missing = [value for value in expected if value not in output]
    if missing:
        raise RuntimeError(f"verification failed, missing {missing}:\n{output}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    update_sql = (BASE / "update.sql").read_text(encoding="utf-8")
    postcheck_sql = (BASE / "postcheck.sql").read_text(encoding="utf-8")
    deploy = deploy_module()
    deploy.load_local_deploy_secrets(os.environ, deploy.LOCAL_DEPLOY_SECRET_FILE)
    client = deploy.connect()
    candidate = None
    try:
        production_precheck = deploy.database_command(client, """
SELECT CONCAT('occupied_ids=',COUNT(*)) FROM policies WHERE id BETWEEN 99 AND 118;
SELECT CONCAT('existing_batch_sources=',COUNT(*)) FROM sources WHERE notes LIKE '%import_batch=policy-gd-zj-xj-20260826%';
""")[1]
        if "occupied_ids=0" not in production_precheck or "existing_batch_sources=0" not in production_precheck:
            raise RuntimeError(f"production precheck failed:\n{production_precheck}")

        candidate = deploy.prepare_candidate_database(client, datetime.now().strftime("%Y%m%d%H%M%S"))
        candidate_sql = update_sql.replace("USE opc_platform;", f"USE {candidate.name};", 1)
        candidate_checks = postcheck_sql.replace("USE opc_platform;", f"USE {candidate.name};", 1)
        deploy.candidate_database_command(client, candidate.name, candidate_sql)
        verification = deploy.candidate_database_command(client, candidate.name, candidate_checks)[1]
        assert_checks(verification)
        print("candidate_verified=true")
        print(verification)
        deploy.cleanup_candidate_database(client, candidate)
        candidate = None

        if args.apply:
            deploy.database_command(client, update_sql)
            production_verification = deploy.database_command(client, postcheck_sql)[1]
            assert_checks(production_verification)
            print("applied=true")
            print(production_verification)
        else:
            print("applied=false")
    finally:
        if candidate is not None:
            deploy.cleanup_candidate_database(client, candidate)
        client.close()


if __name__ == "__main__":
    main()
