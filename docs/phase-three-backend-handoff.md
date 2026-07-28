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
| 地区语义 | 产品 + 数据后端 | case/policy region_id | case region_role、policy applicability_region | 现有 regions 层级 | 注册/经营/适用语义明确；旧 region_id 兼容迁移；跨层级测试通过 | 是：强结论地区图 | 是 |
| 收入规范化 | 产品 + 数据后端 + 审核 | 历史原文候选、来源 | min/max/currency/period/type/estimated/disclosure/as_of/source | canonical case、证据审核 | unknown 与 withheld 分离；不由 LLM 自动批准；可比组和异常测试通过 | 是：全部收入图 | 是，但 UI 必须标 synthetic fixture |
| 数据版本 | 平台后端 | 审核水位、evidence revision、taxonomy/merge/revenue revision | immutable dataVersion、snapshot metadata | 上述数据治理 | 同版本可复现；任何资格/归并变化产生新版本；旧 Run 可读 | 是：全部 analytics/AI 联动 | 是 |
| 聚合查询与存储 | Analytics 后端 | 指标字典、真实 schema | overview/industry/technology/revenue/region/trend SQL/Mapper/service | canonical IDs、版本 | SQL 黄金集、EXPLAIN、下钻集合一致、SLA 达标 | 是 | 是 |
| 缓存与 ETag | 平台后端 | normalized filters + dataVersion | 私有缓存、强 ETag、失效机制 | 聚合与版本 | 304、Vary/Auth、审核后失效、无跨用户泄漏 | 是：性能 | 是 |
| Analytics API | Analytics 后端 | API contract | 7 个 GET 端点及错误码 | 聚合、鉴权、版本 | OpenAPI/contract tests 全通过；disabled 403；空/partial/stale 正确 | 是 | 是，前端可完全并行 |
| 技术评估工具 | AI 后端 + 产品 | 技术/场景/预算/能力/约束 | 专用受控工具或 evidence plan、结构化评分 | 技术 taxonomy、案例/政策工具 | 每评分有依据、confidence、missing evidence；无单独伪精确总分 | 是：Phase A 技术评估 | 是 |
| 单案例/比较结构结果 | AI 后端 | 产品输出 schema、现有 Agent v2 | versioned structuredResult | 现有 run/tool/citation | 缺失收入诚实、逐结论引用、2–3 案例边界、旧结果兼容 | 是：Phase A | 是 |
| 图表研究快照 | AI + Analytics 后端 | metricId、filters、buckets、dataVersion | analyticsSnapshotId、授权实体集 | 数据版本、聚合 | 服务端重建；拒绝数值/SQL/URL/任意 IDs；快照绑定 Run | 是：Phase C | 是 |
| 报告保存 | AI 后端 | owned completed session/run/message | report entity、revision、citation manifest | session/history、dataVersion | owner-only；running 不能保存；证据失效可见；幂等 | 是：保存报告 | 是 |
| 报告导出 | AI 后端 + 平台 | reportId、format | Markdown/HTML/PDF 或异步 job | 报告保存 | 内容来自持久化结果；含版本/引用/限制；无账户 PII | 是：导出 | 是 |
| 审核进入统计时机 | 数据后端 | review transaction | eligibility event/watermark | dataVersion | 业务和来源双 verified 后才进入；任一失效即退出；原子可测 | 是 | 是 |
| 自动采集 provenance | 后端智能体项目 | URL、发布者、采集时间、content hash | source candidate + lineage | 不在本仓库实现爬虫 | 未审核只进候选；重复抓取可识别；原 URL 可追溯 | 否，存量可先做 | 否，由其项目提供 |
| 重复合并规则 | 数据后端 + 审核 | URL/hash/title/人工判断 | merge decision、canonical revision | provenance | 同源重复不重计；不同案例不因同文章误合；可撤销/审计 | 是 | 是 |
| 历史版本处理 | 平台/AI 后端 | dataVersion、evidence revisions | 历史研究/报告版本与 current availability | 快照、报告 | 不重写旧结论；返回 stale/unavailable；不伪造旧原文 | 是：Phase C | 是 |
| API SLA/观察性 | 平台后端 | endpoint/runtime telemetry | latency、cache、error、refresh metrics | analytics API | 达到 p95；日志无 filters 中的敏感自由文本；requestId 可追踪 | 否但为上线门 | 是 |
| 测试环境和种子 | QA + 后端 | 指标边界案例 | MySQL Testcontainers seed、contract fixtures | 所有 schema | 覆盖重复、多标签、时区、收入、版本、空/partial/error | 是：自动化 | 是 |

## 2. 最小字段变更规格

本轮不执行迁移。后端设计评审至少包含以下前向兼容字段或等价规范化表：

### 2.1 身份与版本

- `case_items.canonical_case_id` 或 `case_canonical_members(case_id,canonical_case_id,decision,reviewed_by,revision)`。
- `sources.canonical_source_id` 或成员表；原 sourceId 永久保留用于引用。
- `analytics_data_versions(version,eligibility_watermark,taxonomy_revision,merge_revision,revenue_revision,created_at)`。
- merge 不能通过物理删除替代；历史 run 需要解析旧成员。

### 2.2 标签

- `tags.semantic_type ENUM/string: industry|technology|topic|other`，并保持 `is_industry` 兼容期。
- `tags.parent_tag_id` 可空；层级必须防循环。
- 关系表增加 `role=primary|secondary`、`review_status` 和 evidence/source 信息；或者建立新的 typed relation 表。
- alias 唯一性按规范化字符串和 semantic scope；别名永不作为稳定 bucketId。

### 2.3 收入

- `revenue_min/revenue_max DECIMAL`，min <= max 且非负；单值可 min=max。
- `currency CHAR(3)`、`revenue_period`、`revenue_type`、`revenue_is_estimated BOOLEAN`。
- `revenue_disclosure_status=reported|estimated|unknown|withheld`。
- `revenue_as_of_date`、`revenue_source_id`、审核状态/修订。
- 不把 currency 默认成 CNY，不把单位默认成元，不从空值推 0。

更推荐独立 `case_revenues` 版本表而不是在 case_items 上覆盖一个当前值，因为同一案例可能有多个周期/类型/日期。表必须支持“同一可比组选择哪个当前值”的确定规则。

## 3. 聚合实现要求

1. 建立一个共享 `EligibleEvidenceScope` SQL/Mapper 片段或数据库 view 等价物，避免七个端点各写不同的 published/verified 条件。
2. 所有聚合先确定 canonical eligible ID 集，再连接多标签；不能在 join 后用行数当案例数。
3. 时间边界使用半开区间，Asia/Shanghai；MySQL session/config 与 Java time 明确一致。
4. 多标签 OR/AND 行为由 filter contract 固定，SQL 黄金测试覆盖。
5. sampleSize、missingCount、totalEligible 和下钻集合来自同一个查询计划/版本。
6. top-N 的 other bucket 保存可下钻 ID 条件，不能丢失长尾。
7. 收入 percentile 算法、区间值策略和 outlier policy 进入 metricVersion；不在 Java/SQL 两处各自实现不同算法。
8. 每个新 SQL 提供 MySQL 8.4 Testcontainers 结果和生产相似数据量 EXPLAIN；不在生产执行写入式预计算迁移作为本轮验证。

## 4. 契约夹具

前端可以在后端实现前使用版本控制的 JSON contract fixtures，但必须满足：

- 路径建议 `docs/fixtures/phase-three-api/` 或测试专用目录，显式字段 `fixture=true`、`contractVersion`。
- 数字是边界测试值，不标为真实 SoloFirm 业务数据；生产构建和运行时不能 import fixtures。
- 至少含 complete、empty、partial、unavailable、stale-version、invalid-filter、cursor-next-page、multi-label、missing-income、disabled-user。
- fixture 的 metricId/字段/错误码由后端 contract test 反序列化，防止前后端漂移。
- 真实生产页面永远调用 API；fixture 只用于 Vitest/component tests 和本地 story harness。

## 5. 阶段门槛

- Phase A 阻塞：单案例/比较结构 schema、技术评估工具、报告最小保存（若 A 要求保存）；可与 UI 通过夹具并行。
- Phase B 阻塞：canonical case、标签语义、dataVersion、聚合 API。收入和技术图可保持 Red empty-state，不阻塞其他 Green/Yellow 图上线。
- Phase C 阻塞：analytics snapshot、from-analytics、报告导出、版本回跳。

实施顺序和每轮不做内容见 [phase-three-roadmap.md](phase-three-roadmap.md)，质量门槛见 [phase-three-evaluation-plan.md](phase-three-evaluation-plan.md)。
