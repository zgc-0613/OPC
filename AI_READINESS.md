# SoloFirm AI Readiness

## Purpose

This file records the stable prerequisites for an AI assistant without choosing a model provider or committing to a multi-agent UI. It is the handoff point for the revised product proposal.

## Data boundary completed

- Anonymous case, policy, and source APIs read `published` content only.
- Public case and policy detail APIs treat non-published records as not found.
- Administrator list and detail APIs retain access to draft, reviewed, pending, and published records.
- Public dashboard totals, covered regions, and recent updates use the same published-only rule.
- The frontend administrator pages use `/api/admin/**` reads instead of reusing public APIs.

Current administrator read endpoints:

```text
GET /api/admin/cases
GET /api/admin/cases/{id}
GET /api/admin/policies
GET /api/admin/policies/{id}
GET /api/admin/sources
```

## Existing data that can support an MVP

Cases already contain a title, region, category, actor, summary, business model, AI tools, outcome, tags, source, original URL, status, and reviewer. Policies and sources already provide traceable records that can be loaded with a case analysis request.

This is enough for a first evidence-aware case analysis prototype, provided the selected cases are manually checked before being included in the evaluation set.

## Data gaps before analytics claims

The current case schema does not contain normalized fields for:

- industry taxonomy;
- technology taxonomy;
- revenue amount, currency, period, and metric type;
- verification state separate from publication state;
- multiple evidence records and claim-level citations;
- source snapshots and content hashes;
- data quality coverage and missing-value reasons.

Industry, technology, and revenue dashboards must not infer these values from free text and present them as verified facts. AI may propose candidate values, but an administrator must approve them before they enter published analytics.

## Provider-neutral backend boundary

The backend should expose one internal provider interface instead of allowing controllers to call Coze or another model directly.

```text
AI controller
-> task orchestrator
-> published data and evidence loaders
-> deterministic calculation tools
-> AI provider adapter
-> structured-output and citation validator
-> analysis record
```

The provider adapter must own authentication, timeout handling, retries, response parsing, and provider-specific request formats. Provider secrets remain in server environment variables and must never be returned to the Vue application.

## Minimum analysis record

Every persisted AI run should retain:

- user ID;
- task type;
- target case or policy ID;
- model provider and model identifier;
- prompt version;
- input data version or content hash;
- referenced source IDs;
- structured result;
- success or failure state;
- token or cost metadata when available;
- created time and latency.

## Decisions required from the revised proposal

1. What is the single primary task for the first release?
2. Which model provider is used for the prototype and what is the monthly budget?
3. Are user prompts and generated results stored, and for how long?
4. What exact result schema and citation rules define a valid case analysis?
5. Which manually verified cases form the initial evaluation set?
6. Which normalized fields and coverage threshold allow a chart to be displayed?
7. What per-user quota and abuse limits apply?

No AI route, provider dependency, prompt, crawler, or analytics schema should be finalized until these decisions are recorded.

## Implemented Phase-One Boundary (2026-07-24)

The first evidence-aware case-analysis slice is implemented and deployed:

- provider-neutral runtime `AiClient` with an OpenAI-compatible adapter;
- AES-GCM encrypted provider configuration and administrator audit trail;
- authenticated capabilities and case-analysis APIs;
- published and explicitly verified case/policy/source evidence only;
- persisted usage, latency, request ID, status, error type, prompt version, and evidence hash;
- daily token quota and one concurrent analysis per user;
- strict JSON parsing and backend-validated citations;
- administrator evidence eligibility controls and model settings UI;
- a standalone protected analysis page with no homepage entry.

Production remains intentionally disabled until the official API Base URL, exact DeepSeek V4 Flash Model ID, and a real API Key are configured. Crawling, general chat, normalized revenue analytics, and automated publication remain outside this phase.

## Implemented Phase-Two Runtime Boundary (2026-07-25)

The local Phase-Two implementation adds an audited, bounded multi-round research runtime without replacing the existing provider or evidence governance:

- owned research sessions and ordered visible messages;
- asynchronous `202 Accepted` submission with polling, refresh recovery, cancellation, and safe retry;
- a finite run lifecycle with at most four model rounds, six tool calls, 8,000 aggregate tokens, a 12-message history window, and a 120-second default timeout;
- a provider-neutral native-tool contract plus a controlled JSON-plan compatibility mode, which remains the default until the configured production model's native tool support is verified;
- four read-only tools: `search_cases`, `search_policies`, `get_source`, and `compare_cases`;
- published-and-verified evidence filtering, current-run ID allowlists, deterministic evidence replay, legal citation validation, and stale-evidence conflicts;
- per-user and per-session active-run guards, per-user idempotency keys, shared daily-token quota reservation, aggregate multi-round settlement, and late-result rejection;
- administrator-safe runtime audit views that omit full private questions, secrets, raw provider responses, and hidden reasoning.

User content is persisted to provide conversation continuity. User messages are limited to 2,000 characters, assistant messages to 12,000 characters, and only the configured bounded history is returned to the model. Active, archived, and trash scopes are explicit. Trash defaults to a 30-day `purge_after`; restore clears the deadline, while manual or scheduled purge irreversibly scrubs readable profiles, titles, messages, citations, tool arguments/results, evidence snapshots, and run results. Minimal non-content run/token metadata remains for security and accounting audit. The product therefore says “永久删除对话内容” rather than claiming all audit rows are removed.

The stabilization pass makes the database run the durable queue source, adds renewable leases and bounded recovery, records each provider round for idempotent actual/estimated usage settlement, locks session mutations in a consistent order, and persists verified clarification context. Agent rollout is explicit, audited, and default-off; provider enablement alone cannot enable it. Tool metadata is closed and shared across native calls, JSON plans, schemas, runtime validation, audit, and tests.

The Assistant workspace pass adds scoped server history/search, stable cursor and message pagination, automatic/manual titles, terminal-run restoration, daily usage projection, explicit lifecycle APIs, and a bounded purge scheduler. The Vue client now keeps only selection/sidebar/drafts locally, renders sanitized Markdown, preserves old-message scroll position, and recovers temporary polling failures without inventing a failed server state. Final verification counts are recorded in `docs/assistant-research-workspace.md`; the 20-fixture deterministic Agent evaluation remains a runtime-contract check rather than a DeepSeek quality score.

Phase Two and the Assistant workspace are deployed in release `20260725-215634`. Migration postchecks, both domains, health, authorization boundaries, Assistant lifecycle checks, and temporary-data cleanup passed. The real `deepseek-v4-flash` probe completed with 3 model rounds, 2 completed tool calls, 1 legal citation, 0 unknown citations, and 6,897 total tokens; the Agent runtime is therefore deployed and evidence-capable rather than only locally implemented.

The Assistant stabilization pass is deployed in release `20260726-015858`. It adds atomic first submission, request-bound idempotency, signed snapshot history cursors, active/latest run separation, retry restoration, canonical industry confirmation, unified quota semantics, purge write revocation, recoverable migrations, responsive workspace ownership and the accessible unified industry combobox. Local gates passed with 299 Spring tests, 58 MySQL 8.4 integration tests, 57 Vitest tests and 63 default Python tests plus the explicit MySQL migration run. Production migration/postcheck, both domains, authorization, atomic replay, more-than-50-record pagination, purge and cleanup gates passed; the real `deepseek-v4-flash` probe completed with 2 model rounds, 1 completed tool call, 1 legal citation, 0 unknown citations and 4,421 total tokens.

The acceptance-closure release `20260726-080227` adds exact partial probe-account cleanup, per-user history metadata revisions with controlled `HISTORY_CURSOR_STALE` refresh, explicit tablet field grouping, and latch-controlled MySQL proof that a late tool/provider callback cannot repopulate purged content. The additive revision migration and postcheck passed in production. Local gates passed with 306 Spring tests including 62 MySQL 8.4 integration tests, 60 Vitest tests, 73 default Python cases and the explicit 7-case MySQL migration run. The real `deepseek-v4-flash` probe completed with finish reason `stop`, 3 model rounds, 2 completed tools, 1 legal citation, 0 unknown citations, 7,016 total tokens and 9,943 ms latency; an independent preflight confirmed the release, hashes, service ownership, single backend process and restored temporary-administrator count.

The readiness-and-settlement closure release `20260726-092000` removes unrelated readiness requests during profile editing, clarifies the first-question workflow, retains the Composer inside the dedicated viewport shell, and completes tablet touch and industry keyboard behavior. Runtime changes preserve actual Provider usage after cancellation, block purge until settlement, increment automatic-title history revision once, and serialize concurrent starts before child-row creation. No new database field or migration was needed in this pass. Local gates passed with 307 Spring tests including 63 MySQL 8.4 tests, 65 Vitest tests, all 8 frontend scripts and 74 explicit Python tests. Production migration/postcheck, both domains, authorization, readiness/start/message/history and cleanup probes passed. The real `deepseek-v4-flash` probe completed with finish reason `stop`, 3 rounds, 2 completed tools, 1 legal citation, 0 unknown citations, 6,956 total tokens and 8,593 ms latency; independent preflight confirmed matching hashes, loopback-only backend ownership and one backend process.

The locally verified research-quality closure publishes `agent-research-v2`, which validates one closed plan, executes several bounded evidence searches without a model round per search, restricts dependent tools to current-run authorized IDs, and validates a versioned structured result before rendering compatible Markdown. A separate ten-scenario quality evaluation covers policy lookup, Wuhan cases, case comparison, technology assessment, source verification, insufficient and cross-region evidence, follow-up, budget and stage differences.

The new user evidence surface is `GET /api/ai/research/runs/{runId}/evidence`. It reauthorizes Run ownership and current published/verified domain state, caps and deduplicates items, exposes only bounded user DTO fields and safe HTTP(S)/internal links, and marks changed evidence unavailable without returning stored tool JSON or content snapshots. The Assistant renders this as an independent grouped research-material area rather than burying it in the technical process drawer.

Provider Call and Run usage transitions now share an independent row-locked transaction and are replay-safe across cancellation, failure, late usage and concurrent estimate replacement. Compatibility session history uses one active-run projection rather than per-session reads. Agent planning/synthesis requests use bounded 1600/3200 output allowances under the configured aggregate Run limit; other AI capabilities keep the administrator's shared setting. Final synthesis receives the exact owned source allowlist; unknown numeric IDs are excluded from all persisted/user-visible source fields, unsupported facts become inference, and completion still requires a legal citation. Exact-tag Hubei case search has a controlled canonical-name and registered-alias fallback for legacy cases lacking the direct industry relation. Local acceptance passed with Spring `322` (`321` passed and `1` opt-in Provider smoke skipped), MySQL 8.4 `67/67`, Vitest `73/73`, all 8 frontend scripts, Python default `77` plus explicit MySQL `7/7`, production frontend/JAR builds and repository gates. Release `/opt/opc/releases/20260726-162930` is live; the real `deepseek-v4-flash` probe completed with 3 rounds, 2 tools, 5 case items, 2 policy items, 4 legal citations, 0 unknown citations, 11,039 total tokens and 46,160 ms latency. Independent preflight confirmed matching hashes, active services, one loopback-owned backend process and restored temporary-account counts.

The Phase 27 predeploy closure establishes `AgentResearchContract` as the single Java source for v2 prompt version, planning/synthesis budgets, field and aggregate limits, output sections and controlled diagnostics. Provider-facing schemas, prompt boundary text and Java validators now share those values, while fifteen sanitized replay fixtures preserve real incompatibility shapes without preserving model output or production content. Evidence coverage is re-derived from current-run authorized tool evidence, and cross-region IDs must be resolved from the database directory before the run can authorize them.

Deployment now creates an isolated migrated database candidate and transient loopback runtime before changing `/opt/opc/current`. The candidate first tests the configured production Provider, then completes an Agent v2 start/poll/evidence/settlement probe and validates Provider calls, model rounds, tools, legal citations, Token totals, latency, finish reason, settlement and case/policy/source coverage. A failed candidate cannot switch or restart the live release. Local verification passed with Spring `341` (`340` passed and `1` opt-in Provider smoke skipped), MySQL 8.4 `68/68`, Vitest `77/77`, all 8 frontend scripts, Python deployment/migration `83/83`, explicit Python MySQL `7/7` and both production builds.

Release `/opt/opc/releases/20260726-213258` is live after the mandatory isolated candidate completed on `deepseek-v4-flash`: 2 model/Provider rounds, 3 completed tools, 4 legal citations, zero unknown citations, 5 case, 2 policy and 4 unique-source evidence items, server-derived `sufficient` coverage, `settled_actual` usage, zero remaining reservation and `release_switched=false` before rollout. The postdeploy Agent probe also completed with 2 rounds, 2 tools, 4 legal citations and 9,965 total Tokens. Independent preflight confirmed matching remote hashes, three active services, valid nginx, one loopback-owned backend process and the new current release.

Phase 28 adds a true bounded continuation protocol: initial planning can request only independent case/policy searches, and a later model round receives the compact same-Run authorized results before it can request `compare_cases` or `get_source`. User-selected industry and server-derived region scopes are immutable within an established research session; explicit changes require a new research before any message persistence or Token reservation. Full tool audit JSON is bounded at 128 KiB UTF-8, while the model receives a deterministic 12 KiB `_authorized` projection whose retained IDs alone define authorization. Evidence responses now expose compatible available/total/unavailable counts, and `/assistant` is a lazy top-level protected workspace rather than a public-layout child. No database migration is added. Final local gates passed with Spring `357` (`356` passed, one opt-in smoke skipped), MySQL `70/70`, Vitest `83/83`, eight frontend scripts, Python `77/77 + 14/14 + 7/7` and both builds. Three isolated candidates made zero production changes: two policy responses exposed and closed `INVALID_DEPENDENCIES` and `UNCITED_FACT`; the final attempt passed policy but returned `evidence_insufficient` for case comparison and never reached source verification. Phase 28 was not deployed and the second-stage completion claim remains blocked.

The 2026-07-27 closure pass adds bounded recovery for invalid initial/continuation planning, removes continuation once server-derived evidence is terminal, supplies synthesis with current-run source allowlists, and raises the measured runtime defaults to five rounds, 28,000 aggregate Tokens and 3,200 planning output Tokens. The forward `20260727_agent_multiround_budget` migration changes settings data only and preserves higher administrator values. Local gates passed with focused orchestration/contract `53/53`, Spring `368` (`367` passed and one opt-in smoke skipped), MySQL `70/70`, Vitest `85/85`, eight frontend scripts, Python discovery `101` with seven explicit-MySQL skips, static migration `14/14`, Vite/JAR builds and repository checks. Real candidates have not yet produced one all-green three-scenario run, and the latest candidate stopped at `PROVIDER_CONNECTION_FAILED`; no production backup, migration, restart or switch occurred. Production remains `20260726-213258`, so Phase Two remains open.

The final 2026-07-27 closure fixes the remaining model-orchestration ownership defect exposed by candidate `/opt/opc/releases/20260727-070820`. A validated optional `requestedIntent` plus conservative current-message signals now resolve a server-owned operation set. Model intent may supplement that set but cannot remove required policy search, case search, case comparison or source verification. The same operation set drives terminal eligibility, controlled correction and the persisted `resolvedIntent`; region, industry, evidence authorization, citation and quota boundaries remain unchanged.

The forward `20260727_agent_multiround_budget` migration now also adds `ai_analysis_runs.requested_intent VARCHAR(40) NOT NULL DEFAULT 'auto'`; it is not data-only. Old clients remain compatible because omission resolves to `auto`. Local verification passed Spring `373`, MySQL 8.4 `71/71`, Vitest `87/87`, Assistant `36/36`, all eight frontend scripts, Python default `106`, explicit MySQL `7/7`, deployment hardening `85/85`, both builds and repository gates. Real Provider compatibility and production status are unchanged until the single allowed deploy workflow completes all three isolated scenarios and postdeploy checks; Phase Two remains open meanwhile.

The single permitted deploy was executed after a successful remote preflight. The isolated case-comparison scenario completed the real dynamic chain with six legal citations, and source verification completed `search_policies -> get_source` with two legal citations. Policy was correctly prevented from completing without its required chain and returned `REQUIRED_TOOL_CHAIN_UNSATISFIED`; therefore `release_switched=false` and no production backup, migration, restart or release switch occurred. A post-candidate red-to-green deployment fix now captures actual tool sequence before a failed Run's terminal gate; Python discovery passes `107` tests and deployment hardening `86/86`, but this reporting fix was not redeployed in the same round. Production remains `20260726-213258`, and Phase Two remains open.

The 2026-07-28 stabilization makes intent priority explicit: a validated non-`auto` request is authoritative, deterministic server operations are authoritative for `auto`, and model intent can supplement only when neither source provides an operation. This closes the policy-to-case priority inversion while keeping profile, Provider, authorization, citation and quota contracts unchanged. Full local verification passes Spring `380`, MySQL 8.4 `71/71`, Vitest `87/87`, all eight frontend scripts, Python default `109` plus explicit MySQL `7/7`, and both production builds.

Four bounded candidate executions were used: one initial attempt, one transient same-build retry and two code-corrected retries. The final batch passed policy and source verification but again ended case comparison as controlled evidence insufficiency after `search_cases, search_policies`, without `compare_cases`. The repeated diagnostic triggered the required stop; `release_switched=false`, all candidate resources were removed, and production remains `/opt/opc/releases/20260726-213258`. The implementation is locally verified, but production deployment and Phase Two closure are not complete.

## Phase Two Production Closure (2026-07-28)

The remaining zero-result branch is fixed and the runtime now owns completion of every required tool chain. A selected case search may be followed by exactly one cross-region expansion; two distinct authorized IDs trigger audited `compare_cases`. Required policy and source-verification tools receive reserved budget and execute through the same registry, request authorization, evidence hash and settlement path as model-planned tools.

Tool audit diagnostics are safe and operationally useful: they retain request/dependency identity, scope, field-presence flags, limits, returned counts and distinct authorized counts without query/category text. Comparison dimensions use the shared supported enum and deterministic fallback, closing the real `INVALID_TOOL_ARGUMENTS` candidate shape.

All local gates passed, the final candidate proved policy, dynamic comparison and dynamic source verification in one batch, and production switched once to `/opt/opc/releases/20260728-130142`. Production migration, health, authorization, history, evidence, citations, settlement and cleanup checks passed. `/opt/opc/current`, local/remote hashes, backup, database dump, rollback backend and previous release were independently verified. Agent Phase Two is complete; manual responsive visual review is the only remaining non-engineering check.

## Phase Three Decision Workbench Readiness (2026-07-29)

The preparation audit is complete and is specified by seven linked documents: `docs/phase-three-readiness-audit.md`, `docs/analytics-metric-dictionary.md`, `docs/phase-three-product-spec.md`, `docs/phase-three-api-contract.md`, `docs/phase-three-backend-handoff.md`, `docs/phase-three-evaluation-plan.md`, and `docs/phase-three-roadmap.md`.

Production was accessed through the existing protected read-only path. At `2026-07-29 01:08–01:10 CST`, 105 case rows, 57 policies and 121 sources satisfied the published/verified evidence chain. Policy publish dates and all eligible source chains are complete. Case business dates, explicit technology taxonomy and normalized revenue do not exist. The case dataset also has 42 exact duplicate candidates across 23 groups, so the unique business-case total remains unknown until canonical identity review.

AI readiness is asymmetric. The session/message/run/tool/citation/evidence/history foundation is ready; single-case analysis and comparison are partial extensions; technology assessment lacks a dedicated evidence/taxonomy contract; analytics APIs, dataVersion, snapshot rehydration, saved reports and report export are not implemented. Existing `/analysis` client-side aggregation is not an approved formal statistic.

Third-stage statistics keep the double published + verified rule, explicit missing values, business-time-only trends, canonical de-duplication, server-owned aggregation and server-rehydrated AI snapshots. Revenue charts are prohibited until normalized, reviewed comparable groups reach the metric-dictionary threshold. Pending tags and AI-inferred values never enter formal dashboards.

Implementation is ordered as Phase Three A (case analysis and technology evaluation), B (analytics APIs and accessible dashboards), then C (analytics-to-Agent snapshots, reports, export, performance and final evaluation). This readiness round changed documentation only; it did not implement Phase Three features, migrate data, call the model for a bulk evaluation or deploy production.
