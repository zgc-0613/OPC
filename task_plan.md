# Task Plan: Apply the Prisma visual system to SoloFirm

## Goal
Preserve every existing SoloFirm core function, name, icon, route, and content contract while rebuilding the frontend user page with the supplied Prisma visual language and interactions, then verify the result in real browsers.

## Current Phase
Phase 33 (Phase Three readiness, metrics, product and API specification complete)

## Phases

### Phase 1: Baseline, Requirements, and Product Context
- [x] Load the user-requested skills and supplied Prisma specification
- [x] Inventory the repository, active user-facing entry points, assets, dependencies, and local instructions
- [x] Map all current user-visible content, routes, controls, API calls, and other public behavior that must remain intact
- [x] Run the current build/tests and capture a functional baseline
- [x] Complete Impeccable product/design context from repository evidence
- **Status:** complete

### Phase 2: Architecture and Visual Mapping
- [x] Decide whether Vue migration improves the result without expanding regression risk
- [x] Map current SoloFirm content and actions onto the Prisma Hero, About, and Features composition
- [x] Define responsive behavior, motion fallbacks, external-media handling, and accessibility constraints
- [x] Record file-level implementation scope and regression seams
- **Status:** complete

### Phase 3: Implementation
- [x] Add or adjust the frontend toolchain only where required
- [x] Implement the Prisma typography, palette, texture, video, layout, and reusable reveal components
- [x] Preserve the SoloFirm name, icon assets, existing content, links, forms, routes, and backend integrations
- [x] Implement responsive and reduced-motion behavior
- [x] Convert the public frontend to the user-requested light palette
- [x] Restyle the analytics overview with the same four/two/one-column feature-card system
- [x] Replace rounded geometric typography with Songti/Kaiti-led sharp serif typography
- [x] Preserve atmosphere through paper grain, ink contrast, cinematic media, and restrained multi-hue accents
- [x] Apply Bookman Old Style-led English display typography with Songti/Kaiti Chinese pairings
- [x] Unify public and admin buttons, fields, selects, chips, pagination, tables, navigation, login controls, empty states, and metadata under the Prisma component system
- [x] Keep background media scoped to the Hero frame and login media panels; use paper/tonal surfaces elsewhere
- [x] Reset legacy gradient text, white text, and shadows; prove readable contrast in browser screenshots
- [x] Apply the newly supplied Prisma specification as the base while retaining all explicit user overrides
- [x] Keep the inner Hero video autoplaying; use an optimized 5.5s Hero still only on the outer Hero background; leave lower sections white and other videos unchanged
- [x] Remove only the decorative About `AI + OPC` kicker; preserve every functional block and action
- [x] Simplify all public and admin button colors to one primary ink style and one neutral secondary style
- [x] Enforce grayscale buttons globally: black primary, white/gray secondary, transparent tertiary
- [x] Rework capability pills and analytics cards for WCAG-level contrast
- [x] Apply one Prisma token/state layer to every public and admin route; remove visible legacy blue gradients, glows, and mismatched variants
- **Status:** complete

### Phase 4: Verification and Visual QA
- [x] Run existing automated tests, type checks, lint, and production build as available
- [x] Exercise mapped public and admin behavior and integration seams
- [x] Inspect desktop, tablet, and mobile layouts in a real browser
- [x] Check animation, overflow, media loading, console errors, accessibility, and regression risks
- [x] Fix all issues found and repeat verification
- **Status:** complete

### Phase 5: Completion Audit and Delivery
- [x] Audit every explicit user and Prisma requirement against authoritative evidence
- [x] Review the final diff for accidental functional, brand, icon, or content changes
- [x] Document run instructions, verification results, and external service caveats
- [x] Start the local development server and provide the URL
- **Status:** complete

### Phase 6: Production Server Deployment
- [x] Inventory the existing server deployment, reverse proxy, services, database, and application directories without mutating them
- [x] Build and checksum the current frontend and backend production artifacts
- [x] Create timestamped remote backups and identify an explicit rollback path
- [x] Apply only required database migrations without replacing existing business data
- [x] Deploy the frontend and backend using the server's established process layout
- [x] Restart/reload only the affected services and verify health, logs, API routing, and administrator authentication
- [x] Browser-check the public IP across home, user login, and administrator login/workspace
- [x] Record the final remote paths, service names, deployment result, and remaining infrastructure risks
- **Status:** complete

### Phase 7: Post-deployment UI Spacing and Login Copyright
- [x] Reproduce the production recent-update card collision and identify the winning legacy selectors
- [x] Add scoped card gaps, horizontal padding, and mobile spacing without changing update data
- [x] Add page-bottom `Copyright © 2026 SoloFirm® - All rights reserved` treatment to both login pages
- [x] Keep login/register card geometry stable by reserving the username field slot while disabling it in login mode
- [x] Build and browser-check desktop/mobile layouts
- [x] Deploy the frontend-only hotfix with a new rollback directory and verify production hashes/console
- **Status:** complete

### Phase 8: Administrator Subdomain
- [x] Check the authoritative DNS state and current TLS certificate scope
- [x] Determine that Cloudflare orange-cloud proxying is optional, not required for deployment
- [x] Add and propagate `admin.findopc.online` DNS to `39.105.25.189` using the Huawei DNS direct A record
- [x] Add the administrator-only Nginx virtual host and obtain a valid TLS certificate
- [x] Move administrator entry/navigation to the subdomain while retaining public-site return links
- [x] Browser-check HTTPS routing, authentication, API access, direct route refreshes, and main-domain redirects
- **Status:** complete

### Phase 9: ALTCHA Registration Protection and Settings
- [x] Remove the obsolete `MVP 管理模式` label from the administrator settings page
- [x] Add persisted ALTCHA enablement, cost, and lifetime settings to the existing administrator settings area
- [x] Add signed ALTCHA v2 challenge generation, purpose binding, expiry checks, proof verification, and replay rejection
- [x] Require ALTCHA only before sending registration email codes while leaving login unchanged
- [x] Add focused backend tests and build the frontend with the official ALTCHA widget loaded only on registration
- [x] Deploy the backend secret through the protected environment file and apply only additive database settings
- [x] Browser-check desktop and 320x844 settings/registration layouts, console state, and exact login/register card equality
- [x] Deploy the final checksum-verified frontend release and audit Nginx, TLS, MySQL, backend health, and rollback paths
- **Status:** complete

### Phase 10: Password Registration and Login
- [x] Replace email-code login with username-or-email plus password login
- [x] Keep email verification and ALTCHA only on registration-code delivery
- [x] Store passwords only as BCrypt hashes and use generic invalid-credential errors
- [x] Add a nullable password hash and unique username index through an idempotent migration
- [x] Allow legacy email-code accounts to set a password through the registration flow without losing identity or data
- [x] Show password configuration state in administrator account management
- [x] Keep desktop/mobile login and registration cards exactly equal while exposing only relevant fields
- [x] Add service regression tests for password login, hashed registration, registration-only codes, invalid passwords, and legacy upgrades
- [x] Back up, migrate, deploy, browser-check, and run a cleaned-up production password-login smoke account
- **Status:** complete

### Phase 11: Administrator Login Description Alignment
- [x] Remove the inherited 360px paragraph width cap on the administrator login media panel
- [x] Keep the full desktop administrator description on one line
- [x] Use an equivalent compact single-line description on narrow screens without reducing readability
- [x] Build, browser-check desktop/mobile, and deploy the checksum-verified frontend-only release
- **Status:** complete

## Key Questions
1. Which Vue files/routes make up the user-facing page and its public behavior?
2. Which current actions, navigation targets, forms, storage behavior, and backend calls define the core-function baseline?
3. Does a Vue migration preserve those contracts more reliably than a scoped redesign in the current stack?
4. Which supplied Prisma assets are reliable at runtime, and what non-disruptive fallbacks are needed?
5. Does the repository fully establish users, purpose, brand personality, anti-references, and accessibility needs for Impeccable PRODUCT.md?

## Decisions Made
| Decision | Rationale |
|----------|-----------|
| Treat preservation of all existing behavior as the highest implementation constraint | The user explicitly emphasized that every core function must remain unchanged. |
| Treat Vue migration as optional until the current architecture is known | The user allowed an upgrade but did not require it; unnecessary migration would increase regression risk. |
| Keep the existing Vue 3 + Vite stack | The repository is already upgraded; migrating again would add no user value. |
| Use the Prisma prompt as the visual and motion specification, not as replacement page content | The user wants Prisma presentation with SoloFirm's existing content, name, and icon. |
| Verify through existing public interfaces before adding new tests | New TDD seams require confirmation; the current test suite and browser-visible behavior can establish the first baseline without inventing contracts. |

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
| PowerShell parsed `$name:` as an invalid scoped variable while checking planning files | 1 | Retried with `${name}` interpolation and confirmed all three files were initially absent. |
| Planning update patch referenced a task-plan line while editing findings.md | 1 | Split the update by target file and reapplied with valid context. |
| `npx --package @playwright/cli playwright-cli --help` produced no output and timed out after 124 seconds | 1 | Terminated the command and switched to the configured in-app browser automation surface. |
| Phase-transition patch used an overly broad multi-file context and failed verification | 1 | Read the current plan header and reapplied the update with smaller, exact hunks. |
| `lucide-vue-next@0.468.0` installed with a deprecation warning | 1 | Removed it and followed the package guidance to use `@lucide/vue`. |
| `@lucide/vue@^0.468.0` did not exist in the registry | 1 | Queried the registry, found current version 1.25.0, and installed `@lucide/vue@^1.25.0` successfully. |
| Initial large HomeView template patch failed context verification | 1 | Inspected the exact current file and split the replacement into smaller Hero, About/Features, and section-closing patches. |
| Baseline in-app browser tab was no longer present after implementation | 1 | Reused the existing browser binding and created a fresh localhost tab as required by the browser workflow. |
| `@lucide/vue` renders in SSR but throws from `useLucideProps()` in the Vite client | 1 | Established a browser-console reproduction and began dependency/provider/version diagnosis before resuming visual QA. |
| `@lucide/vue` 1.25.0 calls Vue `inject()` from its functional render body and crashes the client | 2 | Ruled out duplicate Vue copies and selected the older stable `lucide-vue-next` implementation for a one-variable verification. |
| Write access to the C-drive project was temporarily rejected after an environment permission change | 1 | User restored Full Access; no rejected patch content was applied, and work resumes directly in the requested repository. |
| Combined validation command ran `npm run build` from the repository root where no package.json exists | 1 | JSON validation passed; reran the build from `opc-frontend` and it succeeded. |
| The configured Playwright shell wrapper invoked an outdated WSL installation | 1 | Used the same Playwright CLI through its native Windows `npx` entry and completed the browser audit. |
| SSH password authentication was attempted against the initially supplied IP and rejected | 1 | User corrected the production IP to `39.105.25.189`; discard all conclusions about the first host and restart read-only inventory on the corrected target. |
| Initial production `mysqldump` reported missing MySQL `PROCESS` privilege while the gzip pipeline masked the failure | 1 | Do not trust the first dump; rerun with `--no-tablespaces`, `set -o pipefail`, a temporary output, and `gzip -t` before deployment. |
| In-app browser backend rejected the documented `networkidle` load state | 1 | Use the supported `load` state plus explicit DOM, media-ready, HTTP, and console checks. |
| First homepage metric probe referenced an obsolete CSS class that was absent in the deployed DOM | 1 | Use the current `h1` and null-guarded selectors from the fresh DOM snapshot; the page itself had no runtime error. |
| A combined username-slot patch included task-plan context in a CSS hunk, then a planning update used stale multi-file context | 1 | Neither failed patch changed files; reapplied the Vue/CSS fix and planning updates using smaller file-local hunks. |
| Paramiko hash comparison treated remote SHA-256 output as bytes and local hashes as strings | 1 | Confirmed every digest was textually identical, decoded remote output explicitly, and kept production unchanged until verification passed. |
| The first frontend cutover gate required anonymous `/api/policies`, which returns the same 403 on the old deployment | 1 | Automatic rollback restored the old frontend; verified the 403 was pre-existing, then used static routes/assets plus backend service health as the frontend-only gate and redeployed successfully. |
| Phase 13 planning update used a stale findings heading in a multi-file patch | 1 | No file changed; inspected each current file tail and reapplied exact file-local additions. |
| Initial export-auth `@WebMvcTest` loaded the application's global MyBatis mapper scan and failed before reaching the HTTP assertion | 1 | Replaced the polluted Boot slice with a minimal `@EnableWebMvc` test context containing only the controller, interceptor, MVC config, exception handler, and mocked services. |
| The first public-export test matcher could not infer MyBatis `Wrapper<Policy>` as `AbstractWrapper` | 1 | Kept the production-facing assertion and moved the concrete wrapper cast inside the typed Mockito predicate. |
| The public-export wrapper predicate inspected MyBatis parameters before lazy SQL-segment rendering | 2 | Reused the repository's established `Wrapper<Policy>` helper pattern and called `getSqlSegment()` before checking bound values. |
| The isolated export service test had no MyBatis-Plus Lambda metadata cache for `Policy` | 3 | Initialized `Policy` table metadata with `TableInfoHelper` in the test, matching what the Spring/MyBatis runtime normally provides. |
| Mock servlet response appended `charset=UTF-8` to the Excel MIME type | 1 | Asserted the authoritative Excel MIME prefix while retaining workbook content and row-count assertions. |
| PowerShell parsed the comma-separated Maven `-Dtest` selector as an expression | 1 | Quoted the complete `-Dtest=...` argument before rerunning the targeted test set. |
| `@RequestAttribute` rejected a runtime-computed interceptor attribute name | 1 | Replaced `Class.getName()` concatenation with the stable compile-time key `opc.authenticatedUser`. |

## Login Alignment Follow-up (2026-07-18)
- [x] Remove `SOLOFIRM ACCOUNT`, `Policy / Case / Insight`, and `USER ACCESS` from the user login page.
- [x] Remove `ADMIN WORKSPACE`, `Manage / Review / Export`, and `ADMIN ACCESS` from the administrator login page.
- [x] Reuse the same SoloFirm brand mark, media treatment, left display font, form surfaces, and footer alignment on both login pages.
- [x] Keep user login and registration visually distinct while hiding the administrator route from public login navigation.
- [x] Make login, registration, and administrator headings continuously scale with `clamp()` while remaining on one line.
- [x] Verify both pages at 1440x900, 768x1024, and 320x844 in a real browser with no horizontal overflow or console warnings.
- [x] Re-run the frontend production build and backend test suite.
- **Status:** complete

## Notes
- Re-read this plan before architecture or implementation decisions.
- Update findings.md after every two repository or browser inspection operations.
- Do not remove or rename existing brand assets, public content, routes, actions, or integrations.
- Do not mark completion until the requirement-by-requirement audit is fully evidenced.

### Phase 12: Account Deletion And Analytics/Contact Follow-up
- [x] Add public-user deletion with session cleanup.
- [x] Add administrator deletion with self-delete and last-active-admin protection.
- [x] Separate user row actions and add administrator deletion controls.
- [x] Normalize homepage and administrator trend surfaces.
- [x] Add quarter, half-year, year, and all-time policy trend ranges with point details.
- [x] Remove the About frame and unify the contact typography/information layout.
- [x] Fix ALTCHA width, checkbox geometry, proof-reset semantics, and final registration submission.
- [x] Build, package, deploy, preserve rollback artifacts, and remove temporary production QA data.
- **Status:** complete

### Phase 13: Trusted Data And AI Platform Foundation
- [x] Initialize the repository CodeGraph structural index
- [x] Confirm the generated `.codegraph/` directory is ignored by Git
- [x] Freeze the actual deployed baseline and current API/status matrix
- [x] Close the anonymous policy-export authorization gap with a regression test
- [x] Define the provider-neutral AI boundary, evidence contract, user-auth seam, and failure modes
- [x] Add the first disabled/fake provider foundation without exposing provider secrets to Vue
- [x] Add the authenticated `/api/ai/capabilities` readiness endpoint
- [x] Run the full backend tests and frontend production build
- [x] Deploy the completed foundation with timestamped backup and rollback artifacts
- **Status:** complete

## Phase 13 Decisions
| Decision | Rationale |
|----------|-----------|
| Deliver one visible `SoloFirm 研究助手` with capability modes | The product should feel like one coherent research tool, not several unrelated bots. |
| Make evidence-aware case analysis the first AI vertical slice | Current published case/source data can support a manually verified golden set; broad chat and revenue analytics cannot yet make trustworthy claims. |
| Keep deterministic analytics and scoring outside the model | Counts, rankings, coverage, and technical scores must be reproducible and testable. |
| Put all model access behind a backend `AiClient`/provider adapter | Vue must never contain provider keys, Bot IDs, or provider-specific request contracts. |
| Treat ingestion as a governed pipeline rather than a public chat agent | Raw artifacts, extraction candidates, human review, revisions, and publication require explicit state and auditability. |

### Phase 14: Real Case Analysis Vertical Slice
- [x] Add database-backed, audited AI model settings with AES-GCM provider-key encryption.
- [x] Add a managed OpenAI-compatible provider with timeout, bounded retries, usage metadata, request IDs, and safe failures.
- [x] Keep fake provider behind an explicit test-only gate and preserve the disabled provider.
- [x] Add published-and-verified evidence eligibility for cases, policies, and sources.
- [x] Add authenticated, quota-limited, concurrency-protected `POST /api/ai/case-analysis`.
- [x] Validate structured model output and citations against backend-loaded source records.
- [x] Add the Prisma administrator model-settings tab and standalone protected case-analysis page.
- [x] Pass 64 backend tests, frontend production build, CodeGraph sync, and `git diff --check`.
- [x] Deploy with timestamped backup/rollback artifacts and verify the disabled production state.
- **Status:** complete

### Phase 15: Production Reverse Proxy And Service Hardening
- [x] Make the Spring listener address environment-configurable and bind production to loopback.
- [x] Add a bounded, rate-limited exact Nginx proxy for `POST /api/ai/case-analysis` on both hosts.
- [x] Run the backend under the no-login `opc` system account with systemd sandbox restrictions.
- [x] Protect runtime configuration with `root:opc` ownership and mode `0640`.
- [x] Extend deployment backups, checksum checks, unit installation, rollback, and runtime audits.
- [x] Add deployment regression coverage for wildcard, native loopback, and IPv4-mapped IPv6 listeners.
- [x] Pass backend tests, frontend production build, deployment tests, and `git diff --check`.
- [x] Deploy release `20260724-030722` and independently verify Nginx, auth, rate limiting, and external port closure.
- **Status:** complete

### Phase 16: Standalone Entrepreneurship Research Assistant
- [x] Preserve `/cases/:id/analysis` as the single-case analysis workflow.
- [x] Add a separate login-protected `/assistant` route without adding a homepage or sidebar entry.
- [x] Add bounded entrepreneurship profile input for venture type, region, industry, stage, budget, goal, resources, and an optional question.
- [x] Add authenticated `POST /api/ai/entrepreneurship-advice` behind the existing provider-neutral `AiClient`.
- [x] Load only published and explicitly verified local cases, policies, and sources on the backend.
- [x] Rank local evidence by the selected region and industry while keeping database matches separate from model-generated advice.
- [x] Validate model citations against backend-assigned source IDs, persist run metadata, and reuse daily quota/concurrency controls.
- [x] Build the standalone page as a responsive SoloFirm Prisma Light research workspace with profile, transcript, evidence, failure, retry, and provider-disabled states.
- [x] Add exact bounded Nginx proxy coverage and an additive migration for multi-task AI run records.
- [x] Pass the full backend test suite, frontend production build, deployment-hardening tests, and local diff checks.
- [x] Deploy after the user confirms concurrent frontend copy changes can wait, then verify both hosts, auth boundaries, migration columns, and service health.
- **Status:** complete and deployed in disabled-provider mode.

### Phase 17: AI Evidence Stabilization
- [x] Reproduce and cover the provider-not-called/evidence-insufficient workflow through public API test seams.
- [x] Centralize quota reservation, active-run recovery, provider execution, and token settlement for both AI workflows.
- [x] Bind evidence hashes to source content/version and reject factual output without legal citations.
- [x] Add the audited administrator evidence-review queue with verified source-chain enforcement.
- [x] Require authenticated user context for readiness and correct deployment validation for complete enabled provider configurations.
- [x] Run the full backend suite, frontend build, auth-session check, deployment-hardening tests, and diff check.
- [x] Deploy release `20260724-074745` with backups and rollback artifacts; verify public/admin health and protected API boundaries.
- [ ] Build a 10-20 item golden evidence set from human-reviewed production records. This is intentionally pending because no records were fabricated or auto-verified.
- **Status:** implementation and deployment complete; human evidence-review backlog remains.

### Phase 18: First-Round AI Stabilization Fixes
- [x] Prevent fuzzy and low-confidence AI industry suggestions from becoming a final tag without explicit user confirmation.
- [x] Keep debounced readiness deterministic and free of provider calls; route all paid classification through `AiTaskExecutionService` with server-side abuse controls.
- [x] Reserve conservative prompt-token estimates plus maximum output tokens, then settle actual or conservative usage on every terminal path.
- [x] Classify direct, broader, in-province, national/unknown, and cross-region evidence using a cycle-safe multi-level region tree.
- [x] Replace binary readiness with `sufficient`, `partial`, and `insufficient`, where sources support evidence rather than form a third mandatory content type.
- [x] Normalize source publishing on `published`, reject illegal states, migrate historic `active` rows idempotently, and preserve public/AI visibility.
- [x] Include reliable policy-side industry tags while producing a manual-review list for ambiguous non-industry tags.
- [x] Make every stabilization migration column/index/backfill independently repeatable and add pre/post verification SQL.
- [x] Require the AI connection test to parse a complete fixed JSON acknowledgement and reject empty, malformed, incorrect, timed-out, or redirected responses.
- [x] Ignore stale frontend readiness responses and remove avoidable full-scan pagination paths within this phase's scope.
- [x] Run the two-axis code review, full backend/frontend verification, deployment-hardening checks, backup/migration/deploy, and production API smoke tests.
- [x] Produce the real `湖北省 + 人工智能应用` evidence-review checklist without promoting unreviewed production data.
- **Status:** complete and deployed; production golden evidence remains an explicit human-review task.

### Phase 19: Integrated Evidence Review Workbench
- [x] Close ordinary case, policy, and source DTO/form paths that previously accepted AI evidence status.
- [x] Centralize single, batch, automatic invalidation, dependency invalidation, reason, administrator, and operation audit handling.
- [x] Add independent evidence revisions, atomic optimistic transitions, source-joined verification, cascade guards, and governed deletion rules.
- [x] Add lightweight queue filtering plus on-demand detail, checks, related records, safe original links, history, and batch preflight APIs.
- [x] Replace the status table with a Prisma Light queue/detail workbench, URL-restored context, in-place editor, approve-next, and mobile flow.
- [x] Add an independently guarded evidence-workbench migration and deployment smoke checks without auto-verifying production data.
- [x] Pass correctness/security review, 140 backend tests, five frontend test scripts, 14 migration/deployment tests, production build, and diff checks.
- [x] Deploy the timestamped release and record production evidence counts and rollback paths.
- **Status:** complete and deployed; one historic verified case has no verified source chain and remains a human-review item.

### Phase 20: Audited Multi-Round Agent Runtime
- [x] Finish the phase-one case-analysis response-validation, evidence-insufficient audit, tag-conflict, and policy-batch safeguards through focused red-green slices.
- [x] Extend the shared AI audit ledger and add owned sessions, ordered messages, and bounded tool-call persistence with idempotent MySQL migrations.
- [x] Add provider-neutral model turns/tool calls plus a strictly validated fallback tool-plan contract when the configured model lacks native tool calls.
- [x] Implement the read-only `search_cases`, `search_policies`, `get_source`, and `compare_cases` registry with verified-evidence and current-run authorization boundaries.
- [x] Implement the bounded Agent state machine, aggregate token quota, single-active-run controls, cancellation, expiry, evidence conflict checks, and retry/idempotency behavior.
- [x] Add authenticated asynchronous research-session APIs and administrator-safe run audit APIs.
- [x] Upgrade the existing `/assistant` Prisma Light workspace and administrator settings surface with persistent sessions, polling recovery, progress, cancellation, retry, citations, and run records.
- [x] Add a deterministic 20-question golden evaluation set and complete unit, MySQL Testcontainers, frontend, migration, deployment, and build verification.
- [x] Deploy through the repository rollback workflow and pass the semantic production Agent probe before declaring completion.
- **Status:** complete and deployed in release `20260725-215634`; the real Agent probe completed with legal evidence and zero unknown citations.

### Phase 21: Agent Runtime Stabilization, Trusted Evaluation, And Production Gate
- [x] Fix named-index postcheck accounting and add the forward-only stabilization migration.
- [x] Make Agent rollout explicit, audited, default-off, and independent from provider enablement.
- [x] Establish one closed tool metadata source for native calls, JSON plans, schemas, runtime validation, audit, and tests.
- [x] Replace executor-only dispatch with leased database recovery, heartbeat renewal, bounded attempts, and terminal-state protection.
- [x] Add provider-call settlement records for actual, estimated, released, and late-usage reconciliation without reopening cancelled runs.
- [x] Lock sessions before submit/archive/cancel and persist structured clarification context that resolves real region/tag IDs.
- [x] Replace keyword-driven evaluation with 20 deterministic fixtures and keep real DeepSeek smoke metrics separate.
- [x] Harden the deployment probe for provider metadata, positive token accounting, tool evidence snapshots, permissions, and secret redaction.
- [x] Remove the remaining heavy Agent UI rails/shadows while preserving Prisma Light structure and interactions.
- [x] Pass 270 backend tests, including 48 MySQL 8.4 integration tests; 14 Vitest tests; all frontend contract scripts; 17 Python deployment tests; both production builds; diff, secret, artifact, and ignore checks.
- [x] Run the repository deployment workflow, database postcheck, dual-domain checks, and real DeepSeek Agent probe.
- **Status:** complete and deployed in release `20260725-215634`; backup and rollback artifacts are retained.

### Phase 22: Assistant Research Workspace And Server History
- [x] Add forward-only, repeatable Assistant Workspace precheck/migration/postcheck SQL without rewriting prior migrations.
- [x] Add stable scoped history search, strict cursors, message pagination, automatic/manual titles, usage projection, and explicit session lifecycle APIs.
- [x] Add archive, unarchive, trash, restore, permanent content purge, and a bounded multi-instance-safe purge scheduler.
- [x] Preserve minimal run/token audit while scrubbing session profiles, research context, messages, citations, tool arguments/results, evidence snapshots, and run result content.
- [x] Replace the single-page Assistant form with a persistent Prisma Light research desk, independent history sidebar, mobile drawer, safe Markdown, citations/process drawer, per-session drafts, and resilient polling.
- [x] Delay server session creation until the first user message and keep a created session visible/recoverable if message submission fails.
- [x] Add Vue behavior, Markdown XSS, utility, service, controller, MySQL 8.4 migration/query/purge, and deployment-order tests.
- [x] Extend deployment upload/checksum/migration gates and the temporary production Agent probe for history, pagination, latest run, usage, title, pin, archive, trash, and restore.
- [x] Pass 287 backend tests including 52 MySQL 8.4 tests, 22 Vitest tests, all 8 frontend scripts, 26 Python tests, both production builds, and repository diff/ignore/artifact/secret checks.
- [x] Deploy the combined release and pass the expanded real DeepSeek production probe.
- **Status:** complete and deployed in release `20260725-215634`; expanded Assistant history/lifecycle and Agent evidence probes passed.

### Phase 23: Assistant Workspace Stability, Concurrency, Purge, And Responsive Completion
- [x] Replace split first submission with request-bound atomic `/sessions/start` and durable replay semantics.
- [x] Add signed, user/scope/query-bound history snapshot cursors and verify concurrent activity without duplicates or omissions in MySQL 8.4.
- [x] Separate `activeRun` from terminal `latestRun`, restore owned retry content, and gate stale frontend responses.
- [x] Enforce one-time automatic titles, canonical industry confirmation, shared usage-ledger semantics, purge generations, guarded late writes, and content-free purge audits.
- [x] Stabilize the dedicated Assistant shell, container-responsive research profile, scroll/draft/IME behavior, focus-managed drawers, and the unified searchable industry combobox.
- [x] Add the forward, repeatable workspace stabilization migration and exact precheck/postcheck/index recovery coverage.
- [x] Pass 299 Spring tests including 58 MySQL 8.4 tests, 57 Vitest tests, all 8 frontend scripts, 63 default Python tests plus the explicit MySQL migration run, both production builds and repository security gates.
- [x] Deploy the stabilization release and pass the expanded production probes.
- **Status:** complete and deployed in release `20260726-015858`; migration/postcheck, dual-domain, atomic start, history second page, purge, authorization and real DeepSeek evidence probes passed.

### Phase 24: Probe Cleanup, History Consistency, Tablet Layout, And Purge Concurrency
- [x] Make the temporary probe administrator identity available before database writes and clean partial creation by exact unique username.
- [x] Preserve the original deployment failure while reporting any separate, sanitized cleanup failure; verify administrator counts return to their pre-probe value.
- [x] Add a per-user history metadata revision to signed cursors and return controlled `HISTORY_CURSOR_STALE` conflicts after rename, pin, archive, trash, restore, or purge changes.
- [x] Refresh the first history page once on a stale cursor while preserving the selected session and current search query.
- [x] Correct the research-profile container layout to six-track desktop, explicit two-column tablet grouping, and one-column phone behavior.
- [x] Add real MySQL latch-controlled purge races for active-run rejection and late callback denial after the terminal purge boundary.
- [x] Pass 306 Spring tests including 62 MySQL 8.4 integration tests, 60 Vitest tests, all 8 frontend scripts, 73 default Python cases, 7 MySQL migration cases, both production builds, and final repository gates.
- [x] Deploy the forward migration and application release, pass dual-domain, authorization, stale-cursor, temporary-account cleanup, and real DeepSeek probes, then repeat production preflight independently.
- **Status:** complete and deployed in release `20260726-080227`; backup, database dump, backend rollback artifact, and previous release remain available.

### Phase 25: Readiness Trigger, First-Question UX, Settlement, And History Closure
- [x] Split local profile persistence from evidence dependencies so only region and canonical industry changes schedule readiness or industry resolution.
- [x] Give new research and existing sessions distinct Composer labels/placeholders while retaining atomic start and ordinary message routes.
- [x] Keep the Composer in the dedicated viewport shell, add coarse-pointer tablet targets, and scroll keyboard-active industry options only inside the listbox.
- [x] Block permanent purge while a cancelled Provider call still owes usage, then settle actual usage once without publishing an answer.
- [x] Increment history revision exactly once for an automatic title and serialize concurrent atomic starts before child-row creation to avoid MySQL lock-upgrade deadlocks.
- [x] Preserve the original deployment exception when rollback also fails and attach only a fixed sanitized recovery note.
- [x] Pass 307 Spring tests including 63 MySQL 8.4 tests, 65 Vitest tests, all 8 frontend scripts, 74 explicit Python tests, both production builds, and repository gates.
- [x] Deploy, pass migration/postcheck, dual-domain, authorization, readiness/start/message/history, cleanup and real DeepSeek probes, then repeat preflight independently.
- **Status:** complete and deployed in release `20260726-092000`; backup, database dump, backend rollback artifact, and previous release remain available.

### Phase 26: Research Orchestration, Evidence Workspace, And Reliability Closure
- [x] Reproduce and fix the low-height Assistant clipping path with one bounded flex height chain, one conversation scroll owner, and a non-shrinking Composer.
- [x] Give Assistant commands explicit secondary/danger states, keyboard focus, active feedback, accessible names, and coarse-pointer touch targets.
- [x] Add an owned, sanitized and bounded run-evidence API plus a grouped Prisma Light evidence panel beside the answer.
- [x] Publish `agent-research-v2`: closed intent/plan/result schemas, multiple validated tool requests, deterministic dependencies, structured synthesis, and compatibility Markdown.
- [x] Expand case lookup to selected-region descendants, policy lookup to descendants/selected/ancestors, and preserve exact industry-tag matches before bounded relevance ordering.
- [x] Distinguish sufficient, partial and insufficient evidence so partial verified evidence remains useful without weakening publication or verification requirements.
- [x] Add the ten-scenario research-quality evaluation separately from the existing runtime protocol evaluation.
- [x] Make Provider Call settlement and Run usage reconciliation atomic and replay-safe in an independent transaction, including cancellation and concurrent estimate replacement.
- [x] Replace compatibility-session Active Run N+1 reads with one owner-scoped projection and preserve automatic-title revision behavior.
- [x] Preserve the original deployment exception across reconnect, shutdown, rollback and cleanup failures; strengthen the real production probe for v2 mixed research and evidence.
- [x] Pass Spring `322` (`321` passed, `1` opt-in Provider smoke skipped), MySQL 8.4 `67/67`, Vitest `73/73`, all `8` frontend scripts, Python default `77` (`70` passed, `7` explicit MySQL skipped), explicit Python MySQL `7/7`, both production builds and repository gates.
- [x] Exercise the real deployment gate through four `TRUNCATED_RESPONSE`, two `UNCITED_FACT`, one `INVALID_OUTPUT_SECTIONS`, one `INVALID_STRUCTURED_RESULT`, one `UNKNOWN_SOURCE_ID` and one legacy `INVALID_AGENT_PLAN` rollback plus two pre-rollout Provider connection rejections; close each deterministic mismatch with a red-capable contract test and re-run the complete backend suite. Planning failures now use fixed content-free diagnostics instead of the generic plan code.
- [x] Deploy the verified release and record the backup, rollback path, remote hashes, mixed-evidence DeepSeek metrics and temporary-data cleanup.
- **Status:** complete and deployed in release `20260726-162930`; independent preflight confirmed hashes, service ownership, one backend process and restored temporary-account counts.

### Phase 27: Predeploy Provider Evaluation, Contract Convergence, And Assistant Quality Closure
- [x] Centralize Agent v2 prompt version, planning/synthesis output budgets, field limits, aggregate limits, output sections and controlled diagnostics in `AgentResearchContract`.
- [x] Generate separate closed planning and synthesis schemas from the same contract constants used by Java validation and prompt boundary text.
- [x] Add fifteen sanitized replay fixtures for truncation, unknown fields, invalid arrays, source-ID confusion, uncited content, invalid structured results, forged coverage and Provider connection failure.
- [x] Derive evidence coverage from current-run authorized tool evidence rather than trusting model-declared counts or status.
- [x] Add database-backed region resolution and authorize only resolved or profile-owned region IDs before cross-region retrieval.
- [x] Bind Assistant citations and evidence items to the message Run so historical citations cannot resolve against the latest Run.
- [x] Move the real Provider connection and complete Agent v2 contract probe ahead of `/opt/opc/current` switching by using an isolated candidate database and transient runtime.
- [x] Add complete candidate audit validation for Provider calls, rounds, tool completion, citations, Token settlement, latency, three evidence-count categories and an explicit `release_switched=false` record.
- [x] Pass Spring `341` (`340` passed, `1` opt-in real Provider smoke skipped), MySQL 8.4 `68/68`, Vitest `77/77`, all `8` frontend scripts, Python deployment/migration `83/83`, explicit Python MySQL `7/7`, both production builds and repository gates.
- [x] Run the mandatory real DeepSeek candidate probe before rollout, then switch production only after its contract, citation, coverage and settlement gates pass.
- [x] Complete postdeploy compatibility and Agent probes plus an independent service/hash/process preflight.
- **Status:** complete and deployed in release `20260726-213258`; candidate validation ran before the production symlink switch and the previous release remains available.

### Phase 28: Multi-round Tool Closure, Independent Assistant, And Release Gate Convergence
- [x] Restrict the initial Agent plan to independent `search_cases` and `search_policies` requests.
- [x] Add a bounded continuation round that consumes actual same-Run case and source IDs before `compare_cases` or `get_source` can execute.
- [x] Reject guessed IDs, cross-Run dependencies, and unresolved dependent requests with controlled diagnostics.
- [x] Keep the session profile industry fixed and derive selected, parent, national and cross-region scopes on the server.
- [x] Reject explicit region or industry changes in an established research session before message persistence or Token reservation.
- [x] Separate the bounded full tool audit from the 12 KiB `_authorized` model projection, using deterministic UTF-8 field truncation.
- [x] Add compatible available, total and unavailable evidence counts and keep unavailable material outside citation authorization.
- [x] Move `/assistant` to a lazy, top-level protected `AssistantLayout` without the public archive sidebar.
- [x] Add mutually exclusive, focus-restoring history and evidence drawers plus a single Assistant navigation and command surface.
- [x] Reorder deployment so candidate database migration, Provider connection and three real Agent scenarios complete before any production backup, migration, restart or symlink switch.
- [x] Add deployment tests proving candidate failure performs zero production migrations and zero production switches, while a successful release migrates and switches once.
- [x] Pass final local gates: Spring `357` (`356` passed, one opt-in smoke skipped), MySQL `70/70`, Vitest `83/83`, all eight frontend scripts, Python deployment `77/77`, migration `14/14`, explicit MySQL migration `7/7`, both builds and repository checks.
- [x] Run three bounded isolated candidate attempts without any production mutation and retain controlled diagnostics for each rejected build.
- [ ] Run the isolated real Provider policy, case-comparison and source-verification candidate scenarios.
- [ ] Perform one production migration/switch only after all three candidate scenarios pass, then run production health and cleanup checks.
- **Status:** local implementation, tests and builds complete. The candidate gate rejected two policy-contract responses (`INVALID_DEPENDENCIES`, then `UNCITED_FACT`) and, after those fixes, rejected the case-comparison scenario as `evidence_insufficient`. Production migration/switch count is zero; Phase Two is not complete.

### Phase 29: Real-provider Orchestration Closure And Final Candidate Gate
- [x] Add current-run source allowlists to synthesis and prevent terminal evidence states from scheduling redundant searches.
- [x] Give invalid initial planning and continuation `UNKNOWN_FIELDS` at most two bounded, content-free planning recoveries.
- [x] Raise the measured Agent runtime defaults to five model rounds and 28,000 aggregate Tokens, and raise the planning response budget to 3,200 Tokens without changing the six-tool cap.
- [x] Add the idempotent `20260727_agent_multiround_budget` precheck/migration/postcheck set and keep candidate/production migration hashes identical.
- [x] Preserve `release_switched=false`, actual usage settlement and zero reservation for every rejected candidate path.
- [x] Pass the focused orchestration/contract suite `53/53`, Spring `368` (`367` passed, one opt-in Provider smoke skipped), MySQL `70/70`, Vitest `85/85`, the Assistant subset `35/35`, all eight frontend scripts, Python `101` (`94` passed, seven explicit-MySQL cases skipped), static migration `14/14`, both builds and repository checks.
- [x] Run bounded real candidates without production backup, migration, restart or switch; retain only controlled diagnostics and scalar usage metrics.
- [ ] Re-run one isolated three-scenario candidate only after Provider connectivity is plausibly restored.
- [ ] Perform one production migration/switch and postdeploy probe only after policy, dynamic comparison and source verification all pass in the same candidate gate.
- **Status:** local implementation, deterministic verification and production artifacts are complete. Real candidates proved policy and source-verification success in separate attempts, but no attempt passed all three mandatory scenarios; the latest attempt stopped at `PROVIDER_CONNECTION_FAILED` before scenarios. Production remains `20260726-213258`, formal switch count is zero, and Phase Two is not complete.

### Phase 30: Server-Owned Intent Requirements And Final Single-Deploy Gate
- [x] Reproduce the `20260727-070820` candidate failures where model intent lowered the required case-comparison and source-verification tool chains.
- [x] Add server-owned `ResearchExecutionRequirements` so validated API intent and deterministic message signals can only be supplemented, never removed, by model intent.
- [x] Add optional backward-compatible `requestedIntent` to start and message APIs, persistence, idempotency identity and the four Assistant starters; reset edited starter prompts and continuations to `auto`.
- [x] Normalize persisted result intent to the server-resolved intent and keep profile region/industry binding independent from task intent.
- [x] Preserve complete sanitized probe metrics before tool-sequence validation, run all three candidate scenarios independently and aggregate their records.
- [x] Delete only the exact unswitched release created by the failing deploy command while protecting current, previous and historic releases.
- [x] Add the forward repeatable `requested_intent` schema migration and candidate/production precheck/postcheck validation.
- [x] Pass Spring `373` (zero failures/errors, one opt-in Provider smoke skipped), MySQL 8.4 `71/71`, Vitest `87/87`, Assistant `36/36`, all eight frontend scripts, Python default `107` (seven opt-in MySQL skips), explicit MySQL `7/7`, deployment hardening `86/86`, both production builds and repository gates.
- [x] Pass remote preflight and invoke the single allowed `python .codex_deploy_opc.py deploy` workflow; stop without production mutation after the policy candidate returned `REQUIRED_TOOL_CHAIN_UNSATISFIED`.
- [ ] Require policy, dynamic case comparison and dynamic source verification to pass in the same isolated candidate before the one production migration/switch and postdeploy checks.
- **Status:** local implementation, tests, builds and static gates are complete. The one allowed deploy ran all three scenarios: comparison and source verification passed, while policy failed the server tool-chain requirement. `release_switched=false`; production remains `/opt/opc/releases/20260726-213258`, and Phase Two remains open.

### Phase 31: Intent Priority Stabilization And Bounded Deployment Closure
- [x] Reproduce explicit `policy_lookup` plus conflicting model `case_analysis` and prove the former incorrect merge added `CASE_SEARCH`.
- [x] Make non-`auto` requested intent authoritative; allow model intent to supplement only when `auto` has no deterministic server operation.
- [x] Preserve explicit `general_research` and `technology_assessment` instead of degrading either to `follow_up`.
- [x] Add orchestrator coverage proving an explicit policy Run can complete with only the required policy tool chain despite a conflicting model intent.
- [x] Record actual tool sequence and scalar metrics before candidate terminal validation, including SQL `NULL` normalization.
- [x] Pass focused orchestration `32/32`, deployment hardening `88/88`, Spring `380` with one opt-in Provider skip, MySQL 8.4 `71/71`, Vitest `87/87`, Assistant `36/36`, all eight frontend scripts, Python default `109` with seven opt-in MySQL skips, explicit MySQL `7/7`, Vite/JAR builds and repository gates.
- [x] Run one initial candidate, one same-build transient retry, and two code-corrected candidate retries without changing production on failure.
- [x] Verify cleanup leaves zero candidate services, databases/users, environment files, port 18082 listeners, and current-attempt release directories.
- [ ] Pass policy, comparison and source verification in one real candidate batch and switch production once.
- **Status:** bounded stop reached. The final candidate passed policy and source verification but returned `CANDIDATE_CASE_COMPARISON_EVIDENCE_INSUFFICIENT` with `search_cases, search_policies` and no `compare_cases`. `release_switched=false`; production remains `/opt/opc/releases/20260726-213258`, so Phase Two remains open.

### Phase 32: Deterministic Tool-chain And Production Closure
- [x] Reproduce the zero-result comparison branch and require one selected search plus at most one broader cross-region search before controlled insufficiency.
- [x] Make the server reserve budget and complete required policy, case, comparison and source-verification tools through the audited registry.
- [x] Normalize required search parameters, deduplicate case IDs, preserve request-level dependencies and redact query/category text from tool audit diagnostics.
- [x] Restrict comparison dimensions to the shared product enum and fall back to `businessModel, outcome` when model suggestions are unsupported.
- [x] Pass focused orchestration/contract `70/70`, Spring `387` with one opt-in Provider skip, MySQL 8.4 `71/71`, Vitest `87/87`, Assistant `36/36`, all eight frontend scripts, Python discovery `110` with seven explicit-MySQL skips, explicit MySQL `7/7`, deployment hardening `89/89`, both production builds and repository gates.
- [x] Run four deploy batches: one transient Provider failure, one candidate-report parser failure, one deterministic dimension failure, then one all-green candidate and production switch.
- [x] Deploy `/opt/opc/releases/20260728-130142` once, retain `/opt/opc/releases/20260726-213258` as previous, and verify backup, dump and backend rollback assets.
- [x] Pass three independent production research probes, migration postchecks, domain/health/auth checks, one-process/loopback checks and complete candidate/temporary-data cleanup.
- **Status:** complete. `/opt/opc/current` resolves to `/opt/opc/releases/20260728-130142`; Agent Phase Two exit criteria are satisfied. Manual responsive visual review remains a non-blocking user check.

### Phase 33: User Decision Workbench Readiness And Contract Freeze
- [x] Read all mandatory skills, product/design register, prior phase closure documents, dependencies and work records.
- [x] Audit the current Assistant, public user routes, frontend clients, backend controllers/services/mappers/entities/DTOs, all migrations and MySQL Testcontainers fixtures.
- [x] Use the existing protected read-only SSH/database path to audit production MySQL without reading account/session data or mutating production.
- [x] Quantify eligible cases, policies, sources, industry/technology/region/time/revenue coverage, source chains and exact duplicate candidates.
- [x] Freeze the 20 statistical principles and publish the sole metric dictionary with Green/Yellow/Red readiness.
- [x] Define the user decision workbench IA, case/comparison/technology outputs, dashboard visuals, safe analytics-to-Agent flow and report lifecycle.
- [x] Define analytics/report API contracts, backend ownership, contract fixtures, evaluation gates and Phase Three A/B/C sequence.
- [x] Add seven formal specifications and update the four project readiness records.
- [ ] Implement Phase Three A, B or C business features; intentionally outside this preparation round.
- [ ] Run any production migration or deployment; intentionally outside this preparation round.
- **Status:** specification preparation complete. Production audit at `2026-07-29 01:08–01:10 CST` found 105 eligible case rows (canonical business-case count unknown because 42 exact duplicate candidates), 57 eligible policies and 121 eligible sources. No application code or production state was changed.
