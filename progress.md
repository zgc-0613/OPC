# Progress Log

## Session: 2026-08-09 - MySQL 8.4 Testcontainers resource closure

- Established the red feedback loop with `scripts/test_phase_one_mysql_runner.py`. It initially failed against the old runner because no run-owned container query/cleanup boundary existed.
- A real single-method run first disproved the naive fix of relying only on JUnit `@Container`: Spring resolved the `DynamicPropertySource` URL before the extension started the container. The final minimal fix keeps early startup, adds the validated `com.opc.phase-one.run-id` label and an idempotent `@AfterAll` stop.
- The runner now generates a UUID v4, passes `-Dopc.phase-one.mysql.run-id`, validates exact image/label ownership, treats successful residual containers as a non-zero resource leak, and never queries or cleans generic Testcontainers labels.
- Real single test passed `1/1` with current run count `0` and unchanged preexisting container set. Full run ID `06b0c950-985b-4b53-85e4-61d2fa21b297` passed MySQL `80/80`; Surefire `885.353s`, total `903.203s`, container ready `36.812s`, Spring `44.812s`, current run count `0`, `resource_leak_detected=false`, historical set unchanged.
- Runner tests passed `6/6`; Python discovery passed `133` tests with seven explicit opt-in MySQL skips; focused backend `67/67`, non-MySQL Spring `467` (zero failures/errors, one existing skip), JAR package, Python syntax and ignore checks passed. No production deployment was run.

## Session: 2026-08-09 - Source-verification orchestration and MySQL 8.4 stability closure

- Added `sourceVerificationWithOnlyUnresolvedClaimsSuppressesLegacyAnswerMarkdownAndCitations` to `AgentOrchestratorTest`. The deterministic public-boundary fixture uses `action=final`, an authorized evidence bundle, unresolved-only claims and deliberately poisoned legacy answer/Markdown/citation content. It verifies server-owned `evidence_insufficient`, zero fact/citation coverage, unknown publisher state and no poisoned output leakage.
- The new test first found the terminal state incorrectly remained `completed`. `AgentOrchestrator` now derives that state from the sanitized assembled source-verification result. No Provider, tool protocol, evidence allowlist or database model changed.
- Added `scripts/run_phase_one_mysql_test.py` as the reusable bounded MySQL 8.4 execution path. It proves report freshness, measures Maven/container/Spring/test stages, captures redacted thread dumps during long runs and cleans only the exact UUID-labeled run containers on failure or success-leak detection.
- Docker was available and `mysql:8.4` cached. An externally killed 900.844-second diagnostic run was recorded as a failure, not a pass, and its known `happy_chebyshev` test container was inspected then removed. Two later 80-test runs passed in 808.2s and 766.953s. Main-thread snapshots showed active migration and schema DDL, not a hang; container/Spring startup was under 37 seconds.
- Final checks passed: focused unresolved-only test `1/1`, source-verification backend `67/67`, Vue defense `10/10`, non-MySQL Spring `467` (0 failures/errors, 1 existing skip), MySQL `80/80` twice, full Vitest `173/173`, all eight npm contracts, Vite build, JAR package, Python migration `17/17`, deployment hardening `103/103`, syntax, diff, secret, artifact and ignore checks.
- Ran one guarded release after the runtime terminal-status correction. Candidate and postflight passed; production switched to `/opt/opc/releases/20260809-193452` from `/opt/opc/releases/20260809-150138`. Backup `/opt/opc/backups/20260809-193452`, database dump and rollback JAR were retained. Public/Assistant/admin/health routes and anonymous AI/admin `code=401` envelopes were confirmed. No commit or push was performed.

## Session: 2026-08-09 - Source verification and publisher closure

- Corrected the server-owned source-verification matrix: no claims or unresolved-only evidence is `insufficient`; supports-only is `supports/sufficient`; supports plus unresolved is `partially_supports/partial`; authorized contradicts-only is `does_not_support/sufficient`; supports and contradicts on one claim is `conflicting/conflicting`.
- Added server-derived publisher verification. Only publishers present on authorized Run evidence can produce `publisherAssessment` items; absent publisher metadata stays `unknown` and is never inferred from title, URL, user text, or model output.
- Recorded the required RED first: four new assembler boundary assertions failed for no-claim final results, same-source conflicts and publisher/citation coverage. GREEN passed assembler `21/21`, `AgentOrchestratorTest` `44/44`, focused Agent/assembler/report `80/80`, full frontend Vitest `172/172`, all eight frontend scripts, Vite build, Spring `545` tests (0 failures/errors, 1 opt-in provider skip), independent MySQL 8.4 `80/80`, JAR package, Python migration `17/17`, deployment hardening `103/103`, syntax, diff, secret, artifact and ignore checks.
- The guarded release workflow rejected a transient Provider `503/ack_empty` candidate and a later pre-switch case-comparison `AGENT_TIMEOUT`; each failure left production unchanged and candidate resource counts at zero. A same-build isolated candidate and the final formal deploy both passed all three scenarios without loosening Provider, runtime budget or evidence rules.
- Production now resolves to `/opt/opc/releases/20260809-133127`; previous release `/opt/opc/releases/20260809-024820`, backup `/opt/opc/backups/20260809-133127`, database dump `/opt/opc/backups/20260809-133127/opc_platform.sql.gz`, and backend rollback `/opt/opc-backend.rollback.20260809-133127` are retained. Independent postflight confirmed active services, valid Nginx, matching hashes, one loopback backend and zero candidate resources.
- The production probe covered the guarded source-verification workflow, not independent synthetic live runs for every verdict or publisher branch. Local deterministic tests are the evidence for those branches. No commit or push was performed.

## Session: 2026-08-08 - Phase Three deep closure final release

- Completed the four closure slices in the existing worktree: server-owned source-verification verdicts, full Markdown/HTML/PDF report snapshots, bounded Analytics contracts with honest unavailable states, and complete technology-assessment task context persistence/restoration.
- Final local acceptance passed: frontend Vitest `171/171` (28 files), all eight frontend contract scripts, Vite build (1,822 modules), MySQL 8.4 Testcontainers `80/80`, full Spring `532` (0 failures/errors, one explicit opt-in provider smoke skip), executable JAR, Python migration `17/17`, deployment hardening `103/103`, syntax, `git diff --check`, high-confidence secret scan, build-artifact and ignore checks.
- Preflight confirmed three active services, valid Nginx, one loopback backend and eligible evidence. The guarded deployment completed its candidate gate, additive migration precheck/migration/postcheck, timestamped backup, atomic switch and post-switch probes.
- Production release: `/opt/opc/releases/20260808-225959`; previous release `/opt/opc/releases/20260808-162621`; backup `/opt/opc/backups/20260808-225959`; database dump `/opt/opc/backups/20260808-225959/opc_platform.sql.gz`; backend rollback `/opt/opc-backend.rollback.20260808-225959`.
- Candidate and production research probes used the configured real provider only through the guarded server workflow. Results retained authorized citations, bounded tool sequences and settled actual usage; temporary probe records were cleaned. Independent curl checks returned public/health/admin HTTP 200 and established anonymous 401 envelopes.
- This remains a Phase Three user-facing v1 / partial release. Technology and revenue statistics are unavailable until the documented verified-data threshold is met; desktop/tablet/mobile, keyboard and assistive-technology acceptance remains a user manual check. No commit or push was performed.

## Session: 2026-08-08 - Phase Three user-facing v1 release

- Repaired the direct Python deployment-hardening test entry point so repository-local `scripts.deployment_hardening` is selected consistently. `python scripts/test_deployment_hardening.py` passed `103/103`; migration tests passed `17/17`; Python syntax compilation and `git diff --check` passed.
- Re-ran the final local acceptance matrix: all frontend Vitest tests passed `157/157`; all eight repository frontend contract scripts passed; Vite production build and Spring Boot executable JAR packaging passed.
- MySQL 8.4 Testcontainers passed `80/80` with Docker Desktop Linux Engine running. Full Spring Boot regression passed `509` tests with zero failures/errors and one expected opt-in real-provider smoke skip.
- Ran `.codex_deploy_opc.py deploy` only after local acceptance. Its candidate gate completed policy, case-comparison, and source-verification scenarios; production migration prechecks, additive migrations, postchecks, atomic switch, health/auth checks, and guarded probes all completed.
- Production now points to `/opt/opc/releases/20260808-162621`; backup `/opt/opc/backups/20260808-162621`, database dump `/opt/opc/backups/20260808-162621/opc_platform.sql.gz`, previous release `/opt/opc/releases/20260807-173031`, and backend rollback `/opt/opc-backend.rollback.20260808-162621` are retained.
- No commit or push was performed. The release remains a partial Phase Three v1 release: technology/revenue formal statistics and user browser-based responsive/accessibility acceptance remain outside this automated deployment evidence.

## Session: 2026-08-06 - Assistant terminal synchronization recovery and evidence correction

- Added and verified a run-bound terminal-detail synchronization state in `AssistantView`. A terminal server result remains visible when `getResearchSession` fails; the UI states that the research is terminal but session content is still synchronizing, exposes a keyboard-accessible `同步研究结果` command, and disables the composer until successful synchronization.
- Manual synchronization re-reads the existing run and session, then refreshes messages, usage, and history. It does not create a new session, Run, model invocation, or token reservation. Session-change and unmount request gates discard late responses.
- New terminal-sync behavior was driven through completed, failed, session-switch, and unmount cases. Full frontend Vitest passed `25` files / `148` tests; Vite production build passed.
- Maven focused Agent tests passed `50/50`; all `*Agent*Test` passed `163` run / `0` failures / `0` errors with one expected opt-in `AgentDeepSeekSmokeTest` skip; executable JAR packaging passed.
- Python migration tests passed `17/17`; deployment-hardening tests passed `94/94`; Python syntax compilation passed. `git diff --check` passed during local validation.
- Docker preflight found Docker Desktop Linux Engine stopped. `PhaseOneMySqlIntegrationTest` was deliberately run once and failed at Testcontainers initialization because the Docker named pipe was unavailable. The integration test was not disabled, skipped, or altered. No test process remained.
- No deployment, commit, push, remote access, or real provider request was performed.

## Session: 2026-07-18

### Phase 1: Baseline, Requirements, and Product Context
- **Status:** complete
- **Started:** 2026-07-18
- Actions taken:
  - Loaded Using Superpowers, Planning with Files, Impeccable, Frontend Design, TDD, and Playwright instructions.
  - Read the full supplied Prisma specification.
  - Ran the planning session catch-up script; no prior unsynchronized state was reported.
  - Ran the Impeccable context loader; PRODUCT.md and DESIGN.md are absent.
  - Loaded the mandatory Impeccable Teach flow.
  - Initialized persistent task planning, findings, and progress records.
  - Confirmed the repository was otherwise clean and identified its frontend/backend/video/docs top-level structure.
  - Confirmed the frontend is already Vue 3 + Vite, with Vue Router, Axios, a `/api` proxy, the SoloFirm document title, and the existing favicon.
  - Enumerated public and admin Vue views, layouts, API modules, and assets; identified the absence of automated frontend test scripts.
  - Read the router, public layout, and full home page; captured all public routes, navigation behaviors, home data sources, dynamic content blocks, and decorative effects.
  - Inspected all remaining public views and recorded login, filtering, pagination, export, source-link, detail, analytics, and empty/error-state behaviors; flagged three files for focused rereads due output truncation.
  - Completed focused reads of PolicyList and CaseList, confirming pagination windows, debounce timings, search logging, type/category matching, URL-seeded filtering, and export behavior.
  - Completed the PolicyDetail and API-layer baseline, recording all public endpoints, token/session behavior, response unwrapping, detail fields, visit/search analytics, and export URL contracts.
  - Verified installed frontend dependencies and Playwright CLI prerequisites are present before baseline execution.
  - Ran the pre-change production build successfully and confirmed it introduced no tracked-file changes.
  - Inspected backend runtime requirements and confirmed the frontend proxy target, development auth mode, database dependency, and data-import assets.
  - Inspected application boot and the legacy stylesheet outline; selected a scoped public-style layer to avoid rewriting 10,000+ shared/admin CSS lines.
  - Checked runtime services: frontend port 5173 is free, backend port 8082 conflicts with QQ, and MySQL 3306 is not listening.
  - Started the baseline Vite server on localhost:5173 and confirmed the Playwright CLI wrapper cannot run through Bash on this Windows host, while npx remains available.
  - Connected the in-app browser, captured the baseline home DOM and full-page visual, and identified scroll-reveal invisibility in static full-page capture plus major differences from Prisma.
  - Created and reloaded Impeccable PRODUCT.md from repository evidence and the user-supplied Prisma direction; confirmed DESIGN.md is the remaining context artifact.
  - Documented the target Prisma visual system in DESIGN.md and `.impeccable/design.json`, including tokens, component states, motion, breakpoints, and guardrails.
- Files created/modified:
  - `task_plan.md` (created)
  - `findings.md` (created)
  - `progress.md` (created)
  - `PRODUCT.md` (created)
  - `DESIGN.md` (created)
  - `.impeccable/design.json` (created)

### Phase 2: Architecture and Visual Mapping
- **Status:** complete
- Actions taken:
  - Retained existing Vue 3 + Vite and selected a public-only Prisma style layer after the legacy global stylesheet.
  - Mapped every home content block into Hero, About, Features, analytics, and contact positions without deleting data or actions.
  - Defined shared Vue reveal components, scoped public styling, Lucide usage for new affordances, reduced-motion behavior, and external media fallbacks.
- Files created/modified:
  - `task_plan.md` (updated)
  - `findings.md` (updated)
  - `progress.md` (updated)

### Phase 3: Implementation
- **Status:** in_progress
- Actions taken:
  - Added the maintained `@lucide/vue` dependency after removing the deprecated package name and resolving its current version.
  - Added Google font loading, the public Prisma stylesheet import, and shared Vue word/character reveal components.
  - Replaced the home Hero and inserted the About/Features structures while preserving all original analytics and footer content.
  - Added the public-only Prisma CSS layer covering home, all public work routes, user login, responsive states, reduced motion, noise textures, and shared components.
  - Removed the old hero pointer trail and converted home chart/source accent literals from blue/teal to the target cream and quiet-success palette.
  - Ran the first implementation build successfully.
  - Captured a client-only Lucide runtime failure that the build did not detect and started a deterministic browser-console diagnosis loop.
  - Incorporated the user's screenshot feedback: light public theme plus analytics cards matching the four-column workflow system.
  - Incorporated the follow-up typography and atmosphere direction: Songti/Kaiti-led serif hierarchy, paper grain, ink contrast, pine and clay accents.
  - Added Bookman Old Style as the requested English display direction.
  - Diagnosed the Lucide black screen to an invalid client-side inject call inside `@lucide/vue` 1.25.0, ruling out duplicate Vue instances.
  - Swapped to `lucide-vue-next`, converted the working code to the light paper palette, added Songti/Kaiti/Bookman typography, and changed analytics to the matching responsive card grid.
  - Confirmed the replacement build passes; browser still referenced the stale old Vite dependency chunk, so a dev-server restart is required for a valid runtime verdict.
  - Safely restarted only the OPC Vite process; dependency optimization reran and localhost:5173 is ready on Node PID 20684.
  - Verified the Lucide replacement in a clean browser tab: the complete home DOM renders with no application console warnings/errors.
  - Inspected the light hero screenshot and incorporated the user's latest correction: remove all non-login background media and explicitly reset low-contrast/legacy gradient text styles.
  - Restored Full Access and resumed directly in `C:\Users\ACha_\Documents\GitHub\OPC` after confirming the temporary rejected patch made no change.
  - Read the newly supplied Prisma specification and established it as the layout/motion/component base beneath the user's light-theme, typography, static-frame, contrast, and button-color overrides.
  - Added an explicit no-feature-removal invariant covering every existing public workflow and content block.
  - Re-inspected HomeView, prisma.css, LoginView, and DESIGN.md after the interruption; confirmed exact remaining media, contrast, component, and documentation fixes.
  - Verified FFmpeg/FFprobe availability for deterministic local extraction of the hero still frame.
  - Downloaded and inspected the 10.04-second Hero reference video, then received the final clarification to keep it playing and extract the lower card's still from the lower feature video instead.
  - Added global Prisma consistency as a completion invariant across every public route and micro-component.
  - Sampled four Hero frames and selected the balanced 5.5-second composition for the outer Hero background only.
  - Finalized the media layout: playing inset Hero video, static outer Hero frame, white sections below, unchanged lower feature video and login image.
  - Finalized a grayscale-only button system across the public frontend.
  - Generated and visually inspected `public/media/solofirm-hero-frame.webp` from the selected Hero frame for the outer Hero background.
  - Removed the decorative About `AI + OPC` kicker and raised the scroll-linked body opacity floor for readability.
  - Added the final high-specificity Prisma public style layer covering Hero resets, capability pills, feature cards, analytics internals, contact/footer, every public working route, and login components.
  - Updated home chart colors to ink/gray/semantic green and preserved all existing data/behavior markup.
  - Rewrote DESIGN.md and `.impeccable/design.json` to the final visual system, then reloaded Impeccable context.
  - Validated sidecar JSON and completed a successful production build.
  - Opened the final homepage in a clean browser tab; verified the complete preserved DOM and zero console warnings/errors.
  - Captured the final desktop Hero and confirmed the legacy SoloFirm gradient/shadow and multicolor CTA styling are removed.
  - Captured About and workflow sections; confirmed the requested kicker removal, restored pill contrast, unchanged lower feature video, and unified light Prisma cards.
  - Switched browser scrolling methods after coordinate scrolling did not advance the feature viewport.
  - Added a stable analysis anchor, captured the analytics cards, corrected internal trend/hot/source contrast, and removed the remaining dark visit-summary wrapper.
  - Verified the login DOM and grayscale control system; diagnosed its missing visible photo as an unreliable remote Unsplash asset rather than a component/style removal.
  - Downloaded, optimized, inspected, and wired `public/media/solofirm-login.webp` so the retained login image no longer depends on an unreliable redirect.
  - Re-ran the current production build and started a fresh Vite service for the requested repository on localhost:5173.
  - Completed the first desktop browser audit across home, regions, policies, cases, sources, and the analytics anchor; confirmed the Hero video/still boundary and exposed the remaining public navigation, blue-caption, decorative-header, and error-contrast defects.
  - Replaced Vue's default public sidebar active-class styling with explicit route-name state, neutralized legacy public header decoration/numbers, strengthened error contrast, and added visible analysis loading/failure announcements while preserving all API logic.
  - Extended the Prisma scope to administrator login and all `/admin` workspaces after the user's follow-up, including exact admin navigation state, grayscale CRUD controls, paper forms/tables/analytics, and responsive admin layout.
- Files created/modified:
  - `opc-frontend/package.json` (updated)
  - `opc-frontend/package-lock.json` (updated)
  - `opc-frontend/index.html` (updated)
  - `opc-frontend/src/main.js` (updated)
  - `opc-frontend/src/components/WordsPullUp.vue` (created)
  - `opc-frontend/src/components/WordsPullUpMultiStyle.vue` (created)
  - `opc-frontend/src/components/AnimatedLetters.vue` (created)
  - `opc-frontend/src/views/HomeView.vue` (updated)
  - `opc-frontend/public/media/solofirm-hero-frame.webp` (created)
  - `opc-frontend/public/media/solofirm-login.webp` (created)

### Phase 4: Verification and Visual QA
- **Status:** complete
- Actions taken:
- Completed real-browser desktop, tablet, and mobile checks for all public routes, both detail routes, user login, admin login, and all five admin workspaces.
- Verified Hero and workflow video playback, Hero still scoping, responsive navigation, exact active states, search/filter interaction, login/logout, forms, tables, analytics rows, empty/error states, overflow, and console output.
- Fixed tablet Hero navigation cascade conflicts, mobile nested scrollbars, public/admin mounted-hook rejections, admin responsive navigation, and remaining visible cyan/blue accents.
- Files created/modified:
- `opc-frontend/src/layouts/MainLayout.vue`
- `opc-frontend/src/layouts/AdminLayout.vue`
- `opc-frontend/src/views/PolicyListView.vue`
- `opc-frontend/src/views/CaseListView.vue`
- `opc-frontend/src/views/admin/PolicyAdminView.vue`
- `opc-frontend/src/views/admin/CaseAdminView.vue`
- `opc-frontend/src/views/admin/AdminHomeView.vue`
- `opc-frontend/src/styles/prisma.css`

### Phase 5: Completion Audit and Delivery
- **Status:** complete
- Actions taken:
- Completed the final requirement audit and production build with 1,660 transformed modules.
- Confirmed `http://localhost:5173` is served by the current repository's Vite process and responds successfully.
- Recorded backend/database 500 responses as an environment limitation while preserving all real API requests and readable failure states.
- Files created/modified:
- `task_plan.md`
- `findings.md`
- `progress.md`

## Test Results
| Test | Input | Expected | Actual | Status |
|------|-------|----------|--------|--------|
| Planning session recovery | Run session-catchup.py in repository root | Report prior context or no output when none exists | No unsynchronized context reported | PASS |
| Impeccable context load | Run load-context.mjs in repository root | Discover PRODUCT.md/DESIGN.md state | Both absent; Teach flow required | PASS |
| Baseline frontend build | `npm run build` in `opc-frontend` | Existing frontend builds successfully before redesign | Vite built 106 modules successfully | PASS |
| Implementation build 1 | `npm run build` in `opc-frontend` | New Vue templates/components/CSS compile | Vite built 1,869 modules successfully | PASS |
| Final Prisma build | `npm run build` in `opc-frontend` | Final public/admin templates, tokens, static assets, and CSS compile | Vite built 1,660 modules successfully | PASS |

## Error Log
| Timestamp | Error | Attempt | Resolution |
|-----------|-------|---------|------------|
| 2026-07-18 | PowerShell parser rejected `$name:` during planning-file existence check | 1 | Switched to `${name}` and reran successfully. |
| 2026-07-18 | Planning update patch included a context line from the wrong file | 1 | Removed the mismatched hunk and reapplied the findings/progress update only. |
| 2026-07-18 | Playwright CLI help invocation timed out without output after 124 seconds | 1 | Terminated the session and loaded the in-app browser control workflow as the alternate verification path. |
| 2026-07-18 | Phase-transition patch failed because one multi-file hunk did not verify | 1 | Read the current plan and reapplied smaller exact hunks. |
| 2026-07-18 | `lucide-vue-next` emitted a deprecation warning | 1 | Removed the deprecated package and used the official `@lucide/vue` replacement. |
| 2026-07-18 | Requested `@lucide/vue` using a nonexistent legacy version range | 1 | Queried npm for the current version and installed 1.25.0 successfully. |
| 2026-07-18 | Large HomeView replacement patch failed context verification | 1 | Split the edit into smaller verified template sections and applied them successfully. |
| 2026-07-18 | In-app browser reported the previous tab was missing | 1 | Kept the browser session and created a new localhost tab instead of reselecting the browser. |
| 2026-07-18 | `@lucide/vue` caused a client render exception and black page | 1 | Captured the exact stack trace; SSR is green, so diagnosis is focused on client injection/dependency behavior. |
| 2026-07-18 | C-drive patch was rejected after the sandbox temporarily lost write access | 1 | Waited for Full Access restoration and resumed without applying changes to a different workspace copy. |
| 2026-07-18 | Build command was first run from repository root without package.json | 1 | Kept the successful JSON check and reran npm build from `opc-frontend`. |
| 2026-07-18 | Playwright wrapper attempted to start an outdated WSL environment | 1 | Switched to the native Windows `npx` entry for the same Playwright CLI. |

## Login Alignment Verification (2026-07-18)
- Unified the public and administrator login media, logo, typography, form controls, footer alignment, and responsive behavior.
- Removed the six requested decorative labels and removed the public-facing administrator entry without deleting the guarded `/admin/login` route.
- Added fluid single-line login, registration, and administrator heading sizes using shared `clamp()` rules.
- Exercised the user login/register toggle in a real browser and confirmed it returns to the default login state.
- Browser-checked `/login` and `/admin/login` at 1440x900, 768x1024, and 320x844; no horizontal overflow, Vue warnings, route errors, or CSS/resource errors were observed.
- `npm run build`: PASS, Vite transformed 1,663 modules and emitted `index-Bi_jRiAh.css` plus `index-BZ03F9Wn.js`.
- `.\\mvnw.cmd test`: PASS, 1 test run with 0 failures and 0 errors.
- Runtime: current-project Vite is reachable at `http://localhost:5173`; Spring Boot listens on 8082; MySQL 3306 is not listening.

## Production Deployment (2026-07-18)
- **Status:** in_progress
- Confirmed the local production shape is Vite static output plus a Spring Boot service on 8082 with MySQL.
- Confirmed Paramiko 4.0.0 is available locally; credentials will remain process-scoped and will not be written into the repository.
- Deployment sequence is read-only remote inventory, local build/checksum, timestamped backup, minimal migration/replacement, service reload, then HTTP/browser verification.
- The first supplied IP was incorrect. The user corrected the production target to `39.105.25.189`; no deployment mutation occurred on the first host.
- Completed the corrected server's initial read-only inventory and identified the existing frontend, backend, Nginx, and MySQL deployment boundaries. Detailed remote facts are recorded in `findings.md`.
- Confirmed the current backend is split between a manual `/root` process and an enabled `/opt` systemd service in a restart loop. The deployment will normalize this to one backed-up systemd-managed process.
- Final production build passed immediately before upload: Vite built 1,663 modules, and Maven ran 1 test with 0 failures/errors before producing the Spring Boot jar.
- Created release `20260718-2037`, backed up current frontend/jars/config/unit, stopped only the failing systemd restart loop, and uploaded verified artifacts while the live manual backend continued returning 200.
- The first database dump emitted a tablespace privilege error masked by its pipeline; it is explicitly untrusted and will be replaced by a verified `--no-tablespaces` dump before cutover.
- Replaced the dump with a verified 110 KB gzip backup containing 10 table definitions and 10 data insertion sections.
- Reproduced the MySQL reserved-word failure with a temporary table, then patched both schema files and the MyBatis `AppSetting` field mapping to quote `sensitive`.
- Rebuilt and re-uploaded the corrected jar/schema; local and remote SHA-256 values match.
- Applied the idempotent production migration successfully. `app_settings` and `search_logs` now exist, 14 default settings were inserted, and all pre-existing business/user row counts remained unchanged.
- Staged the canonical systemd jar/config/environment and the six-file frontend release; `nginx -t` passes and the old manual backend still returns 200 before cutover.
- Cut over the backend to `opc-backend.service` and atomically switched `/var/www/opc`; backend/public HTTPS health checks all returned 200 and the deployed hashes match local artifacts.
- Verified administrator login with `opc2026`, authenticated settings retrieval, two-account listing, and logout against production; every call returned HTTP/application code 200.
- Opened the deployed HTTPS homepage in a real browser and confirmed the current Hero/media/data DOM, zero horizontal overflow, and an empty warning/error console.
- Browser-verified desktop/mobile user login, administrator login, dashboard, account list, SMTP/auth settings, email preview, and explicit administrator logout on the production domain.
- Completed final server audit: nginx/mysqld/opc-backend active, one Java process, zero service restarts, clean journal, HTTPS/API 200, current hashes verified, protected configs mode 600, and rollback artifacts present.
- **Deployment status:** complete at `https://findopc.online` using release `20260718-2037`.

## Post-deployment UI Hotfix (2026-07-18)
- **Status:** complete
- Production measurement confirmed the recent-update list had `gap: 0`, approximately zero distance between five bordered rows, and `12px 0` row padding.
- Added a scoped Prisma override for 12px/10px row gaps and desktop/mobile card padding; no data or component behavior changed.
- Added page-bottom copyright text to both login pages and changed the user auth form to reserve a disabled, invisible username slot in login mode.
- Before the stable-slot fix, browser measurements showed login/register panel heights of about 681px and 733px respectively; the regression check requires exact equality after HMR/build.
- Rebuilt successfully with Vite 7.3.6 and 1,663 transformed modules.
- Browser-checked `/login` and `/admin/login` at 1440x1000 and 320x844. Login and registration now measure identically, the hidden username field is disabled/non-focusable, copyright follows the card without overlap, document width does not overflow, and the warning/error console is empty.
- Uploaded release `/opt/opc/releases/20260718-213515/frontend`, verified all six SHA-256 hashes, and atomically switched `/var/www/opc` while retaining `/var/www/opc.rollback.20260718-213515`.
- Verified the public root, user login, administrator login, new JS, and new CSS all return 200. Nginx, MySQL, and `opc-backend.service` remain active; the backend was not restarted and reports zero restarts.
- Completed production browser QA for desktop/mobile login, registration, administrator login, and recent-update cards. Desktop card spacing is 12px with `18px 20px` padding; mobile spacing is 10px with `15px 16px` padding.

## Administrator Subdomain (2026-07-18)
- **Status:** complete
- Huawei DNS now resolves `admin.findopc.online` directly to `39.105.25.189`; Cloudflare proxying is not enabled or required.
- The administrator-only Nginx host, HTTPS certificate, main-domain administrator redirects, direct route refreshes, login, logout, and API access all passed production checks.
- `https://admin.findopc.online` is live; its certificate is valid through 2026-10-16 and automatic renewal is configured.

## ALTCHA Registration Protection and Final Release (2026-07-18)
- **Status:** complete
- Added the official ALTCHA v2 Java and browser integrations using `PBKDF2/SHA-256`, cost `5000`, and a 300-second challenge lifetime.
- Registration email-code requests now require a signed `register` proof. Login is unchanged; proofs are purpose-bound, expiry-checked, atomically consumed, and rejected on replay.
- Added administrator `注册验证` settings alongside account/session and mail settings. The HMAC secret is stored only in `/etc/opc-backend.env` with mode `600`.
- The backend package and ALTCHA tests passed, including valid proof, replay rejection, wrong-action rejection, disabled-mode compatibility, and Spring context coverage.
- The final frontend build passed with 1,669 transformed modules; `npm audit --omit=dev` reported zero vulnerabilities.
- Production protocol verification solved a real challenge, reached the existing-account guard without sending mail or creating an account, rejected replay, and rejected a missing proof.
- Browser QA confirmed desktop settings keep icons, 320x844 settings tabs remain single-line with icons hidden, `MVP 管理模式` is absent, and both relevant pages have zero horizontal overflow and no application console errors.
- ALTCHA initially added 14px of invisible host line height to registration. A scoped absolute 1px host rule restored exact equality: both 320x844 login and registration panels are `1105.104px`, and both forms are `381px`.
- Final production frontend release: `/opt/opc/releases/20260718-235925/frontend`; rollback: `/var/www/opc.rollback.20260718-235925`.
- Final server audit passed: Nginx config valid; Nginx, MySQL, and `opc-backend` active; one Java process; zero backend restarts; public/admin routes return 200; admin certificate expires 2026-10-16.

## Password Registration and Login (2026-07-19)
- **Status:** complete
- Registration now requires username, email, an 8-64 character password, and the registration email code. ALTCHA remains required before sending that code.
- Login now accepts username or email plus password. The legacy email-code login endpoint explicitly rejects requests with `邮箱验证码登录已停用，请使用密码登录`.
- Passwords are stored as BCrypt hashes in `platform_users.password_hash`; no password is returned in public or administrator API responses.
- Added the unique `uk_platform_users_username` index. Migration preserved both production accounts and found no duplicate usernames.
- Existing accounts retain their IDs, history, and sessions. Because their password hash is initially empty, the registration flow upgrades the existing account after email verification and revokes old sessions before issuing a new one.
- Administrator account management now labels both existing accounts `待设置密码`; no password was generated or assigned on their behalf.
- Added five `UserAuthServiceTest` cases and retained ALTCHA/context tests. Full backend result: 9 tests, 0 failures; frontend build: 1,669 modules; dependency audit: 0 vulnerabilities.
- Browser QA passed at 1440x1000 and 320x844. Login exposes only username/email and password; registration exposes username, email, password, and email code. Desktop forms are both 408px; mobile forms are both 470px with zero horizontal overflow and no application console errors.
- A temporary production QA account completed password login by username and by email, `/me`, and logout. Its sessions and user row were deleted in `finally`; the production account count returned from 2 to 2.
- Verified database backup: `/opt/opc/backups/20260719-003903/opc_platform.sql.gz`. Frontend/backend release: `/opt/opc/releases/20260719-003903`; final backend response patch: `/opt/opc/releases/20260719-004356`.
- Immediate rollbacks: `/var/www/opc.rollback.20260719-004124` and `/opt/opc-backend.rollback.20260719-004356`.
- Final hashes: frontend index `415e6d6f94c4a8a548fd46e74db3304c9aa882d5579f9c7540966583beb6939f`; backend `70fe571ca0c4b8ab311a9b08debed4cfa11866f840f41218cc8dba0698c2caf4`.
- Final audit: Nginx/MySQL/backend active, one Java process, zero backend restarts, zero post-release error-level journal entries, public/admin routes return 200.

## Administrator Login Description Alignment (2026-07-19)
- **Status:** complete
- Removed the inherited `max-width: 360px` constraint from the administrator login media description and enforced a single line.
- Desktop keeps the complete description; screens at 860px and below use the equivalent compact text `管理员入口仅用于平台内容管理。`.
- Production measurements: desktop width `420px`, mobile width `225px`, both `27px` high with a `27px` line height, proving one rendered line.
- Frontend build passed with 1,669 modules. Release: `/opt/opc/releases/20260719-005534/frontend`; rollback: `/var/www/opc.rollback.20260719-005534`.
- Nginx, MySQL, and backend remained active; the backend was not restarted and reports zero restarts.

## 5-Question Reboot Check
| Question | Answer |
|----------|--------|
| Where am I? | Phase 1, discovering and baselining the existing SoloFirm project. |
| Where am I going? | Architecture mapping, Prisma implementation, browser verification, and completion audit. |
| What's the goal? | Preserve every SoloFirm function while applying the supplied Prisma visual system to the user page. |
| What have I learned? | See findings.md. |
| What have I done? | Loaded required instructions/specification and initialized persistent planning files. |

## Account, Trend, Contact, And ALTCHA Follow-up (2026-07-19)
- Added permanent deletion for public users and administrator accounts. Public-user deletion removes active sessions first; administrator deletion rejects self-deletion and deletion of the final active administrator.
- Added stable three-action spacing for user rows and a protected administrator delete action in system settings.
- Unified the homepage seven-day trend and policy trend surfaces with their containing cards. Policy trends now support quarter, half-year, year, and all-time ranges with accessible monthly point details.
- Removed the homepage About frame, aligned the contact typography, and made the two contact information columns borderless and symmetrical.
- Fixed the administrator success-notice spacing with a dedicated layout slot so legacy paragraph margins cannot collapse it.
- Restored ALTCHA to a full-width standard surface with a square checkbox. A consumed proof is no longer required again when submitting the email code; re-sending a code explicitly starts a new proof.
- Backend `clean package` passed 22 tests; frontend production build passed with 1,671 transformed modules.
- Full release deployed at `/opt/opc/releases/20260719-033330`; final frontend follow-up deployed at `/opt/opc/releases/20260719-034543/frontend`.
- Per user preference, subsequent releases should be uploaded immediately after a successful build; browser acceptance is user-operated unless explicitly requested.

## Trusted Data And AI Platform Foundation (2026-07-23)
- **Status:** in_progress
- Initialized `.codegraph/` with `codegraph init -i` in the canonical OPC workspace.
- Indexed 182 files into 3,423 structural nodes and 7,045 edges.
- Confirmed `.gitignore` already excludes `.codegraph/`; no generated index file is tracked or listed by Git.
- Reviewed the revised dual-agent execution proposal against the actual Vue/Spring repository and `AI_READINESS.md`.
- Confirmed the first regression seams: authenticated policy export and authenticated, evidence-limited AI APIs.
- Began Phase 13 with one visible research assistant, provider-neutral server adapters, deterministic analytics, and governed ingestion as the architectural boundaries.
- Used CodeGraph to trace the complete policy export and user-session validation paths before editing.
- Added focused red/green coverage for administrator export authorization, public export routing, and published-only Excel content.
- Targeted export suite passes: 4 tests, 0 failures, 0 errors.
- Added `docs/baseline-audit.md` with the actual repository/deployment baseline, API matrix, data gaps, AI boundary, and verification gates.
- Added `AiClient`, immutable provider request/response/descriptor contracts, disabled and deterministic fake providers, and `SERVICE_UNAVAILABLE` handling.
- Added authenticated `GET /api/ai/capabilities`; anonymous requests return the existing 401 result and authenticated responses expose only safe readiness metadata.
- Focused AI tests pass: 6 tests, 0 failures, 0 errors.
- Full backend test suite passes: 48 tests, 0 failures, 0 errors.
- Frontend production build passes with 1,684 transformed modules.
- CodeGraph sync completed after the AI additions; the `AiClient` impact query reports only the expected provider, controller, configuration, and test symbols.
- The first production AI probe found a Spring Security ordering gap (HTTP 403 before the user-session interceptor). Added `/api/ai/**` to the permit list, extended the capabilities test with the real security filter chain, and confirmed the test is green.
- Rebuilt the backend after the security fix: full package and all 48 tests passed.
- Deployed release `20260723-233915` with timestamped database/config/frontend/backend backups and verified SHA-256 uploads.
- Production smoke checks passed for public routes and APIs; anonymous `/api/ai/capabilities` returns application code 401 instead of HTTP 403.
- **Deployment status:** complete at `https://findopc.online` and `https://admin.findopc.online`.

## AI Case Analysis Phase One (2026-07-24)
- **Status:** complete and deployed in disabled-provider mode.
- Added administrator `/api/admin/ai-settings` read/update/test endpoints with administrator identity audit records.
- Provider API Keys use versioned AES-256-GCM ciphertext; the server master key is stored only in `/etc/opc-backend.env` as `OPC_AI_SETTINGS_MASTER_KEY`.
- Added database-backed runtime provider switching, OpenAI-compatible chat-completions mapping, low-cost connection tests, timeout/retry controls, safe upstream errors, token usage, latency, and request-ID capture.
- Production cannot activate `FakeAiClient`; fake mode requires the explicit test-only `opc.ai.allow-fake=true` gate.
- Added `ai_evidence_status` to cases, policies, and sources. Historical rows remain `legacy_unverified`; administrators can explicitly mark reviewed records `verified` or `excluded`.
- Added `POST /api/ai/case-analysis` with backend-owned evidence loading, published/verified gating, quota and concurrent-run protection, strict JSON/citation validation, persisted results, and explicit evidence-insufficient responses.
- Added a standalone protected `/cases/:id/analysis` page without adding a homepage or case-detail entry, plus the administrator `智能体模型` Prisma settings surface.
- Verification: 64 backend tests passed; frontend Vite build passed with 1,687 modules; `git diff --check` and deployment-script syntax passed; CodeGraph is current at 240 files / 4,511 nodes / 8,800 edges.
- Production release: `/opt/opc/releases/20260724-024039`; backup: `/opt/opc/backups/20260724-024039`; rollback frontend: `/var/www/opc.rollback.20260724-024039`; rollback backend: `/opt/opc-backend.rollback.20260724-024039`.
- Production checks: public/admin routes and health return 200; anonymous AI/admin settings calls return 401; administrator AI settings are readable, secret-free, `encryptionReady=true`, `enabled=false`, and `apiKeyConfigured=false`.
- Remaining operator input: exact official API Base URL, exact DeepSeek V4 Flash Model ID, and a real provider API Key.

## Production Reverse Proxy And Service Hardening (2026-07-24)
- **Status:** complete and deployed.
- Added environment-controlled Spring binding, exact Nginx AI endpoint limits, and a hardened non-root systemd unit.
- Added five deployment regression tests, including the production server's `[::ffff:127.0.0.1]:8082` mapped-loopback representation.
- Backend package passed 64 tests; frontend Vite build passed with 1,687 modules; deployment tests and `git diff --check` passed.
- Deployed release `/opt/opc/releases/20260724-030722` with backup `/opt/opc/backups/20260724-030722`.
- Production uses the `opc` process user, loopback-only port 8082, `root:opc 0640` runtime configuration, and one Java process.
- Public/admin routes return 200; anonymous AI/admin settings requests return business 401; provider remains disabled and API Key data is not exposed.
- Direct-origin rate-limit verification returned six application responses followed by two Nginx 429 responses.

## Standalone Entrepreneurship Research Assistant (2026-07-24)
- **Status:** complete and deployed in disabled-provider mode.
- Added protected `/assistant` with a responsive profile-and-research layout, bounded follow-up questions, provider-disabled/loading/failure/retry states, structured recommendations, local case/policy links, expandable citations, and AI metadata.
- Added `POST /api/ai/entrepreneurship-advice`, versioned capability reporting, verified local evidence loading, industry relevance ordering, strict source-ID citation validation, quota/concurrency checks, and persisted task metadata.
- Extended `ai_analysis_runs` additively with `task_type` and nullable `case_id`; existing case-analysis rows retain the `case_analysis` default.
- Added an exact Nginx proxy for the new endpoint on public and administrator hosts using the existing 64 KiB body, timeout, cache, and rate-limit controls.
- Full backend verification passed with 77 tests; the Vue production build passed with 1,689 transformed modules; deployment-hardening tests passed with six checks.
- Production release: `/opt/opc/releases/20260724-050106`; backup: `/opt/opc/backups/20260724-050106`; frontend rollback: `/var/www/opc.rollback.20260724-050106`; backend rollback: `/opt/opc-backend.rollback.20260724-050106`.
- Production verification passed for `findopc.online`, `/assistant`, `admin.findopc.online`, health, anonymous AI/admin 401 boundaries, secret-free administrator settings, exact Nginx locations, loopback-only backend binding, and the `task_type`/nullable `case_id` migration.

## AI Evidence Stabilization (2026-07-24)
- **Status:** complete and deployed.
- Added a shared `AiTaskExecutionService` for both case analysis and entrepreneurship advice. It atomically reserves quota, prevents concurrent runs per user, settles actual usage even when parsing fails, and recovers expired running tasks.
- Evidence hashes now include case, policy, and source content/version data. Factual results with missing or invalid citations are rejected instead of being presented as evidence-backed analysis.
- Added the administrator evidence-review queue at `/admin/evidence-reviews` and its audited API. Cases and policies cannot become `verified` without a published, verified source containing a title and URL.
- The readiness route now requires authenticated user context, and deployment validation correctly permits an enabled provider only when its encrypted-key state, HTTPS Base URL, and Model ID are complete.
- Verification passed: backend package 97 tests, frontend production build 1,692 modules, auth-session test, deployment hardening test suite, and `git diff --check`.
- Production release: `/opt/opc/releases/20260724-073422`; backup: `/opt/opc/backups/20260724-073422`; frontend rollback: `/var/www/opc.rollback.20260724-073422`; backend rollback: `/opt/opc-backend.rollback.20260724-073422`.
- No production data was silently promoted to the golden evidence set. Its current verified, human-reviewed count remains `0` until a reviewer selects and verifies real records.
- Final stabilization republish: `/opt/opc/releases/20260724-074745`; backup: `/opt/opc/backups/20260724-074745`; frontend rollback: `/var/www/opc.rollback.20260724-074745`; backend rollback: `/opt/opc-backend.rollback.20260724-074745`.
- Post-release checks: public root, `/assistant`, administrator login, and health return HTTP 200; anonymous AI capabilities, entrepreneurship advice, and administrator AI-settings calls each return the expected application-level 401.

## First-Round AI Stabilization Fixes (2026-07-24)
- **Status:** complete and deployed; human evidence review remains.
- Added explicit industry recommendation confirmation, deterministic readiness, authenticated paid classification, stale-response guards, and three-state readiness presentation.
- Added immutable AI runtime snapshots, conservative full-prompt reservation, bounded model/tag-aware classification caching, and a bounded region-tree cache.
- Corrected multi-level geographic evidence classification and reduced readiness/evidence-review full-scan behavior.
- Unified sources on `published`, exposed the existing industry flag in administrator tag management, and added alias-aware historical case-tag migration.
- Stabilization migration now repairs each required column, primary key, unique key, and index independently; precheck/postcheck cover partial states and missed alias relations.
- Nginx and systemd now share `/opt/opc/current`; deployment prepares a timestamped release and atomically switches the `current` symlink inside one guarded rollback boundary.
- Verification passed: 118 backend tests, user-session test, assistant workflow test, 1,693-module frontend production build, 13 migration/deployment checks, Python syntax, CodeGraph sync, and `git diff --check`.
- Production golden evidence remains `0` before manual review; no legacy record has been silently marked verified.
- Production release: `/opt/opc/releases/20260724-232140`; current link: `/opt/opc/current`; database/config backup: `/opt/opc/backups/20260724-232140`; backend rollback artifact: `/opt/opc-backend.rollback.20260724-232140`.
- Post-deployment migration check returned `0`; Nginx, MySQL, and backend are active; loopback health passed; public root, `/assistant`, and administrator login return HTTP 200.
- Verified production evidence counts remain cases `0`, policies `0`, sources `0`; `active` source count is `0`. The 湖北省 + 人工智能应用 checklist remains the required administrator review input.

## Integrated Evidence Review Workbench (2026-07-25)
- **Status:** complete and deployed.
- Added a dedicated queue/detail evidence workbench with source grouping, searchable server filters, URL-restored context, review checks, full content, safe original links, related records, history, sticky decisions, approve-next, in-place correction, mobile navigation, and batch preflight.
- Closed ordinary evidence-status fields in case/policy/source DTOs and administrator forms. New records always start as `legacy_unverified`; verified content edits automatically invalidate and audit the affected evidence chain.
- Added monotonic evidence revisions, atomic status/version checks, source-joined child verification, same-operation cascade invalidation, governed deletes, safe source URLs, and differentiated audit action types.
- Added idempotent `20260725_evidence_workbench.sql` for evidence revisions and audit metadata, with deployment-time structure verification.
- Verification passed: 140 backend tests, five frontend test scripts, 14 migration/deployment tests, Vite production build with 1,703 transformed modules, and two-axis correctness/security review with all task-specific P0/P1 findings fixed.
- Production release: `/opt/opc/releases/20260725-014753`; current link: `/opt/opc/current`; database/config backup: `/opt/opc/backups/20260725-014753`; backend rollback artifact: `/opt/opc-backend.rollback.20260725-014753`; previous release: `/opt/opc/releases/20260725-001538`.
- Post-deployment checks passed for Nginx, MySQL, backend health, one loopback-only Java process, public root, administrator workbench route, anonymous admin API rejection, authenticated queue/detail/preflight, and evidence-workbench schema verification.
- Raw production evidence states are cases `verified=1 / pending=105 / excluded=0`, policies `0 / 68 / 0`, and sources `0 / 130 / 0`. The single verified case lacks a verified source chain, so the valid golden evidence set remains `0` and requires human review.

## Production Evidence Review Pass (2026-07-25)
- Reviewed the complete production inventory in source-first order: 130 sources, 68 policies, and 106 cases.
- Approved 38 sources after confirming a named publisher, published status, safe reachable original URL, and page content corresponding to the stored title and OPC/AI subject.
- Approved 36 policies after confirming the verified source relationship and matching the summary's key conclusions, dates, quantities, and monetary claims against the original page.
- Moved the historic verified case back to pending because its source publisher metadata remains incomplete. No case currently has a fully verified source chain.
- Retained all uncertain records as pending rather than excluding them: 92 sources, 32 policies, and 106 cases. No record was marked excluded.
- Current effective evidence counts are verified sources `38`, verified policies `36`, and verified cases `0`. The audit produced two atomic batch operation IDs and one single-review operation ID under administrator `ACha_`.

## Audited Agent Runtime Phase Two (2026-07-25)
- **Status:** implementation, verification, and combined Assistant Workspace production deployment complete in release `20260725-215634`.
- Confirmed the user-provided TDD boundaries for REST, orchestration, tool registry, evidence, provider, real-MySQL persistence, and frontend behavior.
- Confirmed the existing `ai_analysis_runs` ledger will be extended rather than replaced, and the four initial tools remain read-only and evidence-governed.
- Completed the case-analysis response diagnostics, complete JSON schema, evidence-insufficient audit ID, tag delete conflict, and 100-policy batch boundary through focused red-green slices.
- Added provider-neutral multi-turn/tool-call contracts, controlled JSON plans, owned sessions, ordered messages, the bounded run lifecycle, tool audit records, cancellation, expiry, idempotency, per-user/per-session active guards, quota aggregation, and stale-evidence rejection.
- Added the four read-only evidence tools, asynchronous user APIs, safe administrator audit APIs, persistent `/assistant` research sessions, polling recovery, cancellation/retry, evidence drawer, run metadata, and administrator run records.
- Added idempotent precheck/migration/postcheck SQL and a deployment probe that requires a completed run, at least one completed tool call, at least one legal citation, complete provider metadata, and cleanup; any semantic failure enters the existing rollback path.
- Initial Phase-Two verification passed before the stabilization pass; the superseding final counts are recorded in the stabilization section below.
- The 20-item deterministic evaluation passed all acceptance metrics; detailed contracts and results are recorded in `docs/agent-runtime-phase-two.md`.
- Production configuration inspection found `deepseek` / `deepseek-v4-flash`, provider enabled, API key configured, and the previous connection test successful. Agent fields are not present on the still-Phase-One production version.
- Production deployment and the paid-model Agent probe were not run because no valid `root` SSH credential is available through the repository's secure deployment path. No deployment version, rollback directory, live token usage, request ID, or citation count is claimed.

## Agent Runtime Stabilization And Trusted Evaluation (2026-07-25)
- **Status:** local acceptance complete; production deployment has not started.
- Added `20260725_agent_runtime_stabilization.sql` and hardened the Agent postcheck to validate 4 tables, 21 run columns, 10 settings columns, 7 foreign keys, 8 unique indexes, all 15 expected named indexes, and rollout consistency.
- Added explicit audited rollout state, persisted research context, lease/recovery columns, provider-call settlement records, and generated unique guards for `received` plus `running` work.
- Implemented durable database leasing, heartbeats, restart/expired-lease recovery, bounded attempts, dispatch-rejection recovery, session-first locking, late-usage reconciliation, and clarification convergence on verified database IDs.
- Unified all four tool contracts behind closed schemas and changed the model-facing search text field to `query`; unknown fields, wrong types, SQL/URL payloads, over-limit arrays, and unauthorized IDs are rejected before execution.
- Replaced keyword-based golden evaluation with 20 deterministic fixtures. Contract metrics: pass rate `1.0`, expected completion rate `1.0`, accepted unknown citations `0`, average model rounds `0.8`, average tools `0.7`, average tokens `12.45`, P50 `40 ms`, P95 `60 ms`.
- Hardened the deployment probe to require the exact 湖北政策 question, completed tool/citation audits, positive and consistent usage, distinct internal/provider request identifiers, authorization boundaries, evidence-snapshot citation membership, and secret-free API/audit output.
- Simplified the existing Agent UI surfaces by removing 3px colored rails, heavy shadows, and inconsistent backgrounds without changing structure, data, or interactions.
- Verification passed: backend `270` tests (`0` failures/errors, `1` opt-in DeepSeek smoke skipped); MySQL 8.4 `48`; Vitest `14`; all frontend contract scripts; deployment tests `17`; frontend production build; Spring Boot executable JAR; Python compilation; `git diff --check`; `.codegraph/` ignore; untracked build-artifact check; high-confidence secret scan.
- `OPC_SSH_PASSWORD` and initial-admin deployment variables are absent from the current process environment. Repository preflight stopped before SSH with the controlled missing-variable error; production is unchanged, and no release, backup, rollback path, real Provider usage, request ID, latency, or citation result is claimed.
- Read-only checks of the unchanged Phase-One production returned HTTP 200 for `https://findopc.online/`, `https://admin.findopc.online/admin/login`, and `https://findopc.online/api/health`; these are not Phase-Two deployment or Agent-probe evidence.

## Assistant Research Workspace And History (2026-07-25)
- **Status:** local implementation and verification complete; production deployment remains a separate credential-gated step.
- Added session title mode, pin/archive/trash/purge timestamps, three history/purge indexes, a forward-only repeatable migration, and pre/post verification SQL.
- Added authenticated history/search/cursor, message-page, usage, update, explicit archive/unarchive, trash/restore, and permanent-content-delete APIs while preserving old list and DELETE archive contracts.
- Added automatic first-question titles, terminal `latestRun` restoration, latest-50 message detail, older-message pagination, daily Agent token projection, and purged-session read guards.
- Added bounded scheduled cleanup with `FOR UPDATE SKIP LOCKED`; explicit SQL scrubs readable session, message, citation, tool, evidence, and run-result content while retaining minimal accounting metadata.
- Rebuilt `/assistant` as a desktop history rail plus document conversation, fixed composer, research-boundary panel, mobile history drawer, citation/process drawer, safe Markdown, search debounce/latest gate, per-session drafts, and limited exponential polling recovery.
- Added `markdown-it` and DOMPurify. The production build passes; `npm audit` reports six high-severity findings only in the existing `@vue/test-utils` development dependency chain, with no production dependency affected and no safe non-downgrade fix offered.
- Deployment now uploads and checksums all Assistant migration files, runs precheck/migration/postcheck after Agent Runtime, and expands the temporary real-Agent probe through history and lifecycle behavior.
- Final verification passed: backend `287` tests (`0` failures/errors, `1` opt-in DeepSeek smoke skipped), MySQL 8.4 `52`, Vitest `22` across 6 files, all `8` frontend scripts, Python `26`, frontend production build with `1,800` modules, Spring Boot executable JAR, Python syntax, `git diff --check`, `.codegraph/` ignore, build-artifact scans, and high-confidence secret scan.
- Deployed release `/opt/opc/releases/20260725-215634` through `/opt/opc/current`; backup `/opt/opc/backups/20260725-215634`, backend rollback artifact `/opt/opc-backend.rollback.20260725-215634`, and previous release `/opt/opc/releases/20260725-080213` are retained.
- Expanded production probes passed. The Assistant probe completed with `partial` evidence and 2,987 tokens. The real DeepSeek Agent probe completed with 3 model rounds, 2 completed tool calls, 1 legal citation, 0 unknown citations, 6,897 total tokens, and 7,916 ms latency.
- Independent post-deploy checks returned HTTP 200 for both public hosts and health. Anonymous user/admin APIs returned the established HTTP-200 response envelope with business code `401` and null data.
- The Phase 22 deployment facts above remain the current production baseline; later local changes do not imply another release.

## Assistant Workspace Stabilization (2026-07-26)
- **Status:** implementation, verification and production deployment complete in release `20260726-015858`.
- Added atomic first submission with stable, request-bound idempotency and durable `received` recovery.
- Added HMAC-signed history snapshot traversal, independent active/latest run queries, server-owned retry content, one-time auto titles and shared ledger usage semantics.
- Added purge content generations, guarded tool/provider/message finalization, minimal purge audits and repeatable multi-instance cleanup behavior.
- Rebuilt the Assistant height/scroll ownership, container-responsive research profile, user-isolated drafts, IME/scroll behavior, request generations, accessible drawers and unified searchable industry combobox.
- Added `20260725_assistant_workspace_stabilization.sql` plus expanded precheck, postcheck, deployment probe and real MySQL recovery coverage.
- Verification passed: Spring `299` tests (`0` failures/errors, `1` opt-in provider test skipped), MySQL 8.4 `58/58`, Vitest `57/57`, all `8` frontend scripts, Python default suite `63` with `6` opt-in cases skipped plus explicit Python MySQL `6/6`, JAR, Vite build, production npm audit, syntax, diff, secret, artifact and ignore checks.
- Deployed `/opt/opc/releases/20260726-015858`; backup `/opt/opc/backups/20260726-015858`, database dump `/opt/opc/backups/20260726-015858/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260726-015858`, and previous release `/opt/opc/releases/20260725-215634` are retained.
- Real `deepseek` / `deepseek-v4-flash` Agent probe completed with finish reason `stop`, 2 model rounds, 1 completed tool call, 1 legal citation, 0 unknown citations, 3,722 prompt tokens, 699 completion tokens, 4,421 total tokens and 7,842 ms latency. Atomic replay, more-than-50-record history pagination, lifecycle, usage, purge barrier, authorization and temporary-data cleanup also passed.
- Deployment hardening now rejects control characters in SSH commands, reconnects for rollback, and supports the cursor environment variable even when production uses a replacing external Spring YAML. Two pre-release failures rolled back to `20260725-215634`; the final release passed independently repeated production preflight.

## Assistant Acceptance Closure (2026-07-26)
- **Status:** implementation, full verification, production deployment, semantic probes, and independent post-deploy preflight complete in release `20260726-080227`.
- Added early-owned temporary probe identities, exact and idempotent partial-creation cleanup, cleanup-count verification, and dual-failure reporting without exposing credentials.
- Added the forward `20260726_assistant_history_revision` migration and signed cursor revision checks. Metadata changes return `HISTORY_CURSOR_STALE`; message activity remains stable under the existing snapshot watermark.
- Corrected tablet research-profile grouping and retained desktop, phone, Combobox keyboard, AI-confirmation, and 44px target behavior.
- Added two latch-controlled MySQL purge races covering active-run rejection and terminal purge followed by a denied late callback, including single settlement/audit and complete content scrubbing assertions.
- Verification passed: Spring `306` (`305` passed, `1` opt-in smoke skipped), MySQL 8.4 `62/62`, Vitest `60/60`, frontend scripts `8/8`, Python default `73` (`66` passed, `7` skipped), explicit migration `7/7`, deployment hardening `52/52`, combined deployment/migration `65/65`, frontend production build, executable JAR, Python syntax, diff, ignore, artifact, dependency and scoped secret checks.
- Deployed `/opt/opc/releases/20260726-080227`; retained backup `/opt/opc/backups/20260726-080227`, dump `/opt/opc/backups/20260726-080227/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260726-080227`, and previous release `/opt/opc/releases/20260726-015858`.
- Production checks passed for both domains, health, anonymous authorization, signed stale-cursor behavior, atomic start/history/message compatibility, temporary account cleanup, remote hashes and the single expected backend process.
- The real probe completed on `deepseek-v4-flash` with finish reason `stop`, 3 rounds, 2 completed tool calls, 3 provider calls, 1 legal citation, 0 unknown citations, 7,016 total tokens and 9,943 ms latency. The compatibility probe completed with 2,968 total tokens.
- Per instruction, no Playwright run was performed. Desktop, tablet, phone, low-height, sidebar and drawer visual combinations remain the user's manual acceptance surface.

## 2026-07-26 Readiness, First Question, Settlement, And History Closure
- Separated evidence dependencies from profile draft persistence; non-evidence edits no longer clear readiness, start loading, issue HTTP calls or disable the first question.
- Added new-research Composer copy while preserving atomic start, existing-session message send, pending idempotency identities and the single bottom Composer row.
- Added delayed-Provider cancellation settlement proof, purge settlement gating, one-time title revision, and a user-first lock order for concurrent atomic start replay.
- Added tablet coarse-pointer target contracts, long-list keyboard scrolling, and sanitized primary-error-preserving rollback handling.
- Verification passed: Spring `307` with `1` opt-in smoke skipped, MySQL 8.4 `63/63`, Vitest `65/65`, frontend scripts `8/8`, Python `74/74`, Vite/JAR builds, Python syntax, diff, ignore, artifact, production dependency and scoped secret checks.
- Deployed `/opt/opc/releases/20260726-092000`; retained `/opt/opc/backups/20260726-092000`, database dump `/opt/opc/backups/20260726-092000/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260726-092000`, and previous release `/opt/opc/releases/20260726-080227`.
- Production checks passed for both domains, health, anonymous authorization, readiness, atomic first start/replay, subsequent message/history/title behavior, temporary account/data cleanup, migration postcheck, matching artifact hashes and one expected backend process.
- The real Agent probe completed on `deepseek-v4-flash` with finish reason `stop`, 3 rounds, 3 Provider calls, 2 completed tools, 1 legal citation, 0 unknown citations, 6,956 total tokens and 8,593 ms latency. The compatibility advice probe completed with 2,996 total tokens.
- Per instruction, no Playwright run was performed. Desktop, 641-1023 px/coarse-pointer tablet, phone, low-height, expanded-profile, sidebar and drawer combinations remain the user's manual visual acceptance surface.

## Research Capability, Evidence Workspace, And Reliability Closure (2026-07-26)
- **Status:** implementation, verification, production deployment and independent preflight complete in release `20260726-162930`.
- Replaced the Assistant research desk's remaining brittle row layout with a bounded vertical flex chain. The conversation owns the only primary content scroll, and Header/profile/Composer remain visible and non-shrinking at low heights.
- Standardized Assistant secondary, danger and icon commands with explicit borders, hover/focus/active/disabled states, accurate labels and scoped 44 px coarse-pointer targets.
- Added the owned `GET /api/ai/research/runs/{runId}/evidence` contract and `AssistantEvidencePanel`, grouped by case, policy and source with current verified status, safe detail links, safe HTTP(S) originals and citation references.
- Added `agent-research-v2` planning and synthesis: closed task intents, bounded multi-tool plans, within-run ID dependencies, profile-aware output sections, legal citations, evidence coverage and compatibility Markdown plus versioned structured results.
- Expanded Hubei search through Wuhan descendants, ordered policy context across descendants/current/ancestors, and changed evidence readiness to useful `partial` completion rather than false insufficiency.
- Added the ten-scenario research-quality evaluation covering policy, Wuhan cases, comparison, technology, source verification, insufficient evidence, cross-region use, follow-up, budget and stage differences.
- Added independent atomic Provider settlement with row locking and rollback-safe Run reconciliation. MySQL tests reproduce the former partial-commit corruption and prove cancellation, purge and concurrent estimate replacement behavior.
- Removed compatibility history Active Run N+1 reads and hardened every deployment recovery stage to preserve the original sanitized primary error.
- The first two deployment attempts reached the real Agent gate, failed with `TRUNCATED_RESPONSE`, and automatically restored `/opt/opc/releases/20260726-092000`. The fixes separate plan/final schemas, remove the tool catalog from synthesis context, validate compact cross-section totals and give only Agent synthesis a bounded 2000-Token request budget inside the unchanged 8000-Token Run cap.
- The third attempt completed two model rounds and three tools but failed safely with `UNCITED_FACT`; it exposed that the public Schema permitted an uncited fact even though server validation rejected it. Facts now require at least one current-run `sourceId`, while inference and methodology remain explicitly distinct. The deployment gate again restored `20260726-092000`.
- The fourth attempt failed safely at planning with `INVALID_OUTPUT_SECTIONS` (`rounds=1`, `tools=0`). An initial exact-thirteen correction made the advisory plan field redundant and caused a fifth planning `TRUNCATED_RESPONSE`. The final red-to-green contract keeps a bounded unique relevant-section subset in planning and reserves the complete thirteen-field requirement for synthesis, where it is authoritative.
- The sixth attempt was rejected by the existing Provider connection test before rollout. The seventh reached synthesis after two tools but produced another empty-source fact; the new public orchestration regression proves that this is stored and rendered as `inference`, while forged or unowned source IDs remain rejected.
- The eighth candidate again reached the planning output ceiling. A red-capable Schema test exposed the legal `6x300` question plus `6x80` dimension surface; the final compact plan uses `4x120` and `4x40` bounds without reducing the six-tool execution limit.
- The ninth candidate reached synthesis after two tools but returned a Schema-valid array distribution above Java's aggregate result limits. Evidence and supplemental arrays now use per-field `2/1/1/2` limits whose maxima sum to the authoritative six-item caps.
- The tenth attempt was rejected before rollout by the Provider connection gate. The eleventh exposed case/policy IDs being reused as source IDs. The final synthesis prompt now lists the exact allowlist, and server-side normalization guarantees only owned source IDs reach structured history or citations while still requiring a legal citation to complete.
- The twelfth candidate was rejected during planning by the legacy generic `INVALID_AGENT_PLAN` branch and rolled back without changing the live release. The next build splits planning failures into eight fixed, content-free diagnostic codes so a later real probe can identify the exact contract branch without logging model output.
- Verification passed after the final corrections: Spring `322` (`321` passed, `1` opt-in real Provider smoke skipped), including MySQL 8.4 `67/67`; Vitest `73/73`; all 8 frontend scripts; Python default `77` (`70` passed, `7` explicit-MySQL skipped); explicit Python MySQL `7/7`; frontend build with 1,804 modules; executable JAR; syntax, diff, ignore, artifact, production dependency and scoped secret checks.
- The Vite main bundle is `627.07 kB`; the known warning remains a later performance item and did not trigger an unrelated split in this stability pass.
- No new Phase 26 database migration is required. Existing forward Assistant migrations remain repeatable and passed all recovery checks.
- Closed the production-only Hubei case gap with a controlled canonical industry-name and registered-alias fallback for legacy cases lacking the direct `1027` relation. The added MySQL test requires matching AI text evidence and rejects unrelated records.
- A final candidate was rejected with `TRUNCATED_RESPONSE` after two successful tools. A red-to-green orchestration contract corrected compact synthesis from 2,000 to 3,200 output tokens under the configured aggregate Run guard; the rebuilt candidate passed.
- Deployed `/opt/opc/releases/20260726-162930`; retained backup `/opt/opc/backups/20260726-162930`, database dump `/opt/opc/backups/20260726-162930/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260726-162930`, and previous release `/opt/opc/releases/20260726-092000`.
- Production hashes are frontend `43b5354cb0d7dbe09a3c0a1618d0a7af6a83b30455d04ba5d0a115958e05c517` and backend `3ed8b7346314cd75e23b14ad542fe77a65f2a2835c6d67aad2dc9d3865fe36ad`. Independent preflight found all three services active, one `opc` backend process and loopback port 8082.
- The real `deepseek-v4-flash` v2 probe finished `stop` with 3 rounds, 3 Provider calls, 2 completed tools, 5 case evidence items, 2 policy evidence items, 4 legal citations, 0 unknown citations, 11,039 tokens and 46,160 ms latency. Temporary accounts and probe data were cleaned.
- Manual visual acceptance remains for `1366x768`, `1280x600`, `1024x768` and `390x844`; Playwright was not used per instruction.

## 2026-07-26 Predeploy Provider Evaluation And Contract Closure
- Added the canonical `AgentResearchContract`, separate planning/synthesis schemas and validators, and fifteen sanitized real-failure replay fixtures.
- Replaced model-owned coverage with server-derived current-run case, policy, source and geographic-scope coverage; MySQL coverage tests reject unpublished, unverified and historical-run evidence.
- Added database-backed region resolution and current-run region authorization for cross-region searches, including Hubei/Wuhan, provincial, national and cross-region MySQL scenarios.
- Bound Assistant citation/evidence navigation to each message Run and retained safe HTTP(S)-only external links plus the existing Prisma Light interaction states.
- Added an isolated candidate database/runtime deployment gate. Provider connection and full Agent v2 probing now occur before `/opt/opc/current` is changed; cleanup is exact, idempotent and secret-free.
- Extended the candidate record to validate all runtime, citation, Token settlement, latency and case/policy/source coverage fields. Candidate failures preserve a controlled diagnostic plus scalar metrics and always report `release_switched=false` before rollout.
- Final local verification: Spring `341` (`340` passed, `1` opt-in smoke skipped), MySQL 8.4 `68/68`, Vitest `77/77`, all 8 frontend scripts, Python deployment/migration `83/83`, explicit Python MySQL `7/7`, Vite build, executable JAR, Python syntax, diff, ignore, artifact, dependency and high-confidence secret checks.
- The isolated real Provider candidate passed before rollout with `agent-research-v2`, 2 model rounds, 3 completed tools, 4 legal citations, zero unknown citations, matching server-derived coverage, `settled_actual`, zero reservation and `release_switched=false`.
- Deployed `/opt/opc/releases/20260726-213258`; retained backup `/opt/opc/backups/20260726-213258`, database dump `/opt/opc/backups/20260726-213258/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260726-213258`, and previous release `/opt/opc/releases/20260726-162930`.
- The postdeploy Agent probe completed with 2 model rounds, 2 completed tools, 4 legal citations, zero unknown citations and 9,965 total Tokens. Independent preflight confirmed matching hashes, three active services, valid nginx configuration, one `opc` backend process and loopback-only port 8082; temporary probe resources were cleaned by the guarded deployment workflow.

## 2026-07-26 Multi-round Tool Closure And Independent Assistant
- **Status:** local code, final tests and builds complete; isolated candidate rejected the case-comparison scenario, so production deployment did not run and Phase Two remains open.
- Added an initial-search/continuation protocol so actual `search_cases` results drive `compare_cases` and actual search source IDs drive `get_source` inside the same Run.
- Locked profile industry and server-derived region scopes; established sessions reject explicit research-boundary changes before persistence or Token reservation.
- Split full 128 KiB UTF-8 tool audit data from the 12 KiB compact `_authorized` model projection and aligned authorization/evidence counts with the projected items.
- Added available/total/unavailable evidence semantics and prevented unavailable material from resolving as a citation.
- Moved `/assistant` out of `MainLayout` into a lazy protected `AssistantLayout`, with mutually exclusive focus-managed history/evidence drawers.
- Moved candidate database migration, Provider connection and three real Agent probes ahead of every production database and release mutation.
- Final verification passed Spring `357` executed (`356` passed, one opt-in Provider smoke skipped), MySQL 8.4 `70/70`, Vitest `83/83`, all eight frontend scripts, Python deployment `77/77`, migration `14/14`, explicit MySQL migration `7/7`, Vite/JAR builds and repository gates.
- Candidate attempt 1 stopped at `INVALID_DEPENDENCIES` with 2 rounds, 2 tools, 8,440 Tokens and 19,697 ms. Candidate attempt 2 stopped at `UNCITED_FACT` with 2 rounds, 2 tools, 8,557 Tokens and 17,596 ms. Both recorded `release_switched=false` and led to bounded deterministic recovery tests.
- Candidate attempt 3 passed policy lookup, then returned `evidence_insufficient` for case comparison. It did not reach source verification, so the mandatory three-scenario gate did not pass.
- No Phase 28 database migration was added. Production backup/migration/switch/restart counts remain zero, and final preflight confirmed production is still `/opt/opc/releases/20260726-213258` with unchanged hashes and restored temporary identity counts.

## 2026-07-27 Real-provider Orchestration And Candidate Closure
- Added terminal-only continuation Schemas, current-run synthesis source allowlists and two bounded content-free planning recoveries. Server-derived terminal evidence now prevents redundant searches without weakening tool authorization or citation validation.
- Raised the measured defaults to five model rounds, 28,000 aggregate Tokens and a 3,200-Token planning response budget. Added the idempotent `20260727_agent_multiround_budget` precheck/migration/postcheck set; this changes settings data but adds no table, column or index.
- Ran four bounded candidate batches. The first proved policy lookup and source verification but exceeded the 24,000-Token comparison budget. Subsequent runs exposed controlled Schema/citation/question failures; the latest stopped at `PROVIDER_CONNECTION_FAILED` before scenarios. All completed Provider usage settled once, reservations returned to zero and `release_switched=false` remained true.
- Verified focused orchestration/contract `53/53`, Spring `368` with zero failures and one opt-in smoke skip, MySQL `70/70`, Vitest `85/85`, Assistant subset `35/35`, frontend scripts `8/8`, Python discovery `101` with seven explicit-MySQL skips, static migration `14/14`, both production builds, Python syntax and repository gates.
- Production mutation count remains zero. Current release and rollback assets remain `20260726-213258`; the next candidate run is deferred until Provider connectivity is plausibly restored. Phase Two remains open.

## 2026-07-27 Final Single-deploy Closure
- Added deterministic regressions for explicit comparison/source-verification questions misclassified by the model, conflicting model intent, ordinary general research, candidate metric retention, independent scenario aggregation and exact failed-release cleanup.
- Implemented server-owned `ResearchExecutionRequirements` and threaded optional `requestedIntent` through the HTTP DTOs, idempotency identity, Run lifecycle, worker and orchestrator. Model intent can raise but cannot lower server requirements.
- Updated the four Assistant starters to submit their matching intent on the first request, reset to `auto` after material prompt edits, and keep all continuation messages on per-message auto resolution.
- Added repeatable persistence for `ai_analysis_runs.requested_intent`; candidate and production validate the same precheck/migration/postcheck hashes before rollout.
- Candidate reports now preserve expected/actual/missing tools and all available safe scalar metrics before sequence gates. A failure in one scenario no longer prevents the other two from producing independent records.
- Added exact unswitched candidate-release cleanup with current/previous release protection and primary-error preservation. The earlier `/opt/opc/releases/20260727-070820` directory remains untouched as required.
- Final local gates passed: Spring `373`, MySQL `71/71`, Vitest `87/87`, Assistant `36/36`, eight frontend scripts, Python default `106`, explicit MySQL `7/7`, deployment hardening `85/85`, Vite build, executable JAR, Python syntax and repository checks.
- Remote preflight passed with active services, valid Nginx, one loopback backend, 60% disk use and sufficient verified evidence. The one allowed deploy invocation then ran all scenarios and stopped before production mutation.
- Policy failed `REQUIRED_TOOL_CHAIN_UNSATISFIED` after 3 rounds, 2 completed tools, 15,679 Tokens and 41,041 ms. Comparison passed its dynamic `search_cases -> compare_cases` chain in 4 rounds/3 tools with 6 citations and 24,165 Tokens. Source verification passed `search_policies -> get_source` in 4 rounds/3 tools with 2 citations and 18,257 Tokens.
- Fixed the newly exposed diagnostic-order gap with a red-to-green test: failed Runs now record actual tool sequence before terminal validation without replacing their server diagnostic. Deployment hardening passes `86/86`; full Python discovery passes `107` with seven opt-in MySQL skips. The fix was deliberately not redeployed this round.
- Verified `release_switched=false`, no new production backup/migration/restart, current release `/opt/opc/releases/20260726-213258`, and complete cleanup of candidate service, database/user, environment file and current-attempt release. Phase Two remains open.

## 2026-07-28 Intent Priority Stabilization And Bounded Deploy
- Added red-to-green requirements and orchestrator tests for explicit-intent authority, deterministic server-operation authority, auto fallback to model intent, and stable general/technology intents.
- Fixed the explicit policy priority inversion and retained API/database compatibility. Added bounded comparison search, correction-order and continuation-schema regressions without raising runtime caps.
- Candidate reporting now captures actual sequence before terminal checks and treats SQL `NULL` as no tool result. Deployment hardening passes `88/88`.
- Local gates passed: Spring `380` (zero failures/errors, one opt-in Provider skip), MySQL 8.4 `71/71`, Vitest `87/87`, Assistant `36/36`, eight frontend scripts, Python `109` with seven skips plus explicit MySQL `7/7`, Vite/JAR builds and repository checks.
- Attempt accounting: one initial candidate failed transient Provider connection; its one same-build retry failed comparison insufficiency; corrective candidate 1 failed policy `PLAN_REPEATED`, comparison `UNCITED_FACT`, and source `UNCITED_RECOMMENDATION`; corrective candidate 2 passed policy/source and repeated comparison `CANDIDATE_CASE_COMPARISON_EVIDENCE_INSUFFICIENT`.
- Final candidate details: policy `search_policies, search_policies, search_cases`, 2 rounds, 3 tools, 2 citations, 8,765 Tokens, 25,588 ms; comparison `search_cases, search_policies`, 3 rounds, 2 tools, 0 citations, 16,362 Tokens, 37,852 ms; source `search_policies, get_source`, 4 rounds, 2 tools, 2 citations, 17,218 Tokens, 42,430 ms. Every scenario settled actual usage and had zero reservation.
- Bounded stop applied after the same deterministic comparison diagnostic recurred following narrow fixes. Production switch count is zero. Current remains `/opt/opc/releases/20260726-213258`; candidate service/database/user/env/port/release counts are all zero.

## 2026-07-28 Deterministic Tool-chain And Production Closure
- Added zero-result, one-plus-one, duplicate-ID, reserved-budget, missing-required-tool and unsupported-dimension Red replays. Required comparison now performs at most two searches and cannot accept an early model insufficiency when two authorized cases exist.
- Added server-owned required-tool completion without bypassing audit, request authorization, evidence hashes, replay verification, Token caps or profile boundaries.
- Added safe per-tool diagnostics and candidate parsing; a real MySQL header shape was converted into a deterministic parser regression before retry.
- Final local verification passed focused `70/70`, Spring `387`, MySQL `71/71`, Vitest `87/87`, Assistant `36/36`, eight frontend scripts, Python `110` plus explicit MySQL `7/7`, deployment hardening `89/89`, Vite/JAR builds, syntax, diff, ignore, artifact and scoped-secret checks.
- Deploy attempt 1: transient `PROVIDER_CONNECTION_FAILED`, no scenarios, no switch. Attempt 2: all research chains completed but report parsing failed `CANDIDATE_AGENT_TOOL_DIAGNOSTIC_INVALID`, no switch. Attempt 3: policy/source passed; comparison failed `INVALID_TOOL_ARGUMENTS` after 4 authorized cases, no switch. Each candidate was fully cleaned.
- Deploy attempt 4 passed policy, comparison and source verification in one candidate batch, then performed the only production switch to `/opt/opc/releases/20260728-130142`.
- Candidate metrics: policy 2 rounds/1 tool/7,503 Tokens/19,955 ms/2 citations; comparison 2 rounds/2 tools/10,281 Tokens/34,003 ms/2 citations; source 4 rounds/2 tools/18,544 Tokens/65,468 ms/2 citations. All settled actual usage and zero reservation.
- Production postchecks passed both domains, health, anonymous auth rejection, user/admin boundaries, history/latestRun, evidence/citations, migrations, Nginx, one `opc` process and loopback 8082. Three independent production chains passed and their temporary user/session counts returned to zero.
- Current, hashes, backup, database dump, rollback backend and previous release were independently verified. Candidate service/database/user/env/18082 counts are zero. Agent Phase Two is closed; only manual desktop/tablet/phone visual review remains.

## 2026-07-29 Phase Three Specification Preparation
- Loaded all eleven required skills and the Impeccable product register after reading PRODUCT.md and DESIGN.md.
- Synchronized the ignored CodeGraph index, then audited current Assistant/public frontend, AI/domain backend, 26 deployment SQL files, production schemas and the MySQL 8.4 integration fixture path.
- Reused `.codex_deploy_opc.py` secret loading, pinned SSH fingerprint and remote database credentials without printing secrets. Ran only SELECT/SHOW against production business tables; no user/admin/session content was queried.
- Recorded the production audit window `2026-07-29 01:08–01:10 CST`, MySQL `8.0.46`, UTC+08:00 and release `/opt/opc/releases/20260728-130142`.
- Quantified 105 eligible case rows, 57 eligible policies and 121 eligible sources; measured coverage, policy time distribution, evidence rates, policy applicability, multi-industry cardinality and duplicate candidates.
- Froze metric definitions, revenue prohibition, taxonomy forward plan, product IA, analytics API, backend handoff, evaluation gates and A/B/C roadmap in seven new documents under `docs/`.
- No application source, database migration, production data, service, symlink or deployment was changed. Scoped documentation/repository checks are the only verification required for this round.

## 2026-07-29 Phase Three Specification Stabilization
- Read the eight required skills, PRODUCT.md, DESIGN.md, all seven Phase Three specifications, current SecurityConfig/AiWebMvcConfig/UserAuthInterceptor, Agent start/message/profile policies, requestedIntent, tool authorization, structured result and citation contracts.
- Stabilized Analytics auth wiring, phase3-task-v1 and structured results, region roles, technology response states, report/feedback lifecycles, completion naming, completeness macro averages, revenue algorithms, command colors and the 26-file SQL inventory fact.
- Completed two independent read-only specification reviews. Resolved all reported conflicts around starter auto fallback, policy_lookup schema naming, explicit from-analytics regionRole, approved-primary operation counting and feedback rating/reason pairing.
- Markdown relative-link check: PASS across all 7 Phase Three documents; no missing local targets.
- `git diff --check`: PASS with no whitespace errors.
- High-confidence secret scan: PASS; no AWS/GitHub/OpenAI-style token or private-key matches in changed Markdown.
- Ignore checks: PASS for both `.codegraph/` and `.local-secrets/`.
- Change-scope check: PASS for 11 Markdown files (`AI_READINESS.md`, `task_plan.md`, `findings.md`, `progress.md`, and 7 Phase Three docs); no Java, Vue, SQL migration, deployment-script or other application changes.
- Per the stabilization scope, Spring Boot, Testcontainers, Vitest, Vite build, JAR packaging, production database/SSH access and deployment were not run.

## 2026-07-29 Phase A Contract Final Closure

- Re-read the seven Phase Three specifications and the current Agent start/session/history/run/result/evidence/purge paths; confirmed current Run evidence_hash is enqueue identity, not a completion-time evidence version.
- Froze taskContext session version/json/hash storage, canonical hashing, four-part idempotency identity, immutable follow-up behavior, owner-only start/detail/run readback, summary-only history, purge erasure and free-text log redaction.
- Added source_verification selected-source and claim-search modes without opening arbitrary URL fetching or text-derived ID authorization; retained non-empty start content.
- Added a closed Draft 2020-12 phase3 structuredResult schema, six placeholder examples and negative seams. Python jsonschema 4.26.0 validated the schema and all 6 examples.
- Separated mandatory Phase A evidenceVersion from nullable Analytics dataVersion, fixed report version fields, region unavailable/partial/empty behavior, automatic 30-day report purge and the single feedbackEligible matrix.
- Phase A contract closure assertions: PASS; the previous seven RED categories are closed and no conclusion-level legacy reference name remains.
- Markdown relative-link check: PASS across all 11 modified Markdown files; no missing local targets.
- `git diff --check`: PASS. `.codegraph/` and `.local-secrets/` ignore checks: PASS. Added-line high-confidence credential scan: PASS. Scope check: PASS, Markdown only.
- No Java, Vue, CSS, SQL migration, deployment script, generated artifact or production state was changed. Spring Boot, Testcontainers, Vitest, Vite, JAR, Playwright, SSH, database access and deployment were intentionally not run.

## 2026-07-29 Phase A v1 Structured Result Implementation Gate

- The initial read-only gate reproduced all `8/8` final-review conflicts before the contract was edited.
- Contracted the v1 result to the existing runtime: synthesis `3200` Tokens, directAnswer `600` characters, aggregate ClaimItem count `6`, citations `6`, and compatible Assistant rendering `12000` characters. Every schema string and array now has an explicit bound.
- Split immutable taskContext-derived `taskSelectedEvidence` from server-only current-Run `authorizedEvidence`. The latter is capped at `120` per ID type and `120` combined case/policy IDs from the existing `12 x 10` tool/search ceiling.
- Repaired all six placeholder fixtures and froze one-to-three explicit comparison dimensions plus JSON-body report lifecycle compare-and-set.
- The former Schema-only `6/6` result remains valid, but the former service-semantic `6/6` conclusion is superseded because those fixtures did not encode case/source links or independently recomputable evidenceVersion inputs.
- Cross-document field/number assertions: `11/11`. Markdown relative links: PASS for all `11` changed Markdown files. `git diff --check`: PASS.
- Added-line high-confidence credential scan: PASS across seven patterns. `.codegraph/` and `.local-secrets/` ignore checks: PASS. Worktree scope: PASS for `11` changed paths, all Markdown.
- No Spring, Testcontainers, Vitest, Vite, JAR, Playwright, SSH, production database, Provider or deployment command was run. Phase A implementation and deployment have not started; the specification freeze awaits the replacement evidence-fixture gate.

## 2026-07-29 Phase A v1 Explicit Evidence And Recomputable Fixture Closure

- Ran the pre-edit deterministic gates: RED-A failed on all `6/6` explicit-selection conflict signals and RED-B failed on all `6/6` evidence-fixture/recomputation signals.
- Froze pre-persistence rejection for invalid explicit case/source selections as HTTP 400 `PHASE3_CASE_NOT_ELIGIBLE` / `PHASE3_SOURCE_NOT_ELIGIBLE`, with no session, message, Run, Token reservation or evidence projection. Reserved `evidence_insufficient` for accepted Runs that become insufficient during controlled research.
- Added a test-only `phase3-run-evidence-fixture-v1` to every one of the six examples, including entity revision/content hashes/eligibility and machine-checkable case-source/policy-source links. It is explicitly outside the production Schema, API and persistence contract.
- Replaced all zero evidenceVersion placeholders with independently calculated SHA-256 values over the fixed-order canonical authorized-evidence object. Reordering normalizes to the same digest; changes to IDs, revisions, content hashes, eligibility or links invalidate the prior digest.
- Final contract gate: Draft 2020-12 meta-validation PASS; structuredResult Schema `6/6`; complete service-semantic fixtures `6/6`; evidenceVersion independent recomputation `6/6`; positive contract/document assertions `28/28`; original reason-specific negatives `19/19`; new reason-specific negatives `20/20`.
- Final repository gates: Markdown relative links PASS for all 11 changed Markdown paths in the current worktree; `git diff --check` PASS; added-line high-confidence credential scan PASS across 7 patterns; `.codegraph/` and `.local-secrets/` ignore checks PASS; scope PASS with 11 changed Markdown paths and no untracked or non-Markdown path.
- The replacement gate closes both reproduced P1s and restores the formal Phase A v1 specification freeze. No Phase A runtime code, application test, migration, production access, Provider request or deployment was performed.

## 2026-07-29 Phase A v1 Final Targeted Contract Patch

- Ran the pre-edit deterministic gates: RED-1 failed `3/3` start transaction/idempotency ordering conditions, RED-2 failed `5/5` non-vacuous policy-source conditions, and RED-3 failed `4/4` fixture uniqueness conditions. A duplicate fixture entity also changed the naive pre-validation evidence hash.
- Froze the start order so the transaction resolves a locked `userId + idempotencyKey` record before any authoritative evidence check. Exact successful replay returns the original receipt; mismatch returns `409`; only a miss locks and revalidates evidence and relations before atomically creating all research records, reserving Token and saving the receipt.
- Froze revocation races: a revocation before the evidence lock causes `400` and a full rollback; a revocation while start holds the lock waits, after which the accepted Run may become `evidence_insufficient` only through execution-time revalidation.
- Replaced the vacuous `policy_lookup` example with the non-empty `policy 2001 -> source 9004 -> fact -> citation` fixture and independently recomputed evidenceVersion `sha256:8491a7a0ad58ec5c91ef9a7d90553817d7d0049ae40f3f0e99a91f96bd4317aa`.
- Froze per-entity-type ID uniqueness and per-link-type pair uniqueness before canonical sorting/hashing, while retaining legal many-to-many evidence relationships. No runtime code, migration, UI, production access, Provider request or deployment was performed.
- Final targeted gate: Draft 2020-12 meta-validation PASS; structuredResult Schema `6/6`; complete fixture semantics `6/6`; evidenceVersion recomputation `6/6`; start transaction/idempotency `8/8`; policy-source `10/10`; fixture uniqueness `9/9`; original negatives `19/19`; existing reason-specific negatives `20/20`; new targeted negatives `9/9`; frozen budgets, report CAS and legacy-field checks PASS.
- Final repository gates: cross-document forbidden-pattern and legacy-field scans PASS; Markdown relative links PASS for all 11 changed Markdown paths; `git diff --check` PASS; `.codegraph/` and `.local-secrets/` ignore checks PASS; added-line high-confidence credential scan PASS across 7 patterns; Markdown-only scope PASS. No application test, production access or deployment command was run.

## 2026-08-01 Phase Three Productization Delivery Audit

- Reconciled the delivery documents with the actual current worktree. Phase Three is no longer documentation-only: the repository now contains implemented taskContext persistence, structured results, branch material reuse, preferences, reports, run feedback, admin quality aggregation, Analytics snapshots, and the protected Analytics workspace.
- Recorded the current branch-research boundary: `branch-material` returns sanitized `taskContext`, taskContext version/hash, result summary, citations, evidenceVersion, source session/run IDs and intent hints; the frontend stores only a local branch draft and still waits for the first valid send before creating the new session/Run.
- Recorded the current report/export boundary: report creation is tied to the completed Run/final-message path and keeps the saved citation manifest as the export/read boundary; this round does not claim PDF export or production report probes.
- Recorded the current Analytics boundary: overview plus `industry.case_count` are implemented, but the industry slice remains `Yellow`, low-sample buckets degrade to textual treatment, and canonical business-case de-duplication is not yet available.
- Local verification passed: Spring Boot `444` tests (`0` failures, `0` errors, `1` opt-in real DeepSeek smoke skipped), MySQL 8.4 `76/76`, frontend Vitest `119/119`, all existing frontend npm scripts, Vite production build, and Spring Boot executable JAR packaging.
- This closeout deliberately does not claim Python deployment/migration command results, SSH preflight, rollout, rollback, or production probes. Those results were not appended in this round because they were not rerun here as authoritative command evidence.
- **Status:** local Phase Three implementation, local verification, and delivery-document audit complete. Deployment remains unexecuted in this round.

## 2026-08-08 Phase Three Deep Closure - Slice A

- Added failing public-behavior tests for all five source-verification verdicts, readable explanations, conflict source preservation, unauthorized sources, and uncited support; the RED run failed on the existing binary mapping as expected.
- Added the server-owned verdict calculator and connected it to the structured result, bounded response schema, orchestrator validation, synthesis instruction, and existing Vue evidence-status surface.
- Verification passed: `PhaseThreeStructuredResultAssemblerTest` 8 tests; `AgentOrchestratorTest,PhaseThreeStructuredResultAssemblerTest,AgentToolRegistryTest` 67 tests; `AssistantStructuredResult.spec.js` 8 tests.
- No Provider request, production connection, migration, or deployment was executed for this slice.

## 2026-08-09 Source Verification Insufficient-State Closure

- Reproduced the public RED cases for `final` with no verificationClaims and for unresolved-only claims: legacy directAnswer, key findings and citations were visible or persisted despite a server-insufficient verdict. Reproduced the equivalent historical-payload Vue failure.
- Implemented one server-owned insufficient decision path. Sanitized results use the fixed evidence-insufficient answer, empty factual collections and citations, zero fact coverage with `ratio=null`, unknown publisher assessment, and source-free methodology invalidity reasons only.
- AgentOrchestrator now uses the assembled verdict/evidenceStatus for Markdown and citation settlement. The saved message, structuredResult, refreshed history and final citation list therefore share the sanitized outcome.
- Frontend suppression covers old malformed `source_verification/insufficient` payloads without changing layout or CSS.
- Verification: focused backend `66/66`; expanded backend `87/87`; non-MySQL Spring `465` run with `0` failures, `0` errors, `1` existing skip; MySQL 8.4 `80/80`; full Vitest `173/173`; all eight existing npm scripts passed; Vite build and Spring Boot JAR package passed; Python migration `17/17`, hardening `103/103`, `py_compile`, `git diff --check` and ignore checks passed.

## 2026-08-11 Assistant Release Completion

- Replaced the production deployment probe's broad research prompt with a bounded real `source_verification` request and source-only evidence requirement. Candidate policy, case-comparison and source-verification coverage remains unchanged.
- Corrected the MySQL runner's exact pass count from 80 to 81 after the persisted unpin regression increased the integration class count. Runner tests were RED before the constant change and GREEN `6/6` afterward.
- Current acceptance: frontend Vitest `32/210`; eight frontend package scripts; Vite `1836` modules; Spring `549` (`0` failures/errors, `1` skip); MySQL 8.4 `81/81` with zero owned-container leak; JAR; Python `133` with `7` skips; migrations `17/17`; deployment hardening `103/103`; syntax, diff, high-confidence credential, build-artifact and `.codegraph/` checks all passed.
- One formal deployment succeeded. `/opt/opc/current` now resolves to `/opt/opc/releases/20260811-003256`; backup `/opt/opc/backups/20260811-003256`; dump `/opt/opc/backups/20260811-003256/opc_platform.sql.gz`; rollback `/opt/opc-backend.rollback.20260811-003256`; previous `/opt/opc/releases/20260810-183007`.
- Independent postflight returned HTTP 200 for the public route, `/assistant`, administrator login, `/api/health`, and the deployed Assistant JS/CSS. Nginx, MySQL and backend services are active; backend remains loopback-only as `[::ffff:127.0.0.1]:8082`.
- Docker/Testcontainers cleanup was confirmed. No database migration or production deployment was executed in this local verification phase.

## 2026-08-09 Production postflight

- Candidate-only guarded deployment passed with no production database mutation and no release switch; cleanup counts were zero.
- Formal deployment passed and switched `/opt/opc/releases/20260809-150138`; previous release `/opt/opc/releases/20260809-133127` remains available. Backup `/opt/opc/backups/20260809-150138`, dump `opc_platform.sql.gz`, and rollback `/opt/opc-backend.rollback.20260809-150138` were retained.
- Postflight confirmed active services, valid Nginx, current release, loopback listener, matching local/remote frontend hash, public and admin routes, `/api/health`, and anonymous AI/Analytics/admin `code=401` envelopes.
- The guarded candidate and production probes exercised source verification with authorized tools and no unknown citations; temporary probe data and candidate resources were cleaned.

## 2026-08-10 Assistant Research Workspace Phase Four

- Rebuilt `/assistant` as a quiet entrepreneurship-research workbench: history rail, single reading column, local first-research starter, continuation-only Composer, and one on-demand Inspector for conditions, materials, citations, process, and reports.
- Preserved existing authenticated history, drafts, lifecycle controls, run polling/retry/cancel, evidence authorization, citations, reports, profile, quota, and backend request/response contracts. No Provider/tool/API redesign was introduced.
- Removed Assistant `100vh` sizing, nested/competing scroll ownership, fixed content-overlap behavior, unguarded hover, and perpetual animation. Added responsive grid/flex shrinking, `100dvh`, safe-area padding, overflow-safe rich content, focus-managed mobile drawers, and reduced-motion behavior.
- Final evidence: Spring `547` (0 failures/errors, 1 opt-in skip), MySQL 8.4 `80/80` with owned container count 0, explicit MySQL migration `7/7`, Vitest `191/191`, focused Assistant `69/69`, eight frontend scripts, Vite/JAR, Python `133/133` with seven opt-in skips, syntax/diff/secret/artifact/ignore checks.
- Ran one remote preflight then one guarded deployment. `/opt/opc/current` is `/opt/opc/releases/20260810-112153`; backup `/opt/opc/backups/20260810-112153`, dump `/opt/opc/backups/20260810-112153/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260810-112153`, previous `/opt/opc/releases/20260809-193452`.
- Independent postflight confirmed public, admin, Assistant, health, static JavaScript, anonymous `code=401`, matching frontend hashes, active services, loopback-only 8082, backend user `opc`, and zero candidate units/environment files. Playwright was not used; manual viewport acceptance remains at 1440px, 1024px, 768px, and 375px.

## 2026-08-10 Assistant Motion Refinement (Local Only)

- Added restrained pointer-only motion to existing Assistant menus, mobile history/Inspector drawers, sidebar fold/reveal, Inspector close, toasts, run-stage replacement, and button hover/press feedback without changing product data, API calls, or runtime state.
- Corrected the desktop history fold with a transform-driven mask that retains the left 64px command rail, matching the final collapsed geometry and avoiding a blank/right-shifted intermediate state.
- Keyboard actions remain immediate; fine-pointer hover is gated; reduced motion suppresses spatial transitions. No loops, `transition: all`, bounce, `ease-in`, or layout-property animations were introduced.
- Verification: focused history/workspace Vitest `15/15`; the completed motion suite `109/109`, full Vitest `198/198`, eight frontend scripts, and production Vite build passed. No deployment was run; production remains `/opt/opc/releases/20260810-112153`.

## 2026-08-10 Assistant Motion Refinement Production Release

- One explicit guarded formal deployment passed its isolated candidate gate, additive/resumable migration checks, backup, atomic current-link switch, and cleanup.
- `/opt/opc/current` now resolves to `/opt/opc/releases/20260810-130518`; backup `/opt/opc/backups/20260810-130518`, dump `/opt/opc/backups/20260810-130518/opc_platform.sql.gz`, rollback `/opt/opc-backend.rollback.20260810-130518`, previous `/opt/opc/releases/20260810-112153`.
- Independent postflight: HTTP 200 for public, Assistant, admin, health, and deployed JavaScript; active Nginx/MySQL/backend; loopback-only 8082; backend user `opc`; candidate units, environment files, and databases all 0.

## 2026-08-10 Assistant 500ms Motion Continuity Release

- Replaced the desktop history rail's `grid-template-columns` animation with a staged, pointer-only 500ms continuity transition. The real layout changes once; the history cover and research desk use only `transform`/`opacity`, removing the reading-column reflow that caused the visible tear.
- Preserved the fixed left 64px icon rail, connected pointer selection to the mobile drawer close transition, and applied `inert` plus `aria-hidden` to extended history controls while a fold is in progress. Keyboard toggles and reduced-motion paths remain immediate.
- Targeted history/workspace Vitest passed `20/20`; full frontend Vitest passed `32/32` files and `203/203` tests; all eight package scripts, Vite build (`1836` modules), Spring Boot `547` tests (`0` failures, `0` errors, `1` skip) and JAR, MySQL 8.4 Testcontainers, Python deployment/migration tests (`126` passed, `7` skipped), syntax, diff, credential, artifact, and ignore checks all passed.
- One formal deployment switched `/opt/opc/current` to `/opt/opc/releases/20260810-155624`. Backup `/opt/opc/backups/20260810-155624`, dump `/opt/opc/backups/20260810-155624/opc_platform.sql.gz`, rollback `/opt/opc-backend.rollback.20260810-155624`, previous `/opt/opc/releases/20260810-130518`. Public, Assistant, admin, health, and latest static assets passed HTTP 200; Nginx/MySQL/backend were active, 8082 remained loopback-only, and no candidate residue remained.

## 2026-08-10 Assistant Inspector And Motion Finish

- Completed RED/GREEN behavior coverage for inline authorized-evidence source hashes: the click opens the current Run's real citation Inspector; ordinary external links are not intercepted. Corrected report Inspector top spacing and made the 500ms desktop history rail show only its pseudo-element boundary while the static border is transparent in flight.
- Verification passed: frontend Vitest `32` files / `206` tests; seven npm package scripts; Vite (`1836` modules); Spring `547` (`0` failures/errors, `1` skipped), including MySQL 8.4 `80/80`; JAR packaging; Python `133` with `7` skips; explicit MySQL `7/7`; syntax, diff, secret, artifact, and `.codegraph/` checks.
- One frontend-only atomic release moved `/opt/opc/current` to `/opt/opc/releases/20260810-183007` (frontend hash `4ba885...c54b`); `/opt/opc/releases/20260810-155624` remains the rollback release. No SQL, JAR, API, migration, or new database backup was involved. Public, `/assistant`, administrator login/settings, `/api/health`, and new JavaScript/CSS returned HTTP 200; services are active, Nginx is valid, and 8082 is loopback-only.

## 2026-08-11 Ordered Sidebar Control And Responsive Release

- [x] Add a pointer-only 120ms New Research control preparation stage before the 500ms desktop grid-rail fold; the plus icon is settled before the rail moves.
- [x] Remove fixed 276px motion content widths so title/search/history content cannot paint over or pop across the actual rail boundary.
- [x] Add the high-specificity mobile/tablet drawer override so a persisted desktop collapse never reduces the 375px/768px history drawer's New Research command to a 44px icon.
- [x] Preserve 1024px desktop Inspector behavior, focus/inert controls, 44px targets, safe areas, immediate keyboard operation, and reduced-motion behavior.
- [x] Pass layout/history `96/96`, full frontend Vitest `32/212`, all eight package scripts, Vite (`1836` modules), diff/artifact/ignore gates; publish one guarded release at `/opt/opc/releases/20260811-014621` with backup `/opt/opc/backups/20260811-014621`, dump `/opt/opc/backups/20260811-014621/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260811-014621`, and previous release `/opt/opc/releases/20260811-012139`. Public, Assistant, admin, health, and new Assistant assets returned HTTP 200.

## 2026-08-11 Sidebar Motion Handoff Closure

- [x] Use the supplied recording to reproduce the desktop rail clipping sequence instead of inferring it from static screenshots.
- [x] Write failing behavior tests for deferred collapse geometry and for releasing expanded history content from `inert` at its visible handoff; make both pass with a 120ms compact stage, 500ms rail move, and 320ms expansion handoff.
- [x] Replace the expansion keyframe with interruptible transitions, keeping only fixed-width controls visible during rail interpolation.
- [x] Verify targeted Assistant `101/101`, full Vitest `32/218`, eight frontend package tests, Vite, Spring `549` (`0/0/1`), MySQL runner `81/81` zero leak, JAR, Python `126` passed plus `7` skips and explicit migration `7/7`, syntax and repository gates.
- [x] Pass remote preflight and perform exactly one frontend-only atomic deployment: `/opt/opc/releases/20260811-032203`, hash `cd42620b363ac4ec8e8e3d08e0be4522e6ac212caaecb11478a7670c9e1692a1`; rollback release `/opt/opc/releases/20260811-014621`. Public, `/assistant`, admin login/settings, health, and Assistant static JavaScript are HTTP 200.

## 2026-08-11 Assistant Single-Control Rail Completion

- [x] Replaced the delayed two-control sidebar choreography with a single New Research command whose border and width follow the real desktop history rail continuously for the requested 500ms.
- [x] Kept the label single-line, clipped it within the same black command, delayed history-content fade to the final 100ms, and made collapse/expand reversal retain the currently visible material.
- [x] Preserved mobile/tablet drawer behavior, Inspector breakpoints, keyboard/reduced-motion immediacy, focus access, 44px touch targets, and all Assistant runtime/API semantics.
- [x] Passed focused Assistant `106/106`, complete Vitest `32/223`, all eight frontend package scripts, Vite build, Spring/MySQL/JAR gates, Python deployment and migration tests `120` passed, syntax, diff, credential, artifact, and `.codegraph/` ignore checks.
- [x] Released once with an atomic frontend switch: `/opt/opc/releases/20260811-035739`, hash `2fe7f6b790a2d42900496f7b86a1ce41a841ec7bf4880981ffea0d4d9467980b`; rollback `/opt/opc/releases/20260811-032203`. There was no database migration, Provider task, or database backup because the release contained only frontend rendering changes.

## Public Archive High-Density And University OPC Work In Progress (2026-09-01)

- Completed local frontend changes are confined to `MainLayout.vue`, `UniversityOpcView.vue`, public CSS, and their behavior contracts. The home now has a fluid high-density shell/contact-footer treatment; the public `06 高校 OPC` group has its own closeable submenu; and the existing university preview supports bounded client-side pagination.
- The `06` submenu preserves its real `communities`, `support`, `activities`, and `cases` destinations. It closes on pointer selection/outside interaction and Escape, and its active state follows the existing route/query values.
- University rows continue to come only from `/api/public/university-opc`. Filters, source anchors, static-preview disclosure, and all API semantics are retained. Pagination limits rendered rows to `10`, `20`, or `50`, with page controls and an accurate visible-range summary.
- Final local evidence: focused University/navigation Vitest `6/6`; full Vitest `37` files / `241` tests; all eight frontend contract scripts; Vite production build; Python discovery `133` passed with `7` opt-in skips; Python syntax; diff, credential, artifact, and ignore checks. The non-container Spring suite passed `471` tests with `0` failures/errors and `1` explicit skip; Spring Boot JAR packaging succeeded.
- The one remaining required gate is MySQL 8.4 Testcontainers. `mvnw.cmd test` has one Testcontainers initialization error, while the MySQL runner and opt-in migration tests cannot find Docker. Direct runtime checks found no Docker CLI/service and no registered WSL distribution. This remains a residual environment risk.
- Under the user's explicit deployment instruction, the first frontend-only atomic switch completed to `/opt/opc/releases/20260901-235819`, with `/opt/opc/releases/20260901-151149` retained at that time. The postflight reached HTTP `200` for public home, `/assistant`, `/university-opc`, admin login/settings, health, and both root-referenced CSS/JS assets. No migration, database backup, Provider request, or backend restart occurred.
- The bilingual `06 高校 OPC` navigation closure then completed as one further frontend-only atomic switch. Current production is `/opt/opc/releases/20260902-003155`, frontend index SHA-256 `2c2c68af3ba5a93b5bda7a60090a4c6cbc4d8a93f262b8a43adff5f819141b91`, and rollback is `/opt/opc/releases/20260901-235819`. Its postflight returned HTTP `200` for the public home, `/assistant`, administrator login, `/api/health`, and the deployed JavaScript asset, which includes every bilingual University submenu label.

- The mobile 06 submenu follow-up is complete. The submenu now stretches across the drawer's available rail, suppresses the inherited number pseudo-element, and prevents English labels from breaking into individual characters while retaining centered bilingual rows. Focused `MainLayout` tests are `4/4`; full Vitest is `244/244`; the Vite build, eight frontend scripts, Python/repository checks, and JAR packaging pass. A single frontend atomic switch moved production to `/opt/opc/releases/20260902-081330` (index SHA-256 `6235336ba392fee6dfc5f575f855835b69e535a11f75a43bdd20605c4e0deae8`), with `/opt/opc/releases/20260902-003155` retained for rollback.
- The reported `06` font mismatch was traced to a real computed-style difference: ordinary navigation titles inherited `line-height: 1.5`, while the University trigger forced `1.2`. Both now share one explicit `Noto Serif SC` / Songti typography contract, including matching size, weight, line height, subtitle family, and Prisma quiet color. Focused `MainLayout` tests pass `5/5`, complete Vitest passes `37` files / `245` tests, and Vite production build passes. One frontend-only atomic release moved production to `/opt/opc/releases/20260902-235323` (index SHA-256 `67c6154a427e8cb9a9566793f04dd136710f9b09e96db04de1243e88270221df`); `/opt/opc/releases/20260902-081330` is retained for rollback. Public home, `/university-opc`, `/assistant`, administrator login, `/api/health`, and the deployed CSS asset returned HTTP `200`.
