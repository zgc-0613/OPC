import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
src = ROOT / 'outputs/policy-review-20260827/jiangsu17-raw.json'
out = ROOT / 'outputs/policy-review-20260827/jiangsu17-review.md'
d = json.loads(src.read_text(encoding='utf-8'))
prim = {117:'综合发展政策',118:'综合发展政策',119:'综合发展政策',120:'综合发展政策',121:'综合发展政策',122:'制度治理',123:'综合发展政策',124:'综合发展政策',125:'综合发展政策',126:'综合发展政策',127:'综合发展政策',128:'综合发展政策',129:'制度治理',130:'综合发展政策',131:'制度治理',132:'综合发展政策',133:'综合发展政策'}
fields = [('具体政策·算力支持','算力技术'),('具体政策·资金补贴','财政激励'),('具体政策·场地工位','制度治理'),('具体政策·场景需求','场景开放'),('具体政策·人才服务','人才培育'),('具体政策·投资融资','金融资本'),('具体政策·其他','制度治理')]
lines=['# 江苏新增17条政策整理与导入前复核','','口径：每条记录仅设一个主分类；涉及主题仅依据具体措施字段，多选；征求意见稿不计入正式政策统计。','', '|ID|政策标题|原始链接|状态|主分类|涉及主题|复核提示|','|---:|---|---|---|---|---|---|']
for x in d['rows']:
 i=int(x['政策id']); topics=[]
 for f,t in fields:
  if x.get(f) not in (None,'') and t not in topics: topics.append(t)
 if prim[i] not in topics: topics.insert(0,prim[i])
 note=''
 if i in (130,131): note='与另一条记录使用同一扬州官方页面，需确认是否同一文件；同一文件仅保留一条。'
 if i==119: note='原始链接为征求意见页面，表中现行有效状态需核对正式发布页。'
 if i==122: note='市场准入/登记指引类规范文件，标注指导性规范。'
 lines.append(f"|{i}|{x.get('title政策标题','')}|{x.get('url政策原文网页链接','')}|{x.get('政策状态','')}|{prim[i]}|{'、'.join(topics)}|{note or '无'}|")
lines += ['', '## 逐条证据与原表摘要', '']
for x in d['rows']:
 i=int(x['政策id']); lines += [f"### ID{i} {x.get('title政策标题','')}",f"- 原始链接：{x.get('url政策原文网页链接') or '原文链接未提供'}",f"- 辅证链接：{x.get('辅证链接') or '未提供'}",f"- 发布机关：{x.get('发文单位') or '原文未明确'}；文号：{x.get('文号') or '原文未明确'}；成文日期：{x.get('成文日期') or '原文未明确'}",f"- 状态：{x.get('政策状态') or '原文未明确'}；地区：{x.get('region省')} {x.get('region市') or ''} {x.get('region区') or ''}",f"- 主分类：{prim[i]}"]
 for f,t in fields:
  if x.get(f) not in (None,''): lines.append(f"- {t}关键句：{' '.join(str(x[f]).split())}")
 lines += [f"- 原表摘要（待按证据重写）：{x.get('summary摘要（100字左右）') or '原文未明确'}", '']
out.write_text('\n'.join(lines), encoding='utf-8'); print(out)
