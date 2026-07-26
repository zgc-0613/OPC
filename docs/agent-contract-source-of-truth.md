# Agent Contract Source Of Truth

## Canonical owner

`AgentResearchContract` is the canonical Java source for:

- prompt version;
- planning and synthesis output budgets;
- planning question, tool, comparison and dependency bounds;
- synthesis statement, recommendation, supplemental, citation and coverage bounds;
- the complete structured result section list;
- controlled Agent v2 diagnostic codes.

`AgentToolRegistry` builds separate closed planning and synthesis JSON Schemas from these constants. `AgentOrchestrator` uses the same constants for Java validation, aggregate validation, prompt boundary text and result normalization. Planning never receives the final-result Schema; synthesis receives only the executed evidence context and final-result Schema, not an unnecessary copy of the original planning catalog.

## Planning contract

Planning contains `intent`, `researchQuestions`, `toolRequests`, `comparisonDimensions` and `outputSections`. Fields are closed, arrays are bounded and tool names come from the shared registry. Dependencies may reference only prior requests in the same plan. Region requests use a database resolver before their numeric IDs enter the Run authorization set.

Planning diagnostics are content-free branches such as invalid JSON, unknown fields, invalid tool requests, invalid questions, invalid comparison dimensions, invalid output sections and invalid dependencies. Raw Provider content is never copied into an API error.

## Synthesis contract

Synthesis contains the complete direct answer, findings, case/policy insights, comparison, recommendations, risks, assumptions, uncertainties, next questions, citations, confidence and evidence coverage shape. Facts require current-run source IDs. Inference and methodology are labeled separately. Recommendations without authorized evidence do not survive normalization, and a completed result requires at least one legal citation.

Schema per-field maxima sum to the Java aggregate maxima. `additionalProperties` is false for closed objects. Prompt text renders the same bounds from the contract instead of repeating literals.

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

`agent-v2-contract-replays.json` contains twelve minimal sanitized shapes: planning/synthesis truncation, uncited fact, uncited recommendation, source-ID type confusion, aggregate-limit drift, invalid output sections, overlong planning arrays, Provider connection failure, missing legal citation and forged coverage. Fixtures contain no prompt, user question, production text, credential, cookie, private URL or complete model output.

`AgentContractReplayTest` asserts the controlled diagnostic and side-effect policy for each shape. `AgentGoldenEvaluationTest` remains a deterministic runtime protocol test. `AgentResearchQualityEvaluationTest` checks deterministic evidence relevance and output contracts. Real Provider compatibility is a separate opt-in test locally and a mandatory isolated candidate gate before production rollout.

## Database impact

This convergence requires no new table, column or index. It uses existing Run, Provider Call, tool audit, structured result, evidence snapshot and settlement data. Existing forward migrations, prechecks and postchecks remain unchanged and repeatable.
