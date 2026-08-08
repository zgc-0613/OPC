# SoloFirm 第三阶段后端交接清单

> 交接基线：2026-07-29
> API 以 [phase-three-api-contract.md](phase-three-api-contract.md) 为准，指标以 [analytics-metric-dictionary.md](analytics-metric-dictionary.md) 为准。

## 1. 责任矩阵

| 事项 | 负责方 | 输入 | 输出 | 依赖 | 验收标准 | 阻塞前端 | 契约夹具并行 |
|---|---|---|---|---|---|---|---|
| Canonical 案例身份与合并 | 数据后端 + 内容审核 | 23 个精确重复候选组、case/source 字段 | canonical_case_id、merge/audit 记录、保留/版本决策 | 人工判定规则 | 105 合格行每行有 canonical 归属；42 条候选无静默删除；聚合去重黄金测试通过 | 是：案例总量/分布 | 是；前端用固定 canonical IDs 测试 |
| Canonical 来源身份 | 数据后端 + 内容审核 | 121 合格来源、1 组同 URL | canonical_source_id 或明确“不合并”审计 | publisher/URL 规范化 | URL 变体、同 URL 不同发布者规则有测试；引用仍指原 sourceId | 是：来源总量 | 是 |
| 标签语义类型 | 数据后端 + 产品/审核 | tags、is_industry、case_tags、aliases | semantic_type、parent_tag_id、状态/修订 | 兼容现有 is_industry | 现有行业不丢；technology/topic/other 可区分；旧 API 仍可读 | 是：技术与行业 | 是 |
| 主/辅行业和技术关系 | 数据后端 + 审核 | case_tags、policy relations | relation role、审核状态、唯一主行业约束 | 标签语义类型 | 每案例最多一个 primary industry；多关系计数黄金测试通过 | 是 | 是 |
| AI 标签候选治理 | AI 后端 + 审核 | 候选名、证据、理由、sourceId | pending/approved/rejected 审核流 | 标签/alias 规则 | AI 不能直接发布；pending 不进 analytics；批准有审计人和时间 | 否，可先隐藏入口 | 是 |
| 政策适用性回填 | 内容审核 + 数据后端 | 57 个合格 unclassified 政策 | general/specific + industry/technology relations | 标签体系 | 每条审核；specific 至少一关系；unclassified 不进入行业政策数 | 是：相关政策 | 是 |
| 案例业务时间 | 内容审核 + 数据后端 | 原来源发布日期/案例发生时间 | business_published_at/occurred_at + basis/source | provenance | 不用 created/accessed 回填；unknown 保留；时区规则通过 | 是：案例趋势 | 是 |
| 地区语义 | 产品 + 数据后端 | case/policy region_id | case operation/registration relations + primary/secondary、policy applicability region、legacy mapping | 现有 regions 层级 | 未就绪 operation/registration=Red unavailable；legacy=Yellow partial“相关地区分布”；已就绪无记录=empty；country 政策不复制到省 | 是：强结论地区图 | 是 |
| 收入规范化 | 产品 + 数据后端 + 审核 | 历史原文候选、来源 | min/max/currency/period/type/value_status/as_of/source | canonical case、证据审核 | actual/estimated/unknown/withheld 唯一状态；不由 LLM 自动批准；分桶和 Type 7 黄金测试通过 | 是：全部收入图 | 是，但 UI 必须标 synthetic fixture |
| 数据版本 | 平台后端 | 审核水位、evidence revision、taxonomy/merge/revenue revision | immutable dataVersion、snapshot metadata | 上述数据治理 | 同版本可复现；任何资格/归并变化产生新版本；旧 Run 可读 | 是：全部 analytics/AI 联动 | 是 |
| 聚合查询与存储 | Analytics 后端 | 指标字典、真实 schema | overview/industry/technology/revenue/region/trend SQL/Mapper/service | canonical IDs、版本 | SQL 黄金集、EXPLAIN、下钻集合一致、SLA 达标 | 是 | 是 |
| 缓存与 ETag | 平台后端 | normalized filters + dataVersion | 私有缓存、强 ETag、失效机制 | 聚合与版本 | 304、Vary/Auth、审核后失效、无跨用户泄漏 | 是：性能 | 是 |
| Analytics 鉴权接线 | 平台后端 | SecurityConfig、AiWebMvcConfig、UserAuthInterceptor | Security permitAll + MVC 用户拦截器覆盖 `/api/analytics/**` | 现有用户会话 | 合法用户到达 interceptor；匿名/过期 401、disabled 403、管理员 token 不可替代；无 Basic/默认登录页 | 是 | 是 |
| Analytics API | Analytics 后端 | API contract | 7 个 GET 端点及错误码 | 聚合、鉴权、版本 | OpenAPI/contract tests 全通过；technology unavailable/409/empty、regionRole、partial/stale 正确 | 是 | 是，前端可完全并行 |
| 技术评估工具 | AI 后端 + 产品 | 技术/场景/预算/能力/约束 | 专用受控工具或 evidence plan、结构化评分 | 技术 taxonomy、案例/政策工具 | 每评分有依据、confidence、missing evidence；无单独伪精确总分 | 是：Phase A 技术评估 | 是 |
| Phase A taskContext | AI 后端 | AgentSessionStartDTO、requestedIntent、phase3-task-v1 | session 专用 version/json/hash、start/detail/history/run 回读、taskSelectedEvidence 投影 | 现有 session/run/tool allowlist | 显式 case 先验失败=400 PHASE3_CASE_NOT_ELIGIBLE 且不创建 session/message/Run、不预留 Token；通过后才原子保存 | 是：Phase A | 是 |
| 来源核验输入 | AI 后端 | source_verification、可选 sourceId、非空 content | selected_source 或 claim_search 模式 | 现有 search/get_source/allowlist | 显式 source 先验失败=400 PHASE3_SOURCE_NOT_ELIGIBLE 且不创建 session/message/Run、不预留 Token；无任意抓取 | 是：Phase A 来源核验 | 是 |
| Phase A 证据授权与版本 | AI 后端 | taskSelectedEvidence、工具结果、Run 授权 case/policy/source IDs、revision/hash、eligibility、schemaVersion | 服务端 authorizedEvidence + 必填 immutable evidenceVersion | taskContext、现有依赖 allowlist、structuredResult | taskSelected 精确回显；authorized 上限不变；按固定字段顺序/排序/紧凑 UTF-8 JSON 独立重算；普通 A 不依赖 dataVersion | 是：Phase A | 是 |
| 六任务结构结果 | AI 后端 | API 中 Draft 2020-12 schema、现有 Agent v2 | phase3-structured-result-v1 Java DTO/validator | 现有 run/tool/citation | 预算不变；六个 runEvidenceFixture 的来源链、citation、授权和 evidenceVersion 全部可复算；旧/未知版本安全回退 | 是：Phase A | 是 |
| 图表研究快照 | AI + Analytics 后端 | metricId、filters、buckets、dataVersion | analyticsSnapshotId、授权实体集 | 数据版本、聚合 | 服务端重建；拒绝数值/SQL/URL/任意 IDs；快照绑定 Run | 是：Phase C | 是 |
| 报告保存与生命周期 | AI 后端 | owned completed session/run/message、用户 JSON body expectedRevision | active/trash/permanently_purged、revision、purge_after、evidenceVersion、可空 dataVersion、citation manifest | session/history、Run evidence | 三个用户状态变更统一 CAS；非法 revision 400、过期 409；30 天带锁自动清理；全文擦除；session purge 后报告独立可读 | 是：保存报告 | 是 |
| 报告导出 | AI 后端 + 平台 | active reportId、format | Markdown/HTML/PDF 或异步 job | 报告保存 | 内容来自持久化结果；含版本/引用/限制；永久删除清缓存；无账户 PII | 是：导出 | 是 |
| 用户反馈与质量聚合 | AI 后端 + 管理端 | feedbackEligible owned runId、rating/reason/comment/revision | 用户 upsert 反馈、管理员脱敏聚合 | user/admin auth、run ownership、可见 Assistant 结果 | completed 可反馈；evidence_insufficient 仅有持久化可见结果时可反馈；其余状态 false；管理员不能代交 | 是：Phase C 完成 | 是 |
| 审核进入统计时机 | 数据后端 | review transaction | eligibility event/watermark | dataVersion | 业务和来源双 verified 后才进入；任一失效即退出；原子可测 | 是 | 是 |
| 自动采集 provenance | 后端智能体项目 | URL、发布者、采集时间、content hash | source candidate + lineage | 不在本仓库实现爬虫 | 未审核只进候选；重复抓取可识别；原 URL 可追溯 | 否，存量可先做 | 否，由其项目提供 |
| 重复合并规则 | 数据后端 + 审核 | URL/hash/title/人工判断 | merge decision、canonical revision | provenance | 同源重复不重计；不同案例不因同文章误合；可撤销/审计 | 是 | 是 |
| 历史版本处理 | 平台/AI 后端 | evidenceVersion、可选 dataVersion、evidence revisions | 历史研究/报告版本与 current availability | Run、Analytics 快照、报告 | A evidenceVersion 必有；仅 Analytics 有 dataVersion；不重写旧结论或伪造旧原文 | 是：Phase A/C | 是 |
| API SLA/观察性 | 平台后端 | endpoint/runtime telemetry | latency、cache、error、refresh metrics | analytics API | 达到 p95；日志无 filters 中的敏感自由文本；requestId 可追踪 | 否但为上线门 | 是 |
| 测试环境和种子 | QA + 后端 | 指标边界案例 | MySQL Testcontainers seed、contract fixtures | 所有 schema | 覆盖重复、多标签、时区、收入、版本、空/partial/error | 是：自动化 | 是 |

## 2. 最小字段变更规格

本轮不执行迁移。后端设计评审至少包含以下前向兼容字段或等价规范化表：

### 2.1 身份与版本

- `case_items.canonical_case_id` 或 `case_canonical_members(case_id,canonical_case_id,decision,reviewed_by,revision)`。
- `sources.canonical_source_id` 或成员表；原 sourceId 永久保留用于引用。
- `analytics_data_versions(version,eligibility_watermark,taxonomy_revision,merge_revision,revenue_revision,created_at)`。
- merge 不能通过物理删除替代；历史 run 需要解析旧成员。

### 2.2 Phase A 会话任务与证据版本

本轮不执行迁移；下一轮唯一推荐模型是：

- `ai_agent_sessions.task_context_version VARCHAR(40) NULL`
- `ai_agent_sessions.task_context_json JSON NULL`
- `ai_agent_sessions.task_context_hash CHAR(64) NULL`

start 的实现顺序必须与 API 契约一致。事务外只做无副作用工作：验证 active 用户和请求字段/长度/任务交叉约束，规范化 profile/content/requestedIntent/taskContext，并计算 `profileHash,contentHash,requestedIntent,taskContextHash` 四项幂等身份。随后开启 start 事务，先锁 `platform_users.assistant_history_revision` 用户 guard row，再按 `userId + idempotencyKey` 对已存在的 `ai_analysis_runs` 幂等行执行 `SELECT ... FOR UPDATE`；用户 guard row 负责序列化 key 尚不存在时的并发首次创建。

锁内先处理幂等命中：四项身份相同且已有成功 receipt 时直接返回原 202 receipt，不重新检查当前证据资格，不创建 session/message/Run，不投影证据，不重复预留或扣除 Token；四项任一不同返回 409 `PHASE3_IDEMPOTENCY_CONFLICT`，不检查证据且零新副作用；原请求仍在处理中时等待持锁事务或走现有受控 `in-progress` 语义，不能第二次创建。首次成功后证据失效的精确重放仍返回原 receipt，既有 Run 由运行期复检决定状态。

只有幂等记录不存在才进入首次创建。仍在同一 start 事务内，以 `SELECT ... FOR UPDATE` 或等价行锁锁定显式案例、来源和必需 case-source/policy-source 关系，重新读取并验证存在性、用户可用性、published、verified、evidenceRevision、title/publisher provenance、HTTP(S) URL 和 link。case 失败返回 400 `PHASE3_CASE_NOT_ELIGIBLE`，source 失败返回 400 `PHASE3_SOURCE_NOT_ELIGIBLE`，整个事务回滚且无 session/message/Run、Token 或证据副作用。全部通过后才在同一事务内创建 session/message/Run、保存 canonical `phase3-task-v1` 三字段、投影 taskSelectedEvidence/初始 authorizedEvidence、预留 Token 并保存成功 receipt；任一步失败全部回滚。

并发撤销也按同一锁顺序：证据锁前撤销使首次请求锁内复检返回 400；start 持锁后撤销则管理操作等待提交，该请求属于合法受理，运行期失效可以进入 `evidence_insufficient`。同 key 不同身份始终 409；并发相同请求只创建一次。现有可变 `research_context_json` 不可复用。消息 DTO 不增加 taskContext，session update 不可修改；permanent purge 同时清 version/json/hash。日志只允许 taskType/hash/长度等元数据，不允许 applicationScenario、teamCapabilities、existingResources、constraints 全文。

start receipt 和 owned session detail 回读规范化 taskContext；history/list 只返回 taskType/必要摘要；Run detail 通过 session 返回 taskContextHash/taskType，不保存 taskContext 副本。旧 session 返回 null。source_verification 的可选 positive long sourceId 只用于指定来源模式；为空时 content 是待核验结论，后端不得解析正文 ID/URL 授权。

Phase A 结果新增独立 evidenceVersion。唯一输入按 `schemaVersion,cases,policies,sources,caseSourceLinks,policySourceLinks` 排序；实体项只含 `id,evidenceRevision,contentHash,eligibility`，数组和 links 按 API 契约稳定排序。对象字段使用规定顺序，以无 BOM/缩进/尾随空白/结尾换行的紧凑 UTF-8 JSON 序列化，整数不得写成浮点或 null，再对字节执行 SHA-256。当前 `ai_analysis_runs.evidence_hash` 是入队身份占位且完成时不更新，不得直接复用。Phase A 唯一权威存储固定为 `ai_analysis_runs.result_json.structuredResult.evidenceVersion`，Run detail 从该结果返回，报告保存时复制到报告字段；本轮不要求再新增一个可漂移 Run 列。普通 Phase A 的 dataVersion 为 null，Phase B/C Analytics 才生成 dataVersion。

结果证据字段只有两层。`taskSelectedEvidence` 由 canonical taskContext 投影，caseIds <=3、policyIds 恒空、sourceIds <=1；除 case_analysis、case_comparison 和 selected-source 核验外均为空。`authorizedEvidence` 由服务端在 Run 内从通过状态/权限/来源链验证的工具结果累积，前端和模型均不能写入。现有设置校验允许 `agentMaxToolCalls <=12`，两个 search 工具各 `limit<=10`，dependent tools 不扩展 ID，因此 authorizedEvidence.caseIds/policyIds/sourceIds 各 <=120，且 caseIds+policyIds<=120。这是兼容上界，不是目标返回量；展示数组仍 <=6。

DTO/validator 必须在 Schema 后执行服务语义校验：comparisonDimensions 只在比较任务出现且为 1–3 个；所有 ClaimItem（顶层、EvidenceSection、技术 rationale、一般研究 section）递归合计 <=6；EvidenceSection 对象 <=10；sourceIds/citation/supportingCases/relatedPolicies 全为 authorizedEvidence 子集；单案例 section fact 的全部来源必须链接该 caseId，比较维度 fact 必须为每个比较 caseId 覆盖至少一条相连授权来源，policy fact 必须引用与结果 policyId 建链的来源；evidenceCoverage 重算；固定 renderer 结果 <=12000。任何失败都不得写最终 Assistant message。

API 文档外层 `runEvidenceFixture` 仅供契约测试，禁止加入生产 DTO、请求或持久化结果。测试 validator 必须在排序/哈希前拒绝 cases、policies、sources 各自重复 ID 和两类重复 link pair，不得静默去重；不同 entityType 可复用数字 ID，非重复多对多关系合法。随后验证 link 引用完整性、每个授权 case/policy 的合法来源链、selected source、authorizedEvidence 精确集合、citation metadata 和 evidenceVersion。policy_lookup 固定使用非空 `policy 2001 -> source 9004 -> fact -> citation` 正向链。只有合法受理后的运行期检索不足或证据变化可进入 `evidence_insufficient`；首次锁内无效显式 ID 永远是上述 400 零副作用响应。

### 2.3 标签

- `tags.semantic_type ENUM/string: industry|technology|topic|other`，并保持 `is_industry` 兼容期。
- `tags.parent_tag_id` 可空；层级必须防循环。
- 关系表增加 `role=primary|secondary`、`review_status` 和 evidence/source 信息；或者建立新的 typed relation 表。
- alias 唯一性按规范化字符串和 semantic scope；别名永不作为稳定 bucketId。

### 2.4 收入

- `revenue_min/revenue_max DECIMAL`，min <= max 且非负；单值可 min=max。
- `currency CHAR(3)`、`revenue_period`、`revenue_type`、`value_status=actual|estimated|unknown|withheld`。
- 不增加与 value_status 重复的 `revenue_is_estimated`；unknown/withheld 不保存伪金额。
- `revenue_as_of_date`、`revenue_source_id`、审核状态/修订。
- 不把 currency 默认成 CNY，不把单位默认成元，不从空值推 0。

更推荐独立 `case_revenues` 版本表而不是在 case_items 上覆盖一个当前值，因为同一案例可能有多个周期/类型/日期。表必须支持“同一可比组选择哪个当前值”的确定规则。

约束：actual/estimated 必须有 min/max、currency、period、type、as_of_date、source_id，且 `0 <= min <= max`；unknown/withheld 的 min/max 必须为 null。estimated 只进独立系列，不能与 actual 合并。跨 bins 区间持久化原 min/max，由聚合层映射 spans_multiple_bins。

### 2.5 地区关系

- `case_items.region_id` 在兼容期只映射 `legacy_related_region`，不得批量改名为经营地。
- 建议 `case_regions(case_id,region_id,region_role,relation_role,review_status,source_id,revision)`；region_role 为 operation/registration，operation 的 relation_role 为 primary/secondary。
- 每个案例最多一个已审核 primary operation region；默认地区聚合、覆盖率和收入地区比较只计该 primary。可有多个 secondary operation region，但它们仅进入显式足迹明细或未来专门指标；注册地不参与经营地区默认统计。
- 政策 `region_id` 明确定义为 policy_applicability；country 级政策保持 country，不展开复制到省。
- 图表、下钻、snapshot 和 AI filters 都保存 regionRole；收入地区聚合只接受 operation。
- API 状态不能由 data 长度猜测：operation/registration 未就绪为 200/unavailable/Red + `CASE_REGION_ROLE_NOT_READY`；显式 legacy 为 200/partial/Yellow + `LEGACY_REGION_SEMANTICS`；规范化角色已就绪而筛选无记录才为 200/empty/sampleSize=0。quality.region_completeness 的 0% Green 只代表质量事实。

### 2.6 报告清理作业

- `purge_after` 必须有调度消费者；默认 trash 后 30 天，小批量处理，批次和重试策略可配置但不得跳过到期判断。
- 多实例用 `FOR UPDATE SKIP LOCKED`、数据库锁或等价带过期租约；选择条件包含 `status=trash AND purge_after<=now`。
- 用户 trash/restore/permanent 统一从 JSON body 读取正整数 expectedRevision：缺失/null/零/负数/浮点/字符串返回 400，过期返回 409 `REPORT_REVISION_CONFLICT`；转换成功 revision+1。目标状态 + 当前 revision 无变更返回，旧 revision 重放仍为 409。
- 后台 purge 不接收客户端 body，在锁内读取当前 revision 后执行 status + revision compare-and-set；purge 先完成后不能恢复，restore 先完成则清空 purge_after。客户端网络超时后必须 GET 最新 revision 再决定是否重试。
- 永久清除 result 正文、notes、导出缓存/文件和其他可恢复内容，只留最小非内容审计；日志不含正文、引用全文或用户自由文本。
- 到期、未到期、重复执行、两个 worker 竞争、restore 竞争、部分失败重试均为 Testcontainers seam。

## 3. 聚合实现要求

1. 建立一个共享 `EligibleEvidenceScope` SQL/Mapper 片段或数据库 view 等价物，避免七个端点各写不同的 published/verified 条件。
2. 所有聚合先确定 canonical eligible ID 集，再连接多标签；不能在 join 后用行数当案例数。
3. 时间边界使用半开区间，Asia/Shanghai；MySQL session/config 与 Java time 明确一致。
4. 多标签 OR/AND 行为由 filter contract 固定，SQL 黄金测试覆盖。
5. sampleSize、missingCount、totalEligible 和下钻集合来自同一个查询计划/版本。
6. top-N 的 other bucket 保存可下钻 ID 条件，不能丢失长尾。
7. 收入分桶使用单值、单 bucket 区间、spans_multiple_bins 三类；P25/P50/P75 仅 actual point values 并固定 Hyndman-Fan Type 7。算法、bins 和 outlier policy 进入 metricVersion；不在 Java/SQL 两处实现不同算法。
8. 每个新 SQL 提供 MySQL 8.4 Testcontainers 结果和生产相似数据量 EXPLAIN；不在生产执行写入式预计算迁移作为本轮验证。

## 4. 契约夹具

前端可以在后端实现前使用版本控制的 JSON contract fixtures，但必须满足：

- 路径建议 `docs/fixtures/phase-three-api/` 或测试专用目录，显式字段 `fixture=true`、`contractVersion`。
- 数字是边界测试值，不标为真实 SoloFirm 业务数据；生产构建和运行时不能 import fixtures。
- 至少含 complete、empty、partial、unavailable、stale-version、invalid-filter、cursor-next-page、multi-label、missing-income、disabled-user、analytics-auth-wiring、phase3-task-context、task-selected-vs-authorized-evidence、technology-filter-409、legacy-region-role、report-trash/restore/permanent-CAS、feedback-upsert。
- fixture 的 metricId/字段/错误码由后端 contract test 反序列化，防止前后端漂移。
- 真实生产页面永远调用 API；fixture 只用于 Vitest/component tests 和本地 story harness。

## 5. 阶段门槛

- Phase A 实现阻塞：session taskContext 三字段与回读、source_verification 两模式、taskSelectedEvidence/authorizedEvidence、evidenceVersion、精确 phase3 structuredResult Schema+语义 validator、技术评估工具和报告最小保存/CAS/到期清理；不依赖 Analytics dataVersion，可与 UI 通过夹具并行。这里列的是尚未实现的交付，不影响 v1 规格冻结。
- Phase B partial production release 阻塞：canonical case、标签语义、regionRole、dataVersion、鉴权接线和 Green/Yellow 聚合 API。收入和技术 Red 状态不阻塞“部分上线”，但阻塞第三阶段产品完成。
- Phase C 阻塞：analytics snapshot、from-analytics、报告完整生命周期/导出、用户反馈、管理员质量聚合和版本回跳。
- Phase Three product complete 额外要求：technology taxonomy 达标并至少一个正式技术图；收入规范化、同口径 n>=30、覆盖率>=40% 且至少一个正式收入图；完整 40 题与性能门槛通过。

实施顺序和每轮不做内容见 [phase-three-roadmap.md](phase-three-roadmap.md)，质量门槛见 [phase-three-evaluation-plan.md](phase-three-evaluation-plan.md)。
