from pathlib import Path

def q(v):
    if v is None: return 'NULL'
    return "'" + str(v).replace("'", "''") + "'"

policy = ('关于组织开展雄安新区2026年第一批OPC孵化社区集中认定工作的通知','https://www.xiongan.gov.cn/20260822/7027a041b1d04571b16a23c617ff583c/c.html','河北省','雄安新区工信科技数据局','为加快培育人工智能OPC创新生态，完善雄安新区新型创业孵化载体体系，集聚算力、模型、场景等资源，支持OPC超级个体创业发展，雄安新区组织开展2026年第一批OPC孵化社区集中认定。申报受理截至2026年8月31日，认定实行常态化申报、分批认定。')
cases = [
 ('全球大学生OPC创业大赛决赛报道','全球大学生OPC创业大赛','https://hnxjxq.rednet.cn/content/646042/68/16201808.html','湖南省','长沙市','创业支撑','活动赛事','全球大学生OPC创业大赛','534支团队报名，317支提交申报材料，50支晋级线下决赛；奖励池包括50万元现金奖金、100万元算力资源券和50万元AI合规体检券。'),
 ('“超级个体”的闯关之路——来自江苏第一批OPC创业载体的观察（上）','赤峰智能·飒露紫智能体','https://jsnews.jschina.com.cn/jsyw/202608/t20260823_s6a8a4b10e4b027d1d0a3a13d.shtml','江苏省','苏州市','产业应用','工业能源','赤峰智能（苏州）','曾获得车企30万元软件采购订单；入驻青创港后通过资源对接重新连接客户。'),
 ('中关村软件园孵化器：首期OPC实训营孵化32个创业项目','中关村软件园·OPC实训营','https://cn.chinadaily.com.cn/a/202608/06/WS6a744ec7a310d709c2fc1f0d.html','北京市','北京市','创业支撑','活动赛事','中关村软件园孵化器','33名学员、10天实训，产出32个创业项目；综合能力测评均分由2.68提升至3.83，提升43%。'),
 ('2026年“息壤杯”全国人工智能OPC创新大赛泰州赛区选拔赛','息壤杯·泰州赛区','https://m.sohu.com/a/1058490974_121106832/','江苏省','泰州市','创业支撑','活动赛事','息壤杯泰州赛区选拔赛','每支队伍获得基础Token额度1000元；最高奖励为2000元现金加1000元算力券。'),
]
cases = cases[:2]
out=Path('outputs/policy-case-update-20260824'); out.mkdir(parents=True,exist_ok=True)
L=['SET NAMES utf8mb4;','USE opc_platform;','CREATE TABLE IF NOT EXISTS backup_sources_update_20260824 AS SELECT * FROM sources;','CREATE TABLE IF NOT EXISTS backup_policies_update_20260824 AS SELECT * FROM policies;','CREATE TABLE IF NOT EXISTS backup_case_items_update_20260824 AS SELECT * FROM case_items;','START TRANSACTION;']
pt,pu,pr,pi,ps=policy
L += ["INSERT INTO sources(title,source_type,publisher,url,accessed_at,notes,status,ai_evidence_status,evidence_revision) SELECT "+q(pt)+",'government_site',"+q(pr)+","+q(pu)+",'2026-08-24',"+q('import_batch=policy_case_update_20260824')+",'published','legacy_unverified',0 WHERE NOT EXISTS(SELECT 1 FROM sources WHERE url="+q(pu)+");","SET @s=(SELECT id FROM sources WHERE url="+q(pu)+" LIMIT 1);","INSERT INTO policies(title,region_id,issuing_body,publish_date,source_id,policy_level,policy_type,applicability_mode,summary,original_url,accessed_at,status,reviewer,ai_evidence_status,evidence_revision) SELECT "+q(pt)+",(SELECT id FROM regions WHERE name='河北省' AND level='province' LIMIT 1),"+q(pr)+",'2026-08-22',@s,'district','comprehensive','general',"+q(ps)+","+q(pu)+",'2026-08-24','draft','codex-update','legacy_unverified',0 WHERE NOT EXISTS(SELECT 1 FROM policies WHERE original_url="+q(pu)+");","UPDATE sources SET ai_evidence_status='verified',evidence_revision=evidence_revision+1 WHERE id=@s;","UPDATE policies SET ai_evidence_status='verified',evidence_revision=evidence_revision+1 WHERE original_url="+q(pu)+";"]
for title,name,url,prov,city,cat,sub,actor,outcome in cases:
    L += ["INSERT INTO sources(title,source_type,publisher,url,accessed_at,notes,status,ai_evidence_status,evidence_revision) SELECT "+q(title)+",'news',"+q(actor)+","+q(url)+",'2026-08-24',"+q('import_batch=policy_case_update_20260824')+",'published','legacy_unverified',0 WHERE NOT EXISTS(SELECT 1 FROM sources WHERE url="+q(url)+");","SET @s=(SELECT id FROM sources WHERE url="+q(url)+" LIMIT 1);","INSERT INTO case_items(title,article_title,region_id,category,subcategory,actor_name,source_id,summary,ai_tools,outcome,tags,original_url,accessed_at,status,reviewer) SELECT "+q(name)+","+q(title)+",(SELECT id FROM regions WHERE name="+q(prov)+" AND level='province' LIMIT 1),"+q(cat)+","+q(sub)+","+q(actor)+",@s,"+q(outcome)+",'原文明确的AI工具或能力',"+q(outcome)+","+q(cat+','+sub)+","+q(url)+",'2026-08-24','published','codex-update' WHERE NOT EXISTS(SELECT 1 FROM case_items WHERE original_url="+q(url)+");","UPDATE sources SET ai_evidence_status='verified',evidence_revision=evidence_revision+1 WHERE id=@s;"]
    if 'rednet' in url:
        L += ["UPDATE case_items SET summary='全球大学生OPC创业大赛决赛在长沙举行，赛事面向全球青年科创创业者，设置AI+工业制造、AI+安全可信、AI+具身智能、AI+健康与生命科学、AI+新能源物流、AI+文创内容六大赛道。报道显示共有534支团队报名、317支团队提交申报材料，50支团队晋级线下决赛，并通过路演答辩评选获奖项目。赛事为参赛项目提供奖金、算力资源券、AI合规体检券及创业孵化、投融资对接等支持。',ai_tools='AI+工业制造、具身智能、健康与生命科学等应用赛道',outcome="+q(outcome)+" WHERE original_url="+q(url)+";"]
    else:
        L += ["UPDATE case_items SET summary='赤峰智能工业（苏州）有限公司技术团队在苏州独墅湖青创港打磨工业智能代码，企业自研“飒露紫”智能体，面向工业智能化赛道开展业务。报道提到，企业曾获得车企30万元软件采购订单，后经历订单中断；入驻青创港后，借助苏州市人工智能协会的资源对接，重新连接多家客户，推动业务恢复。',ai_tools='“飒露紫”智能体；工业智能化代码',outcome="+q(outcome)+" WHERE original_url="+q(url)+";"]
L += ['COMMIT;','SELECT COUNT(*) AS policy_added FROM policies WHERE original_url='+q(pu)+';','SELECT COUNT(*) AS cases_added FROM case_items WHERE original_url IN ('+','.join(q(c[2]) for c in cases)+');']
(out/'update.sql').write_text('\n'.join(L),encoding='utf8')
(out/'rollback.sql').write_text('SET NAMES utf8mb4; USE opc_platform; START TRANSACTION; DELETE FROM case_items WHERE original_url IN ('+','.join(q(c[2]) for c in cases)+'); DELETE FROM policies WHERE original_url='+q(pu)+'; DELETE FROM sources WHERE notes LIKE '+q('%import_batch=policy_case_update_20260824%')+' AND NOT EXISTS(SELECT 1 FROM policies p WHERE p.source_id=sources.id) AND NOT EXISTS(SELECT 1 FROM case_items c WHERE c.source_id=sources.id); COMMIT;',encoding='utf8')
print(out/'update.sql')
