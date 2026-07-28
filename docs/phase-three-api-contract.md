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

## 2. 公共筛选参数

| 参数 | 类型/默认 | 约束和语义 |
|---|---|---|
| `metricId` | string；端点有唯一默认时可省 | 最大 80；必须在指标字典 allowlist，拒绝任意列名/表达式 |
| `dateFrom` | ISO date；按 metric 默认 | Asia/Shanghai 包含日；不能晚于 dateTo |
| `dateTo` | ISO date；默认今天 | 包含日；不能超过今天；服务端转为 `< nextDayStart` |
| `granularity` | metric 默认 | `day|month|quarter|year`；day 最多 90 天，总范围最多 10 年 |
| `regionId` | positive long；无 | 一个根地区；包含 descendants；必须存在且用户可访问 |
| `regionLevel` | string；`province` | `country|province|city|district`；当前生产只有 country/province，其他层级出现前返回空而非伪造，只影响 bucket 展开，不改变 regionId scope |
| `industryTagIds` | repeated long；无 | 0–10 个 approved industry tags；多选默认 OR，结果回显 canonical 顺序 |
| `technologyTagIds` | repeated long；无 | 0–10 个 approved technology tags；未就绪时返回 409/metric Red metadata |
| `revenueRange` | string；无 | 服务端版本化 bucket ID，不接受前端金额表达式；必须同时固定收入可比组 |
| `currency` | ISO 4217；无 | 收入 metric 必填，首期不换汇 |
| `revenuePeriod` | enum；无 | `monthly|annual|one_off|other`；收入 metric 必填 |
| `revenueType` | enum；无 | `revenue|profit|personal_income|other`；收入 metric 必填 |
| `estimated` | boolean；false | actual 与 estimated 分开请求/系列，不静默混合 |
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

允许 technology 指标。分类尚未就绪时返回 HTTP 200、`status=unavailable`、readiness Red、data 空、caveat `TECHNOLOGY_TAXONOMY_NOT_READY`；不得生成基于自由文本的临时排名。

### `GET /api/analytics/revenue`

允许 revenue 指标。缺少 currency/period/type 时 400 `REVENUE_COMPARABILITY_REQUIRED`。当前字段未就绪时返回 unavailable + `REVENUE_SCHEMA_NOT_READY`。达到门槛后 data 明示 actual/estimated、currency、period、type、binVersion；数值使用 Decimal 字符串或无损 JSON number 的项目统一规范，不能二进制浮点截断。

### `GET /api/analytics/regions`

允许 `region.case_count|region.policy_count|region.industry_distribution`。排序 value DESC, regionId ASC。region bucket 返回 `regionId,parentId,level,label`。地图几何不属于此端点首期契约。

### `GET /api/analytics/trends`

允许 `trend.policy_publish_time|trend.case_business_time|technology.adoption_trend|industry.new_case_trend`。每个 point 返回 `bucketId`（ISO period）、`periodStart`,`periodEndExclusive`,`value`,`sampleSize`,`missingCount`。空桶 `value=0` 仅在请求范围内，且 `isSyntheticEmptyBucket=true`，不是伪造记录。

### `GET /api/analytics/drilldown`

必填 `metricId,dataVersion`，并带与来源图一致的 filters 和可选 bucketId。必填 `entityType=case|policy|source`。返回：

- `items[]`：仅包含公开列表已允许的 bounded fields、evidence status 和内部 detail href。
- `nextCursor`、`hasMore`、`dataVersion`、规范化 filters、`aggregateValue`。
- 稳定排序：业务日期 DESC NULLS LAST、updated_at DESC、id DESC；case 无业务日期时只用 updated_at/id，但 UI 不把它称作时间趋势。

`aggregateValue` 必须与相同 metric/bucket 聚合值一致；下钻所有页去重 ID 合集必须等于聚合黄金集。cursor 过期或版本变化返回 409 `ANALYTICS_CURSOR_STALE`。

## 5. 图表到 AI

### `POST /api/ai/research/from-analytics`

请求字段：

| 字段 | 约束 |
|---|---|
| `metricId` | 必填，指标 allowlist |
| `filters` | 必填对象；只接受第 2 节字段 |
| `selectedDimension` | 可选稳定 dimension ID，最大 80 |
| `selectedBucketIds` | 0–20 个稳定 ID，每个最大 128 |
| `dateRange` | 可选；必须与 filters 一致，冲突则 400 |
| `granularity` | 可选受控 enum |
| `userQuestion` | 必填，1–2,000 字 |
| `dataVersion` | 必填，最大 128 |
| `idempotencyKey` | 必填，`[A-Za-z0-9_-]{8,64}` |
| `sessionId` | 可选；必须由当前用户拥有且 profile/边界兼容 |

禁止字段：客户端 aggregate/total/percentage、evidence body、citations、SQL、URL、任意 caseIds/policyIds/sourceIds。发现禁止字段返回 400 `ANALYTICS_UNTRUSTED_PAYLOAD`，不能仅忽略。

后端处理顺序：验证 active user → 解析 metricId → 规范化 filters → 验证 dataVersion → 在同一版本重建 snapshot → 授权合格实体 → 创建 `analyticsSnapshotId` → 创建/锁定 session → 追加 user message → 以现有运行/配额/幂等服务创建 Run。返回 HTTP 202 的现有 research receipt，加 `analyticsSnapshotId,metricId,dataVersion`。

dataVersion 变化返回 409 `ANALYTICS_DATA_VERSION_STALE`，附 `currentDataVersion` 和 `refreshHref`，不自动用新数据替换用户确认的研究边界。幂等键与不同规范化请求复用返回 409；相同请求返回原 receipt。

保存位置：session 保留用户研究上下文；user message 保存问题；run 保存 snapshot ID/metric/version；tool calls 保存服务器授权证据；assistant message 保存结果；citations 继续由当前 Run 证据 allowlist 校验。

## 6. 报告接口

这些接口在 Phase A/C 实现，路径延续现有 research 资源：

- `POST /api/ai/research/sessions/{sessionId}/reports`：保存一个 completed finalMessageId；幂等键必填。
- `GET /api/ai/research/reports`：scope、q、cursor、limit，稳定排序 createdAt DESC,id DESC。
- `GET /api/ai/research/reports/{reportId}`：仅 owner；返回版本、引用 manifest 和当前可用性。
- `PATCH /api/ai/research/reports/{reportId}`：只改 title/notes，使用 expectedRevision。
- `DELETE /api/ai/research/reports/{reportId}`：软删除/恢复策略需与 session 一致。
- `GET /api/ai/research/reports/{reportId}/export?format=markdown|html|pdf`：服务端生成；大文件可升级异步 job。

报告不能引用 running/failed Run；不得保存前端提交的 Markdown 作为可信报告正文。导出从持久化 message/result/citation manifest 生成。

## 7. dataVersion、缓存与 ETag

`dataVersion` 至少组合：analytics schema version、eligible review watermark、case/policy/source evidence revision watermark、taxonomy revision、canonical merge revision、revenue normalization revision。它不是 `MAX(updated_at)` 的别名，也不能只靠前端时间戳。

- GET 返回强 ETag：由 endpoint + normalized filters + dataVersion + response schema hash 生成。
- `If-None-Match` 命中返回 304；响应头 `Cache-Control: private,max-age=60,must-revalidate`、`Vary: Authorization,Accept-Encoding`。
- Red/unavailable metadata 可缓存 60 秒；鉴权结果和用户特有下钻不能共享公共缓存。
- 审核通过、证据失效、merge/taxonomy/revenue 变化使新请求获得新 dataVersion；旧 Run/报告仍保留旧版本标识。

## 8. 错误契约

| code/message code | HTTP | 场景 |
|---|---:|---|
| `ANALYTICS_INVALID_FILTER` | 400 | 日期、范围、枚举、ID 或组合非法 |
| `ANALYTICS_UNKNOWN_FILTER` | 400 | 未声明参数/字段 |
| `ANALYTICS_FILTER_TOO_LARGE` | 400 | 多选、URL、bucket 或范围超过上限 |
| `REVENUE_COMPARABILITY_REQUIRED` | 400 | 收入可比组不完整 |
| `ANALYTICS_UNTRUSTED_PAYLOAD` | 400 | AI 请求携带数值/正文/SQL/URL/任意实体 ID |
| `UNAUTHORIZED` | 401 | 无有效用户 session |
| `FORBIDDEN` | 403 | 用户 disabled 或资源非 owner |
| `ANALYTICS_METRIC_NOT_FOUND` | 404 | metricId 不存在 |
| `ANALYTICS_DATA_VERSION_STALE` | 409 | AI/下钻请求版本过期 |
| `ANALYTICS_CURSOR_STALE` | 409 | cursor 版本、筛选、用户或有效期不符 |
| `ANALYTICS_IDEMPOTENCY_CONFLICT` | 409 | 同 key 不同规范化请求 |
| `ANALYTICS_RATE_LIMITED` | 429 | 统计/研究频率限制 |
| `ANALYTICS_UNAVAILABLE` | 503 | 聚合存储或刷新不可用且无可标记 partial 的旧快照 |
| `AGENT_RUNTIME_UNAVAILABLE` | 503 | 统计可看但 AI runtime 未启用 |

错误 `Result.data` 可含 `errorCode,fieldErrors,currentDataVersion,refreshHref,retryAfterSeconds,requestId`，不得包含 SQL、内部路径、凭据或原始 Provider 响应。

## 9. 性能与 SLA

- p95：overview/单维排名 <= 800ms；趋势/矩阵 <= 1,200ms；下钻首屏 <= 800ms；ETag 304 <= 200ms。
- 图表到 AI 接受响应 <= 1,000ms；完整 AI 时间沿用异步 Run，不把模型耗时算入 analytics SLA。
- 单响应 bucket 上限：排名 100、趋势 240、矩阵 10×10；超出用 server-side top-N + other，other 仍可下钻。
- 单用户统计读默认 120 req/min，AI 联动沿用现有并发、Token 和幂等限制。
- 查询必须有 EXPLAIN/索引证据；禁止为一个页面逐 bucket N+1 查询。

后端实现与验收责任见 [phase-three-backend-handoff.md](phase-three-backend-handoff.md)，契约测试见 [phase-three-evaluation-plan.md](phase-three-evaluation-plan.md)。
