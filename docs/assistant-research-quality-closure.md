# Assistant Research Quality And Evidence Workspace Closure

Date: 2026-07-26  
Scope: research orchestration, verified evidence presentation, retrieval quality, low-height usability, settlement atomicity and deployment recovery.

## Root causes and fixes

The remaining Composer clipping came from mixed sizing ownership below an otherwise bounded route. `MainLayout`, the Assistant content shell and workspace now pass a real `100vh`/`100dvh` height through `height: 100%` and `min-height: 0`; `research-desk` is a vertical flex container. Header, research profile and Composer use non-shrinking rows, and only the conversation owns remaining-space vertical scrolling.

Assistant commands lacked a consistent visible hierarchy. Secondary commands now use paper backgrounds and neutral 1 px borders; cancel/stop uses a restrained deep-red border; all relevant commands define hover, focus-visible, active and disabled states. Coarse-pointer controls are at least 44 px without globally enlarging desktop UI.

The runtime exposed tool counts and citations but no user-oriented research-material contract. The new evidence service reauthorizes the Run owner, reads only completed tool audits, reloads current case/policy/source rows, enforces published plus verified status, deduplicates and caps each type at 12, bounds all text, and accepts only safe HTTP(S) origins. Changed evidence becomes an unavailable marker instead of returning stale content.

The earlier orchestration could be protocol-correct while spending a model completion on each tool choice and producing shallow answers. `agent-research-v2` uses one closed plan, deterministic bounded tool execution and one structured synthesis. Planning consumes venture type, region, canonical industry, stage, budget, goal, resources and recent allowed context. Dependent source/comparison tools can use only IDs authorized earlier in the same Run.

Hubei case retrieval previously filtered only the selected province row. It now includes descendant regions such as Wuhan. Policy lookup orders descendants, selected region and ancestors/national context. Exact industry-tag matches no longer depend on a duplicate title/body keyword. Coverage is now `sufficient`, `partial` or `insufficient`; partial verified material still produces a bounded answer.

Provider Call settlement and Run usage could partially commit in separate transaction paths. `AgentProviderSettlementService` uses an independent transaction and a locked Call row to update both records atomically, replace estimates with actual usage once, and roll back on any Run reconciliation failure. Cancellation continues to suppress answer delivery while retaining only the state required for usage settlement.

Deployment recovery could replace the first failure with a later reconnect, shutdown, rollback or cleanup exception. Every recovery stage now adds a fixed secret-free note to the original exception and rethrows that primary failure.

The first production attempt exposed a real V2 response-budget defect: planning and synthesis both received the combined plan/tool/final schema and complete tool catalog, and the final schema permitted overly large section arrays. The probe ended with `TRUNCATED_RESPONSE`; the deployment gate rolled production back to `20260726-092000`. JSON-plan mode now uses a plan-only first-round schema and a final-only second-round schema, replaces the tool-planning system message before synthesis, and enforces compact per-field and cross-section totals. Agent requests now declare 1600 for planning and 3200 for synthesis under the configured aggregate Run limit. Other AI capabilities still use the administrator's shared output setting.

The third candidate completed within budget but the gate rejected it as `UNCITED_FACT`. The external result Schema had allowed an empty `sourceIds` array for a factual statement while the authoritative server validator required at least one current-run source. Evidence statements now use a closed `oneOf`: `fact` requires a non-empty source list, while `inference` and `methodology` may omit it. The synthesis prompt states the same constraint, and the failed candidate again rolled back to `20260726-092000`.

The fourth candidate stopped during planning with `INVALID_OUTPUT_SECTIONS`, before any tool ran. Java had required the relevant-section hint to include specific final fields even though that list is not carried into synthesis and the final Schema already requires every output field. An initial exact-thirteen correction caused a fifth planning `TRUNCATED_RESPONSE` at the 1200-Token ceiling. The retained regression tests now express the real boundary: planning returns 2-13 known unique relevant sections; synthesis returns all thirteen fields, using empty arrays where inapplicable. No quality or citation rule was relaxed.

The sixth attempt stopped at the existing Provider connection test before any rollout. The seventh candidate reached synthesis after two completed tools but returned another `fact` with an empty source list. DeepSeek therefore cannot be treated as a perfect enforcer of the response Schema. The server now changes only this exact unsupported-fact shape to `inference` before validation and storage. Unknown, duplicate, non-positive or unowned source IDs still fail, recommendations still require sources, and every remaining fact is source-backed.

The eighth candidate again ended planning with `TRUNCATED_RESPONSE`. The published plan contract had permitted six research questions of 300 characters and six comparison dimensions of 80 characters, so a schema-valid response could exceed the bounded first-round output. Both Schema and Java now cap these at four 120-character questions and four 40-character dimensions. The tool request limit remains six; research capability is unchanged while plan prose is bounded.

The ninth candidate completed two tools and synthesis but failed `INVALID_STRUCTURED_RESULT`. Each evidence array had allowed four items and each supplemental array six, while Java capped the totals across four arrays at six. Schema and Java now share per-field maxima of `2/1/1/2` for both groups. Their maximum sums are exactly six, so a response accepted by the published Schema cannot cross the aggregate validator boundary.

The tenth attempt stopped at the Provider connection gate before rollout. The eleventh candidate completed two tools but confused another domain record ID with `sourceId`. The synthesis request now lists the exact current-run allowed source IDs. Server normalization removes every unowned integer from statements, recommendations and citations before persistence; a fact with no legal source becomes inference, a recommendation with no legal source is omitted, and completion still fails unless at least one legal citation remains. Invalid source shapes are not normalized and still fail.

The twelfth candidate was rejected during planning by the remaining generic `INVALID_AGENT_PLAN` diagnostic. It did not switch the live release. Planning validation now emits only fixed content-free branches for invalid JSON, repeated plans, unknown fields, invalid tool requests, invalid question or comparison arrays, invalid output sections and invalid dependency arrays. A public orchestration regression locks the branch behavior without reading, logging or persisting raw model content.

Production inspection then isolated a legacy classification gap: all eight Hubei cases contain controlled AI, AI application or AIGC text evidence, but none has the canonical `1027` industry relation. Exact-tag lookup now retains the direct relation as the primary path and falls back only to the canonical tag name and registered aliases across bounded case fields. A real MySQL 8.4 test proves Wuhan/Hubei AI cases are recovered and unrelated records remain excluded.

The first final candidate completed planning and two tools but the complete 16-field synthesis hit the remaining 2,000-token completion ceiling. The request-contract test reproduced the exact mismatch before the implementation changed. Only the compact synthesis allowance moved to 3,200; field caps, model rounds, tool count, total Run budget and all evidence validation remained unchanged. The rebuilt candidate finished with `stop`.

## Contracts

`GET /api/ai/research/runs/{runId}/evidence` preserves existing endpoints and returns:

- Run ID and current Run status.
- Deduplicated `case`, `policy` and `source` items with controlled IDs.
- Bounded title, brief, region, geographic level, industry/type and match reason.
- Current evidence status, publisher, source title, safe original URL and controlled detail URL.
- An `available` flag and grouped counts.

It never returns raw `resultSummaryJson`, tool parameters, tool result JSON, prompt text, model response, chain-of-thought, credentials or unowned evidence.

The V2 plan schema contains `intent`, `researchQuestions`, `toolRequests`, `comparisonDimensions` and `outputSections`. Supported intents are policy lookup, case analysis, case comparison, technology assessment, source verification, mixed research and follow-up. The structured result contains direct answer, findings, case/policy insight, comparison, recommendations, risks, assumptions, uncertainties, next questions, citations, confidence and evidence coverage. Compatibility Markdown is rendered only after validation.

## Database and compatibility

Phase 26 adds no database field, index or migration. It reuses the existing Provider Call, Run, tool audit, evidence snapshot and structured result columns. The previous forward-only Assistant workspace stabilization and per-user history revision migrations remain idempotent and are still executed with precheck and postcheck during deployment.

Legacy `GET /sessions`, DELETE-as-archive, start/message, history and Run endpoints retain their shapes. Compatibility session listing now projects all active runs in one owner-scoped query instead of issuing one query per session.

## Verification

- Spring Boot: `322` executed, `321` passed, `1` opt-in real Provider smoke skipped.
- MySQL 8.4 Testcontainers: `67/67`, including settlement rollback, cancellation/purge, retrieval hierarchy, canonical industry alias fallback, ownership, evidence DTO and concurrent estimate replacement.
- Frontend Vitest: `73/73` across 14 files.
- Repository frontend scripts: `8/8`.
- Python default deployment/migration suite: `77` executed, `70` passed and `7` explicit-MySQL cases skipped by default.
- Explicit Python MySQL 8.4 migration suite: `7/7`.
- Vite production build: passed, 1,804 modules; main JS `627.07 kB` with the known non-blocking warning.
- Spring Boot executable JAR: passed.
- Python syntax, `git diff --check`, `.codegraph/` and `.local-secrets/` ignore/untracked checks, build-artifact tracking, production dependency audit and scoped high-confidence secret scan: passed.
- Real local Provider evaluation: not run; it remains opt-in and did not block deterministic verification. The mandatory deployment probe used the real Provider and is recorded below.

## Deployment status

Release `/opt/opc/releases/20260726-162930` is live through `/opt/opc/current`. The timestamped backup is `/opt/opc/backups/20260726-162930`, the database dump is `/opt/opc/backups/20260726-162930/opc_platform.sql.gz`, the backend rollback artifact is `/opt/opc-backend.rollback.20260726-162930`, and the previous release `/opt/opc/releases/20260726-092000` remains available.

Remote frontend hash `43b5354cb0d7dbe09a3c0a1618d0a7af6a83b30455d04ba5d0a115958e05c517` and backend hash `3ed8b7346314cd75e23b14ad542fe77a65f2a2835c6d67aad2dc9d3865fe36ad` match the deployed artifacts. Independent preflight confirmed three active services, nginx configuration validity, one backend process owned by `opc`, and loopback-only port `8082`. Temporary administrator and probe-data counts returned to their pre-probe baseline.

The real Provider probe used `deepseek` / `deepseek-v4-flash` and `agent-research-v2`. It completed with finish reason `stop`, 3 model rounds, 3 Provider calls, 2 completed tools, 5 case evidence items, 2 policy evidence items, 4 legal citations, 0 unknown citations, 7,108 prompt tokens, 3,931 completion tokens, 11,039 total tokens and 46,160 ms latency. The compatibility advice probe also completed with `partial` evidence and 3,076 total tokens.

## Manual acceptance

Playwright was intentionally not used. The user should inspect Assistant history expanded/collapsed, profile expanded/collapsed, evidence panel, citation/process drawers, long conversation and the fixed Composer at `1366x768`, `1280x600`, `1024x768` and `390x844`.

## Phase 27 citation and candidate closure

Assistant citations now resolve with the message's own `runId` plus `sourceId` and `citationId`. A historical message never borrows `latestRun` evidence. Inline source markers focus the matching `AssistantEvidencePanel` item only when the source is present in that message's citation allowlist. The evidence and citation surfaces continue to reject non-HTTP(S) URLs and URLs containing embedded credentials.

The stop command retains the Prisma Light danger treatment: a 1 px dark-red border, restrained red hover/active feedback, a neutral high-contrast focus-visible ring, disabled feedback and a 44 px coarse-pointer target. No absolute-positioned Composer or new decorative surface was introduced.

The deterministic quality suite now runs behind one canonical Agent contract and includes sanitized replay coverage for real Provider incompatibilities. It remains a runtime/evidence quality gate, not a human claim about prose quality. Human review still owns usefulness, clarity and recommendation judgment.

Local Phase 27 results are Vitest `77/77` across 14 files, all 8 frontend scripts and a production Vite build of 1,804 modules. The main JavaScript bundle is `629.52 kB`; the existing non-blocking size warning remains outside this stabilization scope. Manual viewport checks remain `1366x768`, `1280x600`, `1024x768` and `390x844`.

The complete Phase 27 backend result is Spring `341` (`340` passed and one opt-in real Provider smoke skipped), including MySQL 8.4 `68/68`. Python deployment/migration tests passed `83/83`, and the explicit MySQL migration suite passed `7/7`. The executable JAR, Python syntax and repository gates passed.

The isolated candidate completed on `deepseek-v4-flash` with `agent-research-v2` before production switching: 2 model/Provider rounds, 3 completed tools, 4 legal citations, zero unknown citations, 5 case, 2 policy and 4 unique-source evidence items, `sufficient` server-derived coverage, 11,723 total Tokens and 27,586 ms latency. Usage settled as actual, reservation reached zero and the audit recorded `release_switched=false`.

Release `/opt/opc/releases/20260726-213258` is live through `/opt/opc/current`. Backup `/opt/opc/backups/20260726-213258`, database dump `/opt/opc/backups/20260726-213258/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260726-213258`, and previous release `/opt/opc/releases/20260726-162930` are retained. Remote frontend hash `ccc5886f1517799da838b4955eecd8e2d26e07957631e24a30a0758c032bf1fb` and backend hash `f998c99c56c31bb4fd130d60aa27ce0193f8eeac8ef739813ffe39cc74bb3eea` match the deployed artifacts. Independent preflight confirmed three active services, valid nginx configuration, one `opc` backend process and loopback-only port 8082.
