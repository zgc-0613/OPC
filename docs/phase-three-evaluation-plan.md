# SoloFirm 第三阶段评测与验收计划

> 版本：`phase3-eval-v1`
> 本轮只定义 seams、夹具和门槛，不调用真实模型批量评测。产品规格见 [phase-three-product-spec.md](phase-three-product-spec.md)。

## 1. 测试策略

采用 TDD：每个阶段先提交失败的契约/黄金测试，再实现最小业务逻辑，再重构。统计事实由 MySQL 8.4 Testcontainers 黄金集证明；AI 正确性分成 deterministic contract tests 和 bounded real-provider evaluations，不能用单次演示代替门槛。

测试层级：

1. 纯函数：筛选规范化、时间桶、百分位、标签多选、响应转换、图表键盘模型。
2. MySQL 集成：eligible scope、聚合、下钻、版本和并发审核。
3. API contract：参数上限、响应/错误、ETag、disabled user、owner boundary。
4. Vue component：loading/empty/partial/error、Tooltip、表格替代、响应式控制和 reduced-motion。
5. Agent deterministic：tool selection、allowlist、structuredResult、citation legality、snapshot rehydration。
6. 真实模型评测：固定审核题集，记录 provider/model/promptVersion/dataVersion 和 Token/延迟。

## 2. 首批真实评测集

首批 40 题，五类各 8 题；每题使用已人工核验的真实案例/政策/来源 ID，问题文本去除个人信息。题集只保存必要证据 ID、预期工具和评分 rubric，不保存生产用户会话。

| 类别 | 数量 | 必含构成 |
|---|---:|---|
| 单案例分析 | 8 | 2 个证据充分、2 个信息缺失、1 个缺失收入、1 个跨地区、1 个冲突来源、1 个来源失效 |
| 多案例比较 | 8 | 2 个同业、2 个跨行业、1 个不一致收入口径、1 个 3 案例、1 个证据强弱差、1 个未授权 ID 攻击 |
| 技术评估 | 8 | 不同预算/团队/阶段；至少 2 个证据不足、1 个替代路线、1 个合规风险、1 个技术标签未审核 |
| 政策适用性 | 8 | general/specific/跨地区/过期或有效期不明；至少 2 个证据不足、1 个冲突来源 |
| 图表上下文研究 | 8 | 行业/地区/政策趋势；empty、partial、stale dataVersion、篡改总数、伪造引用各至少 1 |

40 题中必须显式覆盖：证据不足、数据版本变化、缺失收入、冲突来源、跨地区案例、过期政策。正式运行前由产品、数据审核和后端三方冻结题目版本。

## 3. Agent 评价指标

| 指标 | 计算 | Phase A/C 上线门槛 |
|---|---|---:|
| 工具选择正确率 | 选择与 rubric 允许工具一致的题 / 可评题 | >= 95% |
| 必要工具完成率 | server-required tool chain 完成题 / 要求题 | 100% |
| 引用合法率 | 当前 Run allowlist 内且当前可用引用 / 全部引用 | 100% |
| 引用与结论一致性 | 审核为直接支撑的事实结论 / 有引用事实结论 | >= 95% |
| 证据覆盖率 | 有合法引用的应引用事实 / 全部应引用事实 | >= 90% |
| 非法引用数量 | unknown/unauthorized/stale source IDs | 0 |
| 研究任务完成率 | 满足任务 schema 且非非预期失败 / 全题 | >= 90% |
| 技术评估维度完整率 | 成功结果已填必需维度 / 必需维度 | 100% |
| 缺失数据诚实表达 | 未将 unknown/withheld/不兼容收入转为数值的相关题 / 相关题 | 100% |
| 首次响应时间 | 请求到 202 receipt/可见 progress | p95 <= 2s；receipt <=1s |
| 完整响应时间 | 请求到 terminal result | p95 <= 90s，hard timeout 120s |
| Token 消耗 | Run aggregate totalTokens | p95 <= 28,000；首批 median 目标 <=12,000 |
| 用户反馈 | pilot 中 helpful/完成反馈 | >=70% helpful，且负反馈可关联 runId |

工具选择正确率不奖励“多调工具”。证据不足题的正确结果可以是 controlled insufficiency，仍计任务完成；伪造补全则失败。

### 3.1 人工 rubric

每题由两名审核者独立标注：关键结论、允许证据、必须工具、禁止推断、缺失字段、地区和时间边界。分歧由第三人裁决。引用一致性使用结论级标注，不用“回答里有引用”替代。

### 3.2 回归规则

- provider/model/prompt/tool/schema/dataVersion 任一改变，至少运行 deterministic 全集和 10 题冒烟。
- prompt/tool/structuredResult 重大变化运行完整 40 题；与上一正式版本逐题对比，不只比较平均分。
- 引用合法率、必要工具完成率、非法引用是硬门；平均分不能抵消单项失败。
- 真实 Provider 暂不可用时 deterministic 测试继续，真实质量状态标 blocked，不填写猜测分。

## 4. Analytics 黄金夹具

新增独立 seed，至少包含：

- published/verified、legacy、excluded、draft 和来源链失效记录。
- 同 canonical case 多行、同源重复、近似但不应合并的不同案例。
- 0/1/2 个行业和技术标签、primary/secondary、pending tag。
- Asia/Shanghai 月末、年末、闰日、UTC 边界和空月份。
- unknown/withheld/estimated/actual 收入，min=max、区间、不同 currency/period/type。
- 0、负数（应拒绝）、极大异常值、边界 bin、同值 percentile。
- 省/市/区父子地区、跨地区政策、general/specific/unclassified。
- dataVersion 前后审核、merge、taxonomy 和 revenue revision 变化。

黄金期望以 canonical ID 集合为真值，再派生 count；不能只硬编码一个总数而不验证成员。

## 5. 数据看板测试 seams

| Seam | 必测内容 |
|---|---|
| 指标 SQL 黄金测试 | 每个 Green/Yellow metric 的 numerator、denominator、missing、bucket members |
| 去重 | caseId 与 canonical case；同源重复不重复；不同案例不误合 |
| 多标签 | unique case/bucket；占比和可 >100%；filter OR 语义 |
| 时间边界 | dateFrom/dateTo 包含日、月/季/年边界、未来日拒绝 |
| 时区 | Asia/Shanghai 与数据库 UTC+8；午夜不跨桶 |
| 空月份 | 仅明确范围补 0，标 `isSyntheticEmptyBucket` |
| 缺失值 | unknown 不为 0；missingCount 与成员集合一致 |
| 收入单位 | currency/period/type 必填；actual/estimated 分离；withheld 单列 |
| 异常值 | 负数拒绝、min>max 拒绝、outlier 规则版本化 |
| 筛选组合 | region descendants、industry/technology 多选、收入/时间组合和上限 |
| API 契约 | complete/empty/partial/unavailable、错误码、字段类型、排序 |
| 下钻一致性 | 全分页 ID 合集与聚合 bucket 成员完全一致 |
| 数据版本 | stale GET cursor、stale AI POST、历史 Run 旧版本可读 |
| ETag/缓存 | 200→304、审核后新 ETag、Authorization Vary、无跨用户缓存 |
| Tooltip | 键鼠等价；值/占比/n/缺失/限制齐全，不依赖 hover 唯一呈现 |
| 键盘访问 | tab 到图/表操作、方向键/Enter/Space、焦点恢复、无焦点陷阱 |
| 移动端布局 | 320/375/768/1024 宽，长标签、200% zoom、无页面横向溢出 |
| reduced-motion | 动画禁用仍可理解，loading 不闪烁，焦点不丢 |
| 状态 | loading、empty、partial、error、stale、Red unavailable 全覆盖 |

本轮禁止 Playwright，因此这里只定义未来验收 seam；组件级自动化用 Vitest/Vue Test Utils，浏览器响应式与读屏验证在对应开发轮使用项目批准的非本轮工具和人工检查。

## 6. API 契约验收

- 所有 query/body 长度、多选上限和未知字段都做 negative tests。
- cursor 绑定 user/filter/version，篡改或复用返回 409。
- `/api/analytics/**` 未登录 401、disabled 403；公开详情链接仍按现有边界工作。
- from-analytics 遇到 aggregate、SQL、URL、citations 或任意实体 IDs 返回 400，且不创建 message/run。
- 相同 idempotencyKey + 相同规范化请求返回原 receipt；不同请求 409。
- snapshot 创建、message/run/tool/citation 写入要么原子成功，要么无半成品可见。
- 报告只能保存 owned completed final message；导出正文来自服务端持久化结果。

## 7. 阶段验收

### Phase A

- 40 题中的单案例/比较/技术/政策 32 题先达到全部硬门；允许图表 8 题待 Phase C。
- structuredResult schema、缺失收入和逐结论引用 contract tests 100% 通过。
- 保存/继续研究不破坏现有历史、幂等、取消和配额。

### Phase B

- 所有上线 Green/Yellow 指标有黄金 SQL、下钻一致性、API 和组件状态测试。
- Red 收入/技术/case trend 不出业务图，质量状态正确。
- p95 SLA 在与生产规模相当的 seed 上达标；无 N+1。

### Phase C

- 40 题完整门槛通过；stale/tamper 题 100% 拒绝非法上下文。
- 图表到 session/message/run/citation/report 的链路可审计、可恢复、可导出。
- 性能、Token、用户反馈和管理端质量观察均有真实指标，不记录隐藏推理或敏感正文。

路线图见 [phase-three-roadmap.md](phase-three-roadmap.md)，后端责任见 [phase-three-backend-handoff.md](phase-three-backend-handoff.md)。
