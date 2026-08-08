# SoloFirm 第三阶段路线图

> 顺序决定：A 案例分析与技术评估 → B 数据洞察看板 → C AI 与数据联动和收尾。
> 每轮只在其验收门槛满足后进入生产验收；本准备轮不实现、不迁移、不部署。

## 1. 为什么按 A、B、C 排序

现有 Agent session/run/tool/citation/history 已成熟，先扩展结构化研究的风险最小、用户价值最快。Analytics 必须等待 canonical identity、dataVersion 和聚合 API，作为 B 单独治理。C 最后连接两套已稳定边界，避免前端统计数被过早送入模型。

收入、技术趋势和案例业务时间即使到 B 仍可能保持 Red；阶段完成不以“所有 tab 都有图”为目标，而以“不误导且解除条件明确”为目标。

## 2. 第三阶段 A：案例分析与技术评估

### 用户价值

用户可以从空白工作台或真实案例详情发起可引用的单案例分析、2–3 案例比较、技术评估和政策研究，保存研究边界并继续历史会话。

### 前端任务

- 在现有 Assistant 空状态增加综合研究和案例分析入口，保留四个现有 starter。
- 新增 case picker、2–3 case comparison picker、1–3 个受控唯一比较维度和技术评估输入；UI 推荐项必须显式提交，后端不补默认值。
- 保持现有 profile 职责；用首次规范化、session 专用字段持久化并冻结的 `phase3-task-v1 taskContext` 承载案例 IDs、可选 sourceId、比较维度、应用场景、团队能力、计划周期、已有资源和限制；start/detail 可回读，history 只显示摘要。
- 渲染精确 `phase3-structured-result-v1`：闭合六任务 discriminator、taskSelectedEvidence/authorizedEvidence、事实/推断/方法、confidence、missingEvidence、唯一结论引用 `sourceIds`、必填 evidenceVersion 和普通 Phase A 的 null dataVersion；遵守 600 directAnswer、6 ClaimItem、6 citations 和 12000 字符渲染预算。
- 案例详情“AI 分析”和列表勾选比较深链；移动端使用现有互斥 drawer/sheet。
- 保存报告的最小 UI；后端未就绪前不使用 localStorage 假保存。

### 后端依赖与数据迁移

- 为 `/sessions/start` 增加向后兼容 taskContext 和 session version/json/hash。先计算 profile/content/requestedIntent/taskContext 四项身份，再在 start 事务内锁定幂等边界；精确 hit 直接返回原 receipt，不复检当前证据或重复扣费。只有 miss 才锁定并权威复检显式 selected cases/source 及关系：case 失败为 400 `PHASE3_CASE_NOT_ELIGIBLE`，source 失败为 400 `PHASE3_SOURCE_NOT_ELIGIBLE`，整笔回滚且不创建 session/message/Run、不预留 Token；只有通过后才在同一事务投影 taskSelectedEvidence/初始 authorizedEvidence 并保存 receipt。
- source_verification 支持指定 sourceId 与结论搜索两模式；前者在持久化前重读 published+verified/provenance/HTTP(S)/revision，后者不解析正文 ID/URL，不开放任意 URL 抓取。合法受理后的检索不足或执行期证据变化才使用 `evidence_insufficient`。
- 扩展 Agent v2 为 API 文档内 Draft 2020-12 `phase3-structured-result-v1`，完成时按固定字段顺序、稳定数组/link 排序、紧凑 UTF-8 JSON 和 SHA-256 生成 evidenceVersion；不替换 Provider、工具协议、证据 allowlist 或安全边界，也不依赖 Phase B dataVersion。
- v1 不扩展 `compare_cases` 维度集合：从现有六个 allowlist 值中选择 1–3 个；技术评估提供受控 evidence tool/plan。未来 4–6 维需要新契约版本和运行时评审。
- 报告最小实体/表、owner/revision、必填 evidenceVersion、可空 dataVersion、finalMessageId/runId/citation manifest，以及 30 天 purge_after 自动小批量幂等清理 seam。
- 标签 semantic_type 可与 A 并行；未完成时技术输入允许自由文本但明确不进入统计。
- 不要求收入迁移；结果必须把收入 unknown/不可比说清楚。

### API

- 复用 `/api/ai/research/sessions/start` 和 messages/runs/evidence/history；按 API 契约扩展 start/session/history/run 回读，不给 message DTO 增加 taskContext。
- `requestedIntent` 使用现有受控枚举，并与 taskContext.taskType 严格一致；旧客户端省略 taskContext 时兼容。
- 新增报告保存/列表/详情/更新/trash/restore/permanent/export 接口；trash/restore/permanent 的用户请求都在 JSON body 携带正整数 expectedRevision，非法为 400、过期为 409 `REPORT_REVISION_CONFLICT`；旧 `/api/ai/case-analysis` 保持兼容，不作为新结构唯一入口。

### 测试与验收

- 先写 start 幂等 hit/mismatch/in-progress 优先级、首次创建锁内证据复检/并发撤销/零副作用、合法受理后 evidence_insufficient、taskContext 持久化/回读、source_verification、taskSelectedEvidence/authorizedEvidence、六个测试专用 runEvidenceFixture 的实体/link 唯一性、非空 policy-source 链与 evidenceVersion 重算、全局 ClaimItem/引用/文本预算、比较 1–3 维、缺失收入、逐结论 sourceIds、报告 revision CAS 和技术评分契约测试。
- 32 题（单案例/比较/技术/政策）达到 [phase-three-evaluation-plan.md](phase-three-evaluation-plan.md) 硬门。
- 现有历史、分页、幂等、取消、quota、session purge 和证据失效测试不得回归；报告增加到期/未到期/重复/恢复竞争/失败重试清理测试。
- 桌面/手机能从详情发起、返回引用、保存、继续；禁用用户 403。

### 风险与可并行项

- 风险：技术 taxonomy 未就绪导致证据弱；以 missingEvidence 和审核标签候选控制，不让自由文本进入统计。
- 风险：结构结果过长；由 Schema 单字段上限、服务端 ClaimItem 合计 6、EvidenceSection 合计 10、3200 synthesis tokens 和 12000 字符最终渲染硬门控制。更大结果只在新 schemaVersion 评审。
- 可并行：前端用 contract fixtures 做输入/结果组件；后端做 schema/tool；QA 冻结 32 题。

### 明确不做

不做统计图、不做前端总体聚合、不做爬虫/自动入库、不批量猜收入、不迁移框架、不改 Provider 和鉴权。

## 3. 第三阶段 B：数据洞察看板

### 用户价值

用户可以用一致口径查看合格政策趋势、行业/地区分布和数据质量，点击任何可用图表回到真实明细；Red 数据明确不可用原因。

### 前端任务

- 将旧 `/analysis` 演进为数据洞察，移除前端拉全量列表聚合。
- 实现概览、行业、技术、收入、地区与趋势 tabs；不为 Red 能力创建空路由。
- URL 持久化筛选、稳定 loading/empty/partial/error/stale/unavailable 状态。
- 水平条、折线、矩阵/热力表和收入准备状态；每图表格替代、Tooltip、样本/缺失/更新时间。
- 查看数据列表的 cursor 下钻；键盘、44px 触控、reduced-motion 和 320px 响应式。

### 后端依赖与数据迁移

- canonical case/source、标签 semantic_type、政策 applicability 审核、case operation/registration/legacy region role 和业务时间语义。
- analytics dataVersion、共享 eligible scope、聚合查询、缓存/ETag。
- 收入规范化独立推进；未达到门槛时 API 返回 Red/unavailable，而不是阻塞所有 B。
- 生产数据迁移必须另开实施轮，先 precheck/backup/rollback；本路线图不授权执行。

### API

- 实现 [phase-three-api-contract.md](phase-three-api-contract.md) 的 7 个 GET analytics 端点。
- 完成 SecurityConfig permitAll 与 UserAuthInterceptor `/api/analytics/**` 双层接线，合法用户必须能到达 MVC interceptor。
- 统一 filters、sample/missing/eligible/freshness/caveats/drilldown。
- 暂不实现 from-analytics；按钮可显示但仅在 C 开放，避免半链路。

### 测试与验收

- 每个上线指标有 MySQL 8.4 黄金成员集、去重、多标签、时区、空月、筛选和下钻一致性。
- API complete/empty/partial/unavailable/error/ETag/cursor/auth tests 通过；地区 operation/registration 未就绪、legacy partial、真实 empty 三态严格区分。
- 技术端点的 200 unavailable、409 explicit-filter-unavailable、200 empty 三态和 regionRole 组合测试通过。
- 所有 Green/Yellow 图满足指标字典样本门槛；案例总量在 canonical 前不开放。
- p95 overview/ranking <=800ms，趋势/矩阵 <=1200ms；无 N+1。
- 响应式、键盘、表格替代和状态组件验收通过。

### 风险与可并行项

- 风险：重复治理把 105 行改为不同 canonical 数；dataVersion 和审核决策必须先于 KPI。
- 风险：policy industry 全空；保持 Red，不能用自由文本搜索数替代。
- 可并行：后端聚合/版本、数据审核、前端 fixtures/状态/可访问图表、QA 黄金 seed。

### 明确不做

不做地图（除非可靠几何/语义后另评审）、3D/仪表盘、实时流式大屏、前端总体计算、跨币种换汇、自动文本技术分类入正式图。

## 4. 第三阶段 C：AI 与数据联动和产品收尾

### 用户价值

用户从图表或筛选结果直接发起证据约束研究，能在研究与数据视图间往返，并把带版本和引用的结果保存、导出和反馈。

### 前端任务

- 每张可用图的“基于当前数据研究”预览/确认 sheet。
- 只提交 metric/filter/bucket/date/version/question/idempotency；不提交数值、正文、引用、SQL/URL/IDs。
- Assistant 显示 analytics context chip、返回图表/明细链接和 stale version 提示。
- 已保存报告视图、Markdown/HTML/PDF 导出、用户反馈入口。
- 性能：按路由拆分图表、缓存复用、避免重复请求；Token/延迟反馈保持简洁。
- 管理端只增加聚合质量、延迟、错误、Token 指标，不显示隐藏推理或完整私有问题。

### 后端依赖与数据迁移

- analytics snapshot 表/存储、dataVersion 重建、授权 ID 集合。
- from-analytics 原子创建 snapshot/session/message/run；接入现有 quota/idempotency/citation。
- 报告完整生命周期和导出；沿用 Phase A evidenceVersion/自动清理，Analytics 报告才增加 dataVersion；历史版本与当前 evidence availability。
- 反馈表只保存 bounded rating/reason/runId；服务端计算 feedbackEligible（completed，或有持久化可见结果的 evidence_insufficient），其余状态拒绝；隐私和保留期评审。
- 通常不新增业务数据迁移；如需 snapshot/report/feedback 表必须走独立可回滚迁移。

### API

- `POST /api/ai/research/from-analytics`。
- 报告 list/detail/update/delete/export。
- `PUT/GET /api/ai/research/runs/{runId}/feedback` 与 `GET /api/admin/ai/research/quality`；使用已冻结的 rating/reason/revision、权限和脱敏契约。

### 测试与验收

- 40 题完整评测达到所有硬门；8 个 analytics 题覆盖 tamper、stale、empty、partial。
- 服务端证明重建快照，不信任前端 aggregate/citations/SQL/URL/IDs。
- 图表值、snapshot evidence、tool allowlist、citations、报告 manifest 可逐层追踪。
- 保存/导出 owner boundary、证据失效、evidenceVersion、可选旧 dataVersion、幂等和自动清理测试通过。
- p95 首次可见进度 <=2s、完整 <=90s、Token p95 <=28k；缓存/SLA 达标。
- 用户反馈可关联版本且不含账户秘密，管理端质量观察只用必要聚合。

### 风险与可并行项

- 风险：dataVersion 在用户确认后变化；明确 409 刷新，不自动换数据。
- 风险：报告导出泄漏 profile；导出预览和字段 allowlist 默认最小化。
- 风险：图表筛选导致工具证据过多；snapshot top-N/ID cap 与 Agent 工具上限协同，不能客户端截断后谎称全量。
- 可并行：snapshot/AI 后端、报告/导出后端、联动 UI、全 40 题评测、性能/可访问性收尾。

### 明确不做

不实现后台爬取、AI 自动审核发布、任意 SQL Agent、任意 URL 抓取、通用聊天插件市场、模型 Provider 迁移或生产安全边界放宽。

## 5. 部分上线与最终完成

- A：当前只完成 v1 规格冻结；实现完成还要求研究结构、引用、保存/继续、CAS 生命周期和 32 题质量成立。
- B partial production release：真实聚合、dataVersion、下钻和部分 Green/Yellow 图成立，Red 保持诚实。此状态允许部分生产上线，不等于第三阶段产品完成。
- C：安全 snapshot 联动、报告导出、40 题、性能和反馈成立。

`Phase Three product complete` 必须额外同时满足：行业统计和下钻；technology taxonomy 达到指标字典最小样本且至少一个正式技术分布/采用图；收入规范化、至少 30 个同币种/周期/type 可比案例、覆盖率至少 40%、至少一个收入图通过黄金测试；图表到 AI；报告保存/导出/恢复/永久删除；用户反馈与管理员脱敏聚合；完整 40 题及性能门槛。

若技术或收入数据门槛未达到，只能称“前端实现完成”“Phase B partial production release”或“部分上线”，不得关闭第三阶段产品。

任何阶段的 commit、push、工作区干净或部署都不是功能完成的替代条件；生产切换必须在该阶段另行授权和验收。
