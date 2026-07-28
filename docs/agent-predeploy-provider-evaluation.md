# Agent Predeploy Provider Evaluation

## Purpose

Production Agent rollout must prove compatibility with the configured real Provider before `/opt/opc/current` changes. A successful local Schema test is necessary but cannot prove that the configured model will stay inside the same planning and synthesis contract.

## Why previous deployment attempts failed late

Earlier probes ran only after release switching. Real responses exposed planning truncation, synthesis truncation, uncited facts, case/policy identifiers used as source identifiers, invalid output sections and array totals that passed one boundary but failed another. Rollback protected production, but the incompatibility was discovered later than necessary.

The deterministic replay suite catches known shapes locally. A real candidate is still required because response length, Schema adherence and source selection are model behavior rather than deterministic application behavior.

## Candidate sequence

1. Build the frontend and executable backend JAR locally.
2. Create a timestamped candidate release directory, upload artifacts and verify their hashes without changing `/opt/opc/current`.
3. Copy the current production database into a timestamped isolated candidate database without modifying production schema or rows.
4. Run the same migration prechecks, forward migrations and postchecks against the candidate database only.
5. Remove users, sessions, messages, runs, tool calls, Provider calls and administrator records from the candidate while retaining encrypted Provider configuration and published evidence.
6. Create a random short-lived candidate database account and a root-owned `/run` environment file.
7. Start the uploaded JAR as a transient `opc` systemd service on loopback port 18082 without changing `/opt/opc/current`.
8. Create an exact temporary administrator in the candidate and run the existing Provider connection test.
9. Create random temporary users and execute three `agent-research-v2` flows: policy lookup, dynamic search-to-case-comparison, and dynamic search-to-source-verification.
10. Validate every candidate record and clean candidate identities, data, runtime and database. Candidate failure ends here with zero production backup, migration, restart or switch.
11. Only after all candidate gates pass, create the production backup, run the exact hash-matched migration once, atomically switch the current symlink once, restart the backend and reload nginx.
12. Retain the previous release, timestamped database backup and backend rollback artifact, then run lightweight production health/authorization checks.

## Mandatory gate

The candidate must report the configured Provider and model, `agent-research-v2`, successful planning/continuation/synthesis rounds as required, bounded rounds/tools, legal current-run citations, no unknown citation, positive and internally consistent Token usage, a terminal settlement with no reservation, latency and finish reason. Completed runs must expose authorized evidence; `evidenceCoverage` case, policy and unique-source counts must match the sanitized evidence endpoint. A controlled `evidence_insufficient` result is accepted only for the policy scenario when server-derived coverage also says insufficient. Case comparison and source verification must each execute their dependent tool with an ID returned by an earlier search in the same Run.

The report contains Provider invocation status, rounds, tool count, Token totals, finish reason, latency and `release_switched=false`. It never contains prompts, questions, model output, tool arguments, secrets, cookies or private URLs.

## Failure and cleanup

Candidate failure never changes or restarts the live release. The original controlled diagnostic remains the primary error. Cleanup failures add only fixed notes and cannot replace or reveal the original error. Candidate identities use exact random names; no prefix deletion or production account is used.

The candidate database currently requires server-local `mysql -uroot` socket administration. If unavailable, deployment fails before release switching. This isolation requirement is not bypassed.

## Current result

The Phase 27 mandatory isolated candidate and rollout remain historical facts: release `/opt/opc/releases/20260726-213258` is live, with backup `/opt/opc/backups/20260726-213258`, database dump `/opt/opc/backups/20260726-213258/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260726-213258`, and previous release `/opt/opc/releases/20260726-162930` retained.

Phase 28 deployment tests pass `77/77` and prove that a candidate failure calls production migration and current-release switching zero times, while a successful path uses one production migration and one switch with the same migration hash. Evidence-insufficient failures now retain the same sanitized scalar metrics as other candidate diagnostics.

Three bounded real Provider candidate attempts were executed. Attempt 1 rejected `INVALID_DEPENDENCIES` (`2` rounds, `2` tools, `8,440` Tokens, `19,697 ms`). Attempt 2 rejected `UNCITED_FACT` (`2` rounds, `2` tools, `8,557` Tokens, `17,596 ms`). Both reported `release_switched=false` and led to deterministic bounded-recovery fixes.

Attempt 3 passed the policy scenario but returned controlled `evidence_insufficient` for case comparison, so it did not prove dynamic `search_cases -> compare_cases` and source verification was not run. The older insufficient branch did not retain its scalar metrics; that reporting gap is fixed and tested but was not used to justify another candidate retry. No Phase 28 production backup, migration, restart or switch occurred. Final preflight confirmed the current release remains `/opt/opc/releases/20260726-213258` with unchanged hashes, active services, one loopback-only backend and restored temporary identity counts.

## 2026-07-27 candidate continuation

The next bounded batches used the same isolated candidate order and never reached production mutation. Under the 24,000-Token budget, policy lookup passed (3 rounds, 1 tool, 11,488 Tokens, 2 citations, 30,427 ms), source verification passed (3 rounds, 3 tools, 15,264 Tokens, 2 citations, 49,436 ms), and comparison failed `AGENT_TOKEN_LIMIT` after 4 rounds, 3 tools and 25,422 actual Tokens. This measured result justified the idempotent five-round/28,000-Token settings migration rather than an unbounded increase.

With that budget, a later batch produced `UNKNOWN_FIELDS` for policy, `TRUNCATED_RESPONSE` for comparison and a source-verification pass (4 rounds, 3 tools, 23,524 Tokens, 2 citations). Bounded initial/continuation planning recovery then exposed `AGENT_TOKEN_LIMIT`, `UNCITED_RECOMMENDATION` and `INVALID_RESEARCH_QUESTIONS` in the next batch. All returned usage settled once, reservations reached zero and `release_switched=false` remained recorded.

The terminal-only continuation build passed local gates, but its candidate stopped at `PROVIDER_CONNECTION_FAILED` before the three scenarios. Cleanup completed and production backup, migration, restart and switching remained zero. This is a real unpassed Provider gate, not a successful candidate result. The next isolated run should occur only after connectivity is plausibly restored; all three scenarios must still run independently and be aggregated before rollout.

## 2026-07-27 final single-deploy preparation

The formal deploy started at 07:08 and created candidate `/opt/opc/releases/20260727-070820` without switching production. Policy lookup completed in four model rounds with `search_policies, search_cases`, two legal citations, 20,747 Tokens, `settled_actual` and zero reservation. Case comparison failed `CANDIDATE_CASE_COMPARISON_TOOL_SEQUENCE_INVALID`; source verification independently failed `CANDIDATE_SOURCE_VERIFICATION_TOOL_SEQUENCE_INVALID`. Production remained `/opt/opc/releases/20260726-213258`, and the transient service/database resources were removed.

The root cause was server terminal logic trusting the model-planned intent even when the user's request clearly required a stronger tool chain. The final build adds server-owned execution requirements, optional explicit `requestedIntent` for candidate and Starter requests, and normalized `resolvedIntent`. Model intent can add requirements but cannot lower them.

Probe records are now populated before any sequence gate with `expected_tools`, `actual_tool_sequence`, `missing_tools`, `execution_requirements`, safe model/resolved intent, terminal status, rounds, tool count, citations, Token totals, finish reason, request ID, settlement and reservation. All three scenarios execute independently and aggregate their sanitized records. A failed command removes only its own exact unswitched timestamp release after protecting current and previous releases; the historic `20260727-070820` release is not retroactively removed.

Local gates passed with Spring `373`, MySQL `71/71`, Vitest `87/87`, Assistant `36/36`, all eight frontend scripts, Python default `106`, explicit MySQL `7/7`, deployment hardening `85/85`, Vite/JAR builds and repository checks.

Remote preflight passed, then the one permitted deploy ran all three scenarios. Policy failed `REQUIRED_TOOL_CHAIN_UNSATISFIED` after 3 rounds, 2 completed tools, 15,679 Tokens and 41,041 ms; settlement was actual and reservation zero. The pre-fix reporter checked terminal failure before querying the tool names, so this run cannot truthfully provide the policy sequence. Comparison passed `search_cases, search_policies, compare_cases` with 4 rounds, 3 tools, 6 citations, 24,165 Tokens and 68,165 ms. Source verification passed `search_policies, get_source, get_source` with 4 rounds, 3 tools, 2 citations, 18,257 Tokens and 45,079 ms. Both dependent scenarios used current-Run authorized IDs, settled actual usage and released reservation.

The candidate failure kept `release_switched=false`. Post-failure verification found no candidate service, database/user, environment file or current-attempt release, and no new production backup or rollback artifact. Production remains `/opt/opc/releases/20260726-213258`. A red-to-green deployment test moved sequence collection before terminal validation; deployment hardening now passes `86/86` and Python discovery `107` with seven skips. Per the single-deploy rule, this reporting fix was not deployed and Phase Two remains open.

## 2026-07-28 bounded candidate evaluation

The explicit-intent priority fix passed every local gate before remote execution. Candidate accounting was one initial batch, one same-build retry for a transient Provider connection failure, and two code-corrected retries. No batch changed production before all three scenarios were green.

The final corrective batch used `deepseek` / `deepseek-v4-flash` and `agent-research-v2`:

- Policy: completed; expected `search_policies`; actual `search_policies, search_policies, search_cases`; 2 rounds, 3 completed tools, 2 legal citations, 8,765 Tokens, 25,588 ms, actual settlement and zero reservation.
- Case comparison: failed `CANDIDATE_CASE_COMPARISON_EVIDENCE_INSUFFICIENT`; expected `search_cases, compare_cases`; actual `search_cases, search_policies`; missing `compare_cases`; 3 rounds, 2 completed tools, 0 citations, 16,362 Tokens, 37,852 ms, actual settlement and zero reservation.
- Source verification: completed; expected `search_policies, get_source`; actual `search_policies, get_source`; 4 rounds, 2 completed tools, 2 legal citations, 17,218 Tokens, 42,430 ms, actual settlement and zero reservation.

The aggregate gate recorded `release_switched=false`. The comparison diagnostic repeated after the narrow correction and exhausted the two corrective retries, so deployment stopped without lowering the gate. Cleanup verification found zero candidate services, databases/users, environment files, port 18082 listeners and current-attempt release directories. No new production backup, dump or rollback path exists; the existing `20260726-213258` production artifacts remain authoritative.
