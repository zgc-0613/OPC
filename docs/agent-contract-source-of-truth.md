# Agent Contract Source Of Truth

## Canonical owner

`AgentResearchContract` is the canonical Java source for:

- prompt version;
- planning and synthesis output budgets;
- planning question, tool, comparison and dependency bounds;
- synthesis statement, recommendation, supplemental, citation and coverage bounds;
- the complete structured result section list;
- controlled Agent v2 diagnostic codes.

`AgentToolRegistry` builds separate closed initial-planning, continuation and synthesis JSON Schemas from these constants. `AgentOrchestrator` uses the same constants for Java validation, aggregate validation, prompt boundary text and result normalization. Planning never receives the final-result Schema; synthesis receives only the compact executed evidence context and final-result Schema, not an unnecessary copy of the original planning catalog.

## Planning contract

Initial planning contains `intent`, `researchQuestions`, `toolRequests`, `comparisonDimensions` and `outputSections`. Fields are closed, arrays are bounded and the initial tool set is exactly `search_cases` and `search_policies`; initial `dependsOn` arrays are empty. Search arguments carry controlled keywords and region scope, not arbitrary model-selected region or industry IDs.

After independent searches complete, the continuation contract may request `compare_cases`, `get_source`, another bounded independent search, `final`, or `evidence_insufficient`. Dependent requests require at least one completed same-Run dependency. Their case/source IDs must exist in that dependency's compact `_authorized` result. The protocol does not implement JSONPath, expression evaluation or dynamic code execution.

If the Provider violates only the dependency contract, the runtime may spend one remaining model round on a content-free correction. The correction enumerates the completed request IDs and currently authorized case/source IDs; it never guesses a replacement or executes the rejected request. A second dependency violation remains `INVALID_DEPENDENCIES`.

Planning diagnostics are content-free branches such as invalid JSON, unknown fields, invalid tool requests, invalid questions, invalid comparison dimensions, invalid output sections and invalid dependencies. Raw Provider content is never copied into an API error.

## Synthesis contract

Synthesis contains the complete direct answer, findings, case/policy insights, comparison, recommendations, risks, assumptions, uncertainties, next questions, citations, confidence and evidence coverage shape. Facts require current-run source IDs. Inference and methodology are labeled separately. Recommendations without authorized evidence do not survive normalization, and a completed result requires at least one legal citation.

Schema per-field maxima sum to the Java aggregate maxima. `additionalProperties` is false for closed objects. Prompt text renders the same bounds from the contract instead of repeating literals.

One bounded synthesis correction is permitted for `INVALID_SOURCE_ID`, `UNCITED_FACT`, `UNCITED_RECOMMENDATION` or `MISSING_CITATIONS` when verified tools already ran and a model round remains. The discarded response is not stored in the conversation. The correction contains only the controlled diagnostic and current-run source allowlist; repeated or unrelated failures remain terminal.

## Evidence coverage

The model coverage object is a suggestion. After synthesis, `AgentToolContext` derives:

- case count;
- policy count;
- unique source count;
- exact-region count;
- parent-region count;
- national count;
- cross-region count;
- sufficient, partial or insufficient status.

The server replaces model values before persistence and user display. A mismatch emits a sanitized diagnostic marker. Current-run authorization, published status and verified evidence remain mandatory.

## Replay and compatibility verification

`agent-v2-contract-replays.json` contains fifteen minimal sanitized shapes: invalid planning JSON, planning truncation, unknown planning fields, overlong questions, overlong comparison dimensions, invalid output sections, invalid dependencies, Provider connection failure, synthesis truncation, uncited facts, uncited recommendations, source-ID type confusion, missing legal citations, aggregate-limit drift and forged coverage. Fixtures contain no prompt, user question, production text, credential, cookie, private URL or complete model output.

`AgentContractReplayTest` asserts the controlled diagnostic plus Provider/tool invocation counts for each shape. Service and MySQL lifecycle tests own the real persistence and settlement side effects: final Run/Provider Call state, actual usage, released reservation, result/message/evidence absence, rollback and replay idempotency. `AgentGoldenEvaluationTest` remains a deterministic runtime protocol test. `AgentResearchQualityEvaluationTest` checks deterministic evidence relevance and output contracts. Real Provider compatibility is a separate opt-in test locally and a mandatory isolated candidate gate before production rollout.

## Tool-result contract

The complete typed audit JSON and the model projection are deliberately separate. Audit persistence is capped at 128 KiB measured in UTF-8 bytes. The model projection is capped at 12 KiB and uses deterministic per-field UTF-8 truncation before reducing items. It retains item identity, source identity, title, region, industry/type, match reason and necessary summary. Metadata reports `totalCount`, `returnedCount` and `truncated`; only items present in `_authorized` enter the same-Run authorization set.

## Database impact

This convergence adds no table or index. The repeatable `20260727_agent_multiround_budget` precheck/migration/postcheck adds `ai_analysis_runs.requested_intent VARCHAR(40) NOT NULL DEFAULT 'auto'`, raises stored settings below five model rounds or 28,000 aggregate Tokens, and preserves higher administrator values. Candidate and production execute the same hash-matched migration set.

Planning output is bounded at 3,200 Tokens. Invalid initial planning and continuation `UNKNOWN_FIELDS` may receive at most two content-free recoveries; terminal server-derived evidence removes continuation from the next Schema. Synthesis receives current-run legal source allowlists, and no recovery may authorize a new ID or expose discarded Provider content.

## Server-owned execution requirements

`requestedIntent` is an optional backward-compatible request field with the closed values `auto`, `policy_lookup`, `case_analysis`, `case_comparison`, `source_verification`, `technology_assessment` and `general_research`. Omission is `auto`; an invalid value is rejected by the existing Bean Validation error path.

`ResearchExecutionRequirements` is the authoritative terminal contract for each submitted message. It merges validated API intent with conservative deterministic signals from the current user message. Model intent can only add operations and cannot remove a server-required `POLICY_SEARCH`, `CASE_SEARCH`, `CASE_COMPARISON` or `SOURCE_VERIFICATION`. This operation set, not the planning string, decides whether search, dependent comparison or source verification is still missing. Structured history normalizes its display intent to the server-resolved value.

Starter intent is client metadata for the first submission only. Editing starter copy resets it to `auto`, and every continuation defaults to `auto` so the service evaluates the current message. The field is independent from the immutable session profile and cannot modify region or industry authorization.

## Intent authority clarification (2026-07-28)

The authority order is closed and non-additive across stronger sources: non-`auto` `requestedIntent`, then deterministic server operations for `auto`, then model intent only when the first two provide no operation. A model intent cannot add `CASE_SEARCH` to explicit `policy_lookup`, cannot widen a deterministic policy request, and cannot replace explicit `general_research` or `technology_assessment` with `follow_up`. It may provide the supported fallback intent only for a genuinely unresolved `auto` request.

`ResearchExecutionRequirementsTest` owns this public resolution contract. `AgentOrchestratorTest` proves that the resolved operation set, rather than the model-planned intent string, controls terminal tool-chain eligibility. The Provider-facing final intent remains normalized to the server result.
