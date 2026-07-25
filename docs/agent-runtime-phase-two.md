# SoloFirm Agent Runtime Phase Two

## Delivery status

Local implementation, automated tests, migration tests, and production builds are complete as of 2026-07-25. Production deployment and the semantic real-model probe have not run because the established deployment workflow cannot authenticate to the production host with the currently available SSH credentials. This document therefore does not claim a production version, rollback directory, paid-model token record, request ID, or live citation result.

## Phase-one closure fixes

- `CaseAnalysisService` consumes the complete `AiProviderResponse` and distinguishes `stop`, `length`, `content_filter`, and abnormal finish reasons.
- The case-analysis response contract defines object properties, types, required fields, `additionalProperties: false`, array/object citation structure, confidence bounds, and content/count limits.
- JSON syntax, missing fields, confidence, blank claims, missing citations, and source IDs outside the evidence allowlist have separate diagnostic codes.
- Evidence-insufficient case analysis creates a secret-free audit row and returns its `analysisId` without storing the user's question or a raw model response.
- Deleting a tag referenced by a case or policy returns a controlled HTTP 409 conflict instead of exposing a foreign-key exception.
- Policy applicability batch updates are limited to 100 records per request.

## Data model and migration

Migration: `deploy/sql/20260725_agent_runtime.sql`, guarded by `20260725_agent_runtime_precheck.sql` and `20260725_agent_runtime_postcheck.sql`.

`ai_agent_sessions` owns a session by `user_id`, stores a 120-character title, `active/archived` status, bounded profile JSON, optimistic `version`, and activity timestamps. User deletion is restricted. The public DELETE API archives rather than physically deletes.

`ai_agent_messages` stores only visible `user/assistant` messages with `pending/completed/failed` status, stable `sequence_no`, optional run link, bounded citation JSON, and creation time. `(session_id, sequence_no)` is unique. Physical session deletion cascades to messages; run deletion sets the optional message run link to null.

`ai_agent_tool_calls` belongs to a real `ai_analysis_runs` row and stores a unique step number, whitelisted tool name, validated bounded arguments, bounded result summary, evidence hash/count, status, diagnostic, latency, and timestamps. Run deletion cascades to its tool audits.

`ai_analysis_runs` remains the unified ledger and adds `session_id`, `user_message_id`, `idempotency_key`, `step_count`, `tool_call_count`, `current_stage`, `visible_progress`, `cancelled_at`, `completed_at`, and generated `session_active_guard`. Foreign keys restrict deletion of referenced sessions/messages. Unique guards enforce one running task per user, one running Agent task per session, and one Agent run per `(user_id, task_type, idempotency_key)`.

`ai_model_settings` adds `agent_enabled`, `agent_max_model_rounds`, `agent_max_tool_calls`, `agent_max_tokens`, `agent_history_window`, `agent_timeout_seconds`, and `agent_tool_mode`. Defaults are `false`, 4, 6, 8,000, 12, 120 seconds, and `json_plan`. Allowed administrator ranges are 1-8 rounds, 1-12 calls, 512-32,000 tokens, 1-24 history messages, and 10-600 seconds; tool mode is `json_plan` or `native`.

The migration is additive and rerunnable through `information_schema` guards. The postcheck requires 3 Agent tables, 10 run columns, 7 settings columns, 6 foreign keys, 4 unique indexes, and 0 invalid settings rows. Real MySQL tests execute the migration twice.

## State machine

The externally observable states are `received`, `clarification_needed`, `planning`, `waiting_for_model`, `tool_requested`, `tool_running`, `synthesizing`, `completed`, `evidence_insufficient`, `failed`, `cancelled`, and `expired`.

The deterministic clarification policy runs before persistence of a provider-backed run. If region, industry, or research objective is missing, it writes one visible clarification question and a safe `clarification_needed` audit without calling the provider or tools. Otherwise submission reserves quota and returns `202 Accepted`; the worker loads a bounded history, iterates at most the configured rounds, validates each requested tool, records tool results, replays their evidence hashes before synthesis, validates final citations, and atomically settles usage and status.

Only these progress summaries are user-visible: analyzing requirements, planning research, searching/verifying evidence, and organizing the answer. No chain-of-thought is requested, stored, or returned. Cancellation changes only a running row; usage/stage/completion updates require `status='running'`, so late provider results cannot overwrite cancelled, failed, or expired runs.

## Tool contracts

### `search_cases`

Arguments: optional `regionId`, `industryTagId`, `industry` (100 characters), `keywords` (120), `category` (50), and `limit` (1-10, default 5). It returns bounded `caseId`, title, region, category, summary, business model, outcome, `sourceId`, verified evidence status, and match reason. SQL returns only published, verified cases whose sources are also published and verified.

### `search_policies`

Arguments: required `regionId`; optional `industryTagId`, `industry` (100), `keywords` (120), and `limit` (1-10, default 5). It searches the selected region and its hierarchy and returns bounded policy identity, type, summary, support measures, `specific/general/unclassified` applicability, geographic level, `sourceId`, and match reason. `unclassified` is labelled only as a regional reference, never an industry-specific policy.

### `get_source`

Argument: required `sourceId`. The ID must already be allowed by a prior tool result in the same run. The source must still be published and verified and must have a safe stored HTTP/HTTPS URL. The bounded response contains title, publisher, URL, access time, and necessary notes; no arbitrary URL or local file is accepted.

### `compare_cases`

Arguments: 2-3 distinct `caseIds` already returned by `search_cases`, plus up to six dimensions from `businessModel`, `technicalPath`, `targetCustomer`, `outcome`, `regionalContext`, and `evidenceStrength`. The backend deterministically reads the still-published/verified case-source chain and returns bounded case summaries and per-dimension conclusions with `sourceId`.

Every tool has a provider-neutral interface, JSON Schema, strong DTO, Bean Validation, result limits, run-local authorization, evidence hash, latency/status/diagnostic audit, and redacted model-facing output. There is no SQL, internet, arbitrary URL, or write-capable tool.

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

The existing token ledger reserves the configured per-run maximum against the daily user quota, aggregates every model round's prompt/completion/total tokens, and releases the unused reservation at a terminal state. Generated unique guards prevent concurrent runs per user and per session. Idempotency returns the existing run instead of inserting or charging twice.

User text and database text are marked as untrusted data in the system contract. The runtime accepts only four registered tools, closed argument schemas, known dimensions, run-local case/source IDs, published/verified data, and backend-built queries. It rejects prompt-injected tool names, SQL, arbitrary URLs, unknown citations, blank citation claims, missing citations, stale evidence, oversized input/history/results, excessive rounds/calls/tokens, and abnormal provider endings.

Secrets, raw provider bodies, raw full prompts, and chain-of-thought are not stored. User messages are stored for multi-round continuity and refresh recovery. Current deletion semantics are archival; there is no automated retention/purge job yet. Administrators see operational audits rather than full private questions.

## Frontend and administration

The existing `/assistant` route remains the SoloFirm research workspace. It keeps the entrepreneurship profile and readiness context while adding new/history sessions, ordered user/assistant messages, a single clarification question, 202 submission, run polling, refresh recovery, cancel, safe retry with a fresh idempotency key, stage summaries, bounded tool summaries, citation count, an expandable evidence drawer, source/case/policy links, restrained model/token metadata, AI-content notice, and session archive.

The implementation keeps Prisma Light paper/ink tokens, existing Songti/Kaiti/Bookman typography, Lucide icons, 44px touch targets, non-bubble message hierarchy, responsive single-column layouts, and reduced-motion fallbacks. Motion is limited to short opacity/transform transitions. Per user instruction, Playwright was not used.

The existing administrator settings page adds bounded Agent controls and an `AgentRunAuditPanel` for list/detail inspection. It does not add evidence mutation controls.

## Verification

- Maven full suite: 247 tests, 0 failures, 0 errors, 0 skipped.
- MySQL Testcontainers: 36 tests against MySQL 8.4, all passed.
- Frontend Vitest: 13 tests in 2 files, all passed; `AssistantView` contributes 12.
- Frontend package scripts: all 8 passed (`auth-session`, `assistant`, `assistant-component`, two evidence-review scripts, evidence-workbench, admin-concurrency, policy-applicability).
- Frontend build: passed with 1,705 transformed modules.
- Python deployment/migration tests: 13 passed; syntax compilation passed.
- Backend package: passed after tests.
- Deterministic golden evaluation: 20 questions passed. Completion 1.0, legal citations 1.0, citation/claim consistency 1.0, accepted unknown source IDs 0, tool success 1.0, evidence-insufficient refusal 1.0, average rounds 2.1, average tools 1.1, average tokens 31.5, P50 40 ms, P95 60 ms.

## Changed-file inventory

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

Before Phase Two can be declared complete, the existing secure SSH credential path must be restored. The deployment script will then create a timestamped backup/release, precheck and apply the migration, validate the 3/10/7/6/4/0 postcheck, switch frontend/backend together, validate Nginx and health, and run a temporary QA-user research probe. The probe must execute at least one tool, complete, return at least one legal citation, persist completed run/tool audits with provider/model/finish reason/request ID/token/latency metadata, clean up all QA rows, and automatically roll back on any failure.

Current production inspection, before deployment: provider `deepseek`, model `deepseek-v4-flash`, provider enabled, encrypted API key configured, and prior connection test successful. Agent fields are absent on the deployed Phase-One schema. There is no verified case, so the initial production probe is designed around a verified policy. Native tool-call support is not assumed; controlled `json_plan` remains the deployment default unless official capability is confirmed and explicitly configured.
