# Assistant Workspace Stabilization Delivery

Date: 2026-07-26  
Previous production release: `20260725-215634`  
Delivery state: deployed and production-probed in release `20260726-015858`

## Scope

This pass stabilizes the existing Vue 3 and Spring Boot Assistant research workspace. It does not add Agent tools, change providers, move model calls into the browser, or replace the current MySQL-backed Agent Runtime.

The completed slices cover the dedicated Assistant route shell, responsive research profile, atomic first submission, request-bound idempotency, stable history pagination, stale-response rejection, independent active/latest run restoration, canonical industry matching, one-time automatic titles, purge barriers, purge audits, migration recovery, quota presentation, drafts, scrolling, dialogs, accessibility, and the unified industry combobox.

## Defect Roots And Resolutions

| Area | Root cause | Resolution |
| --- | --- | --- |
| Route height and scrolling | The standard public-page title and fixed viewport subtraction both consumed workspace height. | `/assistant` now uses a dedicated full-height shell with flex/grid remaining-space ownership, `min-height: 0`, safe viewport fallbacks, and one primary conversation scroll owner. |
| Research profile | Five columns followed viewport width instead of the component's real container. | Container queries cap the regular desktop form at three columns, use two columns at medium widths and one on phones; goal/resources share the second desktop row. |
| First submission | The browser created a session and then sent a separate message, so response loss could leave an orphan. | `POST /api/ai/research/sessions/start` creates the session, first message, automatic title, run and reservation in one rollback-capable database boundary. Durable `received` remains the worker source of truth. |
| Idempotency | Retry keys were not bound to the complete request and the browser could replace a pending key. | Keys are bound to user, operation kind, session where applicable, normalized content hash, canonical profile hash and session content generation. Replays return the same IDs; mismatches return controlled `409`. |
| History pagination | Ordering used mutable `last_message_at`. | The first page captures a database timestamp watermark. Every page uses message activity frozen at that watermark and a signed cursor bound to user, scope and normalized query. |
| Request races | Late A-session, readiness and industry responses could update the currently selected B session. | Abort controllers and monotonically increasing request generations gate detail, pagination, search, readiness, industry and usage writes. |
| Run restoration | `activeRun` also stood in for the most recent terminal result, and retry depended on in-memory text. | Active and terminal runs use separate queries. Retry text is loaded only from the owned run-linked user message and is omitted after content purge. |
| Industry matching | The new workspace did not consistently consume canonical and AI-resolved tags. | Exact/alias/fuzzy matching remains first. AI suggestions are latest-request gated and require explicit user confirmation before a canonical tag ID is submitted. |
| Automatic titles | Later user messages could regenerate an automatic title. | Only the first valid user message can set an auto title. Manual titles and an existing auto title are never overwritten. |
| Permanent deletion | Cancelled work could still write late tool/provider content after scrubbing. | `content_generation`, run generation snapshots, lease ownership, terminal state and `purged_at` form a purge barrier on every content-bearing write path. |
| Migration recovery | A partially applied workspace migration could have the column but miss the historic-title backfill or retain same-name wrong-order indexes. | The forward stabilization migration independently runs the bounded backfill and repairs exact index signatures on every rerun. |
| Usage display | The UI exposed settled usage without the active reservation component used by submission checks. | `/usage` now returns settled `usedTokens`, active `reservedTokens`, submission-rule `remainingTokens`, `dailyLimit` and `resetAt` from the shared ledger. |
| Industry selector | The searchable combobox used a text triangle and visual metrics unrelated to native selectors. | Native selectors and the combobox share field tokens. `ChevronDown`, keyboard navigation, listbox semantics, explicit AI suggestion states, touch sizing and reduced-motion behavior preserve all search/free-input features. |

## API Contract

`POST /api/ai/research/sessions/start`

Request:

```json
{
  "profile": {},
  "content": "Research question",
  "idempotencyKey": "stable-client-key"
}
```

Response data contains `session`, `messageId`, `runId`, and `status`. A replay with the same normalized identity returns the original tuple. A reused key with different content, profile, session, user or operation kind is rejected.

Existing endpoints remain compatible. The old session `DELETE` keeps archive semantics; explicit archive/unarchive/trash/restore/permanent endpoints remain available. Session detail returns both `activeRun` and `latestRun`. Run status may return owned `retryContent` only for retryable terminal states while the original message content still exists.

History cursors are opaque HMAC-signed values. Production must provide `OPC_ASSISTANT_CURSOR_HMAC_SECRET` with at least 32 characters. Cursor payloads bind version, user, scope, query hash, snapshot watermark, pin rank, snapshot activity and session ID.

## Data And Migration

Forward migration: `deploy/sql/20260725_assistant_workspace_stabilization.sql`

Added or normalized data:

- `ai_agent_sessions.content_generation BIGINT NOT NULL DEFAULT 0`
- `ai_analysis_runs.submission_kind VARCHAR(20) NOT NULL DEFAULT 'message'`
- `ai_analysis_runs.request_content_hash CHAR(64) NULL`
- `ai_analysis_runs.start_profile_hash CHAR(64) NULL`
- `ai_analysis_runs.session_content_generation BIGINT NOT NULL DEFAULT 0`
- `ai_agent_content_purge_audits` with operation, non-content identifiers, actor type/ID, result, diagnostic code and timestamp

Exact indexes repaired or created:

- `idx_agent_sessions_history_active (user_id,deleted_at,pinned_at,last_message_at,id)`
- `idx_agent_sessions_history_archived (user_id,archived_at,last_message_at,id)`
- `idx_agent_sessions_purge_due (purge_after,purged_at,id)`
- `idx_agent_messages_session_created (session_id,created_at,id)`
- `uk_agent_message_sequence UNIQUE (session_id,sequence_no)`
- `idx_agent_purge_audits_session_created (session_id,created_at)`
- `idx_agent_purge_audits_user_created (user_id,created_at)`

The rollout timestamp is persisted once in `app_settings`. Historic sessions before that boundary are repaired to manual-title ownership without changing post-rollout automatic titles. Precheck and postcheck validate field counts, types, nullability/defaults, bounded backfill completion, exact index order/uniqueness and the intentionally independent purge-audit table.

## Purge Semantics

Manual and scheduled permanent deletion scrub session title/profile/research context, message bodies/citations, run result and request fingerprints, tool arguments/results/evidence hashes, and provider content-bearing fields. Minimal run ID, terminal status, provider/model, token, latency and diagnostic metadata remains for billing and security audit.

Every late message, tool, evidence, citation or provider finalization write must still own the live run lease, match the session content generation, target an unpurged session and remain in a write-permitted run state. Purge increments the session generation. Stale writes are rejected and only a content-free audit diagnostic is recorded.

## Frontend Layout And Accessibility

The workspace keeps public navigation but removes the ordinary page title band for `/assistant`. Desktop uses the history rail plus one constrained conversation reading column; references and process details open on demand. Tablet and phone use focus-managed drawers. The composer stays in the available flex area and respects dynamic viewport and safe-area insets.

Research fields respond to the profile container: three columns on regular desktop, two at medium width and one on phones. On mobile the profile starts collapsed. The industry combobox matches standard select geometry while retaining free input, local suggestions and confirm-before-apply AI resolution.

History, citation and process dialogs trap focus, close on Escape, restore the trigger, inert the background and expose dialog labels. Current and pressed states use ARIA. Controls meet the 44px mobile target. IME composition does not submit, Shift+Enter inserts a newline, drafts are namespaced by user/session, old-message loading preserves the scroll anchor, and reduced motion removes nonessential transitions.

## Verification

- Spring Boot: `299` tests executed, `0` failures/errors, `1` opt-in real-provider test skipped.
- MySQL 8.4 Testcontainers inside Spring: `58` tests passed.
- Deterministic Agent fixtures: `20`, contract pass rate `1.0`, accepted unknown citations `0`.
- Frontend Vitest: `57` tests across `12` files passed.
- Frontend package scripts: all `8` passed.
- Python migration/deployment suite: `63` tests executed in the default run, with `57` passed and `6` opt-in container cases skipped; the explicit MySQL 8.4 migration run passed `6/6`.
- Spring Boot executable JAR: built successfully.
- Vite production build: `1,802` modules transformed successfully; the existing `621.33 kB` chunk warning remains non-blocking.
- `npm audit --omit=dev --audit-level=high`: `0` production vulnerabilities.
- Python syntax, `git diff --check`, high-confidence production secret scan, tracked-artifact scan and `.codegraph/` ignore check passed.

## Deployment State

Release `/opt/opc/releases/20260726-015858` is live through `/opt/opc/current`. The timestamped configuration/database backup is `/opt/opc/backups/20260726-015858`, the backend rollback artifact is `/opt/opc-backend.rollback.20260726-015858`, the database dump is `/opt/opc/backups/20260726-015858/opc_platform.sql.gz`, and `/opt/opc/releases/20260725-215634` remains the previous application release.

Migration precheck, forward migrations and postcheck passed before the atomic switch. The post-deployment preflight confirmed one `opc` backend process, loopback-only `127.0.0.1:8082`, active Nginx/MySQL/backend services, valid Nginx configuration, and matching deployed frontend/backend hashes. Both production domains and the authenticated/anonymous permission probes passed inside the deployment workflow.

The real Agent probe asked `检索湖北省已核验的人工智能相关政策，并引用证据概括一项可用支持。` and completed on `deepseek` / `deepseek-v4-flash` with finish reason `stop`, 2 model rounds, 2 provider calls, 1 completed tool call, 1 legal citation, 0 unknown citations, 3,722 prompt tokens, 699 completion tokens, 4,421 total tokens and 7,842 ms latency. Internal request ID `d1028f0c-600a-40be-b80b-469e5238e9a1` and provider request ID `0efbf351-e03f-4fb3-8a7a-26c4ccf8dc6a` were independently recorded. The legacy Assistant advice probe also completed with finish reason `stop` and 3,276 total tokens.

The expanded temporary-data probe passed atomic start replay, automatic title, history first/second page traversal over more than 50 records, search, rename/pin, archive/restore, trash, active/latest run, usage metadata, purge barrier and final cleanup. Temporary accounts and research data were removed in `finally`.

Two failed release attempts were safely rejected before this successful deployment. The first exposed an SSH command containing Python-interpreted NUL/newline control characters; the second exposed that the production external YAML replaced the JAR configuration and did not map the cursor secret. The deployment script now reconnects before rollback, sends literal `tr` escapes, and the history service directly accepts the required environment-variable fallback. Both attempts restored `/opt/opc/releases/20260725-215634` before the final deployment.

## Changed File Responsibilities

- Backend controllers/DTOs/services: atomic start, request-bound replay, title policy, usage ledger, snapshot cursor, run restoration, retry content and purge orchestration.
- Backend entities/mappers/schema: start fingerprints, content generations, guarded writes, exact history queries, purge audit and canonical schema.
- Backend tests: REST authorization, idempotency, title ownership, stable MySQL pagination, active/latest runs, quota, purge barriers and guarded tool writes.
- Vue API/view/composables/components: start API, persistent pending keys, latest-request gates, dedicated route shell, responsive profile, unified industry combobox, scroll/draft/IME behavior and accessible drawers.
- Vue tests/scripts: public behavior, keyboard/ARIA, responsive contracts, stale-response rejection, retry restoration, usage and industry confirmation.
- SQL/deployment/Python: forward migration, exact postcheck, artifact upload/order, atomic replay/pagination/purge probes and MySQL 8.4 recovery tests.

## Exact File Inventory

- `.codex_deploy_opc.py`: uploads and applies the stabilization SQL, injects the cursor secret without command interpolation, reconnects safely after restart/for rollback, and probes atomic replay, second-page pagination and purge output.
- `AI_READINESS.md`: records the deployed stabilization state and real production evidence.
- `deploy/sql/20260725_assistant_workspace_precheck.sql`: reports existing stabilization fields and purge-audit structure before mutation.
- `deploy/sql/20260725_assistant_workspace_stabilization.sql`: forward, repeatable fields, title repair, purge audit and exact index repair.
- `deploy/sql/20260725_assistant_workspace_postcheck.sql`: validates exact types/defaults/backfill/index signatures and purge-audit isolation.
- `docs/agent-runtime-phase-two.md`: documents the stronger submission and content-write boundaries.
- `docs/assistant-research-workspace.md`: adds the workspace stabilization relationship and deployment distinction.
- `docs/assistant-workspace-stabilization.md`: records this delivery, contracts, tests and deployment gate.
- `findings.md`: records defect roots and final local evidence.
- `progress.md`: records completed slices and current production boundary.
- `task_plan.md`: adds Phase 23 and its deployment-pending status.
- `opc-backend/src/main/java/com/opc/platform/ai/controller/AgentResearchController.java`: exposes authenticated atomic session start while preserving old routes.
- `opc-backend/src/main/java/com/opc/platform/ai/dto/AgentSessionStartDTO.java`: validates the first profile/content/idempotency request.
- `opc-backend/src/main/java/com/opc/platform/ai/entity/AiAgentContentPurgeAudit.java`: maps minimal, content-free purge audit fields.
- `opc-backend/src/main/java/com/opc/platform/ai/entity/AiAgentSession.java`: adds content generation and stable history activity projection.
- `opc-backend/src/main/java/com/opc/platform/ai/entity/AiAnalysisRun.java`: adds submission identity hashes and session generation snapshot.
- `opc-backend/src/main/java/com/opc/platform/ai/mapper/AgentUsageLedgerRow.java`: projects settled and reserved ledger totals.
- `opc-backend/src/main/java/com/opc/platform/ai/mapper/AiAgentContentPurgeAuditMapper.java`: persists purge audit rows.
- `opc-backend/src/main/java/com/opc/platform/ai/mapper/AiAgentSessionMapper.java`: implements snapshot history order, atomic title update, locking and purge claims.
- `opc-backend/src/main/java/com/opc/platform/ai/mapper/AiAgentToolCallMapper.java`: guards tool insert/update by run state, lease, purge state and generation.
- `opc-backend/src/main/java/com/opc/platform/ai/mapper/AiAnalysisRunMapper.java`: persists start identity, separates active/latest queries, projects usage and scrubs fingerprints.
- `opc-backend/src/main/java/com/opc/platform/ai/service/AgentContentPurgeAuditService.java`: writes success/rejected/failed content-free purge audits.
- `opc-backend/src/main/java/com/opc/platform/ai/service/AgentOrchestrator.java`: carries lease/generation-aware tool execution through model rounds.
- `opc-backend/src/main/java/com/opc/platform/ai/service/AgentOrchestratorInput.java`: adds the live lease and session generation execution context.
- `opc-backend/src/main/java/com/opc/platform/ai/service/AgentProfilePolicy.java`: canonicalizes and fingerprints supported research profiles.
- `opc-backend/src/main/java/com/opc/platform/ai/service/AgentResearchQueryService.java`: returns independent active/latest runs and safe persisted retry content.
- `opc-backend/src/main/java/com/opc/platform/ai/service/AgentResearchService.java`: owns atomic start, request-bound replay and rollback of concurrent orphan sessions.
- `opc-backend/src/main/java/com/opc/platform/ai/service/AgentResearchStartReceipt.java`: returns the stable session/message/run/status tuple.
- `opc-backend/src/main/java/com/opc/platform/ai/service/AgentResearchWorker.java`: passes the claimed lease and generation into orchestration.
- `opc-backend/src/main/java/com/opc/platform/ai/service/AgentRunFinalizer.java`: rejects stale or purged provider completion writes.
- `opc-backend/src/main/java/com/opc/platform/ai/service/AgentRunLifecycleService.java`: reserves quota with request identity and generation metadata.
- `opc-backend/src/main/java/com/opc/platform/ai/service/AgentSessionHistoryService.java`: signs snapshot cursors, accepts the external-YAML-safe cursor environment fallback, reports ledger usage and coordinates audited purge.
- `opc-backend/src/main/java/com/opc/platform/ai/service/AgentSessionService.java`: locks owned sessions and limits automatic title generation to the first valid question.
- `opc-backend/src/main/java/com/opc/platform/ai/service/AgentSubmissionIdentity.java`: models operation/content/profile/generation replay identity.
- `opc-backend/src/main/java/com/opc/platform/ai/tool/AgentToolContext.java`: carries lease and generation authorization to tools.
- `opc-backend/src/main/java/com/opc/platform/ai/tool/AgentToolRegistry.java`: uses guarded tool audit writes and rejects stale content restoration.
- `opc-backend/src/main/java/com/opc/platform/ai/vo/AgentRunStatusVO.java`: exposes retry content only in the safe run projection.
- `opc-backend/src/main/java/com/opc/platform/ai/vo/AgentUsageVO.java`: adds active reserved tokens to the public quota contract.
- `opc-backend/src/main/resources/application.yaml`: binds the required Assistant cursor HMAC secret.
- `opc-backend/src/main/resources/db/schema.sql`: mirrors stabilization fields, audit table and exact indexes for fresh installs.
- `opc-backend/src/test/java/com/opc/platform/ai/controller/AgentResearchControllerTest.java`: covers start authorization, validation and response contract.
- `opc-backend/src/test/java/com/opc/platform/ai/service/AgentGoldenEvaluationTest.java`: updates deterministic tool audits to use guarded writes.
- `opc-backend/src/test/java/com/opc/platform/ai/service/AgentOrchestratorTest.java`: verifies guarded orchestration success and limits.
- `opc-backend/src/test/java/com/opc/platform/ai/service/AgentProfilePolicyTest.java`: covers canonical profile ordering and fingerprints.
- `opc-backend/src/test/java/com/opc/platform/ai/service/AgentResearchServiceTest.java`: covers start replay/mismatch and normal message idempotency.
- `opc-backend/src/test/java/com/opc/platform/ai/service/AgentSessionHistoryServiceTest.java`: covers signed cursor binding, quota projection and purge transitions.
- `opc-backend/src/test/java/com/opc/platform/ai/tool/AgentToolRegistryTest.java`: covers guarded insert/update rejection and normal tool audits.
- `opc-backend/src/test/java/com/opc/platform/integration/PhaseOneMySqlIntegrationTest.java`: covers real-MySQL start races, snapshot pagination, titles, active/latest, quota and purge barriers.
- `opc-frontend/scripts/test-assistant-workflow.mjs`: checks canonical industry confirmation and readiness consumption.
- `opc-frontend/src/api/ai.js`: adds atomic start and the stabilized history/run contracts.
- `opc-frontend/src/components/assistant/AssistantCitationDrawer.vue`: adds focus-managed, labelled citation dialog behavior.
- `opc-frontend/src/components/assistant/AssistantComposer.vue`: adds IME-safe send, cancellation and accessible controls.
- `opc-frontend/src/components/assistant/AssistantConversation.vue`: preserves scroll ownership, old-message anchors and return-to-bottom behavior.
- `opc-frontend/src/components/assistant/AssistantHistorySidebar.vue`: adds accessible current/pinned/menu states and mobile drawer behavior.
- `opc-frontend/src/components/assistant/AssistantResearchProfile.vue`: implements container layout and the unified searchable industry combobox.
- `opc-frontend/src/components/assistant/AssistantRunProgress.vue`: distinguishes user-facing terminal and recovery states.
- `opc-frontend/src/components/assistant/__tests__/AssistantCitationDrawer.spec.js`: verifies citation focus, Escape and trigger restoration.
- `opc-frontend/src/components/assistant/__tests__/AssistantComposer.spec.js`: verifies Enter, Shift+Enter, IME and send/cancel states.
- `opc-frontend/src/components/assistant/__tests__/AssistantConversation.spec.js`: verifies scroll following and anchor preservation.
- `opc-frontend/src/components/assistant/__tests__/AssistantHistorySidebar.spec.js`: verifies current/pinned states and history interactions.
- `opc-frontend/src/components/assistant/__tests__/AssistantResearchProfile.spec.js`: verifies three/two/one-column contracts, Chevron/listbox semantics and AI confirmation.
- `opc-frontend/src/components/assistant/__tests__/AssistantRunProgress.spec.js`: verifies distinct clarification/quota/provider/error states.
- `opc-frontend/src/composables/__tests__/useAssistantDrafts.spec.js`: verifies per-user/per-session draft and pending-key isolation.
- `opc-frontend/src/composables/useAssistantDrafts.js`: namespaces drafts, profile and pending start keys by authenticated user.
- `opc-frontend/src/layouts/MainLayout.vue`: removes the ordinary page title band and outer scroll chain for `/assistant`.
- `opc-frontend/src/layouts/__tests__/MainLayout.spec.js`: verifies the Assistant-specific route shell contract.
- `opc-frontend/src/utils/assistantWorkflow.js`: validates canonical industry resolution and explicit suggestion confirmation.
- `opc-frontend/src/utils/focusTrap.js`: centralizes dialog focus containment, Escape and trigger restoration.
- `opc-frontend/src/views/AssistantView.vue`: coordinates atomic start, request generations, polling, drafts, drawers, scrolling and responsive workspace state.
- `opc-frontend/src/views/__tests__/AssistantView.spec.js`: covers atomic retry keys, A-to-B races, run restoration, usage, drafts, scrolling and responsive/accessibility contracts.
- `scripts/deployment_hardening.py`: validates atomic replay, disjoint cursor pages, scrubbed purge-barrier records and cursor-secret strength/safety.
- `scripts/test_ai_stabilization_migration.py`: verifies independent guards, title repair and exact schema/index postchecks.
- `scripts/test_assistant_workspace_mysql.py`: runs first/repeat/interrupted/wrong-index recovery against MySQL 8.4.
- `scripts/test_deployment_hardening.py`: verifies deployment ordering, secret-safe cursor installation and expanded probe failure gates.

## Readiness, settlement, and interaction closure (release 20260726-092000)

This focused pass did not add schema fields or a migration. It changed only the remaining stability boundaries:

- `AssistantView.vue` persists the full local draft but schedules industry/readiness work only from region and canonical-industry dependencies.
- `AssistantComposer.vue` distinguishes an unpersisted first question from a persisted-session continuation without changing the start/message API split.
- Assistant component CSS adds scoped coarse-pointer tablet targets; industry listbox Arrow navigation uses nearest-option scrolling without moving the page.
- cancelled Provider work may reconcile actual usage once, and permanent purge waits while Provider settlement remains pending.
- automatic title creation increments history revision once; atomic start locks that revision before child creation so concurrent replay cannot deadlock.
- deployment rollback failure attaches a fixed sanitized note to the original exception instead of replacing or silently discarding it.

Verification: Spring `307` with `1` opt-in smoke skipped, MySQL 8.4 `63/63`, Vitest `65/65`, frontend package scripts `8/8`, deployment Python `53/53`, migration Python `14/14`, explicit MySQL migration `7/7`, Vite and JAR builds, Python syntax, diff, ignore, artifact, dependency and scoped secret checks. The Vite main chunk is `622.27 kB`; its existing size warning remains outside this stabilization scope.

Deployment: release `/opt/opc/releases/20260726-092000`, backup `/opt/opc/backups/20260726-092000`, dump `/opt/opc/backups/20260726-092000/opc_platform.sql.gz`, rollback JAR `/opt/opc-backend.rollback.20260726-092000`, previous release `/opt/opc/releases/20260726-080227`. Local and remote frontend/backend SHA-256 values matched, and independent preflight confirmed three active services, one loopback-only `opc` backend process and the new current release.

Real Agent probe: `deepseek` / `deepseek-v4-flash`, finish reason `stop`, internal request ID `cbc1ec75-dde5-4cf9-ba87-85292d802884`, Provider request ID `c4925d38-fa5b-4f5c-bff8-5f7e63c223b6`, 3 model rounds, 3 Provider calls, 2 completed tool calls, 1 legal citation, 0 unknown citations, 6,254 prompt tokens, 702 completion tokens, 6,956 total tokens and 8,593 ms latency. The compatibility advice probe completed with request ID `2967deb4-aa92-4fee-b34e-7e9011248dcd`, finish reason `stop` and 2,996 total tokens.
