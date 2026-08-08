# SoloFirm 第三阶段准备度审计

> 审计日期：2026-07-29（Asia/Shanghai）
> 审计对象：`C:\Users\ACha_\Documents\GitHub\OPC` 与生产环境 `/opt/opc/releases/20260728-130142`
> 定位：SoloFirm 用户端智能决策工作台
> 口径来源：[analytics-metric-dictionary.md](analytics-metric-dictionary.md)

## 1. 结论

第三阶段可以按 A、B、C 三轮进入开发，但不能把现有 `/analysis` 页面或 105 条合格案例行直接当成正式统计。当前 AI 会话、运行、工具、引用、证据和历史体系可复用；案例业务时间、结构化技术类型、规范化收入、业务案例去重和数据版本尚不存在，必须由后端先补齐相应契约。

生产只读审计成功。审计窗口为 `2026-07-29 01:08:08` 至 `01:10:54 CST`，MySQL `8.0.46`，数据库时差为 `UTC+08:00`。只执行了 `SELECT`、`SHOW` 与只读运行路径查询，没有读取用户、管理员、会话或凭据内容。

### 1.1 当前可用数据

| 对象 | 总行数 | published | published + item verified | 完整合格来源链 | 正式业务数量结论 |
|---|---:|---:|---:|---:|---|
| 案例 | 106 | 106 | 105 | 105 | 105 是合格记录行；业务案例去重后数量 `unknown` |
| 政策 | 68 | 59 | 57 | 57 | 57 可用于政策总量和发布时间趋势 |
| 来源 | 131 | 122 | 121 | 121 | 121 条合格来源记录；有 1 组同 URL 待归并判断 |

“完整合格来源链”要求业务记录与来源均为 `published + verified`，来源标题、发布者非空，URL 为 HTTP(S)。合格案例和政策中无无效来源链记录。

目录表当前有 35 个地区（1 个 country、34 个 province）和 290 个标签（case 非行业 241、case 行业 18、common 行业 1、policy 非行业 30）。这些是目录行数，不等于正式看板覆盖数。

案例存在 23 个“同标题 + 同原始 URL + 同 sourceId”组，共 42 条重复超额记录，最大组 5 条；这些组的正文类字段存在差异，可能是重复导入或未建模版本，不能自动删除或静默合并。临时复合键只有 63 个唯一值，但 63 不是已经确认的正式案例数。近似重复因没有内容指纹、canonical ID 或人工合并记录而为 `unknown`。

## 2. 审计范围与证据

### 2.1 已读取范围

- 产品与历史：`PRODUCT.md`、`DESIGN.md`、`AI_READINESS.md`、三份工作记录和要求指定的五份基线文档。
- 前端：路由、`AssistantView`、Assistant 布局/组件/composables/API、安全 Markdown、公开案例/政策/来源/地区/标签页面、旧分析页及依赖。
- 后端：案例、政策、来源、地区、标签的 Entity、DTO、VO、Mapper、Service、Controller；AI capabilities、单案例分析、创业建议、Agent session/message/run/tool/citation/evidence/history；用户鉴权拦截器和错误处理。
- 数据库：`schema.sql`、全部 26 个部署 SQL 文件、`opc_platform.sql`、生产 `SHOW COLUMNS`、Testcontainers MySQL 8.4 夹具和契约路径。26 来自当前 `deploy/sql` 的实际文件清单，不把目录外 SQL 重复计入。
- 生产访问：受保护的 `.local-secrets/opc-deploy.env` 文件名、现有 SSH 指纹固定和数据库凭据远端加载逻辑；未输出任何秘密。

CodeGraph 索引先执行了同步；结构查询可用时优先使用索引，SQL、字符串和样式用 `rg`。`.codegraph/` 仍被 Git 忽略。

### 2.2 Testcontainers 能证明和不能证明的内容

`PhaseOneMySqlIntegrationTest` 使用 MySQL 8.4，创建与生产关键表一致的字段，并覆盖：published/verified 双层证据筛选、来源链、`search_cases`、`search_policies`、`get_source`、`compare_cases`、运行内 ID 授权、证据失效、历史/清理、并发修订和政策行业关系。

夹具只能证明 SQL 与服务契约，不能证明生产分布、收入存在、技术分类正确或案例没有重复。下一阶段的统计黄金夹具必须新增在独立 analytics 测试域中，不能拿现有少量 AI 夹具推导指标结果。

## 3. 现有能力矩阵

| 能力 | 状态 | 代码事实与第三阶段处理 |
|---|---|---|
| 综合创业研究 | ready | Agent runtime 和创业建议接口均存在；复用 session/run/evidence |
| 单案例分析 | partial | `/api/ai/case-analysis` 有摘要、商业模式、技术评估、机会、风险、行动、引用和模型元数据；缺少第三阶段完整结构 |
| 多案例比较 | partial | `compare_cases` 工具可比较 2–3 个案例和最多 6 个受控维度，但 Phase A v1 规划/结果契约收缩为用户显式提交 1–3 个；收入、政策环境和逐结论引用仍需实现 |
| 技术评估 | partial | `technology_assessment` intent 与结构化研究运行存在；没有专用技术工具、技术分类或评分契约 |
| 政策适用性研究 | partial | 搜索工具按地区与 applicability 工作；生产 57 个合格政策全为 `unclassified`，无行业关系 |
| 来源核验 | partial | `get_source`、运行内授权、合法 URL、证据重放和失效检测已实现；首次 start 尚无受控 sourceId 输入 |
| 会话、消息、运行、工具 | partial | 基础持久化、异步运行、轮询、取消、幂等、配额和审计均存在；尚无冻结 taskContext 专用字段/回读和最终 evidenceVersion |
| 引用与证据面板 | ready | 仅当前 Run 已授权来源可引用；链接安全和证据状态可追溯 |
| 研究历史 | ready | active/archive/trash、搜索、游标分页、恢复和永久清理存在 |
| 保存报告 | blocked_by_backend | 没有报告实体、API 或 purge_after 消费作业；不能把 session title 当报告 |
| 报告导出 | blocked_by_backend | 仅政策公开 Excel 和管理端案例/来源导出；没有研究报告导出 |
| 正式统计聚合 API | missing | 只有旧 dashboard/visit 接口，没有 analytics 聚合契约 |
| 现有 `/analysis` 看板 | partial | 前端拉全量 published 列表后本地聚合，不筛 verified、无数据版本、不能作为正式指标 |
| 行业统计 | blocked_by_data | case industry 关系齐全，但重复业务案例和分类质量未治理；政策行业关系为 0 |
| 技术统计 | blocked_by_data | `ai_tools` 是自由文本；非行业标签没有 technology 类型 |
| 收入统计 | blocked_by_data | 案例表没有任何收入字段 |
| 政策时间趋势 | ready | 57/57 合格政策有 `publish_date` |
| 案例业务时间趋势 | blocked_by_data | 没有业务发布时间；`accessed_at` 是采集/访问日 |
| 地区统计 | partial | 记录覆盖完整，但 case `region_id` 的注册/经营语义未声明 |
| 图表渲染 | frontend_only | 无图表库；现有自制 HTML/SVG 可复用样式，复杂图表需评估轻量库 |
| 安全 Markdown | ready | `markdown-it + DOMPurify`，含表格白名单和安全引用处理 |
| 移动端 Assistant | ready | 独立布局、抽屉焦点管理、44px 粗指针目标和 reduced-motion 已覆盖 |
| 用户权限边界 | ready | `/api/ai/**` 使用 UserAuthInterceptor；无令牌 401、禁用用户 403 |

`phase3-structured-result-v1` 的规格边界已经与现有稳定运行时对齐，但尚未实现：synthesis 3200 tokens、directAnswer 600 字符、全结果 ClaimItem 合计 6、citations 6、Assistant 渲染文本 12000 字符。taskContext 用户选择只进入 `taskSelectedEvidence`；当前 Run 工具授权只进入服务端 `authorizedEvidence`，其单字段上限 120 来自 12 次可配置工具调用乘每次搜索 10 条。这里的“规格已冻结”不得改写为 DTO/校验器/报告 API 已存在。

显式 case/source 的 Phase A 前向契约采用唯一边界：规范化和四项身份 hash 后开启 start 事务，先锁定并处理 `userId + idempotencyKey`；精确成功重放返回原 receipt，不重新验证当前证据。只有幂等 miss 的首次创建才在同一事务内锁定并权威复检实体、revision、provenance 和来源关系；case 失败返回 400 `PHASE3_CASE_NOT_ELIGIBLE`，source 失败返回 400 `PHASE3_SOURCE_NOT_ELIGIBLE`，均为整笔回滚和零持久化/Token 副作用。`evidence_insufficient` 只表示合法受理后的运行期检索不足、证据变化或部分支撑。六个文档夹具使用仅供测试的 `runEvidenceFixture` 验证案例/政策到来源的关系、实体/link 唯一性、citation metadata 与按固定紧凑 UTF-8 JSON 独立重算的 evidenceVersion；policy_lookup 具有非空 policy-source 正向链，该 fixture 不是已实现的生产 API 字段。

公开案例、政策、来源、地区和标签详情当前都可访问；案例/政策列表支持关键词、地区、分类/类型和排序，政策页有公开 Excel 导出。但列表先拉取 published 全量再在浏览器筛选/分页，公开 Service 只强制 published、不强制 verified，因此这些页面可以作为下钻目标，不能作为正式总体统计输入。前端没有图表库或表格库，旧分析页使用自制 HTML/SVG/CSS；Markdown 能力为 `markdown-it + DOMPurify`，研究报告保存/导出均不存在。

## 4. 真实数据结构

### 4.1 案例

- 行业同时保存在必填单值 `category VARCHAR(50)`、自由文本 `tags` 和规范化 `case_tags`。服务会把 category 同步成 `is_industry=1` 的标签。
- 数据库允许一个案例连接多个行业标签；生产 98 个合格案例有 1 个行业标签，7 个有 2 个。没有主行业/辅助行业字段。
- 技术信息只有 `ai_tools TEXT` 与未分类的非行业标签；没有 technology 关系或结构化技术字段。
- 没有收入、币种、周期、收入类型、估算或披露状态字段。
- 没有业务发布时间。`accessed_at` 是资料访问/采集日，`created_at` 是入库时间，`updated_at` 是最后数据库更新。
- 数据库没有名为 `published_at` 的案例字段；政策使用 `publish_date`（DATE），不能把两者混为一个通用字段。

### 4.2 政策

- 业务时间包括 `publish_date`、`effective_date`，`valid_period` 仍为自由文本。
- 行业关系表 `policy_industry_tags` 存在，但生产合格政策关系数为 0；57/57 的 `applicability_mode` 均为 `unclassified`。
- `region_id` 在现有政策检索中作为政策适用地区；正式政策地区统计沿用这一语义。国家级政策保留 country 层级，不复制成 34 条省级记录。

### 4.3 标签、地区和来源

- `tags.tag_type` 当前值表示 `case|policy|common`，不是 `industry|technology`；`is_industry` 只能识别行业。
- 没有标签父子层级、技术类型、主次关系。`tag_aliases` 有 4 行且全部指向同一个行业标签；没有技术别名。
- 30 个 `industry_tag_review_candidates` 全为 pending，不能进入正式统计。
- `regions` 有 level/parent_id 层级。案例 `region_id` 仅表示“相关地区”，正式命名为 `legacy_related_region`，不能解释为注册地、主要经营/落地地区或来源地区。
- 案例正式地区统计默认使用“主要经营/落地地区”；注册地是独立维度，来源地区不进入案例地区分布。多经营地区需要规范化关系及 `primary|secondary` role，当前结构尚不具备。
- 来源保留标题、发布者、原始 URL、访问日。121 个合格来源的三项 provenance 均完整。
- `evidence_revision` 是乐观并发/证据修订号，不是内容快照。系统没有数据版本账本或历史内容版本。

### 4.4 状态、删除和版本

- 业务 publish status 为 `draft|reviewed|published`；生产当前实际有 draft/published。
- 证据状态为 `legacy_unverified|verified|excluded`。正式数据必须满足业务记录和来源的双重 published + verified。
- 案例、政策、来源删除是带依赖和修订检查的物理删除，没有 deleted/archive 标记。AI session 的 archive/trash 与业务记录无关。
- 被编辑的证据相关字段会使 verified 失效。历史研究保存 Run 的证据修订信息，但没有全局 analytics dataVersion。
- 当前 `AgentSessionStartDTO` 没有 taskContext，`ai_agent_sessions.research_context_json` 会被澄清流程更新并在 purge 时清空，不能承担冻结任务边界；Phase A 因此需要独立 session taskContext 三字段，以及进入现有原子 start 前的显式 case/source 资格校验，详见 API 契约。
- 当前 Agent Run 的 `evidence_hash` 在入队时按 session/message/idempotency 生成，完成更新不重算它；它不是证据集合版本。Phase A 新 `evidenceVersion` 必须在完成时从授权实体、修订、contentHash、eligibility 和合法 links 的规范对象生成，Analytics `dataVersion` 仍属于 Phase B。
- 当前 source_verification 只能从受控搜索授权后调用 get_source，没有首次请求指定 sourceId 的 DTO seam；Phase A 只增加受控 positive long sourceId，不开放任意 URL。

## 5. 生产完整度

### 5.1 覆盖率

| 维度 | 合格样本 | 有值/可识别 | 覆盖率 | 可统计性 |
|---|---:|---:|---:|---|
| 案例行业关系 | 105 | 105 | 100.0% | Yellow：重复与分类治理未完成 |
| 案例技术自由文本 `ai_tools` | 105 | 100 | 95.2% | 仅检索辅助，不可做技术分布 |
| 案例显式 technology 类型 | 105 | 0 | 0.0% | Red |
| 案例地区 | 105 | 105 | 100.0% | Yellow：业务语义不明确 |
| 案例业务时间 | 105 | 0 | 0.0% | Red；105 个 accessed_at 不可替代 |
| 案例规范化收入 | 105 | 0 | 0.0% | Red |
| 政策适用地区 | 57 | 57 | 100.0% | Green：正式定义为 policy_applicability |
| 政策发布时间 | 57 | 57 | 100.0% | Green |
| 政策行业关系 | 57 | 0 | 0.0% | Red |
| 有效来源链 | 162 条合格业务记录 | 162 | 100.0% | Green |

案例覆盖 23 个地区，政策覆盖 21 个，合集 26 个。案例共有 19 个 industry 标签；样本最多的是软件开发 25、动漫短剧 20、电商营销 12、办公效率 11、文化创意 10，最小类别为 1。多标签按唯一 caseId 计数，因此占比和可以超过 100%。这些数量尚未消除业务重复记录。

主要行业定义为当前合格行样本 `n>=5`：

| 行业 | caseId 样本量 | 行业 | caseId 样本量 |
|---|---:|---|---:|
| 软件开发 | 25 | 动漫短剧 | 20 |
| 电商营销 | 12 | 办公效率 | 11 |
| 文化创意 | 10 | 教育培训 | 8 |
| 相关会议 | 7 | 人工智能应用 | 6 |

“相关会议”是否是行业本身就是分类质量问题，进一步支持 Yellow，而不是前端改名。未分类非行业标签的主要候选为：大学生创新创业 14、AI 8、算力 6、AI工具 5、AI智能体 4、数据 4、模型 4。这些 label 混合活动、主题、技术和产品，不能称为“主要技术标签”；正式技术标签样本量为 `unknown`，显式 technology 类型覆盖为 0。

地区样本（仍为未 canonical 去重的合格 caseId/policyId）：

| 地区 | 案例 | 政策 | 地区 | 案例 | 政策 |
|---|---:|---:|---|---:|---:|
| 北京市 | 9 | 12 | 天津市 | 2 | 1 |
| 河北省 | 0 | 1 | 山西省 | 0 | 2 |
| 辽宁省 | 6 | 2 | 吉林省 | 2 | 0 |
| 上海市 | 7 | 1 | 江苏省 | 12 | 8 |
| 浙江省 | 11 | 5 | 安徽省 | 1 | 2 |
| 福建省 | 7 | 2 | 江西省 | 1 | 0 |
| 山东省 | 6 | 1 | 河南省 | 2 | 1 |
| 湖北省 | 8 | 2 | 湖南省 | 5 | 2 |
| 广东省 | 2 | 4 | 广西壮族自治区 | 2 | 2 |
| 海南省 | 0 | 1 | 重庆市 | 7 | 1 |
| 四川省 | 5 | 3 | 贵州省 | 2 | 0 |
| 云南省 | 1 | 2 | 陕西省 | 4 | 0 |
| 青海省 | 1 | 2 | 新疆维吾尔自治区 | 2 | 0 |

政策发布时间样本为：2024 年 2、2025 年 3、2026 年 52；月趋势可从 2024-03 至 2026-07 计算，明确范围内的空月可以补 0。案例 105 条全部在 2026-07 被访问，这只能说明采集批次，不能解释业务趋势。

政策非空月份样本为：2024-03 1、2024-06 1、2025-08 1、2025-11 1、2025-12 1、2026-01 3、2026-02 3、2026-03 6、2026-04 11、2026-05 14、2026-06 11、2026-07 4。未列月份只有在请求明确覆盖它时才补 0。

### 5.2 质量分布

- published 中 verified 比例：案例 `105/106 = 99.1%`，政策 `57/59 = 96.6%`，来源 `121/122 = 99.2%`。
- 合格来源链缺失：案例 0、政策 0。
- 案例精确重复候选：23 组、42 条超额；近似重复 `unknown`。
- 来源 URL 重复：1 组、1 条超额，但标题/发布者/URL 三元组没有完全重复。
- 收入空值、负数、离群值：字段不存在，不能以 0 表示；数量为 `not_applicable`，规范化覆盖为 `0/105`。

## 6. 指标上线分级

### Green

- 已核验政策总数
- 政策发布时间趋势
- 地区政策数量（按 policy_applicability；country 不向省复制）
- 已核验比例（案例/政策/来源分别返回）
- 有效来源覆盖率
- 行业字段完整率
- 技术字段完整率（明确“显式 technology 类型”为 0%）
- 收入字段完整率（明确为 0%）
- 地区字段完整率

### Yellow

- 已核验来源总数
- 覆盖地区数量、legacy 相关地区案例数量、地区行业分布
- 覆盖行业数量、行业案例数量、行业案例占比、行业地区分布
- 数据完整度综合指标

Yellow 图表必须展示样本量、缺失量、重复候选和字段语义限制；案例分布在 canonical case 完成前不得用作无警示的市场规模结论。

当前案例显式 legacy 地区图只能是 Yellow/partial，命名“相关地区分布”，回显 `regionRole=legacy_related_region` 和 `LEGACY_REGION_SEMANTICS`。operation/registration 规范化关系未建立或未审核时必须是 Red/unavailable + `CASE_REGION_ROLE_NOT_READY`，不是 empty；完成关系后只有当前筛选确实无记录才是 empty/sampleSize=0。收入按地区比较只能使用 `regionRole=operation`，不能使用 legacy related region。`quality.region_completeness` 可以 Green 如实返回 operation 覆盖率 0，但不解锁业务分布。

### Red

- 已核验业务案例总数（当前只能给出 105 合格行，唯一业务案例数 unknown）
- 覆盖技术数量、全部技术采用/组合/关联指标
- 行业新增案例趋势、案例收录或发布时间趋势
- 行业相关政策数量、技术相关政策
- 全部收入分布、中位数、四分位数和交叉分布

Red 指标不得在正式用户看板显示数据图。可显示解释性空状态和数据准备条件。

## 7. 收入专项结论

第三阶段当前不允许上线任何收入图表。现有数据不能回答金额、币种、元/万元、月/年周期、营收/利润/个人收入、区间、实际/估算或拒绝披露。`business_model`、`outcome` 或正文中可能出现的数字不构成可比较收入。

后端最小新增模型为 `revenue_min DECIMAL`、`revenue_max DECIMAL`、`currency CHAR(3)`、`revenue_period`、`revenue_type`、`value_status=actual|estimated|unknown|withheld`、`revenue_as_of_date`、`revenue_source_id`。不再增加与 value_status 重复的 `revenue_is_estimated`。未知与拒绝披露不能都映射为 null 后丢失语义。历史自由文本进入人工复核队列；AI 只能提出候选，不得批量猜测并自动发布。

收入图表解除条件见 [phase-three-backend-handoff.md](phase-three-backend-handoff.md)：至少 30 个同币种、同周期、同 revenue_type 的可比合格案例，覆盖率至少 40%，且异常值规则通过黄金测试。跨币种或通胀换算在第三阶段默认不做。

## 8. 行业和技术分类决定

继续复用 `tags`、`case_tags`、`tag_aliases`，不在前端建立第二套字典。最小前向兼容方案：

1. 为标签新增稳定 `semantic_type = industry|technology|topic|other`；现有 `is_industry=1` 迁为 industry，保持旧字段兼容读取。
2. 增加可空 `parent_tag_id` 与关系级 `role = primary|secondary`；一个案例只能有一个主行业，辅助行业可多选。
3. alias 仍指向 canonical tag，唯一性应按 semantic type + normalized alias 校验。
4. AI 可以写入候选表并提供 evidence/source/reason；只有人工 approved 后才能创建/关联正式标签。
5. pending/rejected 标签、自由文本 `ai_tools` 和未分类非行业标签不进入正式技术看板。

## 9. 已确定统计原则

以下 20 条全部采用，没有因现有数据不足而放宽：正式统计仅用双层 published + verified；legacy/draft/excluded/已物理删除记录排除；unknown 不等于 0；缺失收入不算零；AI 推断不与实值混合；estimated 单独标记；趋势优先业务时间且不回退 created_at；无业务时间从趋势排除并计缺失；Asia/Shanghai；所有分布返回样本/缺失/更新时间；多标签按包含标签的唯一业务案例数；占比和可超过 100%；总量按 canonical caseId 去重；重复抓取不重复计数；前端不按分页计算总量；图表必须可下钻；只补明确范围内空月份；研究保留数据版本；后端从 filters 重建 AI 快照且不信任前端数值。

冲突是当前没有 canonical caseId 和 analytics dataVersion。解除前案例总量为 Red，后端不得静默改用 title 或 URL 充当永久身份。

## 10. 阻塞项

| 阻塞项 | 负责人 | 解除条件 |
|---|---|---|
| 案例 canonical identity/重复归并 | 数据后端 + 内容审核 | 23 组人工判定；新增 canonical/merge 规则和黄金测试 |
| 技术语义类型 | 数据后端 + 产品 | semantic_type、审核流、至少可用样本门槛 |
| 收入规范化 | 数据后端 + 产品 | 字段语义、人工迁移、覆盖与异常测试达标 |
| 案例业务时间 | 数据后端 + 内容审核 | 明确 occurred/published/as_of 日期，不回填猜测值 |
| 政策行业适用性 | 内容审核 + 数据后端 | 57 个存量政策完成 general/specific 与关系审核 |
| analytics dataVersion | 平台后端 | 快照/水位定义、ETag 和 stale 冲突实现 |
| 报告持久化与导出 | AI 后端 | 报告实体、权限、版本、导出作业契约 |

Phase B 可以先以 Green/Yellow 指标形成“部分生产上线”，但这不等于第三阶段产品完成。最终关闭还要求 technology taxonomy 和至少一个技术可视化、收入规范化与至少 30 个同口径可比案例（覆盖率至少 40%）、至少一个收入可视化、图表到 AI、报告/导出/反馈以及完整 40 题与性能门槛全部通过。

产品、API、交接、评测和实施顺序分别见 [phase-three-product-spec.md](phase-three-product-spec.md)、[phase-three-api-contract.md](phase-three-api-contract.md)、[phase-three-backend-handoff.md](phase-three-backend-handoff.md)、[phase-three-evaluation-plan.md](phase-three-evaluation-plan.md) 和 [phase-three-roadmap.md](phase-three-roadmap.md)。
