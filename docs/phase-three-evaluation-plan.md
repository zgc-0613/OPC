# SoloFirm 第三阶段评测与验收计划

> 版本：`phase3-eval-v1`
> 本轮只定义 seams、夹具和门槛，不调用真实模型批量评测。产品规格见 [phase-three-product-spec.md](phase-three-product-spec.md)。

## 1. 测试策略

采用 TDD：未来实现按一个公开 seam、一个失败契约、一个最小实现的纵向切片推进，重构放在评审阶段。本规格轮先用一次性只读断言复现 RED，再让 Markdown Schema、六个夹具和服务语义门禁转 Green；没有编写应用测试或业务代码。统计事实由未来 MySQL 8.4 Testcontainers 黄金集证明；AI 正确性分成 deterministic contract tests 和 bounded real-provider evaluations，不能用单次演示代替门槛。

测试层级：

1. 纯函数：筛选规范化、时间桶、百分位、标签多选、响应转换、图表键盘模型。
2. MySQL 集成：eligible scope、聚合、下钻、版本和并发审核。
3. API contract：参数上限、响应/错误、ETag、disabled user、owner boundary。
4. Vue component：loading/empty/partial/error、Tooltip、表格替代、响应式控制和 reduced-motion。
5. Agent deterministic：tool selection、allowlist、structuredResult、citation legality、snapshot rehydration。
6. 真实模型评测：固定审核题集，记录 provider/model/promptVersion/evidenceVersion、适用时的 dataVersion 和 Token/延迟。

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
- value_status=unknown/withheld/estimated/actual 收入，min=max、单 bucket 区间、跨 bucket 区间、不同 currency/period/type。
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
| 地区角色 | operation/registration 未就绪=unavailable；legacy=partial；已就绪无记录=empty；policy_applicability country 不复制；收入拒绝 legacy |
| API 契约 | complete/empty/partial/unavailable、错误码、字段类型、排序 |
| 下钻一致性 | 全分页 ID 合集与聚合 bucket 成员完全一致 |
| 数据版本 | stale GET cursor、stale AI POST、历史 Run 旧版本可读 |
| ETag/缓存 | 200→304、审核后新 ETag、Authorization Vary、无跨用户缓存 |
| Tooltip | 键鼠等价；值/占比/n/缺失/限制齐全，不依赖 hover 唯一呈现 |
| 键盘访问 | tab 到图/表操作、方向键/Enter/Space、焦点恢复、无焦点陷阱 |
| 移动端布局 | 320/375/768/1024 宽，长标签、200% zoom、无页面横向溢出 |
| reduced-motion | 动画禁用仍可理解，loading 不闪烁，焦点不丢 |
| 状态 | loading、empty、partial、error、stale、Red unavailable 全覆盖 |
| 完整度宏平均 | component 独立 ratio；case 6 项和 policy 4 项分别算术平均；禁止混合微平均 |
| 收入分桶/分位数 | point、单 bucket interval、spans_multiple_bins；Type 7 奇偶样本、重复值、边界和异常值 |

本轮禁止 Playwright，因此这里只定义未来验收 seam；组件级自动化用 Vitest/Vue Test Utils，浏览器响应式与读屏验证在对应开发轮使用项目批准的非本轮工具和人工检查。

## 6. API 契约验收

- 所有 query/body 长度、多选上限和未知字段都做 negative tests。
- 完整安全接线测试从 SecurityFilterChain 发起真实 MockMvc 请求：`/api/analytics/**` permitAll 后必须到达 UserAuthInterceptor；匿名/过期 401、disabled 403、有效用户 200、管理员 token 不能替代用户 token，且不出现默认登录页或 Basic challenge。
- cursor 绑定 user/filter/version，篡改或复用返回 409。
- `/api/analytics/**` 未登录 401、disabled 403；公开详情链接仍按现有边界工作。
- technology taxonomy 未就绪且无 technologyTagIds 返回 200 unavailable；显式非空技术筛选返回 409；taxonomy 就绪但无数据返回 200 empty。三条契约必须分别有测试。
- `regionRole` 与 entity/metric 组合受控；from-analytics filters 缺少显式 regionRole 返回 400 `ANALYTICS_REGION_ROLE_REQUIRED`。operation/registration 未就绪必须 200/unavailable/Red + `CASE_REGION_ROLE_NOT_READY`；显式 legacy 必须 200/partial/Yellow + `LEGACY_REGION_SEMANTICS` 且标题“相关地区分布”；规范化角色已就绪但筛选无记录才是 200/empty/sampleSize=0。quality operation=0 仍可 Green；country 政策不复制，收入地区只接受 approved primary operation。
- `/sessions/start` taskContext 覆盖：旧客户端省略兼容、版本/未知字段/长度、intent 一致、case 1 与 comparison 2–3 唯一 ID、dimension/outputDepth allowlist、技术 ID 优先、非空 content、canonical hash、首次冻结和跨设备回读。事务顺序定点断言固定 8 项：首次无效 case/source 均在幂等 miss 后的证据行锁内返回 400 且零副作用；格式预检后、证据加锁前撤销仍返回 400；加锁后撤销等待提交；成功后证据失效的四项身份精确重放返回原 receipt 且不重复扣费；同 key 不同身份 409；并发相同请求只创建一次。幂等 hit/mismatch/in-progress 均先于首次资格复检。
- taskSelectedEvidence 必须与 canonical taskContext 精确一致：案例分析 1 个、案例比较 2–3 个、selected-source 核验 1 个来源、其他任务全空，policyIds 在 v1 恒空。只有幂等 miss 的首次创建才在 start 事务内锁定并复检显式证据；case 不存在、不可公开使用、未双 verified 或缺合法来源链时返回 400 `PHASE3_CASE_NOT_ELIGIBLE`，且不创建 session/message/Run、不预留 Token 或生成选择/授权证据。
- source_verification 覆盖指定合法 sourceId、越权/不存在/失效/非 HTTP(S)/provenance 缺失 sourceId、结论搜索模式、正文伪造数字/URL/ID、任意 URL 抓取拒绝，以及不同 taskContext 的幂等冲突。显式 source 资格失败统一返回 400 `PHASE3_SOURCE_NOT_ELIGIBLE`，并具有与 case 相同的零持久化/零 Token 副作用。
- `evidence_insufficient` 只测试已通过提交前资格校验并成功原子创建 session/message/Run 后的受控检索不足、执行期 revision/availability 变化或部分证据；提交前无效 ID 进入该终态必须失败，合法受理后的运行期不足被描述为 400 也必须失败。
- phase3 structuredResult 用 API 文档内 Draft 2020-12 schema 验证六类契约夹具；六个夹具还必须通过 taskContext/选择/授权/引用/覆盖率以及 `runEvidenceFixture` 实体、来源 link、citation metadata、跨案例/政策引用和 evidenceVersion 重算。单案例 section fact 的全部 sourceIds 必须链接该 caseId；比较维度 fact 必须为每个比较案例覆盖至少一条相连授权来源；policy_lookup 固定以非空 `policy 2001 -> source 9004 -> fact -> citation` 作为 policySourceLinks 正向 seam。预算固定为 synthesis 3200 tokens、directAnswer<=600、全结果 ClaimItem 合计<=6、EvidenceSection<=10、citations<=6、nextQuestions<=2、渲染 Assistant 文本<=12000。authorizedEvidence 单字段<=120 且 case+policy<=120，依据是 12 次工具调用配置上限乘搜索 limit 10；所有展示业务 ID 数组<=6。
- 原有 19 个 negative fixtures 继续按稳定原因覆盖未知 schemaVersion、discriminator、无来源 fact、未授权 ID、上下限、未知字段、技术维度、比较集合/维度、task selection 和 source mode。新增集合固定为 20 个 reason-specific 变体：18 个必选变体覆盖 case 不存在/未 published/未 verified/无 link/来源 ineligible/悬空 link，source 不存在/provenance 缺失，授权集合漂移、citation revision 漂移、跨案例来源误引，全零或 revision/contentHash/eligibility/link 不匹配的 evidenceVersion，以及 400/evidence_insufficient 两种状态倒置；另 2 个补充变体覆盖 case-source provenance 与 fixture 中合格但未授权的实体。
- 本轮唯一性定点断言固定 7 项：重复 case/policy/source ID 和重复 caseSourceLink/policySourceLink pair 分别因唯一原因失败；同一 source 连接两个不同 case，以及连接不同 case 与 policy 均合法。另断言唯一性/引用完整性先于排序和哈希，原数组重排不改 evidenceVersion，重复记录绝不能进入规范输入。policy 正向 seam 另覆盖删除 link、非关联来源引用、policyId/sourceId 漂移和旧 evidenceVersion 失败。
- evidenceVersion 对所有 Phase A 新结果必填。唯一规范输入为固定顺序的 `schemaVersion,cases,policies,sources,caseSourceLinks,policySourceLinks`；实体仅含 id/revision/contentHash/eligibility，稳定排序后用紧凑 UTF-8 JSON 和 SHA-256 重算。任一真实证据字段或 link 变化必须改变版本，原数组重排经规范排序后必须不变，空证据也必须得到稳定非零哈希；入队 identity evidence_hash 不可冒充。普通 Phase A dataVersion=null，from-analytics 必须非空，历史值不互相改写。
- from-analytics 遇到 aggregate、SQL、URL、citations 或任意实体 IDs 返回 400，且不创建 message/run。
- 相同 idempotencyKey + 相同规范化请求返回原 receipt；不同请求 409。
- snapshot 创建、message/run/tool/citation 写入要么原子成功，要么无半成品可见。
- 报告只能保存 owned completed final message，并固化 evidenceVersion、可空 dataVersion、finalMessageId/runId/citation manifest；其他状态拒绝。trash/restore/permanent 都只接受 JSON body 正整数 expectedRevision：缺失/null/零/负数/浮点/字符串=400，过期=409 `REPORT_REVISION_CONFLICT`，成功 revision+1；目标状态+当前 revision 无变更，旧 revision 重放仍 409，超时后 GET 再决策。另测 30 天 purge_after、到期/未到期、重复 worker、锁内 revision、restore/purge 竞争、失败重试、运行中作业拒绝 permanent、永久清正文/notes/导出缓存、日志无全文、证据 unavailable、session purge 后独立报告仍可读。
- 反馈测试以服务端 feedbackEligible 为准：owned completed=true；owned evidence_insufficient 仅有用户可见持久化 Assistant 结果时=true；received/running/planning/clarification_needed/cancelled/expired/failed=false。另覆盖 helpful/not_helpful reason allowlist、comment 500 code points、revision/upsert、跨用户/管理员 token 拒绝；管理员质量接口只返回聚合且不含问题、回答、comment、思维链或 Provider 原始响应。

## 7. 阶段验收

### Phase A

- 40 题中的单案例/比较/技术/政策 32 题先达到全部硬门；允许图表 8 题待 Phase C。
- phase3-task-v1 持久化/回读、source_verification、taskSelectedEvidence/authorizedEvidence、evidenceVersion、structuredResult Schema+服务语义、缺失收入和逐结论 sourceIds contract tests 100% 通过；Phase A 不等待 dataVersion 账本。
- 保存/继续研究不破坏现有历史、幂等、取消和配额。

实现前门禁以 [phase-three-api-contract.md](phase-three-api-contract.md) 为唯一提取源：Schema 必须唯一、Draft 2020-12 meta-validation 通过、六个正向夹具 Schema/完整服务语义/evidenceVersion 重算均通过，原 19 个和新增 20 个命名负向变体因预期原因失败；本轮事务 8 项、policy 链 10 项、唯一性 9 项定点断言全部通过。显式选择 400/零副作用与运行期 evidence_insufficient 边界唯一，报告三接口 CAS 不回退，且跨文档不得残留旧证据字段。规格门禁通过只允许进入实现，不代表上述 Phase A 运行时代码已经完成。

### Phase B partial production release

- 所有上线 Green/Yellow 指标有黄金 SQL、下钻一致性、API 和组件状态测试。
- Red 收入/技术/case trend 不出业务图，质量状态正确。
- p95 SLA 在与生产规模相当的 seed 上达标；无 N+1。
- 这一门槛允许部分生产上线，但不得标记 Phase Three product complete。

### Phase C

- 40 题完整门槛通过；stale/tamper 题 100% 拒绝非法上下文。
- 图表到 session/message/run/citation/report 的链路可审计、可恢复、可导出。
- 性能、Token、用户反馈和管理端质量观察均有真实指标，不记录隐藏推理或敏感正文。

### Phase Three product complete

- 行业正式聚合与下钻通过。
- technology taxonomy 已上线并达到字典最小样本，至少一个技术分布或采用可视化通过黄金测试。
- 收入字段规范化；至少 30 个同 currency/period/type 的可比案例，覆盖率至少 40%，至少一个正式收入可视化通过分桶和 Type 7 黄金测试。
- 图表到 AI、报告保存/恢复/永久删除/导出、用户反馈和管理员脱敏聚合完成。
- 完整 40 题、性能和 Token 门槛全部通过。未满足时只能称前端实现完成或部分上线。

路线图见 [phase-three-roadmap.md](phase-three-roadmap.md)，后端责任见 [phase-three-backend-handoff.md](phase-three-backend-handoff.md)。
