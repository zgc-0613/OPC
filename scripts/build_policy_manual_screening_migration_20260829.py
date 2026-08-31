from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'deploy' / 'sql' / '20260829_policy_manual_screening.sql'

FORMAL = [4,5,6,7,11,12,13,14,16,17,19,20,21,25,29,31,33,35,36,39,40,41,43,45,46,47,48,49,50,52,53,54,55,57,59,60,62,64,66,69,75,77,78,80,81,82,83,87,88,89,90,91,92,93,94,96,97,98,99,101,105,106,108,109,111,113,114,119,120,121,124,125,126,127,128,129,130,131,132,133,134]
CONSULTATION = [8,30,32,51,61,63,76,79,104,110,112,116,117,22]
DELETE = [10,26,28,34,67,100,118,122,123]
PENDING = [18]
STANDARD = [72, 73, 115]

def q(value):
    return "'" + value.replace('\\', '\\\\').replace("'", "''") + "'"

formal_published = [i for i in FORMAL if i != 66]
all_touched = sorted(set(FORMAL + CONSULTATION + PENDING + STANDARD + [37, 58, 15, 24, 103, 86, 23] + DELETE))
policy_ids = ','.join(map(str, all_touched))
delete_ids = ','.join(map(str, DELETE))

reviewer_cases = {
    **{i: 'manual-screening-formal-20260829' for i in formal_published},
    66: 'manual-screening-formal-20260829;失效',
    **{i: 'manual-screening-consultation-20260829' for i in CONSULTATION if i != 22},
    22: 'manual-screening-consultation-20260829;原名为征求意见稿，现有链接为平台服务页面',
    18: 'manual-screening-pending-20260829;未找到正式文件，待进一步检索',
    37: 'manual-screening-supplement-20260829;OPC具体社区实践',
    58: 'manual-screening-supplement-20260829;权威媒体报道，可作为正式报道',
    15: 'manual-screening-supplement-20260829;官方平台/服务信息',
    24: 'manual-screening-supplement-20260829;官方平台/服务信息',
    103: 'manual-screening-supplement-20260829;官方平台/服务信息',
    86: 'manual-screening-supplement-20260829;政策服务清单',
    23: 'manual-screening-supplement-20260829;官方活动信息',
    **{i: 'manual-screening-standard-20260829;标准规范文件' for i in STANDARD},
    114: 'manual-screening-formal-20260829;未找到详细说明报道',
}

status_cases = {
    **{i: 'published' for i in formal_published},
    66: 'expired',
    **{i: 'consultation' for i in CONSULTATION},
    18: 'draft',
    37: 'published', 58: 'published', 15: 'published', 24: 'published',
    103: 'published', 86: 'published', 23: 'published',
    **{i: 'published' for i in STANDARD},
}

url_updates = {
    35: 'http://www.lst.gov.cn/lst/zfgb/202606/baa78639da694d3290526f3fd3ab2889.shtml',
    40: 'https://www.chengdu.gov.cn/cdsrmzf/c174133/2026-03/25/content_1e6c12e4cd8d4bd1aca35d5dac711a82.shtml',
    75: 'https://scjg.xa.gov.cn/xwzx/sjdt/2085566200442548226.html',
    133: 'https://wxjkq.wuxi.gov.cn/doc/2025/12/16/4701591.shtml',
}
source_updates = {
    35: (34, None),
    40: (39, None),
    75: (137, '西安市推出八项举措，扶持OPC公司，为轻创业者注入新动能--西安市市场监督管理局'),
    133: (248, '拥抱OPC！经开超有AI'),
}

policy_status_case = '\n'.join(f'WHEN {i} THEN {q(v)}' for i, v in sorted(status_cases.items()))
reviewer_case = '\n'.join(f'WHEN {i} THEN {q(v)}' for i, v in sorted(reviewer_cases.items()))

lines = [
    'USE opc_platform;',
    'START TRANSACTION;',
    '',
    "ALTER TABLE policies ADD COLUMN IF NOT EXISTS material_nature VARCHAR(40) NULL COMMENT '资料性质';",
    'CREATE TABLE IF NOT EXISTS policy_manual_screening_backup_20260829 LIKE policies;',
    'CREATE TABLE IF NOT EXISTS policy_manual_screening_sources_backup_20260829 LIKE sources;',
    'CREATE TABLE IF NOT EXISTS policy_manual_screening_tags_backup_20260829 LIKE policy_tags;',
    'CREATE TABLE IF NOT EXISTS policy_manual_screening_industry_tags_backup_20260829 LIKE policy_industry_tags;',
    f'INSERT IGNORE INTO policy_manual_screening_backup_20260829 SELECT * FROM policies WHERE id IN ({policy_ids});',
    f'INSERT IGNORE INTO policy_manual_screening_sources_backup_20260829 SELECT * FROM sources WHERE id IN (SELECT source_id FROM policies WHERE id IN ({policy_ids}));',
    f'INSERT IGNORE INTO policy_manual_screening_tags_backup_20260829 SELECT * FROM policy_tags WHERE policy_id IN ({policy_ids});',
    f'INSERT IGNORE INTO policy_manual_screening_industry_tags_backup_20260829 SELECT * FROM policy_industry_tags WHERE policy_id IN ({policy_ids});',
    '',
    'UPDATE policies SET status = CASE id',
    policy_status_case,
    'END, reviewer = CASE id',
    reviewer_case,
    'END WHERE id IN (' + ','.join(map(str, sorted(status_cases))) + ');',
    '',
    'UPDATE policies SET material_nature = CASE id',
    '\n'.join(f'WHEN {i} THEN {q("formal_policy")}' for i in FORMAL),
    '\n'.join(f'WHEN {i} THEN {q("consultation_draft")}' for i in CONSULTATION),
    '\n'.join(f'WHEN {i} THEN {q("pending_verification")}' for i in PENDING),
    'WHEN 37 THEN ' + q('nonformal_policy_info'),
    'WHEN 58 THEN ' + q('authoritative_media'),
    '\n'.join(f'WHEN {i} THEN {q("official_platform_service")}' for i in [15, 24, 86, 103]),
    'WHEN 23 THEN ' + q('official_activity'),
    '\n'.join(f'WHEN {i} THEN {q("standard_reference")}' for i in STANDARD),
    'END WHERE id IN (' + ','.join(map(str, sorted(set(FORMAL + CONSULTATION + PENDING + STANDARD + [37, 58, 15, 24, 86, 103, 23])))) + ');',
]

for policy_id, url in url_updates.items():
    lines.append(f'UPDATE policies SET original_url={q(url)} WHERE id={policy_id};')
    source_id, source_title = source_updates[policy_id]
    lines.append(f'UPDATE sources SET url={q(url)}' + (f', title={q(source_title)}' if source_title else '') + f' WHERE id={source_id};')

lines += [
    f'UPDATE policies SET title=CONCAT(title, \'（征求意见稿）\') WHERE id=22 AND title NOT LIKE \'%征求意见稿%\';',
    f'DELETE FROM policy_industry_tags WHERE policy_id IN ({delete_ids});',
    f'DELETE FROM policy_tags WHERE policy_id IN ({delete_ids});',
    f'DELETE FROM policies WHERE id IN ({delete_ids});',
    '',
    'SELECT COUNT(*) AS remaining_target_records FROM policies WHERE id IN (' + policy_ids + ');',
    'SELECT status,COUNT(*) AS n FROM policies WHERE id IN (' + ','.join(map(str, sorted(status_cases))) + ') GROUP BY status ORDER BY status;',
    'SELECT id,original_url,status,reviewer FROM policies WHERE id IN (' + ','.join(map(str, sorted(set(url_updates) | set(CONSULTATION) | {18,66}))) + ') ORDER BY id;',
    f'SELECT COUNT(*) AS deleted_records FROM policies WHERE id IN ({delete_ids});',
    'SELECT material_nature, COUNT(*) AS n FROM policies GROUP BY material_nature ORDER BY material_nature;',
    "SELECT COUNT(*) AS valid_formal_policy_count FROM policies WHERE material_nature='formal_policy' AND status='published';",
    "SELECT COUNT(*) AS expired_formal_policy_count FROM policies WHERE material_nature='formal_policy' AND status='expired';",
    'SELECT COUNT(*) AS empty_material_nature_count FROM policies WHERE material_nature IS NULL OR TRIM(material_nature)="";',
    'COMMIT;',
]

OUT.write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(OUT)
