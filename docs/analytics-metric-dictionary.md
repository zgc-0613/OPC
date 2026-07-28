# SoloFirm Analytics 指标字典

> 版本：`phase3-metrics-v1`
> 生效决策日：2026-07-29
> 本文件是第三阶段统计口径的唯一来源。产品文案、SQL、API 和测试不得另行解释指标。数据事实见 [phase-three-readiness-audit.md](phase-three-readiness-audit.md)。

## 1. 统一集合与字段

### 1.1 合格集合

- `E_case`：案例为 published + verified，关联来源也为 published + verified 且 title/publisher/HTTP(S) URL 完整；按未来 `canonical_case_id` 去重。
- `E_policy`：政策满足同样双层证据链；按 `policy.id` 去重，后续如出现 canonical policy 再升级版本。
- `E_source`：来源 published + verified 且 provenance 完整；按未来 `canonical_source_id` 去重。
- `M`：从合格集合中因某指标必要字段缺失而排除的记录数。未知值永不转为 0。

当前数据库没有 canonical case/source 和 dataVersion，因此依赖它们的指标保持 Yellow/Red。临时 title/URL 组合只用于审计候选，不成为生产主键。

### 1.2 每个指标继承的固定字段

每个下列 metric 都继承并必须在响应中返回：

- 数据刷新方式：审核通过或证据失效后异步刷新聚合；不由前端刷新计算。
- 数据版本：不可变 `dataVersion`，绑定 schema version、审核水位和 canonical merge revision；历史 AI Run 保存当时版本。
- 时区：全部日期边界按 `Asia/Shanghai`；`dateTo` 为包含日，服务内部转换为次日开区间。
- 包含条件：仅对应的 `E_*`；排除 draft、reviewed、legacy_unverified、excluded、物理删除和失效来源链。
- 多标签处理：按包含该标签的唯一 canonical case 计数；一个案例可进入多个 bucket，占比和可超过 100%。
- 缺失值：不进入分子或数值分布，进入 `missingCount`；不静默回退其他字段。
- 下钻：必须返回同一 dataVersion 与 filters 对应的服务端游标；下钻 ID 集合与聚合黄金测试一致。
- 数字：API 返回原始整数/Decimal，百分比由后端给出 ratio 原值、前端格式化。
- readiness 值：`Green|Yellow|Red`；Red API 可返回 metadata/caveat，但正式 UI 不画业务图。

## 2. 概览指标

### `overview.verified_cases` — 已核验案例总数

- 用户问题/用途：当前有多少可用于研究的独立真实案例；用于概览和 AI 证据规模提示。
- 定义/公式/分子/分母：`COUNT(DISTINCT canonical_case_id)`；分子为 `E_case` 唯一业务案例，分母不适用。
- 表与字段：`case_items.id,status,ai_evidence_status,source_id`、`sources.*`、待新增 canonical 映射；去重键 `canonical_case_id`。
- 时间/维度：默认全部业务时间；可按 region/industry/technology 过滤，无时间粒度、无收入单位；案例没有业务时间时不接受 date filter。
- 样本/限制/readiness：最小 1；105 个合格行含 42 条重复候选，正式数 unknown；`Red`，下钻案例列表。

### `overview.verified_policies` — 已核验政策总数

- 用户问题/用途：当前有多少证据链完整的公开政策；概览和政策研究基数。
- 定义/公式/分子/分母：`COUNT(DISTINCT policy.id)` over `E_policy`；分子 57，分母不适用。
- 表与字段：`policies.id,publish_date,region_id,status,ai_evidence_status,source_id` + `sources`；去重键 policy.id。
- 时间/维度：默认全部；date filter 用 publish_date；day/month/quarter/year；region 可用，industry/technology 仅在审核关系存在后可用。
- 样本/限制/readiness：最小 1；当前 57；`Green`，下钻政策列表。

### `overview.verified_sources` — 已核验来源总数

- 用户问题/用途：研究资料来自多少个可追溯来源记录；证据规模提示。
- 定义/公式/分子/分母：`COUNT(DISTINCT canonical_source_id)` over `E_source`；分子为合格来源，分母不适用。
- 表与字段：`sources.id,title,publisher,url,status,ai_evidence_status`；待新增 canonical_source_id。
- 时间/维度：默认全部；不按业务日期趋势；source_type 可扩展，地区/行业/技术不直接适用。
- 样本/限制/readiness：121 合格行，有 1 组同 URL；`Yellow`，下钻来源列表。

### `overview.covered_regions` — 覆盖地区数量

- 用户问题/用途：资料覆盖多少个地区；概览与地域筛选提示。
- 定义/公式/分子/分母：`COUNT(DISTINCT region_id)` over selected eligible cases/policies；分母不适用。
- 表与字段：`case_items.region_id`、`policies.region_id`、`regions.id,level,parent_id`；去重键 region.id。
- 时间/维度：默认全部；政策用 publish_date，案例 date filter 暂不可用；regionLevel 可选，行业/技术过滤遵循审核关系。
- 样本/限制/readiness：至少 2 个地区；当前 union 26；案例地区语义含混；`Yellow`，下钻地区排名。

### `overview.covered_industries` — 覆盖行业数量

- 用户问题/用途：正式案例覆盖多少行业；概览和研究入口。
- 定义/公式/分子/分母：`COUNT(DISTINCT industry_tag_id)`，只含 approved industry tag 且至少关联 1 个 E_case/E_policy。
- 表与字段：`tags.id,is_industry/semantic_type`、`case_tags`、`policy_industry_tags`；去重键 tag.id。
- 时间/维度：默认全部；region 可筛，technology 仅审核后；无收入单位；多标签按统一规则。
- 样本/限制/readiness：bucket 至少 3 个案例才可展示；当前 case 19 个、政策关系 0，且重复未解；`Yellow`。

### `overview.covered_technologies` — 覆盖技术数量

- 用户问题/用途：已审核证据覆盖多少种技术；技术研究入口。
- 定义/公式/分子/分母：`COUNT(DISTINCT technology_tag_id)` with semantic_type=technology and approved relations。
- 表与字段：待新增 tag semantic_type 与正式关系；`ai_tools`/自由标签不得充当分子；去重键 tag.id。
- 时间/维度：默认全部；region/industry 可筛；无收入单位；多标签按统一规则。
- 样本/限制/readiness：每个技术至少 3 个案例；当前显式技术标签 0；`Red`。

### `overview.data_completeness` — 数据完整度

- 用户问题/用途：当前筛选结果在哪些关键维度可用于决策；概览质量卡，而非营销分数。
- 定义/公式/分子/分母：返回字段向量及宏平均；case fields=`source,region,industry,businessTime,technology,revenue`，policy fields=`source,region,publishDate,industry`；分子为已填合格字段单元格，分母为 eligible records × 适用字段数。
- 表与字段：上述关系和规范化字段；去重键对应 canonical ID；每个 component 单独返回 numerator/denominator。
- 时间/维度：与当前 filters 一致；无粒度；region/industry/technology 支持；收入本身无单位。
- 样本/限制/readiness：至少 10 个合格对象；权重固定在 metricVersion，不能隐藏 0% component；`Yellow`，下钻质量明细。

## 3. 行业指标

### `industry.case_count` — 行业案例数量

- 用户问题/用途：每个行业有多少独立案例；排名、筛选和研究入口。
- 定义/公式/分子/分母：每个 approved industry tag 的 `COUNT(DISTINCT canonical_case_id)`；分母不适用。
- 表与字段：`case_items`、`case_tags`、`tags.semantic_type`、sources、canonical mapping；去重键 canonical_case_id。
- 时间/维度：默认全部；region/technology 可筛；案例 businessTime 建立后支持 month/quarter/year；无收入单位。
- 样本/限制/readiness：bucket `n>=3` 可画，1–2 只在表格标“样本少”；重复未解；`Yellow`。

### `industry.case_share` — 行业案例占比

- 用户问题/用途：某行业在当前合格案例中的覆盖程度；结构比较。
- 定义/公式/分子/分母：industry.case_count / `COUNT(DISTINCT canonical_case_id in E_case after non-industry filters)`。
- 表与字段：同 industry.case_count；去重键 canonical_case_id；分母不因行业多选而重复。
- 时间/维度：同 case_count；region/technology 可筛；无收入单位；多标签导致各占比和可 >100%。
- 样本/限制/readiness：总分母至少 20、bucket 至少 3；`Yellow`，下钻对应案例。

### `industry.new_case_trend` — 行业新增案例趋势

- 用户问题/用途：某行业何时出现新增业务案例；趋势研究。
- 定义/公式/分子/分母：按 case business publish/occurred date 分桶的唯一 canonical_case_count；分母不适用。
- 表与字段：待新增 `business_published_at/occurred_at` + industry relations；禁止用 accessed_at/created_at 回退。
- 时间/维度：默认最近 24 月；最大 10 年；month/quarter/year；region/industry/technology 可筛。
- 样本/限制/readiness：每个展示序列总样本至少 12；当前业务时间 0/105；`Red`。

### `industry.region_distribution` — 行业地区分布

- 用户问题/用途：某行业案例主要集中在哪些地区；选址与市场研究。
- 定义/公式/分子/分母：按 region 的 industry.case_count；分母可为该行业全部 canonical cases 以返回 ratio。
- 表与字段：case region、industry relation、canonical mapping；去重键 canonical_case_id。
- 时间/维度：默认全部；regionLevel=province；行业必选 1–10，technology 可选；无收入单位。
- 样本/限制/readiness：总样本至少 10，bucket 少于 3 标低样本；case 地区语义未固化；`Yellow`。

### `industry.related_policy_count` — 行业相关政策数量

- 用户问题/用途：某行业有哪些明确适用政策；案例到政策联动。
- 定义/公式/分子/分母：general 政策按所有行业可见但单独标 general；specific 只在 policy_industry_tags 命中时计数；unclassified 不计入。
- 表与字段：`policies.applicability_mode`、`policy_industry_tags`、tags、sources；去重键 policy.id。
- 时间/维度：默认最近 36 月；month/quarter/year；region/industry 支持，technology 需正式关系。
- 样本/限制/readiness：至少 1；生产 57/57 unclassified、关系 0；`Red`。

## 4. 技术指标

### `technology.case_count` — 技术标签案例数量

- 用户问题/用途：采用某技术的独立案例有多少；技术排名。
- 定义/公式/分子/分母：approved technology tag 关联的 `COUNT(DISTINCT canonical_case_id)`；分母不适用。
- 表与字段：待新增 tag semantic_type=technology 与关系审核；禁止从 ai_tools 文本即时推断。
- 时间/维度：默认全部；region/industry/technology 支持；无收入单位；多标签统一处理。
- 样本/限制/readiness：bucket 至少 3；当前 0 个显式 technology tag；`Red`。

### `technology.adoption_trend` — 技术采用趋势

- 用户问题/用途：技术在业务案例中的采用是否随时间变化；路线判断。
- 定义/公式/分子/分母：按业务时间分桶的 technology.case_count；可选 ratio 分母为同桶 E_case。
- 表与字段：技术关系 + case business time + canonical mapping；不使用采集日。
- 时间/维度：默认 24 月、最大 10 年；month/quarter/year；region/industry/technology 支持。
- 样本/限制/readiness：序列至少 12、每桶显示 n；所需字段均未齐；`Red`。

### `technology.industry_matrix` — 技术与行业组合

- 用户问题/用途：哪些行业真实采用哪些技术；技术选型和案例发现。
- 定义/公式/分子/分母：每个 industry × technology 单元格的唯一 canonical case 数；ratio 分母为对应行业案例数。
- 表与字段：两个已审核 semantic tag 关系、case canonical mapping；双多标签只计一次/单元格。
- 时间/维度：默认全部；region 可筛；industry/technology 各最多 10；无收入单位。
- 样本/限制/readiness：单元格 n<3 灰显并保留表格数；当前技术分类缺失；`Red`。

### `technology.related_evidence` — 技术相关案例和政策

- 用户问题/用途：某项技术有哪些案例、政策和来源证据；AI 研究入口。
- 定义/公式/分子/分母：分别返回 E_case/E_policy 的 distinct count；政策必须 general 或有审核技术适用关系。
- 表与字段：技术关系、政策适用关系、sources；去重键 canonical_case_id/policy.id。
- 时间/维度：默认政策最近 36 月、案例全部；region/industry/technology 支持。
- 样本/限制/readiness：案例至少 1；当前没有 policy technology 关系；`Red`。

### `technology.completeness` — 技术数据完整度

- 用户问题/用途：当前有多少案例拥有已审核结构化技术分类；质量门槛。
- 定义/公式/分子/分母：至少一个 approved technology tag 的 E_case / 全部 E_case。
- 表与字段：技术关系；自由文本不计分子；去重键 canonical_case_id。
- 时间/维度：默认全部；region/industry 可筛；无时间粒度、无收入单位。
- 样本/限制/readiness：分母至少 1；当前 0/105，可如实返回；质量指标本身 `Green`，业务技术图仍 Red。

## 5. 收入指标

所有收入指标只接受同 `currency + revenue_period + revenue_type` 可比组；estimated 与 actual 分系列，withheld/unknown 仅进 missing breakdown。默认不换汇、不做通胀调整。

### `revenue.range_distribution` — 收入区间分布

- 用户问题/用途：可比案例的收入落在哪些区间；机会尺度判断。
- 定义/公式/分子/分母：以 revenue_min/max 或规范化单值落入后端版本化 bins 的 canonical case 数；分母为可比收入样本。
- 表与字段：待新增 revenue_min/max,currency,period,type,is_estimated,disclosure_status,as_of_date,source_id；去重 canonical_case_id。
- 时间/维度：默认最近 36 月 as_of_date；year；region/industry/technology；收入单位由 currency+period+type 明示。
- 样本/限制/readiness：同组至少 30、覆盖率至少 40%；当前 0/105；`Red`。

### `revenue.median` — 收入中位数

- 用户问题/用途：可比案例的典型收入水平；避免均值被离群值主导。
- 定义/公式/分子/分母：可比实际值的 P50；区间案例用后端明确策略并单列，不与单值静默混合；分母为数值样本数。
- 表与字段：同 revenue.range_distribution；estimated 默认排除，可由显式 filter 单独查询。
- 时间/维度：同上；region/industry/technology 支持；必须返回 currency/period/type。
- 样本/限制/readiness：n>=30，异常规则通过；当前字段不存在；`Red`。

### `revenue.quartiles` — 收入四分位数

- 用户问题/用途：收入分散程度如何；技术/行业比较的稳健区间。
- 定义/公式/分子/分母：同一可比组 P25/P50/P75，算法固定在 metricVersion；分母为数值样本数。
- 表与字段：同收入字段；去重 canonical_case_id；排除 unknown/withheld/不兼容组。
- 时间/维度：同 median；必须明示单位。
- 样本/限制/readiness：n>=40；当前 0；`Red`。

### `revenue.by_industry` — 分行业收入分布

- 用户问题/用途：同一收入口径在行业间是否有差异；行业研究。
- 定义/公式/分子/分母：每行业返回 n、P25/P50/P75；多行业案例进入每个关联行业但每组只计一次。
- 表与字段：收入字段 + approved industry relation + canonical mapping。
- 时间/维度：默认 36 月；year；region/industry/technology；固定 currency/period/type。
- 样本/限制/readiness：每行业 n>=30，至少 2 行业；当前 0；`Red`。

### `revenue.by_region` — 分地区收入分布

- 用户问题/用途：可比收入在地区间是否不同；选址研究。
- 定义/公式/分子/分母：每地区返回 n、P25/P50/P75；去重 canonical_case_id。
- 表与字段：收入字段 + 语义明确的 operation_region；当前 case.region_id 不足以直接承担。
- 时间/维度：默认 36 月；year；regionLevel=province；industry/technology 支持；固定收入单位。
- 样本/限制/readiness：每地区 n>=30；字段和地区语义均不足；`Red`。

### `revenue.completeness` — 收入数据覆盖率

- 用户问题/用途：有多少合格案例具备可比较收入；决定其他收入图是否开放。
- 定义/公式/分子/分母：满足金额、currency、period、type、disclosure、source 的 E_case / 全部 E_case；estimated 也算完整但单列。
- 表与字段：规范化收入字段；去重 canonical_case_id；unknown/withheld 不计分子但分别计数。
- 时间/维度：默认全部；region/industry/technology 可筛；无粒度；单位为 percentage ratio。
- 样本/限制/readiness：分母至少 1；当前 0/105，可如实返回；质量指标 `Green`，收入业务图 Red。

## 6. 时间与地区指标

### `trend.case_business_time` — 案例收录或发布时间趋势

- 用户问题/用途：业务案例在何时发生或公开；趋势发现。
- 定义/公式/分子/分母：按明确的 `case_time_basis=published|occurred` 分桶计 canonical cases；“收录”若采用 accessed_at 必须使用不同 metricId `quality.case_ingestion_trend`。
- 表与字段：待新增 case business time；禁止 created_at/accessed_at 静默回退。
- 时间/维度：默认 24 月、最大 10 年；month/quarter/year；region/industry/technology 支持。
- 样本/限制/readiness：总 n>=12；当前业务时间 0/105；`Red`。

### `trend.policy_publish_time` — 政策发布时间趋势

- 用户问题/用途：政策发布节奏如何；政策环境研究。
- 定义/公式/分子/分母：按 publish_date 分桶的 `COUNT(DISTINCT policy.id)`；分母不适用。
- 表与字段：policies.publish_date + E_policy；去重 policy.id；缺失日期排除并计 missing。
- 时间/维度：默认 36 月、最大 10 年；month/quarter/year；region 可筛，industry/technology 需审核关系。
- 样本/限制/readiness：总 n>=12；当前 57/57 有日期；`Green`，明确范围补空桶 0。

### `region.case_count` — 地区案例数量

- 用户问题/用途：各地区有多少业务案例；地区排名与下钻。
- 定义/公式/分子/分母：按 region 的 `COUNT(DISTINCT canonical_case_id)`；可返回占全部 E_case ratio。
- 表与字段：case region + canonical mapping；未来需 region_role=registered|operating。
- 时间/维度：默认全部；regionLevel=province；industry/technology 可筛；案例时间字段可用后支持粒度。
- 样本/限制/readiness：bucket n>=3 可画；重复和地区语义未解；`Yellow`。

### `region.policy_count` — 地区政策数量

- 用户问题/用途：各地区有多少适用政策；地区政策入口。
- 定义/公式/分子/分母：按 applicability region 的 `COUNT(DISTINCT policy.id)`；分母为当前 E_policy 可选。
- 表与字段：policies.region_id、regions、sources；去重 policy.id。
- 时间/维度：默认 36 月；month/quarter/year；regionLevel、industry/technology 支持条件同上。
- 样本/限制/readiness：bucket n>=1；当前字段完整但数据库语义未显式命名；`Yellow`。

### `region.industry_distribution` — 地区行业分布

- 用户问题/用途：某地区的案例集中在哪些行业；地区到案例联动。
- 定义/公式/分子/分母：该 region 内每 industry 的 unique canonical case count / 该 region E_case。
- 表与字段：case region、industry relations、canonical mapping；多标签统一处理。
- 时间/维度：默认全部；region 必选 1–10，industry/technology 可选；无收入单位。
- 样本/限制/readiness：地区总 n>=10、bucket n>=3；重复和语义限制；`Yellow`。

## 7. 质量指标

### `quality.verified_rate` — 已核验比例

- 用户问题/用途：已发布数据中有多少完成证据审核；质量观察。
- 定义/公式/分子/分母：各 entity 的 published + verified / published；同时返回 item-only 与 full-chain rate。
- 表与字段：status、ai_evidence_status、source relation；去重实体 id。
- 时间/维度：默认全部；可按 entityType/region/industry 分组；时间用 updated_at 仅作审核运营过滤，不叫业务趋势。
- 样本/限制/readiness：分母>=1；当前案例 99.1%、政策 96.6%、来源 99.2%；`Green`。

### `quality.valid_source_rate` — 有效来源覆盖率

- 用户问题/用途：业务记录是否都可追溯到合格来源；证据质量。
- 定义/公式/分子/分母：具备 E_source 链的 published+verified item / published+verified item。
- 表与字段：case/policy source_id 与 sources provenance/status；去重业务 id。
- 时间/维度：默认全部；entityType/region/industry 可筛；无粒度和收入单位。
- 样本/限制/readiness：分母>=1；当前案例 105/105、政策 57/57；`Green`。

### `quality.industry_completeness` — 行业字段完整率

- 用户问题/用途：多少案例有至少一个已审核行业；是否可画行业图。
- 定义/公式/分子/分母：有 approved industry relation 的 E_case / E_case；政策作为独立 series。
- 表与字段：case_tags/policy_industry_tags/tags；去重 canonical case/policy.id。
- 时间/维度：默认全部；region 可筛；无粒度和收入单位；多标签只算一次完整。
- 样本/限制/readiness：分母>=1；case 105/105、policy 0/57；`Green`（质量事实）。

### `quality.technology_completeness` — 技术字段完整率

- 用户问题/用途：结构化技术覆盖是否达到看板门槛。
- 定义/公式/分子/分母：同 `technology.completeness`；自由文本单列 `freeTextPresence`，不计正式分子。
- 表与字段：technology relations；去重 canonical_case_id。
- 时间/维度：默认全部；region/industry 可筛；无时间粒度和收入单位。
- 样本/限制/readiness：当前正式 0/105，自由文本 100/105；`Green`（质量事实）。

### `quality.revenue_completeness` — 收入字段完整率

- 用户问题/用途：收入是否足以支持分布和中位数。
- 定义/公式/分子/分母：同 `revenue.completeness`，另返 actual/estimated/unknown/withheld。
- 表与字段：规范化收入字段；去重 canonical_case_id。
- 时间/维度：默认全部；region/industry/technology 可筛；无粒度；分组单位必须明确。
- 样本/限制/readiness：当前 0/105；`Green`（质量事实）。

### `quality.region_completeness` — 地区字段完整率

- 用户问题/用途：多少对象有可解释的地区关系；地区图门槛。
- 定义/公式/分子/分母：非空且存在 regions 的 E_case/E_policy / 各自 eligible total；语义 role 完整率另列。
- 表与字段：region_id、regions、待新增 region_role；去重对应业务 ID。
- 时间/维度：默认全部；entityType/industry/technology 可筛；无粒度和收入单位。
- 样本/限制/readiness：当前 case 105/105、policy 57/57，但 case role semantic 0%；`Green`（字段存在率）。

## 8. 展示门槛与变更治理

- `Green` 仍必须返回样本量、缺失量、更新时间、dataVersion 和 caveats。
- `Yellow` 可以上线，但图标题旁显示“样本/口径限制”，Tooltip 和数据表都携带限制；不得用强结论文案。
- `Red` 不画业务值；UI 展示缺什么以及解除条件。质量覆盖率为 0 可以 Green，因为它陈述的是数据质量事实。
- 修改公式、去重、时间字段、收入算法或样本门槛必须提升 `metricVersion`，新增黄金 SQL 结果，并在 [phase-three-api-contract.md](phase-three-api-contract.md) 中保持响应兼容。
- 第三阶段的后端责任和测试门槛见 [phase-three-backend-handoff.md](phase-three-backend-handoff.md) 与 [phase-three-evaluation-plan.md](phase-three-evaluation-plan.md)。
