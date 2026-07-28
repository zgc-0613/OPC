# Agent Phase Two Final Closure

## Status

Agent Phase Two is complete and deployed as `/opt/opc/releases/20260728-130142`. The same-batch real Provider candidate gate, one production switch, production migrations, three production research chains, health/auth checks and resource cleanup all passed.

## Multi-round protocol

`agent-research-v2` no longer asks the model to guess dependent identifiers before retrieval:

1. The initial plan describes intent and independent `search_cases` or `search_policies` requests.
2. The server validates profile boundaries, executes the searches and creates a compact `_authorized` projection.
3. A bounded continuation round receives actual same-Run case, policy and source identities plus necessary evidence summaries.
4. The continuation may request `compare_cases`, `get_source`, another bounded search, `final`, or `evidence_insufficient`.
5. Dependent tools must name a completed same-Run request in `dependsOn` and use only IDs authorized by that request.
6. The final synthesis runs only after dependent execution and is normalized against the current Run source allowlist.

The protocol uses closed Schemas and finite enums. It does not evaluate JSONPath, scripts, expressions or model-supplied code. Existing independent one-plan searches remain compatible.

## Authorization and research boundaries

The current session profile owns the selected industry. Model keywords may narrow retrieval but cannot replace `industryTagId`. Region arguments expose controlled scopes (`selected`, `parent`, `national`, `cross_region_reference`); `AgentRegionResolver` resolves their database IDs and the tool layer rechecks authorization.

Actual search results flow into `AgentToolContext` as request-scoped authorization. Case IDs authorize only case operations; source IDs authorize only source/citation operations. IDs cannot cross Runs or unresolved dependencies. Cross-region evidence is explicitly marked as reference material.

When an established conversation explicitly changes its region or industry, the service returns `CONFLICT` and `需要基于新条件创建研究` before inserting a message or reserving Tokens. Merely comparing another region does not mutate the profile.

## Tool result and evidence counts

The full typed tool audit and Provider context are separate representations. Complete audit JSON is capped at 128 KiB measured in UTF-8 bytes. The compact model projection is capped at 12 KiB and deterministically truncates fields, then items, without emitting partial JSON. It reports `totalCount`, `returnedCount` and `truncated`. Only returned `_authorized` items contribute IDs, coverage and citations.

The evidence API retains `items` and `groups` and adds:

- `availableCount`
- `totalCount`
- `unavailableCount`
- `availableGroups`
- `totalGroups`

Unavailable records remain status markers but do not count as available evidence and cannot be cited. The Assistant shows `可用 X 项，共 Y 项`, or `当前资料已失效，请重新检索` when no usable evidence remains.

## Independent Assistant route

`/assistant` is a lazy top-level protected route under `AssistantLayout`, outside `MainLayout`. The layout supplies the SoloFirm exit, current research title, materials command and account entry without the public archive navigation. `AssistantView` retains history, drafts, citations, polling, retry and first-message behavior.

Desktop contains one history rail, one conversation/Composer area and one optional evidence drawer. Tablet and phone use mutually exclusive, focus-restoring drawers. Escape closes the active drawer. The workspace uses bounded `100vh`/`100dvh` dimensions, independent conversation scrolling, safe-area Composer padding and coarse-pointer targets of at least 44 px.

## Replay and side effects

The sanitized replay fixture contains 15 contract shapes. `AgentContractReplayTest` owns controlled diagnostic plus Provider/tool invocation counts. Service and MySQL lifecycle tests own Run and Provider Call status, actual/reserved Token settlement, rollback, absence of invalid structured results/messages/evidence, and duplicate-settlement safety.

No fixture stores an API key, prompt, original user question, complete Provider response, production evidence text, cookie or private URL.

## Candidate and production order

The release gate is:

1. Pass local tests and builds.
2. Upload a timestamped candidate release and verify hashes without changing current production.
3. Copy the current production database into an isolated candidate database.
4. Apply precheck, migration and postcheck to the candidate only.
5. Start the candidate runtime on `127.0.0.1:18082`.
6. Run the Provider connection check and policy, dynamic case-comparison and dynamic source-verification scenarios.
7. Clean candidate identities, data, runtime and database.
8. Only after success, back up production, run the hash-matched migration once, switch `/opt/opc/current` once and restart production.
9. Run health, authorization, history, settlement and temporary-data cleanup checks.

Candidate failure performs no production backup, migration, restart or switch. Cleanup errors append sanitized notes and never replace the primary candidate error.

## Candidate results

The Phase 28 attempts remain the first deterministic compatibility record: `INVALID_DEPENDENCIES`, `UNCITED_FACT`, then a policy pass followed by case-comparison `evidence_insufficient`. The Phase 29 candidate batches exercised the corrected continuation and measured budgets without changing production:

- With a 24,000-Token runtime budget, policy lookup passed in 3 rounds/1 tool with 11,488 Tokens, 2 citations and 30,427 ms. Source verification passed in 3 rounds/3 tools with 15,264 Tokens, 2 citations and 49,436 ms. Comparison failed `AGENT_TOKEN_LIMIT` after 4 rounds/3 tools and 25,422 actual Tokens.
- With the 28,000-Token build, source verification passed in 4 rounds/3 tools with 23,524 Tokens and 2 citations; policy failed `UNKNOWN_FIELDS` and comparison failed `TRUNCATED_RESPONSE`.
- After bounded planning recovery, policy failed `AGENT_TOKEN_LIMIT` after 5 rounds/3 tools and 31,429 actual Tokens; comparison reached two tools but failed `UNCITED_RECOMMENDATION`; source verification failed `INVALID_RESEARCH_QUESTIONS`.
- The terminal-only Schema build stopped at the pre-scenario connection gate with `PROVIDER_CONNECTION_FAILED`. Candidate runtime and database cleanup completed.

Every recorded run kept `release_switched=false`, settled returned actual usage once and released its reservation. No production backup, migration, restart or switch occurred. A further candidate must wait until Provider connectivity is plausibly restored; it must still run all three independent scenarios and aggregate every result.

The final server-owned-intent build passes Spring `373` with zero failures/errors and one opt-in real Provider smoke skip; MySQL 8.4 `71/71`; Vitest `87/87`; Assistant subset `36/36`; eight frontend scripts; Python default `106` with seven explicit-MySQL skips; explicit MySQL `7/7`; deployment hardening `85/85`; Vite production build; executable JAR; Python syntax, diff, ignore, artifact and scoped high-confidence secret checks.

## Database impact

Phase 30 adds no table or index. The idempotent `20260727_agent_multiround_budget` migration adds `ai_analysis_runs.requested_intent VARCHAR(40) NOT NULL DEFAULT 'auto'` and retains the settings updates: existing values below 28,000 aggregate Tokens and five model rounds are raised, while higher administrator values are preserved. Candidate and production must execute the same hash-matched migration set.

## Server-owned final tool-chain contract

The 07:08 candidate failure showed that request-scoped IDs and continuation mechanics were working, but the model could still lower the minimum business operation by returning `case_analysis`, `policy_lookup` or `general_research`. `ResearchExecutionRequirements` now resolves required operations from optional validated `requestedIntent` and conservative signals in the current message. Model intent may supplement the result but cannot remove it.

The first submission from a Starter carries its matching intent. Materially editing the prefilled question resets the local intent to `auto`; continuation messages are always `auto`. Old clients that omit the field remain compatible. The field is persisted per Run and included in idempotency identity, but cannot change the session profile's region or industry.

The orchestrator uses the resolved operation set for terminal eligibility. Comparison requires successful `search_cases` followed by request-authorized `compare_cases`; source verification requires a search-authorized source followed by `get_source`; policy and case requirements each require their corresponding successful search. Final structured intent is normalized to the server resolution.

## Final candidate baseline

Candidate `/opt/opc/releases/20260727-070820` did not switch production. Policy passed in four rounds with actual sequence `search_policies, search_cases`, two legal citations, 20,747 Tokens, `settled_actual` and zero reservation. Case comparison and source verification failed their respective tool-sequence gates. The final deploy implementation now records full sanitized scenario metrics before those gates, continues all three scenarios and deletes only the exact unswitched release created by the current failing command. Historical release `20260727-070820` is retained.

## Single-deploy result

Remote preflight passed before the command. The candidate results were:

- Policy: expected `search_policies`; terminal diagnostic `REQUIRED_TOOL_CHAIN_UNSATISFIED`; 3 rounds, 2 completed tools, 0 citations, 15,679 Tokens and 41,041 ms; `settled_actual`, reservation zero. The old report order skipped the tool-name query after terminal failure, so the actual policy sequence is unavailable.
- Case comparison: expected `search_cases, compare_cases`; actual `search_cases, search_policies, compare_cases`; completed in 4 rounds/3 tools with 6 legal citations, 24,165 Tokens, 68,165 ms and sufficient coverage of 4 cases, 5 policies and 7 sources.
- Source verification: expected `search_policies, get_source`; actual `search_policies, get_source, get_source`; completed in 4 rounds/3 tools with 2 legal citations, 18,257 Tokens, 45,079 ms and partial coverage of 2 policies and 2 sources.

All scenarios executed independently and all returned usage settled with zero reservation. The aggregate gate kept `release_switched=false`; no production backup, migration, restart or symlink switch occurred. Candidate services, database/user, environment file and the current-attempt release were removed. The historic `20260727-070820` release remains untouched.

A deterministic red-to-green deployment test moved tool-sequence collection before terminal validation while retaining the original Run diagnostic. Deployment hardening passes `86/86`; Python discovery passes `107` with seven opt-in MySQL skips. This reporting fix was not redeployed in the same round. The Phase Two exit criteria remain unmet because policy did not pass in the same candidate batch and production did not switch.

## Exit criteria

Phase Two can be declared complete only after dynamic search-to-compare and search-to-source flows pass on the real candidate; research boundaries, citations and evidence counts remain valid; all local tests/builds pass; the candidate changes no production state; one production rollout succeeds; production health and authorization pass; and all temporary data is removed. Manual visual checks do not block the engineering phase exit but remain required at `1366x768`, `1280x600`, `1024x768`, `390x844` and 375 px phone width.

## 2026-07-28 bounded closure result

The policy priority inversion is fixed locally: explicit intent outranks deterministic server inference, and deterministic server operations outrank model intent. The model can supply an intent only for unresolved `auto` work. Regression coverage also preserves tool-chain-first terminal validation, bounded source repair and complete candidate failure metrics.

All deterministic gates are green: focused orchestration `32/32`, Spring `380` with one opt-in Provider skip, MySQL 8.4 `71/71`, Vitest `87/87`, Assistant `36/36`, eight frontend scripts, deployment hardening `88/88`, Python default `109` with seven skips, explicit MySQL `7/7`, Vite/JAR builds and repository checks.

The last allowable corrective candidate passed policy and source verification in the same batch but did not execute `compare_cases`. It ended `CANDIDATE_CASE_COMPARISON_EVIDENCE_INSUFFICIENT` after `search_cases, search_policies`. Because the same deterministic diagnostic recurred after the narrow fix and two corrective retries were consumed, the bounded stop applies. Production was not switched, current remains `/opt/opc/releases/20260726-213258`, and current-attempt candidate resources are fully cleaned.

Therefore: local implementation is complete, but production deployment is not complete. Phase Two cannot be closed until a future bounded change proves a zero-result comparison search receives one broader search before controlled insufficiency, then all three real scenarios pass in one batch and production postdeploy checks succeed.

## 2026-07-28 final production closure

The zero-result evaluator order was the final comparison defect. The server now executes a normalized selected search, one bounded cross-region expansion when needed, and `compare_cases` once two distinct same-Run case IDs exist. Policy and source verification receive the same deterministic completion and reserved capacity. Every required call remains in `ai_agent_tool_calls` with request-level dependency authorization and evidence replay.

The candidate report now reads safe per-tool diagnostics. Its first live use exposed and fixed a MySQL header parser issue. A subsequent candidate exposed unsupported natural-language comparison dimensions; the planning schema, Java contract and comparison tool now share the same supported enum with deterministic fallback.

Final local results: focused orchestration/contract `70/70`, Spring `387` with one opt-in Provider skip, MySQL 8.4 `71/71`, Vitest `87/87`, Assistant `36/36`, eight frontend scripts, Python discovery `110` with seven skips, explicit MySQL `7/7`, deployment hardening `89/89`, Vite/JAR builds and repository checks.

The fourth deployment batch passed all scenarios together. Policy executed `search_policies` in 2 rounds with 7,503 Tokens, 19,955 ms and 2 citations. Comparison executed `search_cases, compare_cases` in 2 rounds with 10,281 Tokens, 34,003 ms and 2 citations. Source verification executed `search_policies, get_source` in 4 rounds with 18,544 Tokens, 65,468 ms and 2 citations. All used `deepseek-v4-flash`, finish reason `stop`, actual settlement and zero reservation.

Production switched exactly once. Current is `/opt/opc/releases/20260728-130142`; frontend/backend hashes are `30d859fe3cc6fe7c9cfeb0c0c2525e040802dda4b46ecf42b5e853989ba0bc3f` and `f2a789d429a33a18c3b9ec44cdbcd392808f70c7310dc3695572ea52719cd97b`. Backup, dump, rollback backend and previous release exist. Production policy, comparison and source probes repeated the required chains with legal citations and zero reservations. Candidate and temporary probe resources are zero.

Engineering exit criteria are met. Manual visual checks remain at `1366x768`, `1280x600`, `1024x768`, `390x844` and 375 px phone width and do not block Phase Two closure.
