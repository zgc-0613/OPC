# SoloFirm 第三阶段 API 契约

> 契约版本：`phase3-api-v1`
> 指标定义：[analytics-metric-dictionary.md](analytics-metric-dictionary.md)
> 本文件只定义第三阶段接口，不表示接口已经实现。

## 1. 命名与边界决定

仓库现有公开资源使用 `/api/public/{resource}`，用户 AI 使用 `/api/ai/**`，响应使用 `Result<T>{code,message,data}`，异步研究返回 HTTP 202。第三阶段采用：

- 受登录用户访问的统计：`/api/analytics/**`，不放入 `/api/public`。
- AI 联动：继续位于 `/api/ai/research/**`，不创建第二套运行体系。
- 不添加全局 `/v1` 路径；版本放在 `metric.version`、`contractVersion` 和 dataVersion 中，符合现有路由。
- `/api/analytics/**` 必须接入现有 UserAuthInterceptor。缺少/过期令牌 401，disabled 用户 403；不改变管理员或模型 Provider 边界。

成功响应继续用 `Result<T>`。新接口的 HTTP status 与 body `code` 一致：200 成功、202 异步接受、304 ETag、400 参数错误、401/403 鉴权、404 资源、409 dataVersion/cursor 冲突、429 限流、503 聚合或 Agent 不可用。

### 1.1 Analytics 鉴权接线

`/api/analytics/**` 沿用平台用户会话，不引入 Spring Security 登录页、HTTP Basic 或匿名 Authentication。接线必须同时满足两层：

1. `SecurityConfig` 将 `/api/analytics/**` 加入 `requestMatchers(...).permitAll()`，其含义只是让请求穿过 Spring Security 到达 MVC，不代表业务匿名可访问。
2. `UserAuthInterceptor` 必须注册覆盖 `/api/analytics/**`。实现可以扩展当前 `AiWebMvcConfig`，也可以新增职责更清楚的 `AnalyticsWebMvcConfig`；两者只能注册一次，避免重复鉴权。

MVC interceptor 从 `Authorization: Bearer <userToken>` 调用现有 `UserAuthService.getCurrentUser` 并注入 `AUTHENTICATED_USER_ATTRIBUTE`。匿名、空 token 或过期用户返回 401，disabled 用户返回 403，有效用户才进入 Controller。管理员 token 不是平台用户 token，不能替代用户 token访问 analytics 或用户 AI/反馈端点。管理员质量接口继续使用现有 `/api/admin/**` 管理员鉴权。

必须用完整 Web 接线测试证明：合法用户不会在到达 interceptor 前被 Spring Security 拒绝；匿名/过期 401；disabled 403；有效用户 200；管理员 token 不能冒充用户；OPTIONS 保持现有放行行为。

## 2. 公共筛选参数

| 参数 | 类型/默认 | 约束和语义 |
|---|---|---|
| `metricId` | string；端点有唯一默认时可省 | 最大 80；必须在指标字典 allowlist，拒绝任意列名/表达式 |
| `dateFrom` | ISO date；按 metric 默认 | Asia/Shanghai 包含日；不能晚于 dateTo |
| `dateTo` | ISO date；默认今天 | 包含日；不能超过今天；服务端转为 `< nextDayStart` |
| `granularity` | metric 默认 | `day|month|quarter|year`；day 最多 90 天，总范围最多 10 年 |
| `regionId` | positive long；无 | 一个根地区；包含 descendants；必须存在且用户可访问 |
| `regionLevel` | string；`province` | `country|province|city|district`；当前生产只有 country/province，其他层级出现前返回空而非伪造，只影响 bucket 展开，不改变 regionId scope |
| `regionRole` | enum；GET 可按 entity/metric 默认，AI 联动必填 | `operation|registration|legacy_related_region|policy_applicability`。案例正式默认已审核 primary operation；当前 Yellow 相关地区图显式使用 legacy_related_region；政策固定 policy_applicability；不允许来源地区混入。`from-analytics` 的 filters 必须显式回传归一化后的值，禁止重新推断默认 |
| `industryTagIds` | repeated long；无 | 0–10 个 approved industry tags；多选默认 OR，结果回显 canonical 顺序 |
| `technologyTagIds` | repeated long；无 | 0–10 个 approved technology tags；非空时 taxonomy 未就绪或任一 ID 未批准，返回 409 `ANALYTICS_TECHNOLOGY_FILTER_UNAVAILABLE` |
| `revenueRange` | string；无 | 服务端版本化 bucket ID，不接受前端金额表达式；必须同时固定收入可比组 |
| `currency` | ISO 4217；无 | 收入 metric 必填，首期不换汇 |
| `revenuePeriod` | enum；无 | `monthly|annual|one_off|other`；收入 metric 必填 |
| `revenueType` | enum；无 | `revenue|profit|personal_income|other`；收入 metric 必填 |
| `valueStatus` | enum；`actual` | 收入业务分布仅允许 `actual|estimated` 并分系列；unknown/withheld 只由完整度缺失分解返回 |
| `cursor` | opaque string；无 | 最大 1,024；绑定 userId、filters、sort、dataVersion、expiry，禁止客户端解析 |
| `limit` | integer；50 | 1–100；仅下钻使用 |

数组使用重复 query parameter，不接收逗号拼接的任意长字符串。URL 总长度上限 4,096；规范化后 tag ID 去重升序。未知参数返回 `ANALYTICS_UNKNOWN_FILTER`，不静默忽略。

默认时间：policy trend/industry policy 为最近 36 个月；未来 case/technology trend 为最近 24 个月；收入为最近 36 个月；无业务时间的 total/distribution 为全部。空月只在明确 dateFrom/dateTo 内由后端补 0。

## 3. 统一统计响应

`Result.data` 为 `AnalyticsResponse<T>`：

| 字段 | 类型 | 契约 |
|---|---|---|
| `data` | endpoint-specific | 稳定 bucketId、原始 value/ratio；不返回格式化百分号 |
| `metric` | object | `metricId,name,version,readiness,definition,unit,multiLabel` |
| `filters` | object | 服务端规范化后的实际 filters，不原样回显恶意字符串 |
| `sampleSize` | integer | 进入指标分子/分布的唯一合格样本 |
| `missingCount` | integer | 合格集合中因本指标字段缺失而排除的数量 |
| `totalEligible` | integer | 非维度过滤后指标的合格分母；多标签 bucket 和可大于它 |
| `generatedAt` | ISO offset datetime | Asia/Shanghai 生成时间 |
| `dataVersion` | string | 最长 128，不透明且不可变；所有下钻/AI 请求必须绑定 |
| `freshness` | object | `lastEligibleUpdateAt,reviewWatermarkAt,ageSeconds,status` |
| `caveats` | array | `{code,message,severity,affectedFields}`；顺序稳定 |
| `drilldown` | object/null | `href,entityTypes,cursor,available`，绑定同版本和 filters |
| `status` | enum | `complete|partial|empty|unavailable` |

Bucket 契约：`bucketId` 稳定且非显示名，`label` 为当前中文名，`value` 为 integer/decimal 原值，`ratio` 为 0–1 Decimal 或 null，`sampleSize`、`missingCount`、`readiness` 和 `drilldown` 可逐 bucket 返回。标签改名不能改变 bucketId。

空数据返回 HTTP 200、`status=empty`、空数组/空 series、sampleSize=0，并保留 metric/filters/dataVersion/freshness；不能返回 404。部分数据返回 HTTP 200、`status=partial` 和至少一个 caveat。

## 4. 统计端点

### `GET /api/analytics/overview`

返回已开放概览 metrics 和质量向量。`data.cards[]` 每项含 metricId/value/unit/readiness；Red 指标 value 为 null，并给 blocking caveat。支持 region/industry/technology/date filters，但对不具业务时间的 case metric 拒绝 date filter，不能静默忽略。

### `GET /api/analytics/industries`

允许 metricId：`industry.case_count|industry.case_share|industry.new_case_trend|industry.region_distribution|industry.related_policy_count`。默认 `industry.case_count`。数据为 `buckets[]` 或 `series[]`；稳定排序 `value DESC, bucketId ASC`。返回 `multiLabel=true`。

### `GET /api/analytics/technologies`

允许 technology 指标，行为按筛选意图冻结：

- 未提交 technologyTagIds 且 taxonomy 未就绪：HTTP 200、`status=unavailable`、readiness Red、data 空、caveat `TECHNOLOGY_TAXONOMY_NOT_READY`。
- 显式提交非空 technologyTagIds，但 taxonomy 未就绪或任一 ID 未批准：HTTP 409、`ANALYTICS_TECHNOLOGY_FILTER_UNAVAILABLE`。
- taxonomy 已就绪但当前筛选没有合格数据：HTTP 200、`status=empty`、sampleSize=0。
- 任何状态都不得用 ai_tools 或自由文本标签自动生成临时技术排名。

### `GET /api/analytics/revenue`

允许 revenue 指标。缺少 currency/period/type 时 400 `REVENUE_COMPARABILITY_REQUIRED`。当前字段未就绪时返回 unavailable + `REVENUE_SCHEMA_NOT_READY`。达到门槛后 data 明示 valueStatus、currency、period、type、binVersion、metricVersion；actual/estimated 分系列，unknown/withheld 只在 missing breakdown。跨 bins 区间进入 `spans_multiple_bins`，percentile 仅用 actual point values 和 Hyndman-Fan Type 7。数值使用 Decimal 字符串或无损 JSON number 的项目统一规范，不能二进制浮点截断。

### `GET /api/analytics/regions`

允许 `region.case_count|region.policy_count|region.industry_distribution`。案例与政策的响应状态按以下矩阵冻结，不能把未就绪伪装成空数据：

| 请求语义 | HTTP/status/readiness | data/sample | 必要 caveat/标题 |
|---|---|---|---|
| case `operation|registration` 规范化关系未建立或未完成审核 | 200 / `unavailable` / Red | 空 data；sampleSize 不作为“0 个业务案例”展示 | `CASE_REGION_ROLE_NOT_READY` |
| case 显式 `legacy_related_region` | 200 / `partial` / Yellow | 返回现有合格 legacy buckets 与真实 sampleSize | `LEGACY_REGION_SEMANTICS`；标题固定“相关地区分布” |
| case `operation|registration` 已就绪，当前筛选确实无记录 | 200 / `empty` / 指标既定 readiness | 空 data；sampleSize=0 | 不使用 NOT_READY caveat |
| policy `policy_applicability` | 200 / complete、partial 或 empty | 按合格政策 | country bucket 不复制到省份 |

排序 value DESC, regionId ASC。region bucket 返回 `regionId,parentId,level,regionRole,label`。`quality.region_completeness` 即使 operation 覆盖率为 0 也可作为 Green 质量事实返回，但不得据此把 operation 业务分布标成可用。地图几何不属于此端点首期契约。

收入地区指标拒绝 registration 和 legacy_related_region，只接受 operation；不兼容组合返回 400 `ANALYTICS_REGION_ROLE_INVALID`。

### `GET /api/analytics/trends`

允许 `trend.policy_publish_time|trend.case_business_time|technology.adoption_trend|industry.new_case_trend`。每个 point 返回 `bucketId`（ISO period）、`periodStart`,`periodEndExclusive`,`value`,`sampleSize`,`missingCount`。空桶 `value=0` 仅在请求范围内，且 `isSyntheticEmptyBucket=true`，不是伪造记录。

### `GET /api/analytics/drilldown`

必填 `metricId,dataVersion`，并带与来源图一致的 filters 和可选 bucketId。必填 `entityType=case|policy|source`。返回：

- `items[]`：仅包含公开列表已允许的 bounded fields、evidence status 和内部 detail href。
- `nextCursor`、`hasMore`、`dataVersion`、规范化 filters、`aggregateValue`。
- 稳定排序：业务日期 DESC NULLS LAST、updated_at DESC、id DESC；case 无业务日期时只用 updated_at/id，但 UI 不把它称作时间趋势。

`aggregateValue` 必须与相同 metric/bucket 聚合值一致；下钻所有页去重 ID 合集必须等于聚合黄金集。cursor 过期或版本变化返回 409 `ANALYTICS_CURSOR_STALE`。

## 5. Phase A 首次研究请求

### 5.1 向后兼容的 taskContext

现有 `POST /api/ai/research/sessions/start` 保留 `profile,content,idempotencyKey,requestedIntent`，新增可选闭合对象 `taskContext`。旧客户端省略 taskContext 时继续现有 `agent-research-v2` 流程；`POST /sessions/{sessionId}/messages` 的 AgentMessageCreateDTO 不增加 taskContext。

taskContext 版本固定为 `phase3-task-v1`：

| 字段 | 类型与上限 | 规则 |
|---|---|---|
| `version` | string，必填 | 只能是 `phase3-task-v1` |
| `taskType` | enum，必填 | `case_analysis|case_comparison|technology_assessment|policy_lookup|source_verification|general_research`；必须与非 auto requestedIntent 完全一致 |
| `caseIds` | positive long array，0–3 | 唯一、保持选择顺序；不能从 content 解析或补充 |
| `comparisonDimensions` | string array，0–3 | 唯一；只允许 `businessModel|technicalPath|targetCustomer|outcome|regionalContext|evidenceStrength`；仅 `case_comparison` 可非空 |
| `outputDepth` | enum，可选，默认 standard | `concise|standard|deep`；只影响表达深度，不降低证据要求 |
| `technologyTagId` | positive long，可选 | 必须是已批准 technology tag |
| `technologyText` | string，可选，最多 120 code points | 未有正式 tag 时的研究主题，不进入正式统计 |
| `sourceId` | positive long，可选 | 只能由 `source_verification` 使用；指定来源模式必填，结论核验模式必须为空 |
| `applicationScenario` | string，可选，最多 500 code points | 应用场景 |
| `teamCapabilities` | string，可选，最多 500 code points | 团队能力边界 |
| `timeline` | string，可选，最多 120 code points | 计划周期，不解析成可信业务日期 |
| `existingResources` | string，可选，最多 500 code points | 本任务已有资源；不替代 profile.resources |
| `constraints` | string，可选，最多 800 code points | 合规、预算、技术或经营约束 |

taskContext 拒绝未知字段，canonical JSON 最多 8,000 UTF-8 bytes。`case_analysis` 必须恰有 1 个 caseId 且 comparisonDimensions 为空；`case_comparison` 必须有 2–3 个不重复 caseId 和 1–3 个不重复 comparisonDimensions；其他五类任务 comparisonDimensions 必须为空。服务端不补默认比较维度；UI 推荐项只有被用户确认并提交后才进入 taskContext。`technology_assessment` 必须至少有 technologyTagId 或非空 technologyText。`source_verification` 有且仅有两种模式：指定来源时 `sourceId` 必填；结论核验时 `sourceId=null/省略`，非空 `content` 是待核验结论。其他 taskType 禁止 `sourceId`，并不得携带不属于本任务的 caseIds/comparisonDimensions 或技术专属字段。taskContext 非空时 requestedIntent 不能是 auto，二者不一致返回 400 `PHASE3_TASK_INTENT_MISMATCH`。

technologyTagId 与 technologyText 同时存在时，已审核 ID 是分类和检索的权威值，technologyText 只保留用户措辞；ID 不存在、不是 technology 或未批准时整个请求失败，不能降级成自由文本。

taskContext 只在新研究首次提交时规范化、指纹化并冻结。后续消息不能提交 taskContext，也不能通过文本中的数字偷偷更换选中案例、技术、来源或研究边界；显式变更必须新建研究。taskContext 不存入 profile，profile 继续只承担现有创业画像字段。现有 `AgentSessionStartDTO.content` 的非空、最多 2,000 字契约保持不变：UI 可以生成可编辑的默认研究问题，但实际 start 请求必须提交非空 content，后端不得从空 content 猜测目标。

### 5.2 taskContext 持久化、幂等与回读

前向唯一存储位置冻结为 session 三字段：`ai_agent_sessions.task_context_version VARCHAR(40) NULL`、`task_context_json JSON NULL`、`task_context_hash CHAR(64) NULL`。不得借用会被澄清流程更新的 `research_context_json`，也不得塞入 `profile_json`。start 可以在事务外完成无副作用的字段校验、默认值补齐、数组去重/保序、canonical JSON 序列化和 hash 计算；幂等查找、权威证据资格复检、session/message/Run 创建、三字段保存、证据投影、Token 预留和成功 receipt 必须遵守 5.3 的同一事务顺序。三字段要么与首次成功创建一起写入，要么都不写。旧 session 三字段均为 null。

canonical JSON 使用 UTF-8、对象键按 Unicode code point 升序、无无意义空白、数字使用无损十进制规范表示、数组保持契约指定顺序；省略的可选字段在默认值补齐后参与 hash，显式 null 只在 schema 允许时保留。幂等身份必须同时绑定 `profile` canonical hash、`content` hash、`requestedIntent` 和 `task_context_hash`；同一 idempotencyKey 只有四者全部相同才返回原 receipt，任一不同返回 409 `PHASE3_IDEMPOTENCY_CONFLICT`。创建后 taskContext 不可通过 message、session update 或重试修改。

回读冻结如下：

- `POST /api/ai/research/sessions/start` 的 202 receipt 增加 `taskType`、`taskContextHash` 和完整规范化 `taskContext`；本版本不再保留 taskContextSummary 二选一，避免客户端无法无猜测恢复表单。
- `GET /api/ai/research/sessions/{sessionId}` 返回 owner 可读的完整规范化 `taskContext`、`taskContextHash`；旧 session 返回二者 null，并按 legacy `agent-research-v2` 渲染。
- `GET /api/ai/research/sessions` 与 `/sessions/history` 只返回 `taskType` 和必要摘要，不返回 applicationScenario、teamCapabilities、existingResources、constraints 等完整自由文本。
- `GET /api/ai/research/runs/{runId}` 返回 session 权威值派生的 `taskType` 与 `taskContextHash`，Run 不保存可漂移的 taskContext 副本。
- 所有读取继续按 session/run owner 校验；跨用户 ID 统一拒绝。session 永久清除时必须把 `task_context_json` 与 hash/version 一起擦除，日志、审计事件和错误不得打印上述自由文本全文。

### 5.3 服务端授权顺序

start 的唯一顺序如下，幂等命中判断先于首次证据资格校验：

1. 使用现有用户会话验证用户存在且为 active。
2. 验证请求字段、`taskContext`、`requestedIntent`、字符/数组/字节上限和任务交叉约束。
3. 规范化 profile、content、requestedIntent、taskContext。
4. 计算四项幂等身份：`profileHash`、`contentHash`、规范化 `requestedIntent`、`taskContextHash`。
5. 开启 start 数据库事务。
6. 按 `userId + idempotencyKey` 查询并锁定幂等记录。前向实现沿用当前用户级 `platform_users.assistant_history_revision` guard row 先序列化该用户的 start，再对已存在的 `ai_analysis_runs` 幂等行执行 `SELECT ... FOR UPDATE`；这样即使 key 尚无记录，并发相同请求也只有一个首次创建者。

锁内幂等分支固定如下：

- 四项身份完全一致且已有成功 receipt：直接返回原 202 receipt；不重新验证当前案例/来源资格，不创建新 session/message/Run，不重新预留或扣除 Token，不重建 taskSelectedEvidence/authorizedEvidence。首次成功后证据失效也遵守该精确重放语义，既有 Run 状态由运行期证据复检决定。
- 四项身份任一不同：返回 409 `PHASE3_IDEMPOTENCY_CONFLICT`；不执行证据资格校验，不产生任何新副作用，也不泄漏当前证据状态。
- 原请求仍在处理中：等待持锁事务结果或按现有受控 `in-progress` 语义返回；不得启动第二次创建、证据投影或 Token 预留。

仅当锁内确认幂等记录不存在时，才进入首次创建分支：

1. 在同一个 start 事务内用 `SELECT ... FOR UPDATE` 或当前架构等价行锁锁定所选案例、来源和必需的 case-source/policy-source 关系记录。
2. 在锁内重新读取并验证实体存在、当前用户可使用、published、verified、evidenceRevision、来源 title/publisher provenance、合法 HTTP(S) URL，以及必需的 case-source/policy-source link。事务外格式预检不能代替这次权威资格复检。
3. 任一显式 case 失败时返回 400 `PHASE3_CASE_NOT_ELIGIBLE` 并回滚整个事务；任一显式 source 失败时返回 400 `PHASE3_SOURCE_NOT_ELIGIBLE` 并回滚整个事务。响应只给安全稳定原因，不泄漏其他用户归属、内部审核状态或具体失败分支。
4. 全部资格通过后，在同一事务内创建 session、首条 user message、Run，保存规范化 taskContext，投影 taskSelectedEvidence，创建初始 authorizedEvidence，预留 Token，并保存成功幂等 receipt。
5. 任一步失败都回滚，不留下 session/message/Run、Token 预留、taskSelectedEvidence、authorizedEvidence 或半成品 receipt。

TOCTOU 与并发撤销语义固定如下：

- 管理员在 start 取得证据锁前撤销资格：首次请求在锁内权威复检失败，返回对应 400 且零副作用。
- 管理员在 start 持有证据锁期间撤销资格：管理操作等待 start 事务提交；该请求提交时属于合法受理，后续运行期复检发现失效时，既有 Run 可以进入 `evidence_insufficient`。
- 首次成功后证据失效，再以相同 key 和四项相同身份精确重放：返回原 202 receipt，不因当前证据状态改成 400，不重复创建、投影或扣费。
- 同 key 不同身份始终返回 409；并发相同请求只允许一个首次创建者，其余等待结果或走受控 `in-progress` 分支。

`evidence_insufficient` 只用于 start 已成功受理后：后续受控检索没有足够事实证据、Run 执行期间原合法证据失效或 revision/availability 改变，或合法证据只能支撑部分结论。该终态不得包含无来源事实；没有合法事实时 citations 必须为空，但可以保留 caveat、methodology 或明确标记且不伪装事实的 inference。首次创建时已经无效的显式 ID 绝不能转换成 `evidence_insufficient`。

### 5.4 structuredResult 版本

外层 Run 继续使用现有 `resultVersion=agent-research-v2`。Phase A 在 `structuredResult` 内新增 `phase3-structured-result-v1`。provider、model、promptVersion、tokenUsage 和 latency 仍以 Run envelope 为权威值，不在 structuredResult 复制第二套可漂移元数据。

`sourceIds` 是所有结论级证据引用的唯一字段，旧的其他结论级引用字段不得输出。`citations[]` 只按 sourceId 提供展示元数据，前端引用序号只是当前视图编号，不是稳定 ID。fact 必须至少引用一个当前 Run allowlist 内且生成时可用的 sourceId；inference/methodology 可以无 sourceIds，但必须如实标 kind。recommendation 含事实前提时，该前提必须拆成 fact ClaimItem 并携带 sourceIds。schema 无法验证“sourceId 属于当前 Run”及数组间基数相等，服务层必须在持久化前二次校验。

`evidenceVersion` 对所有 Phase A 新结果必填，格式为 `sha256:<64 lowercase hex>`，并且必须按本节下方的唯一规范输入独立重算；版本代表本次答案使用的不可变证据集合。当前 `ai_analysis_runs.evidence_hash` 只是入队身份占位 hash，完成时不会更新，禁止直接改名或当作 evidenceVersion。`dataVersion` 在 v1 顶层固定存在但可为 null：普通 Phase A 为 null；只有 from-analytics snapshot 研究必须为非空 Analytics dataVersion，二者不得互相代替。

unknown、缺失证据和不适用值统一用 `EvidenceSection.status=unknown|not_applicable` 表示，并要求非空 caveat；不得用空字符串、0 或编造默认值代替。以下 Draft 2020-12 schema 是 Java DTO、TypeScript 类型和契约测试的唯一结构来源：

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://solofirm.example/schemas/phase3-structured-result-v1.json",
  "title": "Phase3StructuredResultV1",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "schemaVersion", "taskType", "directAnswer", "keyFindings", "recommendations",
    "risks", "assumptions", "uncertainties", "nextQuestions", "citations",
    "taskSelectedEvidence", "authorizedEvidence", "evidenceCoverage", "confidence", "evidenceVersion",
    "dataVersion", "generatedAt", "taskResult"
  ],
  "properties": {
    "schemaVersion": { "type": "string", "const": "phase3-structured-result-v1", "maxLength": 27 },
    "taskType": {
      "type": "string", "maxLength": 24,
      "enum": [
        "case_analysis", "case_comparison", "technology_assessment",
        "policy_lookup", "source_verification", "general_research"
      ]
    },
    "directAnswer": { "type": "string", "minLength": 1, "maxLength": 600 },
    "keyFindings": { "$ref": "#/$defs/keyFindingList" },
    "recommendations": { "$ref": "#/$defs/recommendationList" },
    "risks": { "$ref": "#/$defs/riskList" },
    "assumptions": { "$ref": "#/$defs/singleClaimList" },
    "uncertainties": { "$ref": "#/$defs/singleClaimList" },
    "nextQuestions": {
      "type": "array", "maxItems": 2, "uniqueItems": true,
      "items": { "type": "string", "minLength": 1, "maxLength": 240 }
    },
    "citations": {
      "type": "array", "maxItems": 6,
      "items": { "$ref": "#/$defs/citation" }
    },
    "taskSelectedEvidence": { "$ref": "#/$defs/taskSelectedEvidence" },
    "authorizedEvidence": { "$ref": "#/$defs/authorizedEvidence" },
    "evidenceCoverage": { "$ref": "#/$defs/evidenceCoverage" },
    "confidence": { "$ref": "#/$defs/confidence" },
    "evidenceVersion": {
      "type": "string", "maxLength": 71, "pattern": "^sha256:[0-9a-f]{64}$"
    },
    "dataVersion": {
      "type": ["string", "null"], "minLength": 1, "maxLength": 128
    },
    "generatedAt": { "type": "string", "maxLength": 35, "format": "date-time" },
    "taskResult": {
      "oneOf": [
        { "$ref": "#/$defs/caseAnalysisResult" },
        { "$ref": "#/$defs/caseComparisonResult" },
        { "$ref": "#/$defs/technologyAssessmentResult" },
        { "$ref": "#/$defs/policyLookupResult" },
        { "$ref": "#/$defs/sourceVerificationResult" },
        { "$ref": "#/$defs/generalResearchResult" }
      ]
    }
  },
  "allOf": [
    { "if": { "properties": { "taskType": { "const": "case_analysis" } } }, "then": { "properties": { "taskResult": { "$ref": "#/$defs/caseAnalysisResult" } } } },
    { "if": { "properties": { "taskType": { "const": "case_comparison" } } }, "then": { "properties": { "taskResult": { "$ref": "#/$defs/caseComparisonResult" } } } },
    { "if": { "properties": { "taskType": { "const": "technology_assessment" } } }, "then": { "properties": { "taskResult": { "$ref": "#/$defs/technologyAssessmentResult" } } } },
    { "if": { "properties": { "taskType": { "const": "policy_lookup" } } }, "then": { "properties": { "taskResult": { "$ref": "#/$defs/policyLookupResult" } } } },
    { "if": { "properties": { "taskType": { "const": "source_verification" } } }, "then": { "properties": { "taskResult": { "$ref": "#/$defs/sourceVerificationResult" } } } },
    { "if": { "properties": { "taskType": { "const": "general_research" } } }, "then": { "properties": { "taskResult": { "$ref": "#/$defs/generalResearchResult" } } } }
  ],
  "$defs": {
    "confidence": { "type": "string", "maxLength": 6, "enum": ["low", "medium", "high"] },
    "positiveLong": { "type": "integer", "minimum": 1, "maximum": 9223372036854775807 },
    "claimItem": {
      "type": "object",
      "additionalProperties": false,
      "required": ["id", "kind", "text", "sourceIds", "confidence", "missingEvidence"],
      "properties": {
        "id": { "type": "string", "maxLength": 64, "pattern": "^[A-Za-z][A-Za-z0-9_-]{0,63}$" },
        "kind": { "type": "string", "maxLength": 11, "enum": ["fact", "inference", "methodology"] },
        "text": { "type": "string", "minLength": 1, "maxLength": 320 },
        "sourceIds": {
          "type": "array", "maxItems": 6, "uniqueItems": true,
          "items": { "$ref": "#/$defs/positiveLong" }
        },
        "confidence": { "$ref": "#/$defs/confidence" },
        "missingEvidence": { "type": "boolean" }
      },
      "allOf": [
        {
          "if": { "properties": { "kind": { "const": "fact" } }, "required": ["kind"] },
          "then": { "properties": { "sourceIds": { "minItems": 1 }, "missingEvidence": { "const": false } } }
        }
      ]
    },
    "keyFindingList": {
      "type": "array", "maxItems": 2,
      "items": { "$ref": "#/$defs/claimItem" }
    },
    "recommendationList": {
      "type": "array", "maxItems": 3,
      "items": { "$ref": "#/$defs/claimItem" }
    },
    "riskList": {
      "type": "array", "maxItems": 2,
      "items": { "$ref": "#/$defs/claimItem" }
    },
    "singleClaimList": {
      "type": "array", "maxItems": 1,
      "items": { "$ref": "#/$defs/claimItem" }
    },
    "evidenceSection": {
      "type": "object",
      "additionalProperties": false,
      "required": ["status", "items", "caveat"],
      "properties": {
        "status": { "type": "string", "maxLength": 14, "enum": ["known", "unknown", "not_applicable"] },
        "items": {
          "type": "array", "maxItems": 6,
          "items": { "$ref": "#/$defs/claimItem" }
        },
        "caveat": { "type": ["string", "null"], "minLength": 1, "maxLength": 240 }
      },
      "allOf": [
        {
          "if": { "properties": { "status": { "const": "known" } }, "required": ["status"] },
          "then": { "properties": { "items": { "minItems": 1 } } }
        },
        {
          "if": { "properties": { "status": { "enum": ["unknown", "not_applicable"] } }, "required": ["status"] },
          "then": {
            "properties": {
              "items": { "maxItems": 0 },
              "caveat": { "type": "string", "minLength": 1, "maxLength": 240 }
            }
          }
        }
      ]
    },
    "citation": {
      "type": "object",
      "additionalProperties": false,
      "required": ["sourceId", "title", "publisher", "url", "evidenceRevision", "availability"],
      "properties": {
        "sourceId": { "$ref": "#/$defs/positiveLong" },
        "title": { "type": "string", "minLength": 1, "maxLength": 240 },
        "publisher": { "type": ["string", "null"], "minLength": 1, "maxLength": 160 },
        "url": { "type": "string", "maxLength": 2048, "pattern": "^https?://" },
        "evidenceRevision": { "type": "integer", "minimum": 0 },
        "availability": { "type": "string", "maxLength": 11, "enum": ["current", "stale", "unavailable"] }
      }
    },
    "taskSelectedEvidence": {
      "type": "object",
      "additionalProperties": false,
      "required": ["caseIds", "policyIds", "sourceIds"],
      "properties": {
        "caseIds": { "type": "array", "maxItems": 3, "uniqueItems": true, "items": { "$ref": "#/$defs/positiveLong" } },
        "policyIds": { "type": "array", "maxItems": 0, "uniqueItems": true, "items": { "$ref": "#/$defs/positiveLong" } },
        "sourceIds": { "type": "array", "maxItems": 1, "uniqueItems": true, "items": { "$ref": "#/$defs/positiveLong" } }
      }
    },
    "authorizedEvidence": {
      "type": "object",
      "additionalProperties": false,
      "required": ["caseIds", "policyIds", "sourceIds"],
      "properties": {
        "caseIds": { "type": "array", "maxItems": 120, "uniqueItems": true, "items": { "$ref": "#/$defs/positiveLong" } },
        "policyIds": { "type": "array", "maxItems": 120, "uniqueItems": true, "items": { "$ref": "#/$defs/positiveLong" } },
        "sourceIds": { "type": "array", "maxItems": 120, "uniqueItems": true, "items": { "$ref": "#/$defs/positiveLong" } }
      }
    },
    "evidenceCoverage": {
      "type": "object",
      "additionalProperties": false,
      "required": ["factClaimCount", "citedFactClaimCount", "missingEvidenceFactCount", "ratio"],
      "properties": {
        "factClaimCount": { "type": "integer", "minimum": 0, "maximum": 6 },
        "citedFactClaimCount": { "type": "integer", "minimum": 0, "maximum": 6 },
        "missingEvidenceFactCount": { "type": "integer", "minimum": 0, "maximum": 6 },
        "ratio": { "type": ["number", "null"], "minimum": 0, "maximum": 1 }
      }
    },
    "caseAnalysisResult": {
      "type": "object", "additionalProperties": false,
      "required": ["type", "caseId", "evidenceStatus", "sections"],
      "properties": {
        "type": { "type": "string", "const": "case_analysis", "maxLength": 13 },
        "caseId": { "$ref": "#/$defs/positiveLong" },
        "evidenceStatus": { "type": "string", "maxLength": 12, "enum": ["sufficient", "partial", "insufficient"] },
        "sections": {
          "type": "object", "additionalProperties": false,
          "required": ["businessModel", "targetCustomers", "revenueModel", "costsAndResources", "technicalRoute", "successFactors", "replicableElements", "nonReplicableConditions", "userFit", "actions"],
          "properties": {
            "businessModel": { "$ref": "#/$defs/evidenceSection" },
            "targetCustomers": { "$ref": "#/$defs/evidenceSection" },
            "revenueModel": { "$ref": "#/$defs/evidenceSection" },
            "costsAndResources": { "$ref": "#/$defs/evidenceSection" },
            "technicalRoute": { "$ref": "#/$defs/evidenceSection" },
            "successFactors": { "$ref": "#/$defs/evidenceSection" },
            "replicableElements": { "$ref": "#/$defs/evidenceSection" },
            "nonReplicableConditions": { "$ref": "#/$defs/evidenceSection" },
            "userFit": { "$ref": "#/$defs/evidenceSection" },
            "actions": { "$ref": "#/$defs/evidenceSection" }
          }
        }
      }
    },
    "comparisonBaseline": {
      "type": "object", "additionalProperties": false,
      "required": ["caseId", "evidenceStatus", "missingFields"],
      "properties": {
        "caseId": { "$ref": "#/$defs/positiveLong" },
        "evidenceStatus": { "type": "string", "maxLength": 12, "enum": ["sufficient", "partial", "insufficient"] },
        "missingFields": { "type": "array", "maxItems": 6, "uniqueItems": true, "items": { "type": "string", "minLength": 1, "maxLength": 80 } }
      }
    },
    "dimensionComparison": {
      "type": "object", "additionalProperties": false,
      "required": ["dimension", "analysis"],
      "properties": {
        "dimension": { "type": "string", "maxLength": 18, "enum": ["businessModel", "technicalPath", "targetCustomer", "outcome", "regionalContext", "evidenceStrength"] },
        "analysis": { "$ref": "#/$defs/evidenceSection" }
      }
    },
    "caseComparisonResult": {
      "type": "object", "additionalProperties": false,
      "required": ["type", "caseIds", "dimensions", "baselines", "commonalities", "differences", "comparisons", "revenueComparability", "regionalAndPolicyContext", "userFit", "conclusion"],
      "properties": {
        "type": { "type": "string", "const": "case_comparison", "maxLength": 15 },
        "caseIds": { "type": "array", "minItems": 2, "maxItems": 3, "uniqueItems": true, "items": { "$ref": "#/$defs/positiveLong" } },
        "dimensions": { "type": "array", "minItems": 1, "maxItems": 3, "uniqueItems": true, "items": { "type": "string", "maxLength": 18, "enum": ["businessModel", "technicalPath", "targetCustomer", "outcome", "regionalContext", "evidenceStrength"] } },
        "baselines": { "type": "array", "minItems": 2, "maxItems": 3, "items": { "$ref": "#/$defs/comparisonBaseline" } },
        "commonalities": { "$ref": "#/$defs/evidenceSection" },
        "differences": { "$ref": "#/$defs/evidenceSection" },
        "comparisons": { "type": "array", "minItems": 1, "maxItems": 3, "items": { "$ref": "#/$defs/dimensionComparison" } },
        "revenueComparability": { "type": "string", "maxLength": 14, "enum": ["comparable", "not_comparable", "unknown"] },
        "regionalAndPolicyContext": { "$ref": "#/$defs/evidenceSection" },
        "userFit": { "$ref": "#/$defs/evidenceSection" },
        "conclusion": { "$ref": "#/$defs/evidenceSection" }
      }
    },
    "assessmentDimension": {
      "type": "object", "additionalProperties": false,
      "required": ["dimension", "level", "rationale", "confidence", "missingEvidence"],
      "properties": {
        "dimension": { "type": "string", "maxLength": 25, "enum": ["maturity", "scenario_fit", "implementation_complexity"] },
        "level": { "type": "string", "maxLength": 7, "enum": ["low", "medium", "high", "unknown"] },
        "rationale": { "$ref": "#/$defs/claimItem" },
        "confidence": { "$ref": "#/$defs/confidence" },
        "missingEvidence": { "type": "boolean" }
      }
    },
    "technologyAssessmentResult": {
      "type": "object", "additionalProperties": false,
      "required": ["type", "technology", "dimensions", "costStructure", "dataAndInfrastructure", "capabilityGaps", "dependencies", "complianceRisks", "operatingRisks", "alternatives", "roadmap", "experiments", "supportingCases", "relatedPolicies"],
      "properties": {
        "type": { "type": "string", "const": "technology_assessment", "maxLength": 21 },
        "technology": {
          "type": "object", "additionalProperties": false,
          "properties": {
            "tagId": { "type": ["integer", "null"], "minimum": 1 },
            "text": { "type": ["string", "null"], "minLength": 1, "maxLength": 120 }
          },
          "anyOf": [
            { "required": ["tagId"], "properties": { "tagId": { "type": "integer", "minimum": 1 } } },
            { "required": ["text"], "properties": { "text": { "type": "string", "minLength": 1, "maxLength": 120 } } }
          ]
        },
        "dimensions": {
          "type": "array", "minItems": 3, "maxItems": 3,
          "items": { "$ref": "#/$defs/assessmentDimension" },
          "allOf": [
            { "contains": { "properties": { "dimension": { "const": "maturity" } }, "required": ["dimension"] }, "minContains": 1, "maxContains": 1 },
            { "contains": { "properties": { "dimension": { "const": "scenario_fit" } }, "required": ["dimension"] }, "minContains": 1, "maxContains": 1 },
            { "contains": { "properties": { "dimension": { "const": "implementation_complexity" } }, "required": ["dimension"] }, "minContains": 1, "maxContains": 1 }
          ]
        },
        "costStructure": { "$ref": "#/$defs/evidenceSection" },
        "dataAndInfrastructure": { "$ref": "#/$defs/evidenceSection" },
        "capabilityGaps": { "$ref": "#/$defs/evidenceSection" },
        "dependencies": { "$ref": "#/$defs/evidenceSection" },
        "complianceRisks": { "$ref": "#/$defs/evidenceSection" },
        "operatingRisks": { "$ref": "#/$defs/evidenceSection" },
        "alternatives": { "$ref": "#/$defs/evidenceSection" },
        "roadmap": { "$ref": "#/$defs/evidenceSection" },
        "experiments": { "$ref": "#/$defs/evidenceSection" },
        "supportingCases": { "type": "array", "maxItems": 6, "uniqueItems": true, "items": { "$ref": "#/$defs/positiveLong" } },
        "relatedPolicies": { "type": "array", "maxItems": 6, "uniqueItems": true, "items": { "$ref": "#/$defs/positiveLong" } }
      }
    },
    "policyLookupResult": {
      "type": "object", "additionalProperties": false,
      "required": ["type", "policyIds", "applicableRegions", "applicableIndustries", "validity", "eligibilityConditions", "supportMeasures", "conflicts", "expirationRisks", "verificationNeeded"],
      "properties": {
        "type": { "type": "string", "const": "policy_lookup", "maxLength": 13 },
        "policyIds": { "type": "array", "maxItems": 6, "uniqueItems": true, "items": { "$ref": "#/$defs/positiveLong" } },
        "applicableRegions": { "$ref": "#/$defs/evidenceSection" },
        "applicableIndustries": { "$ref": "#/$defs/evidenceSection" },
        "validity": { "$ref": "#/$defs/evidenceSection" },
        "eligibilityConditions": { "$ref": "#/$defs/evidenceSection" },
        "supportMeasures": { "$ref": "#/$defs/evidenceSection" },
        "conflicts": { "$ref": "#/$defs/evidenceSection" },
        "expirationRisks": { "$ref": "#/$defs/evidenceSection" },
        "verificationNeeded": { "$ref": "#/$defs/evidenceSection" }
      }
    },
    "sourceVerificationResult": {
      "type": "object", "additionalProperties": false,
      "required": ["type", "mode", "sourceId", "verdict", "publisherAssessment", "supportedClaims", "unsupportedClaims", "conflicts", "invalidityReasons"],
      "properties": {
        "type": { "type": "string", "const": "source_verification", "maxLength": 19 },
        "mode": { "type": "string", "maxLength": 15, "enum": ["selected_source", "claim_search"] },
        "sourceId": { "type": ["integer", "null"], "minimum": 1 },
        "verdict": { "type": "string", "maxLength": 18, "enum": ["supports", "partially_supports", "does_not_support", "conflicting", "insufficient"] },
        "publisherAssessment": { "$ref": "#/$defs/evidenceSection" },
        "supportedClaims": { "$ref": "#/$defs/evidenceSection" },
        "unsupportedClaims": { "$ref": "#/$defs/evidenceSection" },
        "conflicts": { "$ref": "#/$defs/evidenceSection" },
        "invalidityReasons": { "$ref": "#/$defs/evidenceSection" }
      },
      "allOf": [
        { "if": { "properties": { "mode": { "const": "selected_source" } }, "required": ["mode"] }, "then": { "properties": { "sourceId": { "type": "integer", "minimum": 1 } } } },
        { "if": { "properties": { "mode": { "const": "claim_search" } }, "required": ["mode"] }, "then": { "properties": { "sourceId": { "type": "null" } } } }
      ]
    },
    "researchSection": {
      "type": "object", "additionalProperties": false,
      "required": ["id", "title", "content"],
      "properties": {
        "id": { "type": "string", "maxLength": 64, "pattern": "^[A-Za-z][A-Za-z0-9_-]{0,63}$" },
        "title": { "type": "string", "minLength": 1, "maxLength": 120 },
        "content": { "$ref": "#/$defs/evidenceSection" }
      }
    },
    "generalResearchResult": {
      "type": "object", "additionalProperties": false,
      "required": ["type", "sections"],
      "properties": {
        "type": { "type": "string", "const": "general_research", "maxLength": 16 },
        "sections": { "type": "array", "minItems": 1, "maxItems": 3, "items": { "$ref": "#/$defs/researchSection" } }
      }
    }
  }
}
```

Schema 之外的服务语义断言也是 v1 契约的一部分，不能由模型输出绕过：

1. `taskSelectedEvidence` 只表示规范化 taskContext 中用户明确选择的实体，必须由服务端投影：caseIds 最多 3、sourceIds 最多 1、policyIds 在 v1 恒为空。`case_analysis` 精确等于一个 taskContext caseId；`case_comparison` 精确等于 2–3 个 taskContext caseIds；selected-source 核验精确等于一个 taskContext sourceId；其他任务三个数组均为空。模型、前端和工具不得补充或改变它。
2. `authorizedEvidence` 只表示当前 Run 经服务端工具执行、owner/状态/来源链/revision 验证后授权的全部实体；前端不得提交，模型不得扩展。单字段 `caseIds|policyIds|sourceIds` 各最多 120，且 `caseIds + policyIds` 的合计也不得超过 120。依据是现有 Agent 配置 `agentMaxToolCalls <= 12` 与 `search_cases/search_policies limit <= 10`；`compare_cases/get_source` 只能消费依赖授权，不产生新 ID。每轮初始或继续计划仍受 `AgentResearchContract.MAX_PLANNED_TOOLS=4` 约束，本契约没有放宽任何工具限制。
3. taskSelectedEvidence 中每个 case/source 必须出现在 authorizedEvidence 对应集合。显式选中的案例或来源在提交前缺少资格或合法来源链时按 5.3 返回 400 且零持久化；只有已合法受理的 Run 在后续检索不足或执行期证据变化时才能生成外层 `evidence_insufficient`。该终态保留仍合法的 provenance，但不得包含无来源 fact；没有合法事实时 citations 必须为空。
4. 所有顶层 ClaimItem 数组、所有 `EvidenceSection.items`、技术评估三个 `rationale` 与一般研究 section content 中的 ClaimItem 合计不得超过 6（`AgentResearchContract.MAX_STATEMENTS`）。EvidenceSection 对象总数不得超过 10。JSON Schema 只能限制单数组，服务端必须递归计数后再持久化。
5. 每个 ClaimItem.sourceIds 必须是 authorizedEvidence.sourceIds 子集；fact 至少一个 sourceId 且 `missingEvidence=false`。inference/methodology 可以没有 sourceIds，但必须保留 kind，不能伪装事实。所有事实 sourceId 必须由 citations 一一覆盖，citations 不得含未被任何 ClaimItem 使用的来源。`case_analysis.taskResult.sections` 内的 fact 按定义只陈述该 `taskResult.caseId`，其每个 sourceId 都必须通过 fixture/Run provenance link 连接到该案例。`case_comparison.comparisons[].analysis` 内的 fact 按定义比较全部 `taskResult.caseIds`，其 sourceIds 必须为每个案例至少覆盖一条与该案例相连的授权来源；只属于案例 B 的来源不能支撑案例 A 或全案例比较事实。`policy_lookup.taskResult` 内的 fact 只能引用至少连接到一个 `taskResult.policyIds` 的授权来源；单政策结果中的每个事实来源必须连接该 policyId。需要只陈述无法结构化归属的单侧实体时必须改写为 inference/unknown 或留待新 schemaVersion，v1 不从自由文本猜测事实主体。
6. `supportingCases` 是 authorizedEvidence.caseIds 子集；`relatedPolicies` 和 `policyLookupResult.policyIds` 是 authorizedEvidence.policyIds 子集。数组最多 6，不能因为 authorizedEvidence 上限更大而扩大展示结果。
7. comparison 的 caseIds、baselines、taskContext.caseIds 和 taskSelectedEvidence.caseIds 集合相同；dimensions 必须为 1–3 个受控唯一值，且与 comparisons.dimension 一一对应。其他任务的 taskContext.comparisonDimensions 必须为空；服务端不得静默添加默认维度。
8. evidenceCoverage 从合法 ClaimItem 与 citation 覆盖重算，不信任模型手写值；`factClaimCount=0` 时 ratio 为 null，否则 ratio=`citedFactClaimCount/factClaimCount`，三个计数都不得超过 6。普通 Phase A 的 dataVersion 为 null，from-analytics 必须非空；evidenceVersion 永远必填且不得冒充 dataVersion。
9. 服务端用固定渲染器从通过校验的结果生成兼容 Markdown；持久化 Assistant 文本超过 `AgentSessionService.MAX_ASSISTANT_MESSAGE_LENGTH=12000` 时拒绝，不得截断成貌似完整的答案。授权 ID 全集和完整 URL 不进入正文渲染预算，仍留在结构字段/引用记录。

现有模型合成上限为 3200 tokens；v1 还固定 directAnswer 600 字符、ClaimItem 合计 6、citations 6、nextQuestions 2。更长回答、更多 ClaimItem/citations 或更大的任务数组必须发布新 schemaVersion，并重新评审模型 Token、12000 字符持久化、前端渲染和数据库限制。

旧 structuredResult 没有 schemaVersion 时按 legacy `agent-research-v2` 渲染，保留现有 Markdown 和通用字段，不回写、不批量迁移、不伪造新扩展。新客户端对未知 schemaVersion 只显示 directAnswer/原 Markdown（若外层存在）和“结果版本暂不支持”，不尝试解释 taskResult；服务端保留原 JSON 供兼容升级。

### 5.5 测试专用 Run 证据环境与 evidenceVersion

以下六段都是**契约占位夹具**，ID、URL、时间、哈希输入和文字不代表生产数据。每段外层包含 `taskContext`、`runEvidenceFixture` 和 `structuredResult`；只有 structuredResult 是上述 JSON Schema 的实例。`runEvidenceFixture` 只为文档契约测试提供服务端事实环境，不属于 `phase3-structured-result-v1`，生产 API 不接收，用户结果不持久化，模型和前端也不得提交。

`runEvidenceFixture.fixtureVersion` 固定为 `phase3-run-evidence-fixture-v1`，并固定包含 `cases,policies,sources,caseSourceLinks,policySourceLinks` 五个数组。cases/policies 项固定为 `id,evidenceRevision,contentHash,eligibility`；sources 项固定为 `id,title,publisher,url,evidenceRevision,contentHash,eligibility`；links 分别固定为 `caseId,sourceId` 或 `policyId,sourceId`。ID 是正整数，revision 是非负整数，contentHash 是 `sha256:<64 lowercase hex>`，eligibility 只允许 `published_verified|ineligible|unavailable`。

唯一性验证先于排序和 evidenceVersion 计算。`cases[].id` 在 cases 内唯一，`policies[].id` 在 policies 内唯一，`sources[].id` 在 sources 内唯一；不同 entityType 之间允许使用相同数字 ID。`caseSourceLinks` 的 `(caseId, sourceId)` pair 唯一，`policySourceLinks` 的 `(policyId, sourceId)` pair 唯一。同一 source 可以连接多个不同 case/policy，同一 case/policy 也可以连接多个不同 source，只禁止完全重复的 link pair。重复实体 ID 或 link pair 必须使契约验证失败，不得静默去重，也不得让重复记录进入 canonical evidenceVersion 输入。

唯一性和 link 引用完整性通过后，实体数组按 id 升序；caseSourceLinks 按 caseId、sourceId 升序，policySourceLinks 按 policyId、sourceId 升序。排序只生成稳定顺序，不能代替唯一性验证。link 两端必须存在于同一 fixture。每个 authorized case/policy 至少有一条指向 authorized published_verified source 的合法 link；selected-source 的 sourceId 必须存在且为 published_verified。正向夹具的 authorizedEvidence 三个 ID 数组必须与 fixture 中实际授权且 eligibility=published_verified 的实体精确相同。citation 的 title、publisher、url、evidenceRevision 必须与 source fixture 完全一致。

evidenceVersion 的唯一规范输入是按以下字段顺序构造的对象：`schemaVersion,cases,policies,sources,caseSourceLinks,policySourceLinks`。schemaVersion 固定为 `phase3-structured-result-v1`；三个实体数组只保留 authorizedEvidence 中的记录，且每项只按 `id,evidenceRevision,contentHash,eligibility` 顺序输出，source 的 title/publisher/url 不进入版本对象，由 contentHash 覆盖内容变化；links 只保留当前授权实体之间的合法关系并使用上述排序。

规范对象使用 UTF-8 紧凑 JSON：无 BOM、缩进、无意义空白、尾随空格或结尾换行；对象字段严格使用文档给定顺序；数字是十进制整数；不允许 null 或浮点数。对这些 JSON 字节执行 SHA-256，最终写成 `sha256:<64 lowercase hex>`。实现可以沿用 Jackson 和现有 SHA-256 工具，但不得依赖未声明的 Map 遍历顺序。空证据的规范输入固定为：

```json
{"schemaVersion":"phase3-structured-result-v1","cases":[],"policies":[],"sources":[],"caseSourceLinks":[],"policySourceLinks":[]}
```

六个夹具必须同时通过 Draft 2020-12、完整服务语义、实体/link 唯一性、来源链和 evidenceVersion 独立重算；任一 ID、revision、contentHash、eligibility 或 link 改变后，原 evidenceVersion 必须失败。重复 ID/pair 必须在 canonical 排序和哈希前被拒绝；不得用排序、集合转换或哈希结果替代重复检查。

`case_analysis` 最小合法示例：

```json
{
  "taskContext": {
    "version": "phase3-task-v1", "taskType": "case_analysis",
    "caseIds": [1001], "comparisonDimensions": [], "outputDepth": "standard"
  },
  "runEvidenceFixture": {
    "fixtureVersion": "phase3-run-evidence-fixture-v1",
    "cases": [
      { "id": 1001, "evidenceRevision": 1, "contentHash": "sha256:6eabaecbec85a148bba05cdbecfb71ee5d9dd3bf6efd8dae8ea3f320dfecd440", "eligibility": "published_verified" }
    ],
    "policies": [],
    "sources": [
      { "id": 9001, "title": "契约占位来源 A", "publisher": "契约发布者", "url": "https://example.invalid/source/9001", "evidenceRevision": 1, "contentHash": "sha256:87ed45da273c371046ee83570ada5a605bc5f311514595497c1628bdbee3cac2", "eligibility": "published_verified" }
    ],
    "caseSourceLinks": [{ "caseId": 1001, "sourceId": 9001 }],
    "policySourceLinks": []
  },
  "structuredResult": {
    "schemaVersion": "phase3-structured-result-v1",
    "taskType": "case_analysis",
    "directAnswer": "契约占位：该案例的商业模式有一条已核验来源支撑，其余维度仍需补证。",
    "keyFindings": [], "recommendations": [], "risks": [], "assumptions": [], "uncertainties": [], "nextQuestions": [],
    "citations": [
      { "sourceId": 9001, "title": "契约占位来源 A", "publisher": "契约发布者", "url": "https://example.invalid/source/9001", "evidenceRevision": 1, "availability": "current" }
    ],
    "taskSelectedEvidence": { "caseIds": [1001], "policyIds": [], "sourceIds": [] },
    "authorizedEvidence": { "caseIds": [1001], "policyIds": [], "sourceIds": [9001] },
    "evidenceCoverage": { "factClaimCount": 1, "citedFactClaimCount": 1, "missingEvidenceFactCount": 0, "ratio": 1 },
    "confidence": "medium",
    "evidenceVersion": "sha256:9d7a818ff4f57a7dec741fb8d58f4686f7f2b2ccce3604971bd3318689430ee2",
    "dataVersion": null,
    "generatedAt": "2030-01-01T00:00:00+08:00",
    "taskResult": {
      "type": "case_analysis", "caseId": 1001, "evidenceStatus": "partial",
      "sections": {
        "businessModel": { "status": "known", "items": [{ "id": "business_model", "kind": "fact", "text": "契约占位：来源 A 描述了该案例的商业模式。", "sourceIds": [9001], "confidence": "medium", "missingEvidence": false }], "caveat": null },
        "targetCustomers": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
        "revenueModel": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
        "costsAndResources": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
        "technicalRoute": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
        "successFactors": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
        "replicableElements": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
        "nonReplicableConditions": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
        "userFit": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
        "actions": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" }
      }
    }
  }
}
```

`case_comparison` 最小合法示例：

```json
{
  "taskContext": {
    "version": "phase3-task-v1", "taskType": "case_comparison",
    "caseIds": [1001, 1002], "comparisonDimensions": ["businessModel"], "outputDepth": "standard"
  },
  "runEvidenceFixture": {
    "fixtureVersion": "phase3-run-evidence-fixture-v1",
    "cases": [
      { "id": 1001, "evidenceRevision": 1, "contentHash": "sha256:6eabaecbec85a148bba05cdbecfb71ee5d9dd3bf6efd8dae8ea3f320dfecd440", "eligibility": "published_verified" },
      { "id": 1002, "evidenceRevision": 1, "contentHash": "sha256:75787b175aeb609a6815325162865876bb08ece55cf1c5d29b2a31ce795a1343", "eligibility": "published_verified" }
    ],
    "policies": [],
    "sources": [
      { "id": 9001, "title": "契约占位来源 A", "publisher": "契约发布者", "url": "https://example.invalid/source/9001", "evidenceRevision": 1, "contentHash": "sha256:87ed45da273c371046ee83570ada5a605bc5f311514595497c1628bdbee3cac2", "eligibility": "published_verified" },
      { "id": 9002, "title": "契约占位来源 B", "publisher": "契约发布者", "url": "https://example.invalid/source/9002", "evidenceRevision": 1, "contentHash": "sha256:d42b7e46717b0d312496a1168c5b48145b65853b335d934b219050d88240a1f0", "eligibility": "published_verified" }
    ],
    "caseSourceLinks": [
      { "caseId": 1001, "sourceId": 9001 },
      { "caseId": 1002, "sourceId": 9002 }
    ],
    "policySourceLinks": []
  },
  "structuredResult": {
    "schemaVersion": "phase3-structured-result-v1",
    "taskType": "case_comparison",
    "directAnswer": "契约占位：两个案例的商业模式可以基于各自已核验来源进行一维比较。",
    "keyFindings": [], "recommendations": [], "risks": [], "assumptions": [], "uncertainties": [], "nextQuestions": [],
    "citations": [
      { "sourceId": 9001, "title": "契约占位来源 A", "publisher": "契约发布者", "url": "https://example.invalid/source/9001", "evidenceRevision": 1, "availability": "current" },
      { "sourceId": 9002, "title": "契约占位来源 B", "publisher": "契约发布者", "url": "https://example.invalid/source/9002", "evidenceRevision": 1, "availability": "current" }
    ],
    "taskSelectedEvidence": { "caseIds": [1001, 1002], "policyIds": [], "sourceIds": [] },
    "authorizedEvidence": { "caseIds": [1001, 1002], "policyIds": [], "sourceIds": [9001, 9002] },
    "evidenceCoverage": { "factClaimCount": 1, "citedFactClaimCount": 1, "missingEvidenceFactCount": 0, "ratio": 1 },
    "confidence": "medium",
    "evidenceVersion": "sha256:aa512915e31bfdf3be5d0cc242ca058f1a54dd7155614e0d01736b1efb97f38f",
    "dataVersion": null,
    "generatedAt": "2030-01-01T00:00:00+08:00",
    "taskResult": {
      "type": "case_comparison", "caseIds": [1001, 1002], "dimensions": ["businessModel"],
      "baselines": [
        { "caseId": 1001, "evidenceStatus": "sufficient", "missingFields": [] },
        { "caseId": 1002, "evidenceStatus": "sufficient", "missingFields": [] }
      ],
      "commonalities": { "status": "unknown", "items": [], "caveat": "契约占位：暂无共同点结论。" },
      "differences": { "status": "unknown", "items": [], "caveat": "契约占位：暂无差异结论。" },
      "comparisons": [{ "dimension": "businessModel", "analysis": { "status": "known", "items": [{ "id": "model_compare", "kind": "fact", "text": "契约占位：来源 A 与来源 B 分别描述了两个案例的商业模式。", "sourceIds": [9001, 9002], "confidence": "medium", "missingEvidence": false }], "caveat": null } }],
      "revenueComparability": "unknown",
      "regionalAndPolicyContext": { "status": "unknown", "items": [], "caveat": "契约占位：缺少地区与政策证据。" },
      "userFit": { "status": "unknown", "items": [], "caveat": "契约占位：缺少用户适配证据。" },
      "conclusion": { "status": "unknown", "items": [], "caveat": "契约占位：仅完成单维度事实比较。" }
    }
  }
}
```

`technology_assessment` 最小合法示例：

```json
{
  "taskContext": {
    "version": "phase3-task-v1", "taskType": "technology_assessment",
    "caseIds": [], "comparisonDimensions": [], "outputDepth": "standard", "technologyText": "契约占位技术"
  },
  "runEvidenceFixture": {
    "fixtureVersion": "phase3-run-evidence-fixture-v1",
    "cases": [], "policies": [], "sources": [], "caseSourceLinks": [], "policySourceLinks": []
  },
  "structuredResult": {
    "schemaVersion": "phase3-structured-result-v1",
    "taskType": "technology_assessment",
    "directAnswer": "契约占位：技术评估证据不足。",
    "keyFindings": [], "recommendations": [], "risks": [], "assumptions": [], "uncertainties": [], "nextQuestions": [],
    "citations": [],
    "taskSelectedEvidence": { "caseIds": [], "policyIds": [], "sourceIds": [] },
    "authorizedEvidence": { "caseIds": [], "policyIds": [], "sourceIds": [] },
    "evidenceCoverage": { "factClaimCount": 0, "citedFactClaimCount": 0, "missingEvidenceFactCount": 0, "ratio": null },
    "confidence": "low",
    "evidenceVersion": "sha256:d93e8851c631b2eca793eeda59b20eff593db61e95168526bed0f9b9ee2f58df",
    "dataVersion": null,
    "generatedAt": "2030-01-01T00:00:00+08:00",
    "taskResult": {
      "type": "technology_assessment", "technology": { "text": "契约占位技术" },
      "dimensions": [
        { "dimension": "maturity", "level": "unknown", "rationale": { "id": "maturity", "kind": "inference", "text": "契约占位：证据不足。", "sourceIds": [], "confidence": "low", "missingEvidence": true }, "confidence": "low", "missingEvidence": true },
        { "dimension": "scenario_fit", "level": "unknown", "rationale": { "id": "fit", "kind": "inference", "text": "契约占位：证据不足。", "sourceIds": [], "confidence": "low", "missingEvidence": true }, "confidence": "low", "missingEvidence": true },
        { "dimension": "implementation_complexity", "level": "unknown", "rationale": { "id": "complexity", "kind": "inference", "text": "契约占位：证据不足。", "sourceIds": [], "confidence": "low", "missingEvidence": true }, "confidence": "low", "missingEvidence": true }
      ],
      "costStructure": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "dataAndInfrastructure": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "capabilityGaps": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "dependencies": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "complianceRisks": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "operatingRisks": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "alternatives": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "roadmap": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "experiments": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "supportingCases": [], "relatedPolicies": []
    }
  }
}
```

`policy_lookup` 最小合法示例：

```json
{
  "taskContext": {
    "version": "phase3-task-v1", "taskType": "policy_lookup",
    "caseIds": [], "comparisonDimensions": [], "outputDepth": "standard"
  },
  "runEvidenceFixture": {
    "fixtureVersion": "phase3-run-evidence-fixture-v1",
    "cases": [],
    "policies": [
      { "id": 2001, "evidenceRevision": 1, "contentHash": "sha256:25c69d0d7334e552312f82cbb39d898039fc1a15763d803641c8d0a69be82f47", "eligibility": "published_verified" }
    ],
    "sources": [
      { "id": 9004, "title": "契约占位政策来源 D", "publisher": "契约发布者", "url": "https://example.invalid/source/9004", "evidenceRevision": 1, "contentHash": "sha256:57867082282ca54c2d4f6d48d2e1d9faf28bb1e2effb2347620cf2f623a4dd46", "eligibility": "published_verified" }
    ],
    "caseSourceLinks": [],
    "policySourceLinks": [{ "policyId": 2001, "sourceId": 9004 }]
  },
  "structuredResult": {
    "schemaVersion": "phase3-structured-result-v1",
    "taskType": "policy_lookup",
    "directAnswer": "契约占位：政策 2001 的发布状态有一条已核验政策来源支撑。",
    "keyFindings": [], "recommendations": [], "risks": [], "assumptions": [], "uncertainties": [], "nextQuestions": [],
    "citations": [
      { "sourceId": 9004, "title": "契约占位政策来源 D", "publisher": "契约发布者", "url": "https://example.invalid/source/9004", "evidenceRevision": 1, "availability": "current" }
    ],
    "taskSelectedEvidence": { "caseIds": [], "policyIds": [], "sourceIds": [] },
    "authorizedEvidence": { "caseIds": [], "policyIds": [2001], "sourceIds": [9004] },
    "evidenceCoverage": { "factClaimCount": 1, "citedFactClaimCount": 1, "missingEvidenceFactCount": 0, "ratio": 1 },
    "confidence": "medium",
    "evidenceVersion": "sha256:8491a7a0ad58ec5c91ef9a7d90553817d7d0049ae40f3f0e99a91f96bd4317aa",
    "dataVersion": null,
    "generatedAt": "2030-01-01T00:00:00+08:00",
    "taskResult": {
      "type": "policy_lookup", "policyIds": [2001],
      "applicableRegions": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "applicableIndustries": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "validity": { "status": "known", "items": [{ "id": "policy_publication", "kind": "fact", "text": "契约占位：来源 D 记录了政策 2001 的发布信息。", "sourceIds": [9004], "confidence": "medium", "missingEvidence": false }], "caveat": null },
      "eligibilityConditions": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "supportMeasures": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "conflicts": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "expirationRisks": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" },
      "verificationNeeded": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" }
    }
  }
}
```

`source_verification` 最小合法示例：

```json
{
  "taskContext": {
    "version": "phase3-task-v1", "taskType": "source_verification",
    "caseIds": [], "comparisonDimensions": [], "outputDepth": "standard", "sourceId": 9005
  },
  "runEvidenceFixture": {
    "fixtureVersion": "phase3-run-evidence-fixture-v1",
    "cases": [],
    "policies": [],
    "sources": [
      { "id": 9005, "title": "契约占位来源 E", "publisher": "契约发布者", "url": "https://example.invalid/source/9005", "evidenceRevision": 2, "contentHash": "sha256:809a003ea0572ae99ea4908cbe1aedf71d025f88db5ac6d587ae78b2213cf430", "eligibility": "published_verified" }
    ],
    "caseSourceLinks": [],
    "policySourceLinks": []
  },
  "structuredResult": {
    "schemaVersion": "phase3-structured-result-v1",
    "taskType": "source_verification",
    "directAnswer": "契约占位：指定来源的发布者信息已由当前授权来源记录支撑。",
    "keyFindings": [], "recommendations": [], "risks": [], "assumptions": [], "uncertainties": [], "nextQuestions": [],
    "citations": [
      { "sourceId": 9005, "title": "契约占位来源 E", "publisher": "契约发布者", "url": "https://example.invalid/source/9005", "evidenceRevision": 2, "availability": "current" }
    ],
    "taskSelectedEvidence": { "caseIds": [], "policyIds": [], "sourceIds": [9005] },
    "authorizedEvidence": { "caseIds": [], "policyIds": [], "sourceIds": [9005] },
    "evidenceCoverage": { "factClaimCount": 1, "citedFactClaimCount": 1, "missingEvidenceFactCount": 0, "ratio": 1 },
    "confidence": "medium",
    "evidenceVersion": "sha256:8be41268f7949faf0da1d76402363a9011e3f6c61a1b5a34975f541fdf7c17b0",
    "dataVersion": null,
    "generatedAt": "2030-01-01T00:00:00+08:00",
    "taskResult": {
      "type": "source_verification", "mode": "selected_source", "sourceId": 9005, "verdict": "supports",
      "publisherAssessment": { "status": "known", "items": [{ "id": "publisher", "kind": "fact", "text": "契约占位：来源记录包含发布者。", "sourceIds": [9005], "confidence": "medium", "missingEvidence": false }], "caveat": null },
      "supportedClaims": { "status": "unknown", "items": [], "caveat": "契约占位：未提供其他待核验结论。" },
      "unsupportedClaims": { "status": "not_applicable", "items": [], "caveat": "契约占位：没有不支持结论。" },
      "conflicts": { "status": "unknown", "items": [], "caveat": "契约占位：未检出冲突来源。" },
      "invalidityReasons": { "status": "not_applicable", "items": [], "caveat": "契约占位：没有失效原因。" }
    }
  }
}
```

`general_research` 最小合法示例：

```json
{
  "taskContext": {
    "version": "phase3-task-v1", "taskType": "general_research",
    "caseIds": [], "comparisonDimensions": [], "outputDepth": "standard"
  },
  "runEvidenceFixture": {
    "fixtureVersion": "phase3-run-evidence-fixture-v1",
    "cases": [], "policies": [], "sources": [], "caseSourceLinks": [], "policySourceLinks": []
  },
  "structuredResult": {
    "schemaVersion": "phase3-structured-result-v1",
    "taskType": "general_research",
    "directAnswer": "契约占位：研究证据不足。",
    "keyFindings": [], "recommendations": [], "risks": [], "assumptions": [], "uncertainties": [], "nextQuestions": [],
    "citations": [],
    "taskSelectedEvidence": { "caseIds": [], "policyIds": [], "sourceIds": [] },
    "authorizedEvidence": { "caseIds": [], "policyIds": [], "sourceIds": [] },
    "evidenceCoverage": { "factClaimCount": 0, "citedFactClaimCount": 0, "missingEvidenceFactCount": 0, "ratio": null },
    "confidence": "low",
    "evidenceVersion": "sha256:d93e8851c631b2eca793eeda59b20eff593db61e95168526bed0f9b9ee2f58df",
    "dataVersion": null,
    "generatedAt": "2030-01-01T00:00:00+08:00",
    "taskResult": {
      "type": "general_research",
      "sections": [{ "id": "overview", "title": "契约占位章节", "content": { "status": "unknown", "items": [], "caveat": "契约占位：缺少证据。" } }]
    }
  }
}
```

必须固定以下 negative contract tests：未知 schemaVersion 走兼容显示而不按 v1 反序列化；v1 中 taskType 与 taskResult.type 不一致、fact 的 sourceIds 为空、sourceId 不在当前 Run allowlist、任何数组超过上限、任何未知字段、technology assessment 缺任一固定评分维度、comparison 不是 2–3 个唯一案例或 dimensions/comparisons 不一致时均拒绝持久化并返回稳定错误。证据夹具还必须逐项拒绝不存在/不合格实体、缺失或悬空 link、authorizedEvidence 漂移、citation metadata 漂移、跨案例/政策来源误引、全零或不可复算 evidenceVersion，以及 400 与 evidence_insufficient 状态倒置。唯一性定点负向固定为 5 个拒绝：重复 case ID、policy ID、source ID、caseSourceLink pair、policySourceLink pair；另有 2 个正向多对多断言，分别允许同一 source 连接两个不同 case，以及连接不同 case 和 policy。policy_lookup 正向夹具必须保持非空 `policy 2001 -> source 9004 -> fact -> citation` 链，删除 link、替换为非关联来源或漂移 policy/source ID 均必须失败。模型原始输出不得绕过 schema 和服务层语义校验直接写入最终 Assistant message。

## 6. 图表到 AI

### `POST /api/ai/research/from-analytics`

请求字段：

| 字段 | 约束 |
|---|---|
| `metricId` | 必填，指标 allowlist |
| `filters` | 必填对象；只接受第 2 节字段，并必须显式包含归一化后的 `regionRole` |
| `selectedDimension` | 可选稳定 dimension ID，最大 80 |
| `selectedBucketIds` | 0–20 个稳定 ID，每个最大 128 |
| `dateRange` | 可选；必须与 filters 一致，冲突则 400 |
| `granularity` | 可选受控 enum |
| `userQuestion` | 必填，1–2,000 字 |
| `dataVersion` | 必填，最大 128 |
| `idempotencyKey` | 必填，`[A-Za-z0-9_-]{8,64}` |
| `sessionId` | 可选；必须由当前用户拥有且 profile/边界兼容 |

禁止字段：客户端 aggregate/total/percentage、evidence body、citations、SQL、URL、任意 caseIds/policyIds/sourceIds。发现禁止字段返回 400 `ANALYTICS_UNTRUSTED_PAYLOAD`，不能仅忽略。

后端处理顺序：验证 active user → 解析 metricId → 验证 filters 显式携带且允许该 `regionRole` → 规范化其余 filters → 验证 dataVersion → 在同一版本重建 snapshot → 授权合格实体 → 创建 `analyticsSnapshotId` → 创建/锁定 session → 追加 user message → 以现有运行/配额/幂等服务创建 Run。返回 HTTP 202 的现有 research receipt，加 `analyticsSnapshotId,metricId,dataVersion`。

dataVersion 变化返回 409 `ANALYTICS_DATA_VERSION_STALE`，附 `currentDataVersion` 和 `refreshHref`，不自动用新数据替换用户确认的研究边界。幂等键与不同规范化请求复用返回 409；相同请求返回原 receipt。

保存位置：session 保留用户研究上下文；user message 保存问题；run 保存 snapshot ID/metric/version；tool calls 保存服务器授权证据；assistant message 保存结果；citations 继续由当前 Run 证据 allowlist 校验。

## 7. 报告与反馈接口

报告状态固定为 `active|trash|permanently_purged`，全部用户接口使用 UserAuthInterceptor 和 owner 校验：

- `POST /api/ai/research/sessions/{sessionId}/reports`：保存 owned completed finalMessageId。请求含 `finalMessageId,title(1–120),notes(0–1000),idempotencyKey`；服务端从 final message/run 固化必填 evidenceVersion、仅 Analytics snapshot 才有的 dataVersion、runId 和 citation manifest，返回 reportId、revision、status、createdAt。
- `GET /api/ai/research/reports?scope=active|trash&q=&cursor=&limit=30`：稳定排序 updatedAt DESC,id DESC；limit 1–100。
- `GET /api/ai/research/reports/{reportId}`：返回保存时 result、必填 evidenceVersion、可空 dataVersion、finalMessageId、runId、citation manifest、当前 evidence availability、sourceSessionAvailable 和 revision。
- `PATCH /api/ai/research/reports/{reportId}`：只改 title/notes，必填 expectedRevision；trash 报告不可编辑。
- `POST /api/ai/research/reports/{reportId}/trash`：JSON 请求体固定为 `{ "expectedRevision": 3 }`；active → trash，设置 `purgeAfter=trashedAt+30 days`。
- `POST /api/ai/research/reports/{reportId}/restore`：JSON 请求体固定为 `{ "expectedRevision": 4 }`；trash → active，清空 trashedAt/purgeAfter；permanently_purged 不可恢复。
- `DELETE /api/ai/research/reports/{reportId}/permanent`：JSON 请求体固定为 `{ "expectedRevision": 5 }`；仅 trash 且无运行中生成/导出作业时可执行；清除报告正文、notes、可恢复导出内容和缓存文件，状态变 permanently_purged；可以保留 reportId、ownerId、时间、状态、字节/token 计数等最小非内容审计元数据。
- `GET /api/ai/research/reports/{reportId}/export?format=markdown|html|pdf`：仅 active；服务端从持久化 result/citation manifest 生成，不接收前端正文。

三个状态变更的请求示例分别为：

```json
{ "expectedRevision": 3 }
```

```json
{ "expectedRevision": 4 }
```

```json
{ "expectedRevision": 5 }
```

上面 `3/4/5` 只是请求形状示例，不是固定业务 revision。三个生命周期接口都只从 JSON body 读取 `expectedRevision`，不得改用 query/header。它必须是正整数；缺失、null、0、负数、浮点数或字符串返回 400 `REPORT_EXPECTED_REVISION_INVALID`。与当前 revision 不同返回 409 `REPORT_REVISION_CONFLICT`。成功状态转换后 revision 恰好递增 1，响应返回完整当前资源及新 revision。

CAS 幂等规则唯一如下：资源已经处于目标状态且请求携带**当前** revision 时返回当前资源，不改变状态且不递增；任何携带旧 revision 的重放都返回 409，不能伪装成功重放。客户端在网络超时后先重新 GET 报告，再按最新 status/revision 决定是否提交新请求。后台自动 purge 不接受客户端 JSON body；worker 在数据库锁或等价租约内读取当前 revision，并以锁内 status/revision 完成判断与内容擦除。restore 与 purge 竞争时只有一个状态转换成功。

只有 owned completed Run 的 finalMessage 可保存；running、failed、cancelled、expired、evidence_insufficient（无正式 finalMessage）均拒绝。证据后续失效时报告保留历史 citation 元数据并将当前 availability 标成 unavailable，不改写旧结论。

保存时报告复制不可变的结果与 citation manifest，形成独立用户资产。原 session 永久清除后 active/trash 报告不被连带删除，`sourceSessionAvailable=false` 且 session/message 不再可跳转；报告仍按自身 trash/permanent API 管理。若报告先永久删除，原 session 不受影响。

`purgeAfter` 是执行字段而非展示占位。后台任务只选择 `status=trash AND purge_after<=now` 的小批量记录，幂等地执行与 permanent API 相同的内容擦除；多实例必须使用 `FOR UPDATE SKIP LOCKED`、数据库 advisory lock 或等价带过期租约，保证同一 report revision 不被重复清理。restore 与 purge 都以 `status=trash + expected revision` 原子比较；restore 先成功则清空 purgeAfter，purge 先成功则 restore 返回 409 且不能复活内容。清理失败保留 trash 与 purgeAfter 供退避重试，不留下“已永久删除但正文仍在”的状态。永久清除覆盖 result 正文、notes、导出缓存/文件和其他可恢复内容，只保留最小非内容审计；日志不得打印正文或引用全文。

### 7.1 用户反馈

- `PUT /api/ai/research/runs/{runId}/feedback`：仅 Run owner 且响应 `feedbackEligible=true`。请求为 `rating,reason,comment,expectedRevision`。
- `GET /api/ai/research/runs/{runId}/feedback`：读取当前用户自己的反馈；无反馈返回 data=null。

`rating` 只允许 `helpful|not_helpful`。reason 按 rating 使用闭合映射：`helpful` 只允许 `accurate_and_useful|clear_and_actionable|good_evidence|other`；`not_helpful` 只允许 `missing_evidence|incorrect_claim|not_relevant|unclear|too_slow|other`。跨组组合返回 400 `FEEDBACK_REASON_RATING_MISMATCH`。comment 可选，最多 500 Unicode code points，必须经过内容安全和日志脱敏。数据库唯一键为 `(user_id,run_id)`；首次 expectedRevision=0 创建，后续带当前 revision 原子更新，同值重放幂等，旧 revision 返回 409。管理员 token 不能提交或读取某个用户的反馈。

`feedbackEligible` 的唯一判定是：owned `completed` Run 为 true；owned `evidence_insufficient` 仅在存在用户可见且已持久化的 Assistant 结果时为 true；`received|running|planning|clarification_needed|cancelled|expired|failed` 一律为 false，失败诊断或临时文本不算用户可见持久化结果。`GET /runs/{runId}` 必须显式返回该布尔值；管理员 token 不能替用户提交反馈。反馈响应只返回 `runId,rating,reason,comment,revision,updatedAt`，不回显完整问题、回答或 Provider 原始响应。

### 7.2 管理员聚合质量

`GET /api/admin/ai/research/quality` 使用现有管理员鉴权，只接受 `dateFrom,dateTo,taskType,model,promptVersion,granularity`。响应包含 `sampleSize,helpfulCount,notHelpfulCount,helpfulRate,reasonCounts,taskBreakdown,modelBreakdown,latencySummary,tokenSummary,generatedAt`，小样本按后台阈值抑制或合并。

管理员接口不返回 userId、邮箱、完整 comment、用户问题、模型回答、思维链、tool 原始 JSON 或 Provider 原始响应。必要的 reason 仅按受控枚举聚合；自由 comment 不进入管理端明细。

## 8. dataVersion、缓存与 ETag

`dataVersion` 至少组合：analytics schema version、eligible review watermark、case/policy/source evidence revision watermark、taxonomy revision、canonical merge revision、revenue normalization revision。它不是 `MAX(updated_at)` 的别名，也不能只靠前端时间戳。

- GET 返回强 ETag：由 endpoint + normalized filters + dataVersion + response schema hash 生成。
- `If-None-Match` 命中返回 304；响应头 `Cache-Control: private,max-age=60,must-revalidate`、`Vary: Authorization,Accept-Encoding`。
- Red/unavailable metadata 可缓存 60 秒；鉴权结果和用户特有下钻不能共享公共缓存。
- 审核通过、证据失效、merge/taxonomy/revenue 变化使新请求获得新 dataVersion；旧 Run/报告仍保留旧版本标识。

## 9. 错误契约

| code/message code | HTTP | 场景 |
|---|---:|---|
| `ANALYTICS_INVALID_FILTER` | 400 | 日期、范围、枚举、ID 或组合非法 |
| `ANALYTICS_UNKNOWN_FILTER` | 400 | 未声明参数/字段 |
| `ANALYTICS_FILTER_TOO_LARGE` | 400 | 多选、URL、bucket 或范围超过上限 |
| `ANALYTICS_REGION_ROLE_INVALID` | 400 | entity/metric 与 regionRole 不兼容 |
| `ANALYTICS_REGION_ROLE_REQUIRED` | 400 | from-analytics filters 未显式携带 regionRole |
| `REVENUE_COMPARABILITY_REQUIRED` | 400 | 收入可比组不完整 |
| `ANALYTICS_UNTRUSTED_PAYLOAD` | 400 | AI 请求携带数值/正文/SQL/URL/任意实体 ID |
| `PHASE3_TASK_CONTEXT_INVALID` | 400 | taskContext 版本、字段、长度、数量或任务约束非法 |
| `PHASE3_TASK_INTENT_MISMATCH` | 400 | taskType 与 requestedIntent 不一致 |
| `PHASE3_CASE_NOT_ELIGIBLE` | 400 | 显式 caseId 不存在、不可公开使用、未 published+verified 或没有完整 published+verified 来源链；统一安全文案且零持久化副作用 |
| `PHASE3_SOURCE_NOT_ELIGIBLE` | 400 | 指定 sourceId 不存在、不可公开使用、未 published+verified、provenance/HTTP(S) URL 不完整或 revision 已失效；统一安全文案且零持久化副作用 |
| `PHASE3_STRUCTURED_RESULT_INVALID` | 400/内部失败 | v1 schema 或服务层 source allowlist/基数规则不满足，不得持久化为最终结果 |
| `FEEDBACK_REASON_RATING_MISMATCH` | 400 | feedback reason 不属于当前 rating 的闭合 allowlist |
| `UNAUTHORIZED` | 401 | 无有效用户 session |
| `FORBIDDEN` | 403 | 用户 disabled 或资源非 owner |
| `ANALYTICS_METRIC_NOT_FOUND` | 404 | metricId 不存在 |
| `ANALYTICS_DATA_VERSION_STALE` | 409 | AI/下钻请求版本过期 |
| `ANALYTICS_TECHNOLOGY_FILTER_UNAVAILABLE` | 409 | 显式技术筛选在 taxonomy 未就绪或 tag 未批准时不可用 |
| `ANALYTICS_CURSOR_STALE` | 409 | cursor 版本、筛选、用户或有效期不符 |
| `ANALYTICS_IDEMPOTENCY_CONFLICT` | 409 | 同 key 不同规范化请求 |
| `PHASE3_IDEMPOTENCY_CONFLICT` | 409 | 同 start key 的 profile/content/requestedIntent/taskContext 任一不同 |
| `REPORT_EXPECTED_REVISION_INVALID` | 400 | 报告生命周期 expectedRevision 缺失、null、非正整数或类型错误 |
| `REPORT_REVISION_CONFLICT` | 409 | trash/restore/permanent 的 expectedRevision 不是当前报告 revision |
| `FEEDBACK_REVISION_CONFLICT` | 409 | 反馈 expectedRevision 过期 |
| `ANALYTICS_RATE_LIMITED` | 429 | 统计/研究频率限制 |
| `ANALYTICS_UNAVAILABLE` | 503 | 聚合存储或刷新不可用且无可标记 partial 的旧快照 |
| `AGENT_RUNTIME_UNAVAILABLE` | 503 | 统计可看但 AI runtime 未启用 |

错误 `Result.data` 可含 `errorCode,fieldErrors,currentDataVersion,refreshHref,retryAfterSeconds,requestId`，不得包含 SQL、内部路径、凭据或原始 Provider 响应。

## 10. 性能与 SLA

- p95：overview/单维排名 <= 800ms；趋势/矩阵 <= 1,200ms；下钻首屏 <= 800ms；ETag 304 <= 200ms。
- 图表到 AI 接受响应 <= 1,000ms；完整 AI 时间沿用异步 Run，不把模型耗时算入 analytics SLA。
- 单响应 bucket 上限：排名 100、趋势 240、矩阵 10×10；超出用 server-side top-N + other，other 仍可下钻。
- 单用户统计读默认 120 req/min，AI 联动沿用现有并发、Token 和幂等限制。
- 查询必须有 EXPLAIN/索引证据；禁止为一个页面逐 bucket N+1 查询。

后端实现与验收责任见 [phase-three-backend-handoff.md](phase-three-backend-handoff.md)，契约测试见 [phase-three-evaluation-plan.md](phase-three-evaluation-plan.md)。
