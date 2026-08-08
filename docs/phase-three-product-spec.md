# SoloFirm 用户端智能决策工作台产品规格

> 产品版本：Phase Three
> 决策日：2026-07-29
> 数据口径以 [analytics-metric-dictionary.md](analytics-metric-dictionary.md) 为准，事实与缺口以 [phase-three-readiness-audit.md](phase-three-readiness-audit.md) 为准。

## 1. 产品定义

SoloFirm 第三阶段是“用户端智能决策工作台”：用户先用真实指标发现问题，下钻到案例、政策和来源，再让智能体在当前筛选条件、用户创业画像和授权证据内完成研究。它不是通用聊天机器人，也不是只展示数字的数据大屏。

成功标准是：每个重要结论能回到证据，每张图能回到筛选后的明细，研究可以保存、继续和导出，缺失或冲突数据被诚实表达。

## 2. 信息架构

保留现有独立 `/assistant` 工作区和 AssistantLayout，不另建通用聊天模板。第三阶段只新增有真实能力支撑的入口：

| 一级入口 | 形态 | 内容 |
|---|---|---|
| AI 研究助手 | `/assistant` 主工作区 | 综合创业研究、案例分析、案例比较、技术评估、政策研究、来源核验 |
| 数据洞察 | 一个新路由，内部 tabs | 概览、行业、技术、收入、地区与趋势；Red tab 显示数据准备状态而非假图 |
| 研究历史 | 复用 Assistant 现有侧栏 | active/archive/trash、搜索、继续研究 |
| 已保存报告 | 数据洞察/Assistant 内的报告视图 | 有报告 API 后开放；Phase A 可先作为同页 tab，不创建空路由 |

旧 `/analysis` 的布局和入口可演进为“数据洞察”，但其前端聚合逻辑必须停用；正式数值只能来自 analytics API。

## 3. AI 研究助手任务入口

空白状态提供六个任务入口。现有四个 starter 直接复用；新增“综合创业研究”和“案例分析”。结构化 Phase A 入口同时写入受控 `requestedIntent` 和同值 `taskContext.taskType`；用户编辑研究问题只改变 `content`，不得把 `requestedIntent` 回落为 `auto`。只有未提交 `taskContext` 的旧版 prompt-only starter 继续沿用“修改预置文案后回落 `auto`”的兼容规则。

| 入口 | requestedIntent | 初始必填 |
|---|---|---|
| 综合创业研究 | `general_research` | 地区、行业、阶段、目标、研究问题 |
| 案例分析 | `case_analysis` | 1 个授权 caseId、非空研究问题（UI 可预填且可编辑） |
| 案例比较 | `case_comparison` | 2–3 个授权 caseId、比较维度 |
| 技术评估 | `technology_assessment` | 技术/标签、场景、目标和约束 |
| 政策研究 | `policy_lookup` | 地区、行业、适用问题 |
| 来源核验 | `source_verification` | 1 个来自当前授权结果的 sourceId 或待核验结论 |

当前 Agent 运行内 ID allowlist 继续生效。结构化结果把证据边界拆成两层：`taskSelectedEvidence` 只回显用户在冻结 taskContext 中选定的 case/source，`authorizedEvidence` 只由服务端汇总当前 Run 实际授权的 case/policy/source。用户不能在文本里伪造 caseId/policyId/sourceId 获得数据，前端也不能提交 authorizedEvidence。

Phase A 的案例选择、比较维度、技术评估条件和可选 sourceId 使用首次提交的 `taskContext.version=phase3-task-v1`，不塞进创业画像 profile，也不从问题文本解析实体 ID。taskContext 与 requestedIntent 一致，在首次 start 规范化并原子保存到 session 专用字段；后续消息只能追问，显式改变边界必须新建研究。start receipt 和 owned session detail 回读规范化 taskContext，历史列表只显示任务类型/必要摘要，Run 只回读 taskContextHash；旧 session 返回 null 并继续现有 legacy 流程。完整存储与隐私契约见 [phase-three-api-contract.md](phase-three-api-contract.md)。

来源核验有两种明确入口：从可信来源详情发起时提交 `source_verification + sourceId`，后端重读 published+verified 来源、provenance、HTTP(S) URL 和 revision 后授权；从空白 Assistant 输入待核验结论时不提交 sourceId，content 只能触发现有受控搜索/get_source 链。两种模式都不接受任意 URL 抓取，也不把正文中的数字、URL 或形似 ID 的文本当授权。

## 4. 单案例分析

### 4.1 输入

- 案例：从公开详情、筛选结果或工作台搜索中选择；必须是当前可访问且完整证据链的 caseId。
- 用户画像：沿用现有 profile 的地区、行业、创业阶段、预算、目标和资源。团队能力、计划周期、应用场景和任务约束属于 taskContext，不扩张 profile 字段职责。
- 研究问题：UI 可以提供围绕可复制性与下一步验证的可编辑默认值；提交 start API 时必须非空且最多 2,000 字，后端不得根据空 content 猜测目标。
- 输出偏好：简要/标准/深入；只控制表达深度，不改变证据门槛。

### 4.2 流程

1. 后端先验证/规范化请求并计算 profileHash、contentHash、requestedIntent、taskContextHash，再开启 start 事务并锁定当前用户的幂等边界。精确成功重放直接返回原 receipt，不重新检查已失效证据或重复扣费；同 key 不同身份返回 409。
2. 只有首次创建才在同一事务和证据行锁内重新加载显式案例/来源、当前修订与来源关系。无效案例返回 400 `PHASE3_CASE_NOT_ELIGIBLE`，无效指定来源返回 400 `PHASE3_SOURCE_NOT_ELIGIBLE`；事务回滚，不创建 session/message/Run、不预留 Token，也不生成选择或授权证据。普通 Phase A 不依赖 Analytics dataVersion。
3. 资格通过后同一事务原子创建研究对象、证据投影、Token 预留与成功 receipt；工具再获取相关案例/政策作为补充，所有证据绑定 Run。
4. 缺失收入、技术或地区语义时输出 unknown/caveat，不补猜。
5. 生成结构化结果与兼容 Markdown，同步进入现有 message/run/citation 体系。

只有首次创建的锁内资格校验通过并由原子 start 流程成功受理后，后续检索不足、执行期间证据失效/revision 变化或只能支撑部分结论时，Run 才能进入 `evidence_insufficient`。锁前撤销使首次请求返回 400；锁后撤销等待 start 提交，随后由运行期复检决定既有 Run 状态。提交时无效的显式 ID 不能伪装成运行后的证据不足。

### 4.3 输出契约

- 案例摘要与证据状态
- 商业模式、目标客户、收入方式（不是收入数值）
- 成本和资源、技术路线
- 关键成功因素、风险
- 可复制部分、不可复制条件
- 对当前用户的适用性、推荐行动
- 每个事实性结论的 `sourceIds`
- 关键假设、不确定性、缺失数据
- provider、model、promptVersion、generatedAt、必填 evidenceVersion、可空 dataVersion、tokenUsage

每个区块标识 `fact|inference|methodology`。没有合法引用的事实不能进入最终结果。现有 `CaseAnalysisVO` 只覆盖部分字段，第三阶段结果使用 Agent v2 的版本化 structuredResult 扩展，不破坏旧接口。

## 5. 多案例比较

### 5.1 输入与上限

- 选择 2–3 个案例；3 是现有 `compare_cases` 的合理上限，移动端和证据面板都可读。
- 比较维度必须选择 1–3 个且唯一；Phase A v1 只允许 `businessModel|technicalPath|targetCustomer|outcome|regionalContext|evidenceStrength`。UI 可以预选推荐项，但只有用户确认后提交的值才进入 taskContext，后端不得静默补默认维度。
- “收入信息、资源/成本、风险、用户适用性”可以出现在结论与 caveat 中，但不是 v1 compare_cases 的独立受控维度；新增维度需要新的契约版本和运行时评审。

### 5.2 输出

- 比较基线：每个案例的证据状态、缺失字段和当前 Run 共用的 evidenceVersion。
- 共同点、关键差异、商业模式和技术路线比较。
- 收入信息只显示同 currency/period/type 的规范化值；不兼容时写“不可比较”，缺失不显示 0。
- 地区和政策环境、风险、适用性、结论与建议。
- 每个事实结论映射到一个或多个合法 `sourceIds`；不能用 A 的来源证明 B 的事实。
- `taskSelectedEvidence.caseIds`、taskContext.caseIds、比较 baselines 和结果 caseIds 必须完全一致。提交时任一选中案例没有合法来源链即返回 400 `PHASE3_CASE_NOT_ELIGIBLE` 且零持久化；合法受理后证据才发生不足或失效时才返回 `evidence_insufficient`，且不能补猜事实。

桌面使用可横向比较的行列矩阵，手机改为“维度优先”的纵向块；不让表格横向溢出成为唯一阅读方式。始终提供线性文本替代。

## 6. 技术评估

### 6.1 用户输入

- 技术或已审核 technologyTagId；分类未就绪时允许受限 technologyText，但结果不进入统计。两者同时存在时已审核 ID 是规范化主键，文本只保留用户措辞。
- 应用场景、所属行业、地区。
- 预算、团队能力、计划周期、当前阶段。
- 已有资源、目标、限制条件。

### 6.2 结构化输出

- 技术成熟度、场景适配度、实施复杂度。
- 成本构成，不伪造总成本；注明已知/未知和估算依据。
- 数据与基础设施要求、团队能力缺口、依赖项。
- 合规风险、经营风险、可替代方案。
- 分阶段实施路线和建议验证实验。
- 支撑案例、相关政策、来源引用。
- 关键假设、不确定性、evidenceVersion；只有 Analytics snapshot 研究才有 dataVersion。

评分是维度卡而非总分仪表盘。每个维度返回受控 `level`、ClaimItem `rationale.sourceIds`、`confidence` 和 `missingEvidence`。总分默认不提供；如果未来提供，只能是公开权重的派生值。

## 7. 数据洞察

### 7.1 页面结构

顶部是紧凑筛选栏：时间范围、地区层级/地区、`regionRole`、行业、技术和收入可比组。筛选使用 URL query 持久化，可复制链接；应用/清除是明确命令。案例正式默认 operation、注册地独立、政策固定 policy_applicability；Red 维度控件禁用并说明数据条件。

内容顺序：指标摘要、核心图、数据质量与限制、明细入口。页面不堆叠装饰卡，重复指标/图表才使用小半径卡；筛选与说明是无框全宽区域。

### 7.2 各 tab

- 概览：合格政策、来源、覆盖地区/行业、质量向量；案例总量在 canonical identity 前显示“合格记录 105，业务案例数待去重”，不显示误导 KPI。
- 行业：水平排名条、地区分布、政策关联准备状态；多标签说明固定可见。
- 技术：数据完整率与准备状态先行；semantic technology 未完成前不画采用趋势。
- 收入：只显示 0% 结构化覆盖和字段要求；达到字典门槛后再开放直方图/区间条和箱线替代表格。
- 地区与趋势：operation/registration 规范化关系未就绪时显示 Red unavailable 和 `CASE_REGION_ROLE_NOT_READY`，不能显示空图或“0 个经营地区案例”；显式 legacy 数据显示 Yellow partial“相关地区分布”和 `LEGACY_REGION_SEMANTICS`；只有规范化角色已就绪且当前筛选确实无数据才显示 empty/sampleSize=0。政策按 policy_applicability，country 政策不复制到省份。质量页可以 Green 如实显示 operation 覆盖率 0，但这不解锁业务分布；没有可靠地图几何和语义前不使用地图。

### 7.3 图表选择

| 任务 | 图表 | 禁止 |
|---|---|---|
| 排名/分类比较 | 水平条形图 | 3D、气泡面积暗示精度 |
| 时间变化 | 折线图，明确空月 0 | 用 created_at 代替业务时间 |
| 收入区间 | 直方图或区间条 | 混币种/周期、缺失当 0 |
| 行业 × 技术 | 矩阵/热力表或分组条 | 无 technology 类型时上线 |
| 地区 | 排名条 | 无可靠地图数据时画地图 |
| 少量构成 | 最多 5 类的环形图 | 长尾分类强塞环形图 |

不使用 3D、装饰仪表盘、蓝紫渐变、霓虹或厚阴影。继续 SoloFirm Prisma Light：纸白/墨色、中性边框、墨黑主操作、白色/灰色次级命令、少量红色风险状态；绿色只用于成功、核验、可用状态和必要的数据选中信号，不填充普通命令按钮。不复制 Prisma 深色落地页。

### 7.4 每张图的完整状态

每张图必须展示标题、指标解释、时间范围、筛选摘要、sampleSize、missingCount、generatedAt/freshness、Tooltip、必要图例、查看数据列表和“基于当前数据研究”。同时实现：

- loading：保留稳定尺寸，`aria-busy=true`，不造成布局跳动。
- empty：说明“当前筛选无合格数据”，提供清除某筛选。
- partial：保留可用数据，顶部列 caveats/缺失，不伪装完整成功。
- error：可重试，保留筛选，不用空图代替错误。
- stale：dataVersion 过期时要求刷新；不把旧图值提交给 AI。
- Red：解释解除条件，不构造 0 分布。

每张图必须有可访问表格/文本替代。键盘可聚焦 bucket，Enter/Space 触发与点击相同的明确筛选/明细；屏幕阅读器读出“名称、原始值、占比、样本”。动画尊重 reduced-motion，手机触控目标至少 44px。

## 8. 图表与 AI 联动

用户点击“基于当前数据研究”后打开侧栏/底部 sheet，预览 metric、筛选、regionRole、选中 buckets 和问题。前端仅提交 ID、filters、dateRange、granularity、dataVersion、idempotencyKey；不提交自算总数、拼接正文、引用、SQL 或 URL。

后端按 [phase-three-api-contract.md](phase-three-api-contract.md) 重建快照、鉴权、验证版本后：

1. 创建或复用用户拥有的 session。
2. 追加 user message，创建 `agent_research` run。
3. 在 run result/audit 中保存 analyticsSnapshotId、metricId、规范化 filters、dataVersion。
4. 工具返回授权 case/policy/source IDs，引用继续走现有 citation/evidence 服务。
5. 最终消息提供“返回数据视图”，用服务端保存的 filters 恢复图和下钻。

## 9. 十一个用户流

1. 空白 Assistant：选任务 → 填画像/问题 → 预检 → 创建 session/run → 查看结构化结果与证据。
2. 案例详情：点“AI 分析” → 带 caseId 进入 `/assistant` → 后端重验案例 → 输出单案例分析。
3. 两个案例：列表勾选 2–3 个 → “比较” → 选择维度 → run → 对比结果与逐结论引用。
4. 技术图表：聚焦一个 technology bucket → 技术评估 → 自动带技术/行业/地区 filters，用户补预算和能力。
5. 行业图表：点击条形 bucket → URL 筛选更新 → 打开同 dataVersion 的案例下钻列表。
6. 地区图表：点击地区 → 切换政策明细 → 保留 date/industry/regionRole filters；案例 legacy 图显示“相关地区”，政策显示 applicability caveat。
7. 筛选结果：点“基于当前数据研究” → 确认快照边界 → 服务端重建 → 进入 Assistant run。
8. AI 引用：点击引用 → evidence drawer → 内部案例/政策详情或安全来源链接 → 返回保持会话滚动位置。
9. 保存结果：完成 run → “保存为报告” → 输入标题/备注 → 固化 finalMessageId/runId、citation manifest、必填 evidenceVersion，以及仅 Analytics snapshot 才有的 dataVersion。
10. 继续历史：历史侧栏选 session → 加载稳定消息分页和最后 run → 发送 follow-up；数据变更不重写旧答案。
11. 导出报告：报告详情选 Markdown/HTML/PDF → 服务端从保存快照生成 → 明示生成时间、模型、数据版本、引用和限制。

## 10. 保存、导出与反馈

“保存报告”不是浏览器 localStorage。报告必须归属用户，引用已保存的 finalMessageId/runId/sessionId，保存标题、摘要、必填 evidenceVersion、仅 Analytics snapshot 时存在的 dataVersion、citation manifest 和创建时间。原证据变为 unavailable 时历史报告保留引用元数据并显示失效状态，不伪造旧内容快照。

报告生命周期固定为 active → trash → permanently_purged。trash、restore 和用户触发的 permanent 都在 JSON body 携带正整数 `expectedRevision` 并执行 compare-and-set：缺失/类型错误为 400，旧 revision 为 409 `REPORT_REVISION_CONFLICT`；成功转换只递增一次并返回新 revision。已经处于目标状态时，只有携带当前 revision 才无变更返回；旧 revision 重放仍是 409。网络超时后客户端先 GET 最新报告再决定是否重试。

移入回收站设置 30 天 purge_after；到期后由小批量、幂等、多实例带锁/租约的任务自动执行永久清除。后台 purge 不接受客户端 body，而是在锁内读取 revision；restore 与 purge 用锁内 revision/状态原子竞争。永久删除清除报告正文、备注、导出缓存和一切可恢复内容，只保留最小非内容审计，日志不含正文/引用全文。报告保存时复制独立的已验证结果和引用 manifest，因此原 session 后续永久清除不会连带删除 active 报告，报告只标记 `sourceSessionAvailable=false`；报告自身仍须单独删除。running、planning、clarification_needed、failed、cancelled、expired Run 不能保存。

导出首批支持 Markdown 与打印友好 HTML/PDF；Excel 只用于 analytics 数据表，不用于 AI 长报告。导出文件包含：报告标题、用户输入边界、结论、证据、限制、模型/生成时间、evidenceVersion，以及适用时的 Analytics dataVersion。私有 profile 字段按导出预览显式选择，默认不含用户邮箱等账户信息。

反馈入口只按服务端 `feedbackEligible` 展示：owned completed 为 true；owned evidence_insufficient 仅在已有用户可见且持久化的 Assistant 结果时为 true；received、running、planning、clarification_needed、cancelled、expired 和 failed 一律为 false。原因来自受控列表，可选 comment 最多 500 个 Unicode code points；同一用户对同一 Run 更新同一条带 revision 的反馈。管理员 token 不能替用户反馈，管理端只查看按任务、模型、promptVersion、日期和受控原因聚合的质量指标，不读取完整问题、回答、思维链或 Provider 原始响应。

## 11. 响应式与无障碍验收

- Desktop `>=1200`：Assistant 保持历史 rail + 主研究 + 证据 drawer；数据洞察筛选栏可两行，图表 2 列但主要趋势可全宽。
- Tablet `768–1199`：历史与证据互斥抽屉；筛选使用可展开区域；图表 1–2 列按内容宽度决定。
- Mobile `<768`：单列、底部 sheet、固定格式图有稳定 aspect-ratio；不横向滚动整个页面。
- 200% zoom、长中文标签和 320px 宽不重叠；文字不按 viewport 缩放。
- 所有图表操作都有按钮名称、焦点状态、表格替代；颜色不是唯一状态信号；错误和 run 状态使用 live region。

评测与自动化 seams 见 [phase-three-evaluation-plan.md](phase-three-evaluation-plan.md)，实施顺序见 [phase-three-roadmap.md](phase-three-roadmap.md)。

## 12. 发布与完成命名

Phase B 可以在真实 Green/Yellow 聚合、下钻和可访问状态完成后形成 `Phase B partial production release`。若 technology taxonomy、收入可比样本或 Phase C 链路尚未达到门槛，只能称“前端实现完成”或“部分上线”。

`Phase Three product complete` 必须同时具备：行业统计和下钻、达到样本门槛的 technology taxonomy 与至少一个技术可视化、收入规范化且至少 30 个同币种/周期/type 可比案例并达到 40% 覆盖率、至少一个收入可视化、图表到 AI、报告保存/导出/反馈，以及完整 40 题和性能门槛。任何 Red 核心能力未解除时不得宣布第三阶段产品完成。

Phase A v1 的规格预算以 [phase-three-api-contract.md](phase-three-api-contract.md) 为唯一机器契约：3200 synthesis tokens、directAnswer 600 字符、全结果 ClaimItem 合计 6、citations 6、Assistant 渲染文本 12000 字符。规格冻结不代表 DTO、校验器、迁移、服务或界面已经实现。
