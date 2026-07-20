# Progress Log

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
