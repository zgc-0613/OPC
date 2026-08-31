import json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
rows=json.loads((ROOT/'outputs/policies-production-20260826.json').read_text(encoding='utf8'))
outdir=ROOT/'outputs/policy-reclassification-20260828'; outdir.mkdir(exist_ok=True)
CATS={
 'computing_support':('算力技术',['算力','智算','模型券','Token','大模型','智能体','数据集','数据标注','算法','低代码','API']),
 'funding_subsidy':('财政激励',['补贴','补助','奖励','资助','减免','税费','社保补贴','培训补贴','数据券']),
 'scenario_application':('场景开放',['应用场景','场景清单','订单池','订单对接','揭榜','示范项目','应用推广','需求清单','场景需求','成果转化']),
 'ecosystem_community':('创业生态',['OPC社区','创业社区','孵化器','众创空间','创业空间','共享工位','工位','产业园','服务站','创业导师','创业服务','产业联盟','资源对接','创业载体']),
 'finance_investment':('金融资本',['基金','贷款','融资','担保','贴息','股权投资','风险补偿','金融机构','信贷','投资']),
 'institution_market':('制度治理',['注册登记','企业登记','一网通办','专窗','市场准入','合规','知识产权','信用','标准制定','监管','规范指引','算法备案','数据安全','政务服务']),
 'talent_university':('人才培育',['人才','高校','大学生','毕业生','培训','训练营','竞赛','实训','产学研','校企','人才公寓','住房补贴']),
}
TITLE_HINTS=[('institution_market',r'登记|准入|指引|规范|监管|政务服务|一类事'),('ecosystem_community',r'社区|创业空间|创业首选区|孵化|载体'),('computing_support',r'算力'),('finance_investment',r'基金|投融资|融资'),('talent_university',r'人才'),('scenario_application',r'场景|应用推广'),('funding_subsidy',r'补贴|奖励|资助')]
def txt(r): return ' '.join(str(r.get(k) or '') for k in ('title','summary','keyPoints','supportMeasures','tags'))
def classify(r):
 t=txt(r); title=str(r.get('title') or '')
 scores={k:sum(len(re.findall(re.escape(w),t,re.I)) for w in ws) for k,(_,ws) in CATS.items()}
 title_hits=[]
 for k,pat in TITLE_HINTS:
  if re.search(pat,title): title_hits.append(k); scores[k]+=5
 ranked=sorted(scores.items(),key=lambda x:(x[1],x[0]),reverse=True)
 positive=[k for k,v in ranked if v>0]; top,second=ranked[0],ranked[1]
 comprehensive=(len(positive)>=4 and top[1] < max(8, second[1]*1.8) and not (title_hits and top[1]>=second[1]*1.35))
 scope='comprehensive' if comprehensive else 'specialized'
 primary='综合型' if comprehensive else CATS[top[0]][0]
 themes=[k for k,v in ranked if v>0]
 confidence='high' if (comprehensive and len(positive)>=5) or (not comprehensive and top[1]>=max(6,second[1]*1.6)) else 'review'
 return {'id':r['id'],'title':r.get('title'),'oldPrimary':r.get('policyType'),'scope':scope,'primary':primary,'primaryKey':None if comprehensive else top[0],'themes':themes,'themeLabels':[CATS[k][0] for k in themes],'scores':scores,'confidence':confidence,'titleHints':[CATS[k][0] for k in title_hits]}
draft=[classify(r) for r in rows]
# Consultation drafts remain in the database but are outside the classification and statistics scope.
for d in draft:
 is_consultation = str(next(r for r in rows if r['id'] == d['id']).get('status') or '').lower() == 'consultation' or '征求意见稿' in str(d.get('title') or '')
 if is_consultation:
  d['recordNature']='consultation_draft'; d['includeInPolicyStats']=False; d['scope']='excluded'; d['primary']='征求意见稿（不纳入分类）'; d['primaryKey']=None; d['themes']=[]; d['themeLabels']=[]; d['confidence']='high'; d['manualNote']='征求意见稿保留在数据库中，但不纳入七类分类、统计和后续人工核对'
 elif d['id']==10:
  d['recordNature']='official_news'; d['includeInPolicyStats']=False; d['primary']='非政策资料（官方报道）'; d['primaryKey']=None; d['scope']='excluded'
 elif d['id']==37:
  d['recordNature']='policy'; d['includeInPolicyStats']=True; d['scope']='comprehensive'; d['primary']='综合型'; d['primaryKey']=None; d['manualNote']='用户提供济南市科技局官方政策文章，按综合型处理'; d['evidenceUrlOverride']='https://jntzcjj.jinan.gov.cn/col88375/art/2026/art_88375_4785134.html'
 elif d['id']==53:
  d['recordNature']='policy'; d['includeInPolicyStats']=True; d['scope']='comprehensive'; d['primary']='综合型'; d['primaryKey']=None; d['themes']=list(CATS); d['themeLabels']=[CATS[k][0] for k in d['themes']]; d['manualNote']='用户提供完整正文：16项措施同时覆盖算力、资金、场景、社区载体、融资、制度监管和人才高校，按综合型处理'
 elif d['id']==61:
  d['recordNature']='policy'; d['includeInPolicyStats']=True; d['scope']='comprehensive'; d['primary']='综合型'; d['primaryKey']=None; d['themes']=list(CATS); d['themeLabels']=[CATS[k][0] for k in d['themes']]; d['confidence']='high'; d['manualNote']='用户提供完整征求意见稿：CORE四维和12项举措覆盖七类支持机制，按综合型处理'
 elif d['id']==63:
  d['recordNature']='policy'; d['includeInPolicyStats']=True; d['scope']='comprehensive'; d['primary']='综合型'; d['primaryKey']=None; d['themes']=list(CATS); d['themeLabels']=[CATS[k][0] for k in d['themes']]; d['confidence']='high'; d['manualNote']='用户提供完整征求意见稿：五链融合、场景、算力、金融、人才、社区和政务服务均有具体措施，按综合型处理'
 elif d['id']==72:
  d['recordNature']='standard'; d['includeInPolicyStats']=False; d['scope']='specialized'; d['primary']='制度治理'; d['primaryKey']='institution_market'; d['themes']=['institution_market']; d['themeLabels']=[CATS['institution_market'][0]]; d['confidence']='high'; d['manualNote']='《人工智能OPC术语》团体标准，属于标准规范资料；保留来源但不计入财政扶持政策统计'
 elif d['id']==97:
  d['recordNature']='policy'; d['includeInPolicyStats']=True; d['scope']='specialized'; d['primary']='创业生态'; d['primaryKey']='ecosystem_community'; d['themes']=['ecosystem_community','computing_support','scenario_application','finance_investment','institution_market','talent_university']; d['themeLabels']=[CATS[k][0] for k in d['themes']]; d['confidence']='high'; d['manualNote']='用户提供完整工作指引：核心是OPC社区认定、建设和运营，主分类为创业生态；未见直接财政补贴条款'
 else:
  d['recordNature']='policy'; d['includeInPolicyStats']=True
(outdir/'reclassification-v2.json').write_text(json.dumps(draft,ensure_ascii=False,indent=2),encoding='utf8')
lines=['# OPC政策七类新标准重分类对照表（V2）','','说明：综合型是政策性质，不属于七类主题；专项型政策才指定七类主分类。征求意见稿保留在数据库中，但不纳入七类分类、统计和后续人工核对。本文件为候选编码，不修改生产数据库。','', '|ID|标题|记录性质|政策性质|新主分类|涉及主题|置信度|','|---:|---|---|---|---|---|---|']
for d in draft:
  nature = '征求意见稿' if d.get('recordNature') == 'consultation_draft' else ('政策' if d.get('includeInPolicyStats') else '官方报道/非政策资料')
  lines.append(f"|{d['id']}|{d['title']}|{nature}|{'综合型' if d['scope']=='comprehensive' else ('专项型' if d['scope']=='specialized' else '—')}|{d['primary']}|{'、'.join(d['themeLabels'])}|{d['confidence']}|")
lines += ['', '## 政策性质统计','']
for s in ('comprehensive','specialized'): lines.append(f"- {'综合型' if s=='comprehensive' else '专项型'}：{sum(d['scope']==s and d.get('includeInPolicyStats') for d in draft)} 条")
lines += ['', '## 专项型主分类统计','']
for k,(label,_) in CATS.items(): lines.append(f"- {label}：{sum(d['primaryKey']==k and d.get('includeInPolicyStats') for d in draft)} 条")
lines += ['', '## 七类涉及主题覆盖','']
for k,(label,_) in CATS.items(): lines.append(f"- {label}：{sum(k in d['themes'] and d.get('includeInPolicyStats') for d in draft)} 条")
lines += ['', '## 优先人工复核','']
for d in draft:
 if d['confidence']=='review': lines.append(f"- ID{d['id']} {d['title']}：候选={d['primary']}；关键词得分={d['scores']}")
(outdir/'reclassification-v2.md').write_text('\n'.join(lines),encoding='utf8')
print(json.dumps({'records':len(draft),'included':sum(d.get('includeInPolicyStats') for d in draft),'excluded':sum(not d.get('includeInPolicyStats') for d in draft),'comprehensive':sum(d['scope']=='comprehensive' and d.get('includeInPolicyStats') for d in draft),'specialized':sum(d['scope']=='specialized' and d.get('includeInPolicyStats') for d in draft),'review':sum(d['confidence']=='review' and d.get('includeInPolicyStats') for d in draft)},ensure_ascii=False))
