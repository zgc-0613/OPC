"""Read-only production verification for policy review batch 09."""

from __future__ import annotations

import importlib.util
import os
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    spec = importlib.util.spec_from_file_location("opc_deploy", ROOT / ".codex_deploy_opc.py")
    if spec is None or spec.loader is None:
        raise RuntimeError("Cannot load deployment helper")
    module = importlib.util.module_from_spec(spec)
    if str(ROOT) not in sys.path:
        sys.path.insert(0, str(ROOT))
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    module.load_local_deploy_secrets(os.environ, module.LOCAL_DEPLOY_SECRET_FILE)

    sql = """
SELECT id,title,status,policy_type,tags
FROM policies
WHERE id BETWEEN 86 AND 95
ORDER BY id;
SELECT COUNT(*) AS id95_policy_count FROM policies WHERE id=95;
SELECT COUNT(*) AS id95_tag_count FROM policy_tags WHERE policy_id=95;
"""
    client = module.connect()
    try:
        _, output, _ = module.database_command(client, sql)
        print(output)
    finally:
        client.close()


if __name__ == "__main__":
    main()
