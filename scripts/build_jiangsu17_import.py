import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
rows=json.loads((ROOT/'outputs/policy-review-20260827/jiangsu17-raw.json').read_text(encoding='utf8'))['rows']
by={int(r['政策id']):r for r in rows}
# Workbook IDs 117-133 are source-row identifiers; production IDs are assigned by AUTO_INCREMENT.
primary={117:'综合发展政策',118:'综合发展政策',119:'综合发展政策',120:'综合发展政策',121:'综合发展政策',122:'制度治理',123:'综合发展政策',124:'综合发展政策',125:'综合发展政策',126:'综合发展政策',127:'综合发展政策',128:'综合发展政策',129:'制度治理',130:'综合发展政策',131:'制度治理',132:'综合发展政策',133:'综合发展政策'}
fields=[('具体政策·算力支持','算力技术'),('具体政策·资金补贴','财政激励'),('具体政策·场地工位','创业生态'),('具体政策·场景需求','场景开放'),('具体政策·人才服务','人才培育'),('具体政策·投资融资','金融资本'),('具体政策·其他','制度治理')]
def q(v):
 s='' if v is None else str(v)
 return "NULL" if not s or s in ('nan','NaT') else "'"+s.replace('\\','\\\\').replace("'","''").replace('\r','').replace('\n','\\n')+"'"
def date(v):
 s='' if v is None else str(v)
 return q(s[:10] if s and s not in ('nan','NaT') else None)
out=['SET NAMES utf8mb4;','USE opc_platform;','START TRANSACTION;','-- import_batch=policy-jiangsu17-20260828']
for i in range(117,134):
 if i==131: continue
 r=by[i].copy()
 if i==130:
  r['title政策标题']='《扬州市人工智能OPC集聚创业地建设行动计划及政务服务改革八项措施》'
  r['summary摘要（100字左右）']=by[130]['summary摘要（100字左右）']+'；同时整合政务服务改革八项措施，提供线上通办、线下代办、社区帮办和诉接速办等一站式服务。'
  for f,t in fields:
   if by[131].get(f): r[f]=(str(r.get(f) or '')+'；'+str(by[131][f])).strip('；')
 topics=[]
 for f,t in fields:
  if r.get(f) not in (None,'','nan') and t not in topics: topics.append(t)
 if primary[i] not in topics: topics.insert(0,primary[i])
 evidence=[]
 for f,t in fields:
  if r.get(f) not in (None,'','nan'): evidence.append(f'{t}：{str(r[f]).replace(chr(10)," ")}')
 key='主分类：'+primary[i]+'\\n涉及主题：'+ '，'.join(topics)+'\\n'+'\\n'.join(evidence)
 supports='\\n\\n'.join('【'+t+'】\\n'+str(r[f]).replace(chr(10),' ') for f,t in fields if r.get(f) not in (None,'','nan'))
 level=str(r.get('policy_level政策等级') or '')
 levelv='province' if '省' in level else ('district' if '区' in level else ('county' if '县' in level else 'city'))
 url=r.get('url政策原文网页链接'); ev=r.get('辅证链接')
 out += [f"INSERT INTO sources (title,source_type,publisher,url,accessed_at,notes,status,ai_evidence_status) SELECT {q(r.get('title政策标题'))},'government_site',{q(r.get('发文单位'))},{q(url)},'2026-08-28',{q('import_batch=policy-jiangsu17-20260828; workbook_id='+str(i)+'; source pre-reviewed; administrator evidence approval pending')},'draft','legacy_unverified' WHERE NOT EXISTS (SELECT 1 FROM sources WHERE url={q(url)} OR (title={q(r.get('title政策标题'))} AND publisher={q(r.get('发文单位'))}));", f"SET @source_id=(SELECT id FROM sources WHERE url={q(url)} OR (title={q(r.get('title政策标题'))} AND publisher={q(r.get('发文单位'))}) ORDER BY id LIMIT 1);", f"SET @region_id=(SELECT id FROM regions WHERE name={q(r.get('region省') or '江苏省')} ORDER BY id LIMIT 1);", f"INSERT INTO policies (title,region_id,issuing_body,document_no,publish_date,effective_date,valid_period,source_id,policy_level,policy_type,summary,key_points,support_measures,tags,original_url,evidence_url,accessed_at,status,reviewer,ai_evidence_status) SELECT {q(r.get('title政策标题'))},@region_id,{q(r.get('发文单位'))},{q(r.get('文号'))},{date(r.get('成文日期'))},{date(r.get('开始实施时间'))},{q(r.get('政策有效时长'))},@source_id,{q(levelv)},({q({'综合发展政策':'comprehensive','算力技术':'computing_support','财政激励':'funding_subsidy','场景开放':'scenario_demand','人才培育':'talent_service','金融资本':'investment','制度治理':'governance_market'}[primary[i]])}),{q(r.get('summary摘要（100字左右）'))},{q(key)},{q(supports)},{q('，'.join(topics))},{q(url)},{q(ev)},{q('2026-08-28')},'pending','codex-source-review-pending','legacy_unverified' WHERE @region_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM policies WHERE original_url={q(url)} OR title={q(r.get('title政策标题'))});", "SET @policy_id=(SELECT id FROM policies WHERE original_url="+q(url)+" OR title="+q(r.get('title政策标题'))+" ORDER BY id DESC LIMIT 1);"]
 for n,t in enumerate(topics,1): out += [f"INSERT INTO tags (name,tag_type,is_industry,sort_order) VALUES ({q(t)},'policy',0,{n}) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id);",'SET @tag_id=LAST_INSERT_ID();', 'INSERT IGNORE INTO policy_tags (policy_id,tag_id) SELECT @policy_id,@tag_id WHERE @policy_id IS NOT NULL;']
out += ['COMMIT;']
(ROOT/'outputs/policy-review-20260827/jiangsu17-import.sql').write_text('\n'.join(out),encoding='utf8')
print('records',16,'sql',ROOT/'outputs/policy-review-20260827/jiangsu17-import.sql')
