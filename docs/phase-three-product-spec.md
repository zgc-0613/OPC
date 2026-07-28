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

空白状态提供六个任务入口。现有四个 starter 直接复用；新增“综合创业研究”和“案例分析”。每个入口写入受控 `requestedIntent`，用户修改 starter 文案后仍按现有规则回落 `auto`。

| 入口 | requestedIntent | 初始必填 |
|---|---|---|
| 综合创业研究 | `general_research` | 地区、行业、阶段、目标、研究问题 |
| 案例分析 | `case_analysis` | 1 个授权 caseId、研究问题可选 |
| 案例比较 | `case_comparison` | 2–3 个授权 caseId、比较维度 |
| 技术评估 | `technology_assessment` | 技术/标签、场景、目标和约束 |
| 政策研究 | `policy_lookup` | 地区、行业、适用问题 |
| 来源核验 | `source_verification` | 1 个来自当前授权结果的 sourceId 或待核验结论 |

当前 Agent 运行内 ID allowlist 继续生效。用户不能在文本里伪造 caseId/policyId/sourceId 获得数据。

## 4. 单案例分析

### 4.1 输入

- 案例：从公开详情、筛选结果或工作台搜索中选择；必须是当前可访问且完整证据链的 caseId。
- 用户画像：地区、行业、创业阶段、预算、团队能力/资源、目标；沿用现有 profile，并在新任务中补充团队能力与计划周期。
- 可选研究问题：最多 2,000 字，默认围绕可复制性与下一步验证。
- 输出偏好：简要/标准/深入；只控制表达深度，不改变证据门槛。

### 4.2 流程

1. 后端重新加载案例、来源和当前修订，拒绝未授权、失效或 dataVersion 过期请求。
2. 工具获取相关案例/政策作为补充，所有证据绑定 Run。
3. 缺失收入、技术或地区语义时输出 unknown/caveat，不补猜。
4. 生成结构化结果与兼容 Markdown，同步进入现有 message/run/citation 体系。

### 4.3 输出契约

- 案例摘要与证据状态
- 商业模式、目标客户、收入方式（不是收入数值）
- 成本和资源、技术路线
- 关键成功因素、风险
- 可复制部分、不可复制条件
- 对当前用户的适用性、推荐行动
- 每个事实性结论的 citationIds
- 关键假设、不确定性、缺失数据
- provider、model、promptVersion、generatedAt、dataVersion、tokenUsage

每个区块标识 `fact|inference|methodology`。没有合法引用的事实不能进入最终结果。现有 `CaseAnalysisVO` 只覆盖部分字段，第三阶段结果使用 Agent v2 的版本化 structuredResult 扩展，不破坏旧接口。

## 5. 多案例比较

### 5.1 输入与上限

- 选择 2–3 个案例；3 是现有 `compare_cases` 的合理上限，移动端和证据面板都可读。
- 比较维度：商业模式、目标客户、技术路线、收入信息、资源/成本、结果、地区/政策环境、风险、证据强度、用户适用性。
- 现有受控维度 `businessModel|technicalPath|targetCustomer|outcome|regionalContext|evidenceStrength` 继续使用；新增维度需要工具契约先扩展。

### 5.2 输出

- 比较基线：每个案例的数据版本、证据状态和缺失字段。
- 共同点、关键差异、商业模式和技术路线比较。
- 收入信息只显示同 currency/period/type 的规范化值；不兼容时写“不可比较”，缺失不显示 0。
- 地区和政策环境、风险、适用性、结论与建议。
- 每个结论映射到一个或多个案例/政策 citationIds；不能用 A 的来源证明 B 的事实。

桌面使用可横向比较的行列矩阵，手机改为“维度优先”的纵向块；不让表格横向溢出成为唯一阅读方式。始终提供线性文本替代。

## 6. 技术评估

### 6.1 用户输入

- 技术或已审核 technologyTagId；分类未就绪时允许受限自由文本，但结果不进入统计。
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
- 关键假设、不确定性、数据版本。

评分是维度卡而非总分仪表盘。每个维度返回 `level`（low/medium/high 或 1–5 离散级）、`rationale`、`citationIds`、`confidence` 和 `missingEvidence`。总分默认不提供；如果未来提供，只能是公开权重的派生值。

## 7. 数据洞察

### 7.1 页面结构

顶部是紧凑筛选栏：时间范围、地区层级/地区、行业、技术和收入可比组。筛选使用 URL query 持久化，可复制链接；应用/清除是明确命令。Red 维度控件禁用并说明数据条件。

内容顺序：指标摘要、核心图、数据质量与限制、明细入口。页面不堆叠装饰卡，重复指标/图表才使用小半径卡；筛选与说明是无框全宽区域。

### 7.2 各 tab

- 概览：合格政策、来源、覆盖地区/行业、质量向量；案例总量在 canonical identity 前显示“合格记录 105，业务案例数待去重”，不显示误导 KPI。
- 行业：水平排名条、地区分布、政策关联准备状态；多标签说明固定可见。
- 技术：数据完整率与准备状态先行；semantic technology 未完成前不画采用趋势。
- 收入：只显示 0% 结构化覆盖和字段要求；达到字典门槛后再开放直方图/区间条和箱线替代表格。
- 地区与趋势：地区水平排名、政策折线趋势；没有可靠地图几何和语义前不使用地图。

### 7.3 图表选择

| 任务 | 图表 | 禁止 |
|---|---|---|
| 排名/分类比较 | 水平条形图 | 3D、气泡面积暗示精度 |
| 时间变化 | 折线图，明确空月 0 | 用 created_at 代替业务时间 |
| 收入区间 | 直方图或区间条 | 混币种/周期、缺失当 0 |
| 行业 × 技术 | 矩阵/热力表或分组条 | 无 technology 类型时上线 |
| 地区 | 排名条 | 无可靠地图数据时画地图 |
| 少量构成 | 最多 5 类的环形图 | 长尾分类强塞环形图 |

不使用 3D、装饰仪表盘、蓝紫渐变、霓虹或厚阴影。继续 SoloFirm Prisma Light：纸白/墨色、中性边框、绿色主操作、少量红色风险状态；不复制 Prisma 深色落地页。

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

用户点击“基于当前数据研究”后打开侧栏/底部 sheet，预览 metric、筛选、选中 buckets 和问题。前端仅提交 ID、filters、dateRange、granularity、dataVersion、idempotencyKey；不提交自算总数、拼接正文、引用、SQL 或 URL。

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
6. 地区图表：点击地区 → 切换政策明细 → 保留 date/industry filters 并显示 applicability caveat。
7. 筛选结果：点“基于当前数据研究” → 确认快照边界 → 服务端重建 → 进入 Assistant run。
8. AI 引用：点击引用 → evidence drawer → 内部案例/政策详情或安全来源链接 → 返回保持会话滚动位置。
9. 保存结果：完成 run → “保存为报告” → 输入标题/备注 → 固化 message/run/citations/dataVersion。
10. 继续历史：历史侧栏选 session → 加载稳定消息分页和最后 run → 发送 follow-up；数据变更不重写旧答案。
11. 导出报告：报告详情选 Markdown/HTML/PDF → 服务端从保存快照生成 → 明示生成时间、模型、数据版本、引用和限制。

## 10. 保存与导出

“保存报告”不是浏览器 localStorage。报告必须归属用户，引用已保存的 finalMessageId/runId/sessionId，保存标题、摘要、dataVersion、citation manifest 和创建时间。原证据变为 unavailable 时历史报告保留引用元数据并显示失效状态，不伪造旧内容快照。

导出首批支持 Markdown 与打印友好 HTML/PDF；Excel 只用于 analytics 数据表，不用于 AI 长报告。导出文件包含：报告标题、用户输入边界、结论、证据、限制、模型/生成时间、数据版本。私有 profile 字段按导出预览显式选择，默认不含用户邮箱等账户信息。

## 11. 响应式与无障碍验收

- Desktop `>=1200`：Assistant 保持历史 rail + 主研究 + 证据 drawer；数据洞察筛选栏可两行，图表 2 列但主要趋势可全宽。
- Tablet `768–1199`：历史与证据互斥抽屉；筛选使用可展开区域；图表 1–2 列按内容宽度决定。
- Mobile `<768`：单列、底部 sheet、固定格式图有稳定 aspect-ratio；不横向滚动整个页面。
- 200% zoom、长中文标签和 320px 宽不重叠；文字不按 viewport 缩放。
- 所有图表操作都有按钮名称、焦点状态、表格替代；颜色不是唯一状态信号；错误和 run 状态使用 live region。

评测与自动化 seams 见 [phase-three-evaluation-plan.md](phase-three-evaluation-plan.md)，实施顺序见 [phase-three-roadmap.md](phase-three-roadmap.md)。
