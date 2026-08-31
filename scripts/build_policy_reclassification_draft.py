import json,re
from collections import Counter
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
src=ROOT/'outputs/policies-production-20260826.json'
outdir=ROOT/'outputs/policy-reclassification-20260828'; outdir.mkdir(parents=True,exist_ok=True)
rows=json.loads(src.read_text(encoding='utf-8'))
cats={
 'computing_support':('算力技术',['算力','智算','模型券','Token','大模型','智能体工具','数据集','数据标注','算法','低代码','API','技术平台']),
 'funding_subsidy':('财政激励',['补贴','补助','奖励','资助','减免','税费','社保补贴','培训补贴','数据券','场景券']),
 'scenario_application':('场景开放',['应用场景','场景清单','订单池','订单对接','揭榜','示范项目','应用推广','需求清单','场景需求','成果转化']),
 'ecosystem_community':('创业生态',['OPC社区','创业社区','孵化器','众创空间','创业空间','共享工位','工位','产业园','服务站','创业导师','创业服务','产业联盟','资源对接','创业载体']),
 'finance_investment':('金融资本',['基金','贷款','融资','担保','贴息','股权投资','风险补偿','金融机构','信贷','投资']),
 'institution_market':('制度治理',['注册登记','企业登记','一网通办','专窗','市场准入','合规','知识产权','信用','标准制定','监管','规范指引','算法备案','数据安全','政务服务']),
 'talent_university':('人才培育',['人才','高校','大学生','毕业生','培训','训练营','竞赛','实训','产学研','校企','人才公寓','住房补贴']),
}
old_to_new={'computing_support':'computing_support','funding_subsidy':'funding_subsidy','scenario_demand':'scenario_application','investment':'finance_investment','governance_market':'institution_market','talent_service':'talent_university'}
def text(r): return ' '.join(str(r.get(k) or '') for k in ('title','summary','keyPoints','supportMeasures','tags'))
def classify(r):
 t=text(r); scores={k:sum(len(re.findall(re.escape(w),t,re.I)) for w in ws) for k,(_,ws) in cats.items()}
 # Keep community/space evidence distinct from generic platform mentions.
 scores['ecosystem_community'] += 2*len(re.findall(r'OPC社区|创业社区|共享工位|创业空间|孵化器|众创空间',t))
 # Avoid classifying a generic AI platform as ecosystem without a service/network cue.
 if not re.search(r'社区|孵化|工位|园区|服务站|导师|联盟|资源对接|创业服务',t): scores['ecosystem_community']=0
 ranked=sorted(scores.items(),key=lambda kv:(kv[1],kv[0]),reverse=True)
 primary=ranked[0][0] if ranked[0][1]>0 else 'institution_market'
 themes=[k for k,v in ranked if v>0]
 old=r.get('policyType')
 return {'id':r['id'],'title':r.get('title'),'status':r.get('status'),'oldPrimary':old,'primary':primary,'primaryLabel':cats[primary][0],'themes':themes,'themeLabels':[cats[k][0] for k in themes],'scores':scores,'confidence':'high' if ranked[0][1]>=ranked[1][1]*1.5 and ranked[0][1]>=3 else 'review'}
draft=[classify(r) for r in rows]
(outdir/'reclassification-draft.json').write_text(json.dumps(draft,ensure_ascii=False,indent=2),encoding='utf-8')
lines=['# 七类新标准政策重分类初稿','','说明：本文件仅为分类复核草案，不修改生产数据库。主分类按主要干预机制单选；涉及主题按原文具体措施多选。','', '|ID|标题|原主分类|新主分类|涉及主题|置信度|','|---:|---|---|---|---|---|']
for d in draft:
 lines.append(f"|{d['id']}|{d['title']}|{d['oldPrimary'] or ''}|{d['primaryLabel']}|{'、'.join(d['themeLabels'])}|{d['confidence']}|")
lines += ['', '## 新主分类汇总','']
for k,(label,_) in cats.items(): lines.append(f"- {label}：{sum(d['primary']==k for d in draft)} 条")
lines += ['', '## 涉及主题覆盖汇总','']
for k,(label,_) in cats.items(): lines.append(f"- {label}：{sum(k in d['themes'] for d in draft)} 条")
lines += ['', '## 需要人工优先复核','']
for d in draft:
 if d['confidence']=='review': lines.append(f"- ID{d['id']} {d['title']}：候选主分类为{d['primaryLabel']}；各类关键词得分={d['scores']}")
(outdir/'reclassification-draft.md').write_text('\n'.join(lines),encoding='utf-8')
analysis='''# 3.3.1 政策数据分析方法（简化版）\n\n本节采用描述性内容分析与政策工具编码相结合的方法。以政策文本为分析单位，依据预先制定的七类操作性定义，对每条政策进行主分类单选和涉及主题多标签编码。主分类用于计算互斥结构占比，涉及主题用于计算多标签覆盖率。统计仅报告政策供给结构、地区分布和时间分布，不据此推断政策实施效果或因果关系。\n\n统计步骤：\n1. 按发布状态、政策层级和地区确定纳入范围与分母；\n2. 对政策原文中的具体执行措施进行七类编码；\n3. 计算主分类数量及占比；\n4. 计算涉及主题数量及覆盖率；\n5. 比较省份、区域和政策层级差异；\n6. 对征求意见稿、失效政策、重复记录和未核验来源进行单独标记。\n\n主分类占比=某主分类政策数/纳入统计政策总数×100%；主题覆盖率=涉及该主题政策数/纳入统计政策总数×100%。多标签主题覆盖率不要求合计为100%。'''
(outdir/'section-3-3-1-analysis-method.md').write_text(analysis,encoding='utf-8')
print(json.dumps({'records':len(draft),'review':sum(d['confidence']=='review' for d in draft),'output':str(outdir)},ensure_ascii=False))
