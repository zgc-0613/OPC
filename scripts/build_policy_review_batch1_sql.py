import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
md = (root / 'outputs/policy-review-20260827/batch-01-id-4-13.md').read_text(encoding='utf-8')
summaries = {}
for match in re.finditer(r'###\s+(?:\d+\.)?\s*ID\s+(\d+).*?(?=\n###\s|\n##\s|\Z)', md, re.S):
    body = match.group(0)
    found = re.search(r'- 新摘要：(.+?)(?=\n- )', body, re.S)
    if found:
        summaries[int(match.group(1))] = ' '.join(found.group(1).split())

tags = {
    4: '综合发展政策，算力技术，财政激励，场景开放，人才培育，金融资本',
    5: '综合发展政策，算力技术，财政激励，人才培育，金融资本，制度治理',
    6: '综合发展政策，算力技术，财政激励，场景开放，人才培育，金融资本，制度治理',
    7: '综合发展政策，算力技术，财政激励，场景开放，人才培育，金融资本',
    8: '综合发展政策，算力技术，财政激励，场景开放，人才培育，金融资本，制度治理',
    9: '算力技术，财政激励，场景开放，人才培育',
    10: '综合发展政策，算力技术，人才培育',
    11: '制度治理，金融资本',
    12: '综合发展政策，算力技术，财政激励，场景开放，人才培育，金融资本，制度治理',
    13: '综合发展政策，算力技术，财政激励，场景开放，人才培育，金融资本，制度治理',
}

def sql(value):
    return "NULL" if value is None else "'" + value.replace("'", "''") + "'"

lines = ['SET NAMES utf8mb4;', 'START TRANSACTION;']
for rid in range(4, 14):
    if rid not in summaries:
        raise SystemExit(f'missing summary for {rid}')
    status = 'consultation' if rid == 8 else None
    sets = [f"summary = {sql(summaries[rid])}", f"tags = {sql(tags[rid])}"]
    if status:
        sets.append("status = 'consultation'")
        sets.append("title = CASE WHEN title LIKE '%征求意见稿%' THEN title ELSE CONCAT(title, '（征求意见稿）') END")
    lines.append(f"UPDATE policies SET {', '.join(sets)}, updated_at = CURRENT_TIMESTAMP WHERE id = {rid};")
lines += ['COMMIT;', '']
(root / 'outputs/policy-review-20260827/batch-01-deploy.sql').write_text('\n'.join(lines), encoding='utf-8')
print(f'generated={len(summaries)}')
