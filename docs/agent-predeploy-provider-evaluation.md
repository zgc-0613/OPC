# Agent Predeploy Provider Evaluation

## Purpose

Production Agent rollout must prove compatibility with the configured real Provider before `/opt/opc/current` changes. A successful local Schema test is necessary but cannot prove that the configured model will stay inside the same planning and synthesis contract.

## Why previous deployment attempts failed late

Earlier probes ran only after release switching. Real responses exposed planning truncation, synthesis truncation, uncited facts, case/policy identifiers used as source identifiers, invalid output sections and array totals that passed one boundary but failed another. Rollback protected production, but the incompatibility was discovered later than necessary.

The deterministic replay suite catches known shapes locally. A real candidate is still required because response length, Schema adherence and source selection are model behavior rather than deterministic application behavior.

## Candidate sequence

1. Build the frontend and executable backend JAR locally.
2. Create a timestamped release and backup, upload artifacts and verify hashes.
3. Run migration prechecks, forward migrations and postchecks.
4. Copy the migrated production database into a timestamped candidate database.
5. Remove users, sessions, messages, runs, tool calls, Provider calls and administrator records from the candidate while retaining Provider configuration and published evidence.
6. Create a random short-lived candidate database account and a root-owned `/run` environment file.
7. Start the uploaded JAR as a transient `opc` systemd service on loopback port 18082 without changing `/opt/opc/current`.
8. Create an exact temporary administrator in the candidate and run the existing Provider connection test.
9. Create a random temporary user and execute the full `agent-research-v2` start, poll, evidence and settlement flow.
10. Validate the candidate record. Only then may deployment switch the current symlink, restart the production backend and reload nginx.
11. In `finally`, remove temporary identities/data, stop the transient unit, delete the environment file, drop the database user and drop the candidate database.

## Mandatory gate

The candidate must report the configured Provider and model, `agent-research-v2`, a successful planning round and synthesis round, bounded rounds/tools, legal current-run citations, no unknown citation, positive and internally consistent Token usage, a terminal settlement with no reservation, latency and finish reason. Completed runs must expose authorized case and policy evidence; `evidenceCoverage` case, policy and unique-source counts must match the sanitized evidence endpoint. A controlled `evidence_insufficient` result is accepted only when server-derived coverage also says insufficient.

The report contains Provider invocation status, rounds, tool count, Token totals, finish reason, latency and `release_switched=false`. It never contains prompts, questions, model output, tool arguments, secrets, cookies or private URLs.

## Failure and cleanup

Candidate failure never changes or restarts the live release. The original controlled diagnostic remains the primary error. Cleanup failures add only fixed notes and cannot replace or reveal the original error. Candidate identities use exact random names; no prefix deletion or production account is used.

The candidate database currently requires server-local `mysql -uroot` socket administration. If unavailable, deployment fails before release switching. This isolation requirement is not bypassed.

## Current result

Local deployment and migration tests pass `83/83`, with the explicit MySQL migration suite at `7/7`. The mandatory isolated candidate completed on `deepseek-v4-flash` and `agent-research-v2` before rollout: 2 model/Provider rounds, 3 completed tools, 4 legal citations, zero unknown citations, 5 case, 2 policy and 4 unique-source evidence items, server-derived `sufficient` coverage, actual settlement, zero reservation and `release_switched=false`.

Only after that result did deployment switch `/opt/opc/current` to `/opt/opc/releases/20260726-213258`. Backup `/opt/opc/backups/20260726-213258`, database dump `/opt/opc/backups/20260726-213258/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260726-213258`, and previous release `/opt/opc/releases/20260726-162930` are retained. Independent preflight confirmed matching hashes, active services, valid nginx configuration and one loopback-only backend process.
