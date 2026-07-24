# Task Plan: Apply the Prisma visual system to SoloFirm

## Goal
Preserve every existing SoloFirm core function, name, icon, route, and content contract while rebuilding the frontend user page with the supplied Prisma visual language and interactions, then verify the result in real browsers.

## Current Phase
Phase 17 (implementation and deployment complete; human evidence audit pending)

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
