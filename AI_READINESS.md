# SoloFirm AI Readiness

## MySQL 8.4 Testcontainers resource closure (2026-08-09)

The Phase One integration fixture now has explicit test-run ownership. A validated UUID v4 from `opc.phase-one.mysql.run-id` is applied as `com.opc.phase-one.run-id`; the container is started early for Spring's dynamic JDBC properties and stopped idempotently in `@AfterAll`. The runner queries only that exact label and `mysql:8.4`, so preexisting Testcontainers sessions remain outside its cleanup scope.

The reusable runner is `python scripts/run_phase_one_mysql_test.py`. It requires a fresh `80/80` Surefire report with zero failures/errors and zero containers for the current UUID before returning success. A real single test and the full `80/80` suite passed; full run `06b0c950-985b-4b53-85e4-61d2fa21b297` completed in `903.203s` (`885.353s` Surefire), with container readiness `36.812s`, Spring readiness `44.812s`, current-run count `0` and no change to the six preexisting container records. This is a local test-infrastructure correction and was not deployed.

## Source-verification test-closure stability (2026-08-09)

The server-owned insufficient path now has an orchestration-level regression in addition to assembler and UI coverage. A provider `final` result containing only unresolved verification claims cannot settle as `completed`: the final message, Markdown, structured result, citation list and history all use the assembled `evidence_insufficient` result. Legacy factual fields remain suppressed and publisher assessment remains unknown.

The real MySQL 8.4 integration suite is intentionally DDL-heavy: 80 isolated test methods rebuild the base schema and rerun bounded idempotent migrations. Two cached-image runs passed `80/80` in `808.2s` and `766.953s`; container/Spring startup was under 37 seconds, while test execution consumed the remaining time. The former 120-second stop was an external command budget, not an Agent runtime or Docker startup failure. Run it with `python scripts/run_phase_one_mysql_test.py`; it uses a 1,200-second bound, fresh-report validation, redacted JVM diagnostics and exact-container failure cleanup.

The runtime correction and test runner were released through the guarded production flow. Current release: `/opt/opc/releases/20260809-193452`; previous release: `/opt/opc/releases/20260809-150138`; backup: `/opt/opc/backups/20260809-193452`; rollback JAR: `/opt/opc-backend.rollback.20260809-193452`. This remains a Phase Three user-facing v1 / partial release; data-governance and manual browser acceptance boundaries are unchanged.

## Source verification closure (2026-08-09)

The source-verification result is now derived from the authorized evidence bundle rather than the model's verdict field. The server returns `insufficient` for no-claim or unresolved-only evidence, `supports` for supports-only evidence, `partially_supports` when support is mixed with unresolved claims, `does_not_support` only for authorized contradicts-only claims, and `conflicting` when supports and contradicts apply to the same claim. `evidenceCoverage.status`, `taskResult.evidenceStatus`, and `taskResult.verdict` use the same matrix.

Publisher verification is also server-owned. `publisherAssessment` contains only stable fact items generated from publisher metadata already present on authorized evidence. Missing publisher metadata remains `unknown`; no inference is made from titles, URLs, user text, or model output. Existing citation allowlists, evidence revisions, availability and authorization checks remain unchanged.

Closure evidence: four new boundary assertions were RED before implementation; GREEN assembler tests passed `21/21`, `AgentOrchestratorTest` `44/44`, focused Agent/assembler/report tests `80/80`, frontend Vitest `172/172`, all eight frontend contract scripts, Vite build, Spring `545` (0 failures/errors, 1 opt-in provider skip), independent MySQL 8.4 `80/80`, JAR packaging, Python migration `17/17`, deployment hardening `103/103`, syntax and repository gates all passed. Independent post-deploy preflight confirms release `/opt/opc/releases/20260809-133127`, previous release `/opt/opc/releases/20260809-024820`, backup `/opt/opc/backups/20260809-133127`, database dump `/opt/opc/backups/20260809-133127/opc_platform.sql.gz`, rollback `/opt/opc-backend.rollback.20260809-133127`, and eligible evidence counts of 121 verified sources, 51 policies and 105 cases.

The guarded production probe exercised the source-verification workflow but did not independently synthesize all verdict branches or both publisher states. Those branches are covered by deterministic local tests. The release workflow rejected one transient Provider connection failure and one bounded case-comparison timeout before the final all-green switch; both failures kept production unchanged and cleaned every candidate resource. This is a Phase Three user-facing v1 / partial release; technology/revenue data-readiness and browser-based responsive/accessibility review remain explicit boundaries.

## Final closure correction (2026-08-08 22:59 CST)

The current worktree was validated and deployed through the guarded candidate workflow. Frontend Vitest passed `171/171` across 28 files, all eight repository frontend scripts passed, Vite transformed 1,822 modules, MySQL 8.4 Testcontainers passed `80/80`, the full Spring suite passed `532` tests with zero failures/errors and one explicit opt-in provider smoke skip, the executable JAR was packaged, migration checks passed `17/17`, deployment hardening passed `103/103`, Python syntax and repository gates passed.

Production is now `/opt/opc/releases/20260808-225959`; the previous release is `/opt/opc/releases/20260808-162621`, the database backup is `/opt/opc/backups/20260808-225959/opc_platform.sql.gz`, and the backend rollback artifact is `/opt/opc-backend.rollback.20260808-225959`. Candidate policy, case-comparison and source-verification probes completed with authorized citations, bounded tool sequences, settled actual usage and no unknown citations. Post-switch report/export, owner isolation, preference consent/delete, feedback CAS, Analytics snapshot ownership, admin quality authorization and public/health/anonymous-auth probes passed.

This is a **Phase Three user-facing v1 / partial release**. Technology and revenue remain honestly unavailable until verified, normalized data reaches the documented threshold. No new migration was added in this round; the existing additive Phase Three migrations were rechecked and applied by the guarded deployment. Browser responsive, keyboard and assistive-technology review remains manual acceptance work.

## Phase Three User-Facing v1 Release Evidence (2026-08-08)

The release gate is now supported by current command evidence: frontend Vitest `157/157`, all eight repository frontend contract scripts, MySQL 8.4 Testcontainers `80/80`, Spring Boot `509` with `0` failures and `0` errors (`1` explicitly opt-in real-provider smoke skip), executable JAR packaging, migration Python tests `17/17`, deployment hardening `103/103`, Python syntax compilation, and `git diff --check`.

## 2026-08-11 Release Gate Status

The release gate is green after the Assistant workspace and unpin corrections. Current evidence is frontend Vitest `32` files / `210` tests, all eight frontend scripts, Vite `1836` modules, Spring `549` with `0` failures/errors and `1` opt-in skip, MySQL 8.4 Testcontainers `81/81` with no owned-container leak, executable JAR packaging, Python `133` with seven opt-in skips, migration checks `17/17`, deployment hardening `103/103`, syntax, diff, high-confidence credential, artifact and ignore checks.

The production Agent terminal gate is intentionally narrow for this UI-focused delivery: it executes one real `source_verification` path and validates only current-Run source evidence. The three candidate scenarios still cover policy, case comparison and source verification. The successful production probe used three rounds, three tools, one source citation and `17,935` tokens, below the configured `28,000` ceiling.

Release `/opt/opc/releases/20260811-003256` is live. Backup `/opt/opc/backups/20260811-003256`, database dump `/opt/opc/backups/20260811-003256/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260811-003256`, and previous `/opt/opc/releases/20260810-183007` are retained. Independent postflight confirmed public, Assistant, administrator, health and static Assistant asset HTTP 200 responses, active services and a loopback-only backend listener.

The guarded deploy completed successfully. It ran isolated policy, case-comparison, and source-verification candidate probes before any production switch, then executed additive Phase Three migration precheck/migration/postcheck groups, created a timestamped backup, atomically switched production to `/opt/opc/releases/20260808-162621`, and retained the prior target `/opt/opc/releases/20260807-173031` plus `/opt/opc-backend.rollback.20260808-162621`. The database backup is `/opt/opc/backups/20260808-162621/opc_platform.sql.gz`.

Post-switch probes covered an authorized-citation research run, reports and export, cross-user ownership isolation, preferences, feedback CAS, Analytics snapshot ownership, and administrator quality authorization. No secret, raw prompt, raw provider body, or probe-user content is recorded here. The release is Phase Three user-facing v1 / partial release, not Phase Three product complete: formal technology and revenue statistics still lack the required real-data readiness, and manual browser/responsive/accessibility review remains required.

## Local Reliability Correction (2026-08-06)

The Assistant client now distinguishes a terminal server Run from successful session-detail synchronization. When polling receives a terminal status but the subsequent owned session read has a transient network failure, it preserves the server-returned status, reports that result synchronization is incomplete, and offers a bounded manual synchronization action. It does not manufacture `failed`, continue infinite polling, expose safe retry before the terminal details arrive, or allow a new message to be sent into a session whose final message has not yet synchronized. The action only re-reads the existing Run/session and is guarded against stale session and unmount responses.

Local evidence for this correction: frontend Vitest `148/148`; focused Agent tests `50/50`; all `*Agent*Test` `163` run with `0` failures/errors and one expected opt-in real-provider skip; Vite build, Spring Boot JAR package, Python migration `17/17`, Python deployment-hardening `94/94`, and syntax checks passed. MySQL 8.4 Testcontainers was invoked but is not accepted as passed: Docker Desktop Linux Engine was stopped, so `PhaseOneMySqlIntegrationTest` failed during container startup before any business assertion. The test remains enabled. No deployment, production probe, or real DeepSeek request occurred.

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

## Phase Three Contract Stabilization

The Phase Three specifications now close the implementation gaps found during final document review. Analytics authentication is a deliberate two-layer contract: Spring Security permits `/api/analytics/**` through to MVC, then the existing platform UserAuthInterceptor returns 401 for anonymous/expired users, 403 for disabled users and normal responses for active users. Administrator tokens cannot impersonate platform users.

Phase A gains an optional, backward-compatible `phase3-task-v1` taskContext on the first `/sessions/start` request. It carries explicit case selection, approved comparison dimensions and technology-assessment constraints, agrees with requestedIntent, and is frozen for the session. Selected case IDs become usable only after the backend revalidates their complete evidence chains, projects taskSelectedEvidence and creates current-Run authorizedEvidence. The existing profile, Provider, tool and citation boundaries remain unchanged.

Case geography now distinguishes primary/secondary operation regions, registration and legacy related region; policy geography means applicability. Technology endpoints have stable unavailable/409/empty behavior. Reports have active/trash/permanently_purged lifecycle, explicit restore/permanent deletion, independent behavior after session purge and Markdown/HTML/PDF exports. Revisioned user feedback and administrator-only aggregate quality APIs are fully specified without exposing conversation content or hidden reasoning.

Completeness uses separate component-macro caseScore and policyScore. Revenue uses `value_status=actual|estimated|unknown|withheld`, explicit interval bins, `spans_multiple_bins`, and Type 7 percentiles over actual point values only. Phase B may ship a partial Green/Yellow slice, but Phase Three cannot be declared product-complete until the technology and revenue data/visualization gates, analytics-to-Agent flow, reports, feedback, 40-question evaluation and performance gates all pass.

This stabilization is documentation-only. It does not implement Phase A/B/C, change Java or Vue, add migrations, change deployment scripts, access production, run the application test/build matrix or deploy.

## Phase A Contract Final Closure (2026-07-29)

Phase A now has one implementable request boundary. A normalized `phase3-task-v1` taskContext is atomically stored in nullable `ai_agent_sessions.task_context_version/task_context_json/task_context_hash`, never in profile or mutable clarification context. Start and owned session detail return it; history returns only task type/summary; Run detail returns task type/hash through the session authority. The idempotency identity includes profile, content, requestedIntent and canonical taskContext. Permanent session purge removes the context, and logs exclude its bounded free-text fields.

Source verification has two modes. A selected positive sourceId is reloaded and checked for published + verified eligibility, provenance, HTTP(S) URL and current revision before Run authorization. Without sourceId, non-empty content is only a claim to investigate through controlled search/get_source; text IDs and URLs do not confer authorization and arbitrary URL fetching remains prohibited.

`phase3-structured-result-v1` is frozen as a closed Draft 2020-12 discriminated schema for six tasks. ClaimItem.sourceIds is the sole conclusion-level evidence key; facts require a current-Run source. Every new Phase A result has a completion-time evidenceVersion derived from authorized IDs, evidence revisions/content hashes, eligibility and schema version. The existing enqueue-time `ai_analysis_runs.evidence_hash` is not that version. Ordinary Phase A returns dataVersion=null; Analytics dataVersion remains a Phase B/C snapshot concern.

Case geography uses one four-state interpretation: unready operation/registration is Red unavailable, explicit legacy geography is Yellow partial “相关地区分布”, ready-but-no-match is empty, and policy applicability remains formal without copying country policies to provinces. Green 0% region completeness is only a quality fact. Report trash expires into locked, idempotent automatic purge after 30 days. Feedback is eligible only for owned completed Runs, or evidence_insufficient Runs with a persisted user-visible Assistant result.

This closure changes specifications only. It does not implement Phase A, modify application code or schemas, access production, run the application build/test matrix or deploy. Phase A development may start against the frozen contracts; delivery still requires the migrations, DTOs, validators, services, UI and tests listed in the roadmap.

## Phase A v1 Structured Result Implementation Gate (2026-07-29)

The final specification gate contracts `phase3-structured-result-v1` to the existing stable runtime rather than raising runtime limits. `directAnswer` is capped at 600 characters; all top-level and nested ClaimItems share one six-item budget; citations are capped at six; synthesis remains capped at 3,200 output Tokens; and the persisted compatible Assistant rendering remains capped at 12,000 characters. Every string and array in the closed Draft 2020-12 schema has an explicit bound. Larger results require a new schema version and a separate runtime, persistence and rendering review.

Evidence selection now has two non-overlapping meanings. `taskSelectedEvidence` is the immutable projection of normalized `taskContext` (`caseIds` at most three, `sourceIds` at most one, and no user-selected policy IDs in v1). `authorizedEvidence` is generated only by the server from current-Run tool execution and eligibility checks. Its case, policy and source arrays are each capped at 120, with cases plus policies capped at 120, derived from the existing 12-tool-call ceiling and ten-result search ceiling. Claims, citations and task-specific case/policy references must be subsets of that authorization.

Case comparison requires one to three unique, allowlisted and explicitly submitted dimensions; other task types submit none. Report trash, restore and permanent-delete mutations all use a positive integer `expectedRevision` in a JSON body, return 400 for an invalid value, return 409 `REPORT_REVISION_CONFLICT` for a stale value, and do not treat an old-revision replay as success.

The Phase 36 completion claim is superseded: its six examples did not contain a self-contained Run evidence environment and therefore did not prove case-to-source links or independently recomputable evidenceVersion values. The Phase A v1 specification is not considered frozen again until the explicit-selection and runEvidenceFixture gate in the current documentation round passes. Phase A remains unimplemented and undeployed.

## Phase A v1 Explicit Evidence Closure (2026-07-29)

Phase A now has one explicit-selection boundary. A selected case or source is validated before any research persistence or Token reservation. An ineligible case returns HTTP 400 `PHASE3_CASE_NOT_ELIGIBLE`; an ineligible selected source returns HTTP 400 `PHASE3_SOURCE_NOT_ELIGIBLE`. Neither path creates a session, user message, Run, taskSelectedEvidence or authorizedEvidence. `evidence_insufficient` is reserved for a request that was valid and atomically accepted but later lacks enough controlled evidence, loses evidence eligibility/revision during execution or can support only part of the requested conclusion.

Each of the six contract examples now carries a test-only `phase3-run-evidence-fixture-v1` beside taskContext and structuredResult. The fixture records bounded case, policy and source revisions/content hashes/eligibility plus explicit case-source and policy-source links. It is not a production DTO, API field or persisted user result. The examples prove selected-to-authorized set equality, source-chain provenance, citation metadata equality and authorization-subset rules.

`evidenceVersion` is independently reproducible from fixed-order canonical evidence input: schemaVersion, authorized cases, policies, sources, caseSourceLinks and policySourceLinks. Entity inputs contain only ID, non-negative evidenceRevision, `sha256:` contentHash and closed eligibility; arrays and links are stably sorted. Implementations hash compact UTF-8 JSON with fixed object-field order, no BOM, indentation, trailing whitespace or final newline, using SHA-256. Empty evidence has a real non-zero digest rather than a placeholder.

The replacement document gate passed Draft 2020-12 meta-validation, structuredResult Schema `6/6`, complete service semantics `6/6`, independent evidenceVersion recomputation `6/6`, positive contract assertions `28/28`, original negatives `19/19` and new reason-specific negatives `20/20`. The two previously reproduced P1s are closed, so Phase A v1 specifications are formally frozen with no remaining P0/P1. This is specification readiness only: Phase A runtime code, migrations, UI, application tests and deployment have not started.

## Phase A v1 Final Targeted Contract Patch (2026-07-29)

The start boundary now resolves a locked `userId + idempotencyKey` record inside the database transaction before authoritative evidence qualification. An exact successful replay returns its original `202` receipt without revalidation or new side effects; an identity mismatch returns `409 PHASE3_IDEMPOTENCY_CONFLICT`; only a miss locks and revalidates selected evidence and required relations before atomically creating the session, first user message, Run, taskContext/evidence projections, Token reservation and success receipt. Revocation before the evidence lock causes a fully rolled-back `400`; revocation while start holds the lock waits and is handled by execution-time revalidation after a legal commit.

The contract-test `policy_lookup` example now provides a non-empty `policy 2001 -> source 9004 -> fact -> citation` provenance chain with independently reproducible evidenceVersion `sha256:8491a7a0ad58ec5c91ef9a7d90553817d7d0049ae40f3f0e99a91f96bd4317aa`. Fixture entity IDs are unique within each entity type and link pairs are unique within each link type; duplicates fail before sorting and hashing, while legal many-to-many relationships remain supported.

The final targeted gate passed Draft 2020-12 meta-validation, structuredResult Schema `6/6`, complete fixture semantics `6/6`, evidenceVersion recomputation `6/6`, transaction/idempotency `8/8`, policy-source `10/10`, uniqueness `9/9`, original negatives `19/19`, existing reason-specific negatives `20/20` and new targeted negatives `9/9`. Phase A v1 is formally frozen with no remaining P0/P1.

This patch changes specifications only. It does not claim that Phase A runtime code, database changes, UI or deployment have been implemented.

## Phase Three Local Implementation Snapshot (2026-08-01)

Phase Three has now moved beyond the specification-only state in the local repository. The current worktree includes executable taskContext persistence, selected-evidence qualification, structured-result rendering, branch-material reuse, explicit preferences, report lifecycle, run feedback, administrator quality aggregation, Analytics snapshots, the protected `/analytics` route, and Analytics-to-Assistant handoff that remains user-send gated.

The current readiness boundary is still narrower than “Phase Three product complete”. The implemented Analytics slice is overview plus `industry.case_count`; technology remains unavailable by design, revenue normalization is still absent, and `industry.case_count` remains a `Yellow` metric because canonical business-case de-duplication is not yet available and low-sample buckets cannot be promoted to strong chart conclusions.

Local verification is complete for the present worktree: Spring Boot `444` tests with `0` failures/`0` errors and `1` skipped opt-in real DeepSeek smoke, MySQL 8.4 `76/76`, frontend Vitest `119/119`, all existing frontend npm scripts, the Vite production build, and Spring Boot executable JAR packaging. This section does not claim Python deployment/migration verification, remote preflight, rollout, rollback, or production probes, because those command results were not captured in this round.

Therefore the engineering readiness state is: local implementation ready for deployment preparation, but not yet production-ready by evidence standard. The last production baseline remains the deployed Phase Two runtime until a credentialed Phase Three deployment, migration, and postdeploy probe round is actually executed and recorded.

## Source Verification Insufficient-State Closure (2026-08-09)

The runtime now treats a server-derived `source_verification` verdict of `insufficient` as an authoritative content boundary, regardless of provider action. When verification claims are absent or unresolved-only, the persisted and rendered result contains no factual answer, key findings, recommendations, risks, assumptions, uncertainties, next questions or citations. Only source-free methodology invalidity reasons may explain unresolved claims. Evidence coverage is internally consistent (`insufficient`, zero fact counts, null ratio) and publisher assessment remains unknown.

This is implemented without a schema or migration change. Focused and full local verification passed, including MySQL 8.4 Testcontainers `80/80`; production readiness still requires the guarded deployment and postflight evidence described by the deployment runbook.

## Source Verification Production Release (2026-08-09)

The guarded candidate and production probes passed after the insufficient-state closure. Release `/opt/opc/releases/20260809-150138` is current, with backup `/opt/opc/backups/20260809-150138`, database dump `/opt/opc/backups/20260809-150138/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260809-150138`, and previous release `/opt/opc/releases/20260809-133127`. No database migration was added in this round.

Independent postflight confirmed public and admin routes, Assistant, health, anonymous `code=401` envelopes, local/deployed frontend hash parity, and zero candidate resources. The deployed source-verification probe completed with authorized evidence and zero unknown citations. Manual browser inspection remains user-owned; this round did not use Playwright.

## Assistant Research Workspace Phase Four (2026-08-10)

The Assistant user surface is now a research workbench rather than a persistent status dashboard. It preserves the existing runtime readiness boundary while moving low-frequency profile, citation, process, report, model, and Token detail behind one on-demand Inspector. History, draft isolation, first-send creation, current-Run authorization, controlled terminal states, and all API response shapes remain unchanged.

The visual delivery passed Spring `547` (0 failures/errors, 1 opt-in skip), isolated MySQL 8.4 `80/80` with no owned-container leak, explicit migration tests `7/7`, Vitest `191/191`, eight frontend contracts, both production artifacts, Python `133/133` with seven opt-in skips, and repository gates. One formal deployment switched `/opt/opc/current` to `/opt/opc/releases/20260810-112153`; backup `/opt/opc/backups/20260810-112153`, rollback `/opt/opc-backend.rollback.20260810-112153`, previous `/opt/opc/releases/20260809-193452`. Public/admin/Assistant/health/static/auth postflight and candidate cleanup passed. This does not claim that manual visual acceptance was automated; Playwright remains intentionally unused.

## Assistant Motion Refinement (2026-08-10, Local Only)

The Assistant retains its deployed Phase Four runtime/data boundary while gaining a restrained motion pass across menus, off-canvas history/Inspector surfaces, sidebar folding, close actions, toasts, run-stage replacement, and existing buttons. The refinement is CSS/Vue interaction behavior only; it changes no provider, tool, API contract, research result, authorization, citation, report, draft, or quota behavior.

The visual review corrected the sidebar fold's retained edge so the left 64px command rail stays visible all the way through the transition. The initial pointer refinement used existing 120-240ms tokens; the later desktop history/mobile drawer continuity transition uses the requested 500ms. Keyboard open/close behavior stays immediate; fine-pointer hover is gated; and reduced motion disables spatial transitions. Focus management and existing a11y semantics remain intact.

Focused history/workspace Vitest passed `15/15`; the completed motion suite passed `109/109`, complete frontend Vitest passed `198/198`, all eight frontend scripts and Vite production build passed. No deployment was attempted for this local refinement: production continues to serve `/opt/opc/releases/20260810-112153` with its existing backup and rollback artifacts.

## Assistant Motion Refinement Production Release (2026-08-10)

After explicit authorization, one guarded deployment passed the isolated candidate/Agent gate, additive/resumable migration checks, timestamped backup, atomic current-link switch, and cleanup. Production now serves `/opt/opc/releases/20260810-130518`; backup `/opt/opc/backups/20260810-130518`, database dump `/opt/opc/backups/20260810-130518/opc_platform.sql.gz`, rollback `/opt/opc-backend.rollback.20260810-130518`, and previous `/opt/opc/releases/20260810-112153` are retained.

Independent postflight confirmed HTTP 200 responses for the public, Assistant, admin, health, and deployed static asset endpoints. The remote current link, three active services, loopback-only 8082 listener, `opc` backend user, and zero candidate unit/environment/database residues were verified. Manual responsive visual acceptance remains the only user-owned acceptance activity; Playwright was not used.

## Assistant 500ms Motion Continuity Release (2026-08-10)

The workbench runtime/data boundary remains unchanged. The only additional frontend behavior is a staged 500ms pointer transition for the desktop history rail and mobile history drawer: the layout state switches once, then the cover and research desk coordinate with `transform` and `opacity`. This removes layout-thrashing from the reading column while retaining the established 64px collapsed command rail.

The fold path marks extended history content `inert` and `aria-hidden` until it settles, and pointer selection of a mobile history item now follows the same exit transition as the drawer controls. Keyboard invocation remains immediate, reduced motion disables spatial movement, and no Provider/tool/API/research/citation/report/draft/quota contract changes exist.

Targeted history/workspace Vitest passed `20/20`; full frontend Vitest passed `32/32` files and `203/203` tests; the eight package scripts, Vite (`1836` modules), Spring Boot `547` tests (`0` failures, `0` errors, `1` skip) and package, MySQL 8.4 Testcontainers, Python (`126` passed, `7` skipped), syntax, diff, credential, artifact, and ignore gates passed before the one guarded rollout. Production now serves `/opt/opc/releases/20260810-155624`; backup `/opt/opc/backups/20260810-155624`, database dump `/opt/opc/backups/20260810-155624/opc_platform.sql.gz`, rollback `/opt/opc-backend.rollback.20260810-155624`, and previous `/opt/opc/releases/20260810-130518` remain available. Postflight confirmed public/admin/Assistant/health/static HTTP 200, active services, loopback-only 8082, and zero candidate residues.

## Assistant Inspector And Motion Finish (2026-08-10)

The inline authorized-evidence source hash is now a controlled route into the current Run's real citation Inspector. It intercepts only evidence authorized for that Run; ordinary external links retain normal navigation. The report Inspector's top spacing matches the shared surface, and the desktop rail's 500ms transition uses only a pseudo-element boundary while its static border is transparent, preventing double-border tearing. The implementation was developed through public-behavior RED/GREEN coverage and does not alter Provider, tool, API, research, citation authorization, report data, draft, quota, SQL, or JAR behavior.

Frontend Vitest passed `32` files / `206` tests, seven npm package scripts, and Vite (`1836` modules). Spring passed `547` tests with `0` failures/errors and `1` skipped, including MySQL 8.4 Testcontainers `80/80`; JAR packaging, Python `133` with `7` skips, explicit MySQL `7/7`, syntax, diff, secret, artifact, and `.codegraph/` checks passed. One frontend-only atomic release switched production to `/opt/opc/releases/20260810-183007` with frontend hash `4ba885...c54b`; `/opt/opc/releases/20260810-155624` remains the atomic rollback release. No migration or new database backup was required. Public, `/assistant`, administrator login/settings, `/api/health`, and new JavaScript/CSS assets returned HTTP 200; services are active, Nginx is valid, and 8082 remains loopback-only.

## Assistant Sidebar Motion Handoff Release (2026-08-11)

The current visual-only release does not change the AI runtime, Provider settings, prompt/tool protocol, authorized evidence handling, citations, reports, quotas, drafts, APIs, or database schema. It fixes a client-side desktop rail sequence shown in a 59fps user recording: a fluid-width command had remained visible while its parent rail narrowed, creating apparent stretch and a terminal snap.

The pointer path now completes a 120ms handoff to a fixed 44px command before `history-collapsed` begins the 500ms rail transition. Expansion keeps the same compact command until 320ms, at which point the wide command, history content, and their semantic access return together through interruptible 160ms transitions. Keyboard and reduced-motion paths stay immediate; the existing 375px/768px drawers, 1024px desktop Inspector, focus management, and 44px touch targets remain covered.

The release gate passed frontend Vitest `32/218`, eight frontend scripts, Vite (`1836` modules), Spring `549` with `0` failures/errors and `1` explicit skip, MySQL 8.4 runner `81/81` with no current-run container, executable JAR packaging, Python `126` passed plus `7` opt-in skips and explicit migration `7/7`, syntax, diff, high-confidence credential, build-artifact, motion, and `.codegraph/` ignore checks. One frontend-only atomic release switched production to `/opt/opc/releases/20260811-032203`; frontend hash `cd42620b363ac4ec8e8e3d08e0be4522e6ac212caaecb11478a7670c9e1692a1`; retained rollback release `/opt/opc/releases/20260811-014621`. No migration, Provider probe, or database backup was required. Public, Assistant, administrator login/settings, health, and the deployed Assistant JavaScript asset returned HTTP 200; preflight still reports active services, valid Nginx, one loopback-only backend listener, and the `opc` service user.

## Assistant Rail Rendering Closure (2026-08-11)

This follow-up changes no AI readiness boundary: it does not alter the Provider, prompts, tools, run state machine, evidence authorization, citations, reports, quotas, drafts, APIs, schema, or backend package. It replaces only the desktop history rail rendering choreography so the one real New Research command and the native grid rail stay in one continuous coordinate system.

Focused Assistant tests passed `106/106`, complete frontend Vitest passed `32/223`, and the production build plus existing Spring/MySQL/JAR/Python/repository release gates passed. One frontend-only atomic switch moved `/opt/opc/current` to `/opt/opc/releases/20260811-035739` (frontend hash `2fe7f6b790a2d42900496f7b86a1ce41a841ec7bf4880981ffea0d4d9467980b`); `/opt/opc/releases/20260811-032203` remains the rollback release. No migration, AI probe, or database backup was applicable. Public, Assistant, administration, health, and deployed static-asset postflight probes returned HTTP 200.
