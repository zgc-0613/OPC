import json,re,urllib.parse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
rows=json.loads((ROOT/'outputs/policies-production-20260826.json').read_text(encoding='utf8'))
allow=re.compile(r'(gov\.cn|\.gov\.cn|people\.com\.cn|xinhuanet\.com|news\.cn|cctv\.com|chinanews\.com|thepaper\.cn|yicai\.com|jfdaily\.com|sznews\.com)',re.I)
pats=re.compile(r'新闻|报道|实践|活动|论坛|发布会|解读|标准|指引|指南|通知',re.I)
rows_out=[]
for x in rows:
 u=x.get('originalUrl') or ''; host=urllib.parse.urlparse(u).netloc; title=x.get('title') or ''; reason=[]
 if host and not allow.search(host): reason.append('非政府/非权威域名')
 if pats.search(title): reason.append('标题显示报道/解读/标准/指引等资料性质')
 if reason: rows_out.append({'id':x['id'],'title':title,'url':u,'evidenceUrl':x.get('evidenceUrl'),'reason':reason,'sourceTitle':x.get('sourceTitle'),'sourceStatus':x.get('status'),'evidenceStatus':x.get('aiEvidenceStatus')})
out=ROOT/'outputs/policy-reclassification-20260828/initial-source-screening.json'; out.write_text(json.dumps(rows_out,ensure_ascii=False,indent=2),encoding='utf8')
md=['# 政策来源初筛清单','','说明：这是初筛候选，不等于最终排除。政府官网转载、官方解读和有明确政策出处的权威媒体报道仍可保留；请逐条人工确认。','', '|ID|标题|原始链接|初筛原因|','|---:|---|---|---|']
for x in rows_out: md.append(f"|{x['id']}|{x['title']}|{x['url']}|{'；'.join(x['reason'])}|")
(out.with_suffix('.md')).write_text('\n'.join(md),encoding='utf8')
print('candidates',len(rows_out)); print('\n'.join(f"ID{x['id']}\t{x['title']}\t{'；'.join(x['reason'])}" for x in rows_out))
