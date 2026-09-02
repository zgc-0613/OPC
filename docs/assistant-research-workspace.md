# Assistant Research Workspace Delivery

Date: 2026-07-25

## Scope

This pass productizes the existing bounded SoloFirm Agent runtime. It does not replace Vue 3, Spring Boot, MySQL, the provider-neutral model layer, evidence governance, or the four read-only Agent tools.

The user experience now follows a research desk rather than a generic chat template:

- independent server-backed history rail with desktop collapse and mobile drawer;
- new research, search, stable cursor loading, date grouping, rename, pin, archive, trash, restore, and permanent content deletion;
- research profile as an editable pre-send boundary and a read-only historic boundary;
- open document conversation, fixed composer, safe Markdown, copy, citations, and research-process detail;
- temporary run progress in the transcript, cancellation, safe retry, `latestRun` restoration, and bounded connection recovery;
- per-session drafts and selected-session/sidebar state locally, with full history remaining on the server.

## Database and retention

`20260725_assistant_workspace.sql` adds to `ai_agent_sessions`:

- `title_mode` (`auto` or `manual`);
- `pinned_at`;
- `archived_at`;
- `deleted_at`;
- `purge_after`;
- `purged_at`.

It adds active-history, archived-history, and due-purge indexes. The existing unique `(session_id, sequence_no)` message index remains the stable pagination contract. Existing titles are backfilled as `manual`; repeat execution does not alter new `auto` titles.

Trash sets a 30-day purge deadline. Restore clears it. Manual and scheduled purge reject nonterminal runs and explicitly scrub session profile/context, message content, citations, tool arguments/results/evidence hashes, and run result JSON. Minimal run identity, terminal status, provider/model, token totals, latency, diagnostics, and timestamps remain for security and accounting.

## API

Existing routes remain compatible. New user routes are:

```text
GET    /api/ai/research/sessions/history
PATCH  /api/ai/research/sessions/{sessionId}
POST   /api/ai/research/sessions/{sessionId}/archive
POST   /api/ai/research/sessions/{sessionId}/unarchive
POST   /api/ai/research/sessions/{sessionId}/trash
POST   /api/ai/research/sessions/{sessionId}/restore
DELETE /api/ai/research/sessions/{sessionId}/permanent
GET    /api/ai/research/sessions/{sessionId}/messages
GET    /api/ai/research/usage
```

History accepts `scope=active|archived|trash`, a maximum 100-code-point `q`, a strictly parsed cursor, and `limit` capped at 50. Search escapes wildcard syntax and remains owner-scoped. Details return the latest 50 messages, an older-message cursor, active run, and latest terminal run.

## First-send behavior

“New research” is a local draft. The server session is created only when the first question is sent. The session is selected before message submission; if submission fails, it remains visible and its draft is retained, so the user can safely retry rather than losing an orphaned session.

## Security and accessibility

- Every API uses existing active-user authentication and owner checks; purged sessions are unreadable.
- Markdown uses `markdown-it` plus DOMPurify. Raw HTML, scripts, frames, objects, event handlers, executable/data URLs, and unsafe links are rejected.
- External links are HTTP/HTTPS only and receive `target="_blank"` plus `rel="noopener noreferrer"`.
- The UI uses semantic status/alert regions, keyboard rename, Enter/Shift+Enter composition, Escape-close drawers, visible focusable commands, 44px mobile targets, and reduced-motion fallbacks.
- Polling failures keep the last server state, use bounded 1/2/4/8/10-second recovery, and never manufacture a server `failed` state.

## Deployment

The repository deployment script uploads and checksums the Assistant precheck/migration/postcheck files, executes them after the Agent Runtime postcheck, and fails into the existing rollback path on any mismatch. The temporary production Agent probe additionally checks automatic title, `latestRun`, messages, usage, history search, rename/pin, archive/unarchive, trash/restore, authorization, semantic evidence, and cleanup.

Production deployment is recorded only after the repository workflow succeeds with secure environment credentials; the successful release details are recorded below.

## Verification

- Maven full suite: 287 tests, 0 failures, 0 errors, with 1 opt-in real DeepSeek smoke skipped.
- MySQL 8.4 Testcontainers: 52 tests, all passed, including first/repeated migration, cursor queries, usage, lifecycle, and content purge.
- Frontend Vitest: 22 tests across 6 files, all passed; the focused Assistant component run contributes 10 tests.
- Frontend contract scripts: all 8 repository-defined scripts passed, including Assistant, history applicability, authentication, evidence review, and administrator concurrency checks.
- Frontend production build: passed with 1,800 transformed modules. The existing main-chunk size warning remains non-blocking.
- Spring Boot executable JAR: packaged successfully.
- Python deployment and migration tests: 26 passed; deployment script syntax compilation passed.
- Repository checks: `git diff --check`, `.codegraph/` ignore, tracked/pending build-artifact scans, and high-confidence secret scan passed. The two secret-pattern candidates are explicit test-only fake keys.

Per user instruction, Playwright was not used; desktop, tablet, and phone visual review remains manual.

One dependency audit residual is explicit: six high-severity advisories are confined to the `@vue/test-utils` development-only dependency chain. `npm audit` offers only a downgrade; the production dependency graph is unaffected, so no forced dependency rewrite was applied.

## Production status

Release `/opt/opc/releases/20260725-215634` is live through `/opt/opc/current`. The timestamped database/configuration backup is `/opt/opc/backups/20260725-215634`, the backend rollback artifact is `/opt/opc-backend.rollback.20260725-215634`, and the previous release remains `/opt/opc/releases/20260725-080213`.

The Assistant probe completed with a visible partial-evidence result and 2,987 total tokens. The real Agent probe completed with DeepSeek `deepseek-v4-flash`: 3 model rounds, 2 completed tool calls, 1 legal citation, 0 unknown citations, 6,897 total tokens, and 7,916 ms latency. Both public hosts and health returned HTTP 200; anonymous user/admin API requests returned the project's HTTP-200 envelope with business code 401 and null data.

## Stabilization addendum (2026-07-26)

The next local iteration replaces split first submission with atomic `/sessions/start`, freezes history traversal with signed snapshot cursors, separates active and latest terminal runs, restores retry text from owned persisted messages, and revokes late content writes through a purge generation barrier. The Assistant route now owns the available viewport without the public title band, the profile responds to its container, and the searchable industry combobox shares the select field system while retaining explicit AI suggestion confirmation and full keyboard semantics.

All local tests and builds passed; exact results and migration/index definitions are in `docs/assistant-workspace-stabilization.md`. The stabilization iteration is deployed in `/opt/opc/releases/20260726-015858`, with backup `/opt/opc/backups/20260726-015858` and previous release `/opt/opc/releases/20260725-215634`. Its real `deepseek-v4-flash` probe completed with 2 model rounds, 1 completed tool call, 1 legal citation, 0 unknown citations, 4,421 total tokens and 7,842 ms latency.

## Acceptance closure addendum (2026-07-26)

History pagination now combines its fixed activity snapshot with `platform_users.assistant_history_revision`. Signed cursor v2 remains bound to user, scope and search; rename, pin, archive, trash, restore and purge increment only that user's revision. A later page with an obsolete revision receives business conflict `409` and diagnostic `HISTORY_CURSOR_STALE`. The Vue history client discards obsolete pages and reloads page one once without clearing the selected session or search input. Appending messages does not increment the revision and therefore does not turn normal activity into a stale-cursor loop.

The research profile retains its wide six-track layout, uses explicit two-column semantic grouping from 521 through 680 px, and becomes one column at 520 px and below. Industry spans the tablet row; stage pairs with budget and goal pairs with resources. The searchable industry Combobox, AI confirmation, keyboard contract, aligned panel width and mobile 44px targets are preserved.

Permanent-deletion verification now includes two deterministic MySQL concurrency timelines using latches rather than sleeps. One proves an active run follows the product's purge rejection rule without corrupting the still-authorized callback. The other completes terminal purge while a callback is paused, releases the callback, and proves its generation/lease guard cannot restore messages, citations, arguments, results, evidence hashes or run content, cannot settle usage twice, and cannot create a second successful purge audit.

Local acceptance passed with Spring `306` executed (`305` passed and `1` opt-in provider smoke skipped), MySQL 8.4 `62/62`, Vitest `60/60` across 12 files, all 8 frontend scripts, Python default `73` executed (`66` passed and `7` opt-in MySQL skipped), explicit MySQL migration `7/7`, deployment hardening `52/52`, combined deployment/migration `65/65`, frontend production build, executable JAR and repository gates. Playwright was intentionally not used, so desktop, tablet and phone visual combinations still require manual inspection.

Release `/opt/opc/releases/20260726-080227` is live, with backup `/opt/opc/backups/20260726-080227`, database dump `/opt/opc/backups/20260726-080227/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260726-080227`, and previous release `/opt/opc/releases/20260726-015858`. Both domains, health, authorization, stale-cursor behavior, remote hashes, single-process ownership and temporary-account cleanup passed. The real `deepseek-v4-flash` probe completed with finish reason `stop`, 3 rounds, 2 completed tools, 1 legal citation, 0 unknown citations, 7,016 total tokens and 9,943 ms latency.

## Readiness and first-question closure (2026-07-26)

The editable research profile now has two independent reactions. The deep draft watcher only persists the current user's local profile. Evidence validation observes a normalized key made from region ID, canonical industry tag ID and industry text. Venture type, stage, budget, goal and existing resources therefore cannot clear a valid readiness result, display a false verification state, issue a readiness request or disable the first question. Region and industry changes keep the debounce and latest-response guards, and accepting an AI suggestion schedules one readiness request.

New research exposes `提出第一个问题`, a research-specific placeholder and `开始本次研究`; persisted sessions retain `继续研究` and the ordinary message action. Both states use the same single Composer at the bottom of the viewport-owned grid. The profile owns bounded internal scrolling, while the outer Assistant route remains `100vh`/`100dvh` with no second page scroll chain. Coarse-pointer tablet controls use scoped 44 px targets, and Arrow navigation scrolls only the active industry option into its listbox with `block: nearest`.

Local acceptance passed with Spring `307` (`306` passed and `1` opt-in Provider smoke skipped), MySQL 8.4 `63/63`, Vitest `65/65` across 13 files, all 8 frontend scripts, Python `74/74`, Vite/JAR builds and repository gates. Playwright was intentionally not used; desktop, tablet, phone and low-height visual combinations remain manual acceptance.

Release `/opt/opc/releases/20260726-092000` is live, with backup `/opt/opc/backups/20260726-092000`, database dump `/opt/opc/backups/20260726-092000/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260726-092000`, and previous release `/opt/opc/releases/20260726-080227`. Dual-domain, health, anonymous authorization, readiness, atomic start, subsequent message/history/title, cleanup, hashes and single-process probes passed. The real `deepseek-v4-flash` run completed with 6,956 total tokens, 2 completed tools, 1 legal citation, 0 unknown citations and 8,593 ms latency.

## Evidence workspace and bounded-height closure (2026-07-26)

The Assistant route, content shell, workspace and research desk now form one explicit height chain. The research desk is a vertical flex container: Header, research conditions, notices and Composer are non-shrinking rows, while `AssistantConversation` owns the remaining height and its vertical scroll. This prevents the low-height case where the user reached the end of page scroll but the Composer remained below the viewport. Safe-area and coarse-pointer rules remain scoped to mobile/tablet controls.

Command affordances follow the local Prisma Light hierarchy. Primary send/new actions remain ink-filled; fork/retry/recover/load/copy/evidence/process/return commands use paper backgrounds and neutral 1 px borders; cancellation uses a restrained deep-red border and text. Focus-visible, active, disabled and 44 px coarse-pointer behavior are explicit, and icon commands retain accessible names.

`AssistantEvidencePanel` presents current Run research material separately from process telemetry. It groups cases, policies and sources, shows the bounded brief, region, industry or type, match reason, publisher and current evidence state, and exposes only controlled internal detail links and safe HTTP(S) originals with `noopener noreferrer`. Citation numbers are carried from the validated Run references. Loading, empty, unavailable and error states remain user-visible without exposing raw tool arguments/results.

Local verification is recorded in `docs/assistant-research-quality-closure.md`. Playwright was not used; manual viewport acceptance remains required at `1366x768`, `1280x600`, `1024x768` and `390x844`.

The strengthened real research gate rejected invalid candidates and restored `/opt/opc/releases/20260726-092000` until every contract held. Synthesis now receives the exact legal source allowlist; server normalization excludes every unowned number from user-visible and persisted source fields while retaining a mandatory legal citation. Planning validation exposes fixed content-free diagnostic branches without logging raw model content. Hubei exact-tag retrieval also gained a controlled canonical-name and alias fallback for legacy Wuhan/Hubei cases with AI text evidence but no direct canonical relation.

The final local run executed 322 Spring tests (`321` passed and one opt-in Provider smoke was skipped), including MySQL 8.4 `67/67`; Vitest remained `73/73`, all 8 frontend scripts passed, both production builds passed and repository gates stayed green. The last rejected candidate reproduced a 2,000-token compact-synthesis truncation; a red-to-green request contract corrected synthesis to 3,200 under the configured aggregate Run limit.

Release `/opt/opc/releases/20260726-162930` is live, with backup `/opt/opc/backups/20260726-162930`, database dump `/opt/opc/backups/20260726-162930/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260726-162930`, and previous release `/opt/opc/releases/20260726-092000`. The real `deepseek-v4-flash` probe completed with 3 rounds, 2 tools, 5 case items, 2 policy items, 4 legal citations, 0 unknown citations, 11,039 tokens and 46,160 ms latency. Independent preflight confirmed the artifact hashes, three active services, one backend process, loopback-only listening and restored temporary-account counts.

## Phase 28 independent Assistant workspace

`/assistant` is now a lazy top-level protected route under `AssistantLayout`, rather than a child of `MainLayout`. Direct anonymous access retains `/assistant` as the login redirect with reason `ai-login-required`; refresh and session selection remain owned by `AssistantView`. The public archive sidebar, public page heading and public back-to-top control no longer render inside the Assistant.

The lightweight workspace topbar contains one return-to-index command, the current research title, one materials command and an account entry. Desktop keeps one Assistant history rail, the bounded conversation/Composer surface and an on-demand evidence drawer. Tablet and phone use mutually exclusive focus-managed drawers; Escape closes the active drawer and restores the opening control. The existing history, drafts, citations, polling, retry and first-message behavior remain in `AssistantView`.

Evidence presentation now distinguishes available from historical total material. It displays `可用 X 项，共 Y 项`; an all-unavailable result displays `当前资料已失效，请重新检索`. Unavailable items retain their status explanation but are not labeled or linked as citations. Inline evidence and the drawer use separate DOM identity prefixes while sharing `runId`, `sourceId` and `citationId` semantics.

The layout remains SoloFirm Prisma Light and uses a `100vh` fallback plus `100dvh`, bounded flex/grid children, independent conversation scrolling, safe-area Composer padding and 44 px coarse-pointer commands. Playwright is intentionally not used. Manual checks remain required at `1366x768`, `1280x600`, `1024x768`, `390x844` and the requested 375 px phone width.

Final Phase 28 frontend verification passed 83 Vitest tests across 16 files, all eight package scripts and the production build. The public synchronous main chunk is `428.22 kB`; lazy Assistant chunks are `1.37 kB` for the layout and `203.61 kB` for the view. The independent workspace was not deployed because the mandatory real Provider candidate stopped at case-comparison evidence insufficiency. Manual viewport acceptance therefore applies to the eventual deployed release, not the current production baseline.

## Phase 29 workspace verification

The independent route and visual ownership remain unchanged while Agent compatibility fixes continue. The current frontend suite passes `85/85`, including the Assistant component subset `35/35`, all eight package scripts and the production build. The public synchronous bundle remains approximately 428 kB and `AssistantView` remains a lazy approximately 203 kB route chunk, so the public home does not synchronously load the Assistant workspace.

No Phase 29 production rollout occurred because the three-scenario Provider candidate is not green. Manual inspection remains required on the eventual release at `1366x768`, `1280x600`, `1024x768`, `390x844` and 375 px width for single-topbar hierarchy, evidence drawer exclusivity, focus restoration, long-conversation Composer access and safe-area behavior.

## Phase 31 deployment status

No Assistant component or layout change was required for the intent-priority repair. The independent lazy `AssistantLayout`, Starter `requestedIntent` behavior, one-topbar workspace and evidence drawer remain covered by Vitest `87/87`, the Assistant subset `36/36`, all eight package scripts and the Vite production build. The public entry remains `428.22 kB` and the lazy Assistant view remains `204.03 kB`.

The final candidate did not pass all three Agent scenarios, so these already-built Assistant assets were not switched into production. Manual viewport acceptance still applies to the eventual deployed release; current production remains `/opt/opc/releases/20260726-213258`.

## Phase Four research workbench visual delivery (2026-08-10)

The Assistant now uses a dedicated history rail, one bounded research-document column, and an Inspector that is absent until the user opens research conditions, materials, citations, process, or reports. `AssistantResearchStarter` owns local new-research intent and only creates a persisted session at the first valid submit. `AssistantComposer` is therefore unambiguously a current-session continuation control. Existing history search/pagination/lifecycle APIs, per-session drafts, polling/retry/cancel states, evidence/citation authorization, reports, profile, quota, and response contracts remain unchanged.

The route no longer depends on `100vh`. `100dvh`, `minmax(0, 1fr)`, shrinking flex/grid children, safe-area Composer padding, and a single intended scroll owner eliminate bottom clipping and competing page/conversation scroll. Desktop uses 276px history plus an 880px document measure and optional 300-360px Inspector; tablet and phone use focus-managed drawers with Escape restoration and 44px touch targets. Long Markdown, URLs, tables, code, and mixed-language data remain bounded.

Motion is restricted to purposeful, reduced-motion-safe state feedback. There are no infinite spinner/pulse effects, `transition: all`, `ease-in`, layout-property animations, or unguarded hover styles. No Playwright was used. Full Phase Four evidence, including exact test/build commands, the initial visual release `/opt/opc/releases/20260810-112153`, the later 500ms continuity release `/opt/opc/releases/20260810-155624`, backup/rollback paths, independent postflight, and manual checks, is in `docs/assistant-workspace-phase-four.md`.

### Local motion refinement

The subsequent local-only motion pass gives existing controls clearer physical feedback without changing the workbench structure: pointer-opened menus and drawers fade/translate from their trigger edge, the Inspector and its close command have a matching exit, toasts and run-stage changes cross-fade, and buttons use restrained hover/press feedback only on fine pointers. Desktop history folding now uses a transform-driven mask over the extended rail, preserving the same 64px icon column as the settled collapsed state without animating its layout dimensions. Keyboard-triggered changes stay immediate and reduced-motion suppresses spatial movement. This refinement passed the focused `15/15` layout/history Vitest subset and retains the completed `109/109` motion, `198/198` full frontend, script, and Vite-build evidence; it is not a new production deployment.

### Motion deployment

The approved guarded release subsequently switched `/opt/opc/current` to `/opt/opc/releases/20260810-130518`. The previous `/opt/opc/releases/20260810-112153`, backup `/opt/opc/backups/20260810-130518`, database dump `/opt/opc/backups/20260810-130518/opc_platform.sql.gz`, and backend rollback artifact `/opt/opc-backend.rollback.20260810-130518` are retained. Postflight passed public, Assistant, admin, health, and production-static-asset checks; active services, the loopback backend listener, `opc` service user, and zero candidate residues were independently confirmed.

### 500ms motion continuity release

The desktop history collapse no longer interpolates the workspace grid. The grid changes to its target geometry once, while a staged cover on the history rail and a matching translation on the research desk supply the 500ms visual continuity. This keeps the central research manuscript from reflowing on every animation frame and makes the rail boundary and its contents move as one surface. The compact 64px command rail remains visually present throughout.

Mobile history-row selection now carries its pointer event to the drawer close path, so its close choreography matches the explicit close and backdrop interactions. Extended desktop-history content remains mounted until the animation finishes but is `inert` and `aria-hidden` during the hidden phase. Keyboard changes remain immediate, fine-pointer hover remains conditional, and reduced motion removes spatial movement.

The formal release switched `/opt/opc/current` to `/opt/opc/releases/20260810-155624`. Backup `/opt/opc/backups/20260810-155624`, database dump `/opt/opc/backups/20260810-155624/opc_platform.sql.gz`, rollback `/opt/opc-backend.rollback.20260810-155624`, and previous `/opt/opc/releases/20260810-130518` are retained. Targeted history/workspace Vitest passed `20/20`; full frontend Vitest passed `32/32` files and `203/203` tests; the eight frontend scripts, Vite build (`1836` modules), Spring Boot `547` tests (`0` failures, `0` errors, `1` skip) and package, MySQL 8.4, Python (`126` passed, `7` skipped), and repository gates passed before deployment. Public, Assistant, admin, health, and production static assets returned HTTP 200 after the atomic switch.

### Inspector And Motion Finish

An inline source hash now opens the current Run's real citation Inspector only when the target is authorized Run evidence. It does not synthesize citation data or capture ordinary external links, which retain their normal destination behavior. The report Inspector's top spacing now follows the shared Inspector layout. The desktop history rail retains the 500ms continuity choreography, but its in-flight visual boundary is exclusively a pseudo-element; the static border becomes transparent during motion to avoid a split or doubled edge.

Public behavior tests were written RED then made GREEN for the authorized source-hash route. Final verification passed frontend Vitest `32` files / `206` tests, seven npm scripts, Vite (`1836` modules), Spring `547` (`0` failures/errors, `1` skipped) including MySQL 8.4 `80/80`, JAR packaging, Python `133` with `7` skips, explicit MySQL `7/7`, and syntax/diff/secret/artifact/`.codegraph/` checks. A single frontend-only atomic release switched `/opt/opc/current` to `/opt/opc/releases/20260810-183007` with frontend hash `4ba885...c54b`; `/opt/opc/releases/20260810-155624` is retained for atomic rollback. No SQL, JAR, API, migration, or new database backup was required. Public, `/assistant`, administrator login/settings, `/api/health`, and the new JavaScript/CSS returned HTTP 200; services are active, Nginx is valid, and 8082 is loopback-only.

### 2026-08-11 Release Gate Follow-up

The next guarded release carries the persisted unpin fix and the deployment-probe correction, not a new Assistant API. A citation hash still opens only the current Run's authorized Inspector data. The production terminal probe now uses `source_verification` explicitly, verifies one source returned by its own policy search, and requires source evidence from that same Run. Broader policy and case-comparison coverage remains in the isolated candidate stage.

The real MySQL runner was updated when the unpin regression raised its integration test count to 81. It now uses one shared expected-count constant so the progress marker and final verdict cannot drift. The final controlled MySQL run passed `81/81` with zero owned-container leak. The current release is `/opt/opc/releases/20260811-003256`, with backup `/opt/opc/backups/20260811-003256`, dump `/opt/opc/backups/20260811-003256/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260811-003256`, and prior release `/opt/opc/releases/20260810-183007`.

## Ordered History Control And Responsive Delivery (2026-08-11)

Desktop collapse now changes the New Research command into its 44px plus-only control before the history rail moves: a 120ms preparation stage hides the label and settles the icon, then the real `276px` to `64px` grid track runs for 500ms. The rail's native divider moves with that track; no pseudo boundary or fixed-width sidebar overlay remains.

At `<=840px`, history is always a full drawer (including when a desktop collapse preference was saved), which covers 375px phones and 768px tablets. At `<=1023px`, Inspector is an overlay drawer; the persistent desktop rail and Inspector arrangement begins at 1024px. This retains 44px targets, focus-managed drawers, safe-area padding, immediate keyboard behavior, and reduced-motion behavior.

The frontend-only delivery passed targeted layout/history tests `96/96`, full Vitest `32/212`, all eight package scripts, and Vite (`1836` modules). Production is `/opt/opc/releases/20260811-014621`, with backup `/opt/opc/backups/20260811-014621`, dump `/opt/opc/backups/20260811-014621/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260811-014621`, and prior release `/opt/opc/releases/20260811-012139`. Public, Assistant, admin, health, and new Assistant assets returned HTTP 200.

## Recorded Sidebar Motion Closure (2026-08-11)

The desktop history rail has one pointer-only sequence designed to preserve spatial continuity without exposing a fluid-width control inside a narrowing rail:

| Moment | Collapse | Expand |
| --- | --- | --- |
| Initial interaction | The wide New Research command fades out and the fixed 44px plus command takes semantic ownership. | The fixed 44px plus command remains active while the rail opens. |
| 120ms | The actual `276px` to `64px` grid rail transition begins; title, search, and list have already left the visible/accessibility surface. | No separate geometry delay is applied. |
| 320ms | The compact command remains the only visible command. | Wide command, title, search, and history list regain visual and semantic access together. |
| Final state | The compact rail is the only history surface. | The wide history workspace is fully available. |

This is intentionally a narrow rendering fix. Session grouping/search, pin/archive/trash actions, drafts, citations, reports, run states, Inspector behavior, and all API shapes remain unchanged. On phones and tablets (`<=840px`), history remains an independent full drawer even with a persisted desktop collapse preference. Inspector is an overlay through `1023px`; desktop begins at `1024px`. Focus, Escape, 44px targets, safe areas, long-content overflow, keyboard immediacy, fine-pointer hover gating, and reduced-motion rules remain intact.

Verification completed without Playwright: targeted Assistant tests `101/101`, full Vitest `32/218`, all eight frontend package tests, Vite build (`1836` modules), full Spring `549` (`0` failures/errors, `1` skip), controlled MySQL 8.4 `81/81` with no owned container leak, JAR package, Python `126` passed plus `7` skips and explicit migration `7/7`, Python syntax, diff, high-confidence credential, artifact, motion, and `.codegraph/` checks. One frontend-only atomic deployment switched `/opt/opc/current` to `/opt/opc/releases/20260811-032203`; the retained rollback release is `/opt/opc/releases/20260811-014621`. No database mutation or backup was necessary. HTTP 200 was independently verified for the public site, `/assistant`, administrator login/settings, `/api/health`, and the current Assistant JavaScript asset.

## Continuous History Rail Refinement (2026-08-11)

The original Phase Four information architecture remains unchanged: desktop has one history rail, one research-document reading column, and an Inspector only when requested; `<=840px` history and `<=1023px` Inspector remain independent focus-managed drawers. The refinement corrects only the desktop fold, after a frame-by-frame recording review showed that the earlier compact-control handoff made content disappear before the rail moved.

### Rendering Rule

The history rail and its black New Research command are now one visual geometry. The actual grid track interpolates from `276px` to `64px` over 500ms, and the button remains the same DOM control with `overflow: hidden`: its single-line label is clipped/faded inside the button while the icon recentres. The title, search, and grouped history rows remain rendered behind the contracting track and fade only in the final 100ms. Expansion reverses from the current transition state without blanking visible content first.

This retains the existing components and contracts: `AssistantHistorySidebar`, `AssistantView`, history search/grouping/menu actions, drafts, citations, reports, run progress, Inspector focus handling, and every current API shape. Keyboard invocation and `prefers-reduced-motion` remain immediate; fine-pointer hover remains gated, and phone/tablet commands retain 44px targets. No external content, evidence, user history, or model state is fabricated.

### Verification And Delivery

Focused Assistant behavior tests passed `106/106`; complete frontend Vitest passed `32` files / `223` tests; all eight frontend package scripts and the Vite production build passed. Existing Spring Boot, MySQL 8.4, JAR, Python deployment/migration (`120` passed), syntax, diff, high-confidence credential, artifact, and `.codegraph/` ignore gates passed. Playwright was not used.

One formal frontend-only atomic release switched production to `/opt/opc/releases/20260811-035739`, frontend hash `2fe7f6b790a2d42900496f7b86a1ce41a841ec7bf4880981ffea0d4d9467980b`. The atomic rollback release is `/opt/opc/releases/20260811-032203`; no database migration or backup was created because no server or data change shipped. Postflight returned HTTP 200 for the public site, `/assistant`, administration, `/api/health`, and the deployed Assistant asset. Human review remains appropriate for the desktop 1024px/1440px fold and the 375px/768px drawer paths.

## Public Archive Follow-up Boundary (2026-09-01)

The subsequent high-density public-home and `06 高校 OPC` work does not alter this Assistant workspace. `AssistantLayout`, `AssistantView`, history/session APIs, per-session drafts, Composer behavior, Inspector/citation authorization, research profile, run state machine, report behavior, quota handling, and mobile drawers remain outside its file and behavior scope.

The public change adds client-side pagination and a closeable menu group to the existing archive route only. It neither calls an AI Provider nor changes Assistant evidence, citations, reports, or routes. Its current local/deployment status is tracked separately in `docs/university-opc-phase-six.md`; the initial public release reached `/opt/opc/releases/20260901-235819`, the bilingual navigation closure reached `/opt/opc/releases/20260902-003155`, the mobile rail correction reached `/opt/opc/releases/20260902-081330`, and the shared navigation typography correction is current at `/opt/opc/releases/20260902-235323` with `/opt/opc/releases/20260902-081330` retained for rollback. The unavailable local Docker/Testcontainers prerequisite remains a public-release residual risk rather than affecting Assistant behavior.
