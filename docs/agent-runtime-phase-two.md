# SoloFirm Agent Runtime Phase Two

## Delivery status

Local stabilization, deterministic evaluation, automated tests, migration tests, and production builds are complete as of 2026-07-25. Production deployment and the semantic real-model probe have not run because the deployment process has no securely injected `OPC_SSH_PASSWORD`; the credential will not be embedded in a command, log, script, or report. This document therefore does not claim a production version, rollback directory, paid-model token record, request ID, or live citation result.

## Phase-one closure fixes

- `CaseAnalysisService` consumes the complete `AiProviderResponse` and distinguishes `stop`, `length`, `content_filter`, and abnormal finish reasons.
- The case-analysis response contract defines object properties, types, required fields, `additionalProperties: false`, array/object citation structure, confidence bounds, and content/count limits.
- JSON syntax, missing fields, confidence, blank claims, missing citations, and source IDs outside the evidence allowlist have separate diagnostic codes.
- Evidence-insufficient case analysis creates a secret-free audit row and returns its `analysisId` without storing the user's question or a raw model response.
- Deleting a tag referenced by a case or policy returns a controlled HTTP 409 conflict instead of exposing a foreign-key exception.
- Policy applicability batch updates are limited to 100 records per request.

## Data model and migration

Migrations: `deploy/sql/20260725_agent_runtime.sql` plus the forward-only, idempotent `20260725_agent_runtime_stabilization.sql`, guarded by `20260725_agent_runtime_precheck.sql` and the unified `20260725_agent_runtime_postcheck.sql`.

`ai_agent_sessions` owns a session by `user_id`, stores a 120-character title, `active/archived` status, bounded profile JSON, structured `research_context_json`, optimistic `version`, and activity timestamps. The research context contains pending/resolved fields, clarification count, and the last visible question; only database-verified region and industry/tag IDs enter resolved state. User deletion is restricted. The public DELETE API archives rather than physically deletes.

`ai_agent_messages` stores only visible `user/assistant` messages with `pending/completed/failed` status, stable `sequence_no`, optional run link, bounded citation JSON, and creation time. `(session_id, sequence_no)` is unique. Physical session deletion cascades to messages; run deletion sets the optional message run link to null.

`ai_agent_tool_calls` belongs to a real `ai_analysis_runs` row and stores a unique step number, whitelisted tool name, validated bounded arguments, bounded result summary, evidence hash/count, status, diagnostic, latency, and timestamps. Run deletion cascades to its tool audits.

`ai_analysis_runs` remains the unified ledger and adds the Phase-Two session/message links plus `lease_owner`, `lease_expires_at`, `heartbeat_at`, `execution_attempts`, `next_attempt_at`, `last_recovery_reason`, `settlement_status`, `provider_dispatched_at`, `settled_at`, and `settlement_version`. Generated nonterminal guards cover both `received` and `running`. Foreign keys restrict deletion of referenced sessions/messages. Unique guards enforce one nonterminal Agent run per user, one per session, and one run per `(user_id, task_type, idempotency_key)`.

`ai_agent_provider_calls` records each `(analysis_run_id, round_no)` exactly once with an internal request ID, separately nullable provider request ID, reservation and settlement state, prompt/completion/total tokens, finish reason, latency, dispatch/settlement timestamps, and no raw response or secret. It is the idempotent reconciliation source for actual or estimated usage after cancellation, failure, expiry, timeout, or process recovery.

`ai_model_settings` adds the bounded Agent settings plus `agent_rollout_state`, `agent_rollout_changed_at`, and `agent_rollout_changed_by_admin_id`. Agent defaults to `false` and `explicitly_disabled`; provider `enabled` never enables it implicitly. Already explicit Agent settings are preserved during the forward migration, and subsequent changes require the authenticated administrator flow. Runtime defaults remain 4 rounds, 6 calls, 8,000 tokens, 12 history messages, 120 seconds, and `json_plan`.

The migrations are additive and rerunnable through `information_schema` guards. The postcheck requires 4 Agent tables, 21 Agent run columns, 10 settings columns, 7 foreign keys, 8 unique indexes, all 15 expected named indexes, no unexpected Agent indexes, and 0 rollout/settings inconsistencies. Composite indexes are counted by distinct index name. Real MySQL 8.4 tests cover first execution, repeat execution, complete postcheck, composite-index accounting, and incomplete-structure failure.

## State machine

The externally observable states are `received`, `clarification_needed`, `planning`, `waiting_for_model`, `tool_requested`, `tool_running`, `synthesizing`, `completed`, `evidence_insufficient`, `failed`, `cancelled`, and `expired`.

The deterministic clarification policy merges the latest verified answer, persisted session context, and initial profile in that order. If region, industry, or research objective is missing or ambiguous, it writes one visible clarification question and a safe `clarification_needed` audit without calling the provider or tools. Answers such as `湖北省` resolve against current database regions before planning. Clarification is bounded; exhausting the limit enters `evidence_insufficient` instead of looping or inventing context.

An information-complete submission locks the session, persists the visible message, idempotency key, quota reservation, and `received` run, then returns `202 Accepted`. Executor dispatch is only a wake-up optimization. A scheduled worker atomically leases eligible database rows, renews the lease for the bounded execution window, heartbeats during work, recovers expired leases after restart, records every recovery reason/attempt, and moves exhausted work to a terminal state. Two instances cannot own the same lease, and terminal states cannot be reclaimed.

Only these progress summaries are user-visible: analyzing requirements, planning research, searching/verifying evidence, and organizing the answer. No chain-of-thought is requested, stored, or returned. Cancellation changes the visible run state immediately but does not erase already dispatched usage. Late provider results may idempotently settle the provider-call ledger and token totals, while guarded status updates prevent them from overwriting cancelled, failed, or expired states.

## Tool contracts

### `search_cases`

Arguments: optional `regionId`, `industryTagId`, `industry` (100 characters), `query` (120), `category` (50), and `limit` (1-10, default 5). It returns bounded `caseId`, title, region, category, summary, business model, outcome, `sourceId`, verified evidence status, and match reason. SQL returns only published, verified cases whose sources are also published and verified.

### `search_policies`

Arguments: required `regionId`; optional `industryTagId`, `industry` (100), `query` (120), and `limit` (1-10, default 5). It searches the selected region and its hierarchy and returns bounded policy identity, type, summary, support measures, `specific/general/unclassified` applicability, geographic level, `sourceId`, and match reason. `unclassified` is labelled only as a regional reference, never an industry-specific policy.

### `get_source`

Argument: required `sourceId`. The ID must already be allowed by a prior tool result in the same run. The source must still be published and verified and must have a safe stored HTTP/HTTPS URL. The bounded response contains title, publisher, URL, access time, and necessary notes; no arbitrary URL or local file is accepted.

### `compare_cases`

Arguments: 2-3 distinct `caseIds` already returned by `search_cases`, plus up to six dimensions from `businessModel`, `technicalPath`, `targetCustomer`, `outcome`, `regionalContext`, and `evidenceStrength`. The backend deterministically reads the still-published/verified case-source chain and returns bounded case summaries and per-dimension conclusions with `sourceId`.

Every tool has one provider-neutral metadata definition used by native calls, the JSON-plan catalog, JSON Schema, runtime validation, administrator audit labels, and automated tests. All objects, including nested objects, use `additionalProperties: false`; strong DTO/Bean validation enforces required fields, types, enums, lengths, arrays, and result limits. Unknown fields/tools, SQL, arbitrary URLs, and unauthorized IDs are audited as controlled failures before any tool executes.

## API contract

User routes, all protected by the existing active-user session interceptor:

```text
POST   /api/ai/research/sessions
GET    /api/ai/research/sessions
GET    /api/ai/research/sessions/{sessionId}
DELETE /api/ai/research/sessions/{sessionId}
POST   /api/ai/research/sessions/{sessionId}/messages
GET    /api/ai/research/runs/{runId}
POST   /api/ai/research/runs/{runId}/cancel
```

Message submission requires `content` of at most 2,000 characters and an 8-64 character `[A-Za-z0-9_-]+` idempotency key. It returns HTTP 202 with `sessionId`, `messageId`, `runId`, and initial status. Ownership is rechecked for every session and run read/cancel operation. Archived sessions reject new messages.

Administrator-only audit routes:

```text
GET /api/admin/ai-agent-runs?limit=50
GET /api/admin/ai-agent-runs/{runId}
```

They expose run/session identifiers, masked user identity, state, provider/model, rounds, tool counts, token usage, duration, finish reason, diagnostic, request ID, timestamps, and bounded tool summaries. They do not expose the complete user question, hidden reasoning, provider response, API key, or unredacted arbitrary arguments.

## Provider, quota, concurrency, and security

`AiProviderRequest`/`AiProviderResponse` now carry provider-neutral messages, tool definitions, tool calls, finish reason, usage, request ID, and latency. `OpenAiCompatibleAiClient` maps OpenAI-compatible `tool_calls` and maps HTTP 429, 5xx, timeout, and other upstream failures to controlled diagnostics. Business services never parse vendor-specific JSON.

`json_plan` is the default compatibility mode and is parsed with Jackson against a closed schema. `native` tool calls are enabled only by an administrator setting; the production model's official support has not been verified, so no capability is inferred from its name.

The run ledger reserves the configured per-run maximum against the daily user quota, and the provider-call ledger records dispatch and settles every model round exactly once. Cancellation before dispatch releases the reservation. Dispatched calls settle actual usage when available; timeout/crash paths retain a bounded estimate, and a late actual callback may replace the estimate idempotently without reopening the run or charging twice. Cancelled, failed, and expired usage still counts, so cancellation cannot bypass daily quota. Generated nonterminal guards prevent concurrent runs per user and per session; idempotency returns the existing run and reservation.

User text and database text are marked as untrusted data in the system contract. The runtime accepts only four registered tools, closed argument schemas, known dimensions, run-local case/source IDs, published/verified data, and backend-built queries. It rejects prompt-injected tool names, SQL, arbitrary URLs, unknown citations, blank citation claims, missing citations, stale evidence, oversized input/history/results, excessive rounds/calls/tokens, and abnormal provider endings.

Secrets, raw provider bodies, raw full prompts, and chain-of-thought are not stored. User messages are stored for multi-round continuity and refresh recovery. Current deletion semantics are archival; there is no automated retention/purge job yet. Administrators see operational audits rather than full private questions.

## Frontend and administration

The existing `/assistant` route remains the SoloFirm research workspace. It keeps the entrepreneurship profile and readiness context while adding new/history sessions, ordered user/assistant messages, a single clarification question, 202 submission, run polling, refresh recovery, cancel, safe retry with a fresh idempotency key, stage summaries, bounded tool summaries, citation count, an expandable evidence drawer, source/case/policy links, restrained model/token metadata, AI-content notice, and session archive.

The implementation keeps Prisma Light paper/ink tokens, existing Songti/Kaiti/Bookman typography, Lucide icons, 44px touch targets, non-bubble message hierarchy, responsive single-column layouts, and reduced-motion fallbacks. Motion is limited to short opacity/transform transitions. Per user instruction, Playwright was not used.

The existing administrator settings page adds bounded Agent controls and an `AgentRunAuditPanel` for list/detail inspection. It does not add evidence mutation controls.

## Verification

- Maven full suite: 270 tests, 0 failures, 0 errors, 1 skipped opt-in real DeepSeek smoke.
- MySQL Testcontainers: 48 tests against MySQL 8.4, all passed.
- Frontend Vitest: 14 tests in 2 files, all passed; `AssistantView` contributes 13.
- Frontend package scripts: all repository-defined contract scripts passed; the component script is covered by the full Vitest run.
- Frontend build: passed with 1,705 transformed modules.
- Python deployment/migration tests: 17 passed; syntax compilation passed.
- Backend executable JAR package: passed after the full test suite.
- Repository checks: `git diff --check`, `.codegraph/` ignore, tracked build-artifact scan, and high-confidence secret scan passed; the only scanner candidates are a variable-presence check and an explicit test-only fake key.
- Deterministic golden evaluation: 20 fixtures passed. Contract pass rate 1.0, expected completion rate 1.0, controlled failures 8, evidence-insufficient cases 2, accepted unknown citations 0, average rounds 0.8, average tools 0.7, average tokens 12.45, P50 40 ms, P95 60 ms. These are deterministic runtime-contract metrics, not DeepSeek quality metrics.
- Real DeepSeek evaluation: separate `AgentDeepSeekSmokeTest`, disabled unless `OPC_RUN_REAL_DEEPSEEK_EVAL=true`; it made no paid call during local verification.

## Changed-file inventory

The stabilization pass additionally owns `20260725_agent_runtime_stabilization.sql`, `AiAgentProviderCall.java`, `AiAgentProviderCallMapper.java`, `AgentRunDispatcher.java`, `AgentRunQueueService.java`, `AgentClarificationDecision.java`, `AgentDeepSeekSmokeTest.java`, and `AgentResearchServiceTest.java`. Existing lifecycle, session, settings, tool, migration, deployment, evaluation, and Assistant files were updated in place; no framework or runtime was replaced.

- Deployment and documentation: `.codex_deploy_opc.py` adds Agent migrations, semantic probe, cleanup, and rollback gating; `scripts/test_deployment_hardening.py` covers the new deployment requirements; `task_plan.md`, `findings.md`, `progress.md`, `AI_READINESS.md`, and this document record decisions, verification, privacy limits, and deployment status; the three `deploy/sql/20260725_agent_runtime*.sql` files provide precheck, migration, and postcheck.
- Existing AI provider/settings/audit files: `AiCapabilitiesController.java`, `AiModelSettingsUpdateDTO.java`, `AiAnalysisRun.java`, `AiModelSettings.java`, `AiResponseValidationException.java`, `AiAnalysisRunMapper.java`, `AiProviderRequest.java`, `AiProviderResponse.java`, `OpenAiCompatibleAiClient.java`, `AiSettingsService.java`, `CaseAnalysisService.java`, and `AiModelSettingsVO.java` add Agent settings, provider-neutral messages/tool calls, multi-round audit fields, detailed response diagnostics, and safe case-analysis closure behavior.
- Phase-one closure files: `PolicyApplicabilityBatchDTO.java` and `PolicyService.java` enforce the 100-row batch bound; `TagMapper.java` and `TagService.java` translate referenced-tag deletion into a controlled conflict.
- Agent runtime configuration/API: `AgentRuntimeExecutorConfig.java`, `AgentResearchController.java`, `AdminAgentRunController.java`, `AgentMessageCreateDTO.java`, and `AgentSessionCreateDTO.java` add bounded async endpoints and executor setup.
- Persistence: `AiAgentSession.java`, `AiAgentMessage.java`, `AiAgentToolCall.java`, `AiAgentSessionMapper.java`, `AiAgentMessageMapper.java`, `AiAgentToolCallMapper.java`, `AgentEvidenceToolMapper.java`, and `AdminAgentRunMapper.java` implement owned sessions, stable ordering, run/tool audits, evidence queries, and safe administrator projections.
- Provider-neutral Agent contracts: `AgentRuntimeConfig.java`, `AgentRuntimeConfigProvider.java`, `AiProviderException.java`, `AiProviderMessage.java`, `AiProviderToolCall.java`, and `AiToolDefinition.java` define bounded runtime settings, model messages, tool definitions/calls, and upstream diagnostics.
- Orchestration and lifecycle: `AdminAgentRunService.java`, `AgentCitation.java`, `AgentClarificationPolicy.java`, `AgentOrchestrator.java`, `AgentOrchestratorException.java`, `AgentOrchestratorInput.java`, `AgentOrchestratorOutcome.java`, `AgentOrchestratorProgress.java`, `AgentResearchQueryService.java`, `AgentResearchReceipt.java`, `AgentResearchService.java`, `AgentResearchWorker.java`, `AgentRunFinalizer.java`, `AgentRunLease.java`, `AgentRunLifecycleService.java`, and `AgentSessionService.java` implement clarification, submission, bounded execution, persistence, polling, cancellation, quota, evidence replay, and terminal settlement.
- Tool layer: `AgentTool.java`, `AgentToolRegistry.java`, `AgentToolContext.java`, `AgentToolResult.java`, `AgentToolExecution.java`, and `AgentToolException.java` implement the provider-neutral whitelist, validation, execution, result, and controlled-failure contracts; `AgentEvidenceHasher.java`, `AgentCaseSearchRow.java`, and `AgentPolicySearchRow.java` provide deterministic hashes and bounded persistence projections; `SearchCasesArguments.java`/`SearchCasesTool.java`, `SearchPoliciesArguments.java`/`SearchPoliciesTool.java`, `GetSourceArguments.java`/`GetSourceTool.java`, and `CompareCasesArguments.java`/`CompareCasesTool.java` implement the four strong-DTO read-only tools.
- Response projections: `AdminAgentRunDetailVO.java`, `AdminAgentRunRowVO.java`, `AgentMessageVO.java`, `AgentRunStatusVO.java`, `AgentSessionDetailVO.java`, `AgentSessionVO.java`, and `AgentToolCallSummaryVO.java` expose only the bounded user/admin contracts.
- Database source of truth: `opc-backend/src/main/resources/db/schema.sql` mirrors the migration's tables, columns, constraints, indexes, and settings defaults.
- Backend tests: `OpenAiCompatibleAiClientTest.java`, `AiSettingsServiceTest.java`, `CaseAnalysisServiceTest.java`, `PolicyServiceTest.java`, and `TagServiceTest.java` cover updated seams; `AgentResearchControllerTest.java`, `AgentGoldenEvaluationTest.java`, `AgentOrchestratorTest.java`, `AgentRunLifecycleServiceTest.java`, `AgentToolRegistryTest.java`, `PhaseOneMySqlIntegrationTest.java`, and `src/test/resources/ai/agent-golden-evaluation.json` cover REST, provider, orchestration, tools, real-MySQL persistence/concurrency/transactions, and the 20-question evaluation.
- Frontend: `src/api/ai.js` adds session/run/admin calls; `AssistantView.vue` implements the persistent research UI; `AssistantView.spec.js` covers its states and interactions; `AdminSettingsView.vue` adds Agent settings/audits; `AgentRunAuditPanel.vue` and `AgentRunAuditPanel.spec.js` add safe administrator run list/detail behavior.

## Production gate and remaining work

Before Phase Two can be declared complete, `OPC_SSH_PASSWORD` must be injected through a secure process environment. The deployment script will then create a timestamped backup/release, precheck and apply both Agent migrations, validate the 4/21/10/7/8 postcheck plus exact index names and rollout consistency, switch frontend/backend together, validate Nginx and health, and run a temporary QA-user research probe. The probe must execute at least one tool, complete, return at least one legal citation from its tool evidence snapshot, persist completed run/tool/provider-call audits with provider/model/finish reason/internal request ID/provider request ID/token/latency metadata, verify anonymous/ordinary/disabled-user boundaries, clean up all QA rows, and automatically disable Agent plus roll back on any failure.

Current production inspection, before deployment: provider `deepseek`, model `deepseek-v4-flash`, provider enabled, encrypted API key configured, and prior connection test successful. Agent fields are absent on the deployed Phase-One schema. There is no verified case, so the initial production probe is designed around a verified policy. Native tool-call support is not assumed; controlled `json_plan` remains the deployment default unless official capability is confirmed and explicitly configured.
