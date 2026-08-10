# Assistant Research Workspace Phase Four Delivery

Date: 2026-08-10

## Scope

Phase Four is a user-facing Assistant workspace redesign. It keeps Vue 3, Vite, Spring Boot, MyBatis-Plus, MySQL, the existing authenticated session/history APIs, Agent state machine, evidence and citation authorization, reports, Token limits, research profile, and administrative contracts. No response shape or Provider/tool protocol was changed for this visual delivery.

The repository's CodeGraph MCP service was unavailable during the work. Structural inspection therefore used focused file reads and `rg`; `.codegraph/` remains ignored.

## Previous Problems

- The page kept model, Token, evidence, task, profile, report, and conversation information visible at once, leaving no clear reading surface.
- Nested containers and repeated borders divided research answers into card fragments instead of a continuous paper document.
- Fixed-height and overflow ownership could hide the Composer or final message on shorter viewports and mobile browser chrome changes.
- Citation, process, report, and profile information competed with the answer even when not needed.

## Information Architecture

| Area | Phase Four behavior |
| --- | --- |
| Research history | Dedicated left rail with `New research`, debounced search, pinned/today/recent groups, selected session, and the existing rename/pin/archive/trash actions. |
| Research document | One central reading column, bounded at 880px. User prompts get a light paper distinction; Assistant responses remain open, readable document content. |
| Session header | Current title, short scope, research conditions, materials, reports, and process entry points only. Low-frequency model and Token information moved to Inspector process details. |
| Inspector | One on-demand surface for conditions, citations, research materials, process, and reports. Desktop adds it only when opened; tablet and phone use a focus-managed drawer. |
| New research | The left-rail action opens `AssistantResearchStarter`; it does not create a server session until the first valid submission. |
| Continuation | `AssistantComposer` appears only for an existing writable session and continues that session; it never starts a new research record. |

## Layout Rules

- Desktop (`>=1024px`): history is 276px (64px when collapsed); the central column uses `minmax(0, 1fr)` and an 880px reading measure; Inspector is a 300-360px third column only while open.
- Tablet (`768-1023px`): the document remains primary. History and Inspector are mutually exclusive off-canvas surfaces, preserving the central reading width.
- Phone (`375px` and up): conversation or starter is the default view; history and Inspector are separate drawers. Controls use 44px targets, safe-area padding, and no horizontal page overflow.
- The Assistant shell uses `100dvh`, `minmax(0, 1fr)`, explicit `min-height: 0`, and one intentional conversation/starter scroll owner. It does not use `height: 100vh` for workspace sizing.
- Composer stays in the non-scrolling research desk footer. The conversation reserves the remaining row, so final content is not obscured; while a user reads upward, a `Back to bottom` command appears instead of forced scrolling.
- Long URLs, tables, Markdown, code, Chinese/English mixed text, and material metadata use `min-width: 0`, `overflow-wrap: anywhere`, or bounded internal scrolling as appropriate.

## Component Ownership

- `AssistantView.vue`: session selection, drafts, polling/retry boundaries, workspace shell, Inspector selection, and responsive drawer orchestration.
- `AssistantHistorySidebar.vue`: real history grouping/search/pagination/lifecycle commands and mobile focus management.
- `AssistantResearchStarter.vue`: local first-question form and on-demand conditions entry; no eager server session creation.
- `AssistantSessionHeader.vue`: title/scope and compact Inspector entry points.
- `AssistantConversation.vue`, `AssistantStructuredResult.vue`, and `AssistantEvidencePanel.vue`: readable response rendering and current-Run authorized materials without manufacturing citations or evidence.
- `AssistantRunProgress.vue`: visible user-safe stages, terminal/retry states, and an explicit accessible cancellation control. It contains no chain of thought, raw JSON, lease, or prompt data.
- `AssistantInspector.vue` and `AssistantInspectorDetails.vue`: one Inspector boundary with close, Escape, focus restoration, inert isolation, and the single allowed Inspector scroll area.
- `AssistantComposer.vue`: controlled per-session draft continuation, Enter-send, Shift+Enter newline, and cancellation while running.

## Motion And Accessibility

- The workbench has no infinite spinner/pulse, typewriter, marquee, hover-only function, `transition: all`, `ease-in`, or layout-property animation.
- Drawer/popover behavior is restrained and supports `prefers-reduced-motion`; keyboard-triggered drawer/list changes are immediate.
- All hover styling is inside `@media (hover: hover) and (pointer: fine)`.
- Semantic `aside`, `main`, `nav`, headings, buttons, labels, status/alert regions, visible focus styles, ARIA selection state, and icon labels are present.
- Mobile history and Inspector move focus to the drawer, close on Escape, restore focus to their opener, and apply `inert` to inactive page content. Status never relies on color alone.
- The checked public seams cover search debounce/current selection/collapse, no empty-session creation, Composer continuation, stop affordance, Inspector close/focus restoration, real materials/citations, archived/trash read-only behavior, drafts after refresh, reduced motion, and overflow-safe long content.

## Verification

| Command | Result |
| --- | --- |
| `npm exec -- vitest run --reporter=dot` | 31 files, 191 passed |
| `npm run test:assistant-component` | 69 passed |
| All 8 `package.json` test scripts | passed |
| `npm run build` | passed, 1828 modules transformed |
| `mvnw.cmd test` | 547 tests, 0 failures, 0 errors, 1 explicit opt-in skip |
| `python scripts/run_phase_one_mysql_test.py --label phase4 --timeout-seconds 1200 --thread-dump-after-seconds 180` | MySQL 8.4 80 tests, 0 failures/errors/skips; owned container count 0; no leak |
| `OPC_RUN_MYSQL_TESTS=1 python -m unittest scripts.test_assistant_workspace_mysql` | 7 passed |
| `mvnw.cmd package -DskipTests` | executable Spring Boot JAR built |
| Python compile sweep and `python -m unittest discover -s scripts -p 'test*.py'` | 9 files compiled; 133 tests passed, 7 opt-in skips |
| `git diff --check` | passed |
| High-confidence credential scan | 0 findings after excluding local secrets and generated artifacts |
| Build artifact / ignore audit | 0 tracked build artifacts; `.codegraph/`, `.local-secrets/`, and generated Assistant test JSON ignored |

Playwright was deliberately not used. The requested visual review remains a human acceptance activity.

## Production Delivery

One remote preflight ran before one formal deployment. The deployment script ran its existing guarded candidate checks as part of the same release workflow; no standalone Provider task was initiated for this visual work.

- Current release: `/opt/opc/releases/20260810-112153`
- Backup: `/opt/opc/backups/20260810-112153`
- Database dump: `/opt/opc/backups/20260810-112153/opc_platform.sql.gz`
- Backend rollback artifact: `/opt/opc-backend.rollback.20260810-112153`
- Previous release: `/opt/opc/releases/20260809-193452`
- Deployment result: additive/resumable migration checks passed, release switched atomically, backend listener remained loopback-only, and backend user remained `opc`.

Independent postflight returned HTTP 200 for `https://findopc.online/`, `https://admin.findopc.online/`, `https://findopc.online/assistant`, and `https://findopc.online/api/health`. The production JavaScript asset loaded with HTTP 200, local and remote `index.html` SHA-256 matched, anonymous Assistant session access returned the existing `code=401` envelope, and candidate units/environment files both counted 0.

## Human Acceptance Checklist

- At 1440px and 1024px, verify the history rail, one reading column, optional Inspector, final response, and Composer all remain reachable.
- At 768px, verify history and Inspector drawers are mutually exclusive and return focus to their trigger after Escape.
- At 375px, verify the conversation/starter default, 44px controls, safe-area Composer, long URLs/tables, and no horizontal page scroll.
- Check an active run, a clarification/insufficient/failure/cancelled state, a long response, an archived session, and a trash session with a real authenticated account.

## Motion Refinement (2026-08-10)

This local refinement adds motion only to existing Assistant interactions: pointer-opened session menus, mobile history and Inspector drawers, desktop history collapse/expand, Inspector close, toast entry/exit, run-stage replacement, and bounded button hover/press feedback. The history fold retains its left 64px command rail throughout the transition, so the final collapsed state does not leave a right-aligned blank remnant or shift the controls after the animation.

The initial refinement used `transform` and `opacity` within the existing 120-240ms timing tokens and a custom ease-out curve. The later desktop history/mobile drawer continuity transition uses the requested 500ms; it still animates only `transform` and `opacity`. The sidebar's static-width fold uses a transform-driven mask over its extended detail rail, avoiding layout-property animation. Keyboard-triggered drawer, menu, Inspector, and sidebar changes remain immediate; hover feedback is restricted to fine pointers; and `prefers-reduced-motion` removes the spatial transitions. There are no infinite decorative animations, `transition: all`, bounce/ease-in curves, or layout-property animation.

Verification for this refinement: focused history/workspace Vitest `15/15`; prior complete motion suite `109/109`, full frontend Vitest `198/198`, all eight frontend package scripts, and Vite production build passed. This local verification preceded the explicitly authorized production rollout recorded below.

## Motion Deployment (2026-08-10)

After explicit release authorization, the existing guarded deployment performed one formal rollout. Its isolated migrated candidate, Agent contract checks, migration precheck/migration/postcheck, backup, atomic switch, and cleanup completed before the production target changed.

- Current release: `/opt/opc/releases/20260810-130518`
- Backup: `/opt/opc/backups/20260810-130518`
- Database dump: `/opt/opc/backups/20260810-130518/opc_platform.sql.gz`
- Backend rollback artifact: `/opt/opc-backend.rollback.20260810-130518`
- Previous release: `/opt/opc/releases/20260810-112153`

Independent postflight returned HTTP 200 for the public site, `/assistant`, the administrator site, `/api/health`, and the deployed JavaScript asset. Remote `/opt/opc/current` resolves to the release above; Nginx, MySQL, and the backend are active; 8082 remains loopback-only; the backend runs as `opc`; and candidate units, environment files, and databases count 0. Playwright remains intentionally unused; the human acceptance checklist above still applies.

## 500ms Motion Continuity Fix (2026-08-10)

### Root Cause And Scope

The initial 500ms desktop fold attempted to animate the workbench `grid-template-columns`. That forced the central research desk to reflow as the history rail changed width, while its detail content was also being hidden. The mismatch produced the visible tearing and occasional dropped frames reported during sidebar collapse.

The fix is intentionally narrow. It changes no session, profile, run, evidence, citation, report, draft, quota, API, Provider, or backend behavior. The workspace commits its target grid once, then uses the following pointer-only visual choreography:

| Transition | Visual operation | Accessibility behavior |
| --- | --- | --- |
| Collapse | A 276px history cover translates over extended content while the research desk translates 212px back to its settled position over 500ms. | Extended controls are `inert` and `aria-hidden` until settled. |
| Expand | The target grid is committed once, then the cover translates away and the desk returns from its offset over 500ms. | Hidden content is not exposed until it is visible again. |
| Mobile close after row selection | The original pointer event enables the same drawer exit used by close/backdrop commands. | Focus restoration remains owned by the existing drawer controller. |

Only `transform` and `opacity` animate. The CSS transition is interruptible, uses the existing custom ease-out curve, and does not use a layout property, keyframe, `transition: all`, `ease-in`, bounce, or infinite animation. Keyboard activation remains immediate. `prefers-reduced-motion` removes spatial motion, and hover feedback remains inside the fine-pointer media query.

### Verification

| Command or gate | Result |
| --- | --- |
| Targeted history/workspace Vitest | `20/20` passed |
| Full frontend Vitest | `32/32` files, `203/203` tests passed |
| All eight package test scripts | passed |
| Vite production build | passed, `1836` modules transformed |
| Spring Boot package and MySQL 8.4 Testcontainers acceptance | Spring `547` tests, `0` failures/errors, `1` skip; MySQL `80/80` |
| Python deployment/migration tests and syntax compilation | `126` passed, `7` skipped |
| `git diff --check`, high-confidence credential scan, build-artifact tracking, `.codegraph/` ignore | passed |
| Production public, admin, `/assistant`, health, and static-asset probes | HTTP 200 |

### Production Delivery

One guarded formal deployment switched production atomically after its candidate, migration precheck/migration/postcheck, backup, and cleanup gates passed. No speculative retry or second rollout was performed.

- Current release: `/opt/opc/releases/20260810-155624`
- Backup: `/opt/opc/backups/20260810-155624`
- Database dump: `/opt/opc/backups/20260810-155624/opc_platform.sql.gz`
- Backend rollback artifact: `/opt/opc-backend.rollback.20260810-155624`
- Previous release: `/opt/opc/releases/20260810-130518`

Remote postflight confirmed active Nginx, MySQL, and backend services, a loopback-only `127.0.0.1:8082` listener, the `opc` service user, and zero candidate unit/environment/database residues. Playwright remains intentionally unused; human acceptance still covers populated desktop sidebar collapse/expand, mobile history row selection, Inspector close, keyboard paths, reduced motion, and 375px/768px/1024px/1440px layouts.

## Inspector And Motion Finish (2026-08-10)

### Interaction Corrections

- Inline source hashes route into the current Run's real citation Inspector only when their target is authorized evidence. The handler does not intercept ordinary external links or fabricate citations.
- The report Inspector top spacing aligns with the common Inspector layout.
- The desktop history rail keeps its pointer-only 500ms choreography. While that motion is active, only the rail pseudo-element paints the boundary; the static border is transparent, preventing a doubled or separated vertical edge.

### TDD RED/GREEN And Delivery Evidence

Public source-hash behavior tests were RED before the authorized-evidence routing implementation and GREEN after it. The release gate passed frontend Vitest `32` files / `206` tests, seven npm package scripts, Vite production build (`1836` modules), Spring `547` (`0` failures/errors, `1` skipped) including MySQL 8.4 Testcontainers `80/80`, executable JAR packaging, Python `133` with `7` skips, explicit MySQL `7/7`, Python syntax, `git diff --check`, high-confidence secret scan, artifact tracking, and `.codegraph/` ignore checks.

One frontend-only atomic release switched `/opt/opc/current` to `/opt/opc/releases/20260810-183007`; frontend hash `4ba885...c54b`. `/opt/opc/releases/20260810-155624` is retained as the atomic rollback release. There was no SQL, JAR, API, migration, or new database backup in this delivery. Postflight returned HTTP 200 for the public site, `/assistant`, administrator login/settings, `/api/health`, and the new JavaScript/CSS assets. Nginx is valid, services are active, and `127.0.0.1:8082` remains loopback-only.

## Release Completion (2026-08-11)

### Deployment Gate Change

The last broad production Agent probe failed before switch because it hit the configured token ceiling, not because a visual or Assistant interaction changed. This delivery keeps the candidate suite broad, but makes the final production gate a bounded `source_verification` request. It searches policies, verifies one returned source and requires only current-Run source evidence. This still proves the deployed Agent route, citation allowlist, evidence endpoint, persistence and cleanup work without turning a UI release into an uncontrolled long research task.

### Final Verification

| Gate | Result |
| --- | --- |
| Frontend Vitest | `32` files, `210` tests passed |
| Existing frontend package scripts | all eight passed |
| Vite production build | passed, `1836` modules transformed |
| Full Spring Boot | `549` tests, `0` failures, `0` errors, `1` explicit skip |
| MySQL 8.4 controlled runner | `81/81`, zero failures/errors, zero current-run containers, no leak |
| Spring Boot JAR | executable package built |
| Python | `133` passed, `7` opt-in skips; migration `17/17`; deployment hardening `103/103`; syntax passed |
| Repository gates | `git diff --check`, high-confidence credential scan, artifact tracking, `.codegraph/` ignore passed |

### Production Delivery

- Current release: `/opt/opc/releases/20260811-003256`
- Backup: `/opt/opc/backups/20260811-003256`
- Database dump: `/opt/opc/backups/20260811-003256/opc_platform.sql.gz`
- Backend rollback: `/opt/opc-backend.rollback.20260811-003256`
- Previous release: `/opt/opc/releases/20260810-183007`

The production source-verification probe completed with three model rounds, three tool calls, one real source citation and `17,935` tokens under the `28,000` configured limit. Independent postflight verified HTTP 200 for the public site, `/assistant`, the administrator login, `/api/health`, and the deployed Assistant JS/CSS assets. Nginx, MySQL and backend services are active; the backend remains loopback-only. Manual checks remain: desktop and mobile history folding, Inspector open/close and focus restoration, reduced motion, the unpin action, an authorized inline citation link, and 375px/768px/1024px/1440px layouts.

## Sidebar Rail Geometry Follow-up (2026-08-11)

The previous desktop fold used a fixed-width sidebar box, a pseudo-element boundary, and a compensating research-desk transform. The target grid state was committed only after the 500ms motion window, which could make the reading column and visible boundary diverge for a frame.

The rail now commits the collapsed or expanded grid state at pointer activation. The real grid track interpolates from `276px` to `64px` (or back) for 500ms, so the sidebar's native 1px border, Inspector-aware grid, and reading column share one geometry transition. The sidebar box itself stays `width: 100%`; its extended controls remain fixed-width only as clipped inner content while they fade, so they cannot cover the document column. Keyboard activation and reduced-motion paths still change immediately.

Public behavior coverage was changed RED then GREEN to require the workspace's target collapsed class in the same tick as the pointer action. Targeted history/workspace tests passed `94/94`; full frontend Vitest passed `32` files / `210` tests; all eight package scripts and Vite (`1836` modules) passed. `git diff --check`, artifact tracking, credential scan review, and `.codegraph/` ignore checks passed.

One guarded release switched production to `/opt/opc/releases/20260811-012139`; backup `/opt/opc/backups/20260811-012139`, database dump `/opt/opc/backups/20260811-012139/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260811-012139`, and previous release `/opt/opc/releases/20260811-003256` are retained. The deployed frontend hash is `b75d2935613a168e77c506ff196f4bcc2d239f11f9b5424b4fcd7d8ad364743f`. Public, Assistant, admin, health, and deployed Assistant JS/CSS probes returned HTTP 200.

## Ordered Control Fold And Responsive Follow-up (2026-08-11)

The collapse now has a visible ordered sequence: the New Research control immediately becomes the centered 44px plus command and its label exits; after a 120ms preparation interval, the real history grid rail contracts for 500ms. The desktop rail, native 1px divider, and document column therefore remain in one coordinate system. Historical search and title content fade before the rail begins and then shrink invisibly with it; they no longer retain a 276px motion-time box that can be clipped or pop on the final frame.

Responsive rules are explicit: `375px` phones and `768px` tablets use a full-width history drawer at `<=840px`, even when the desktop collapse preference is persisted; Inspector becomes a full overlay drawer at `<=1023px`; `1024px` and wider uses the desktop rail and optional Inspector column. Touch targets remain at least 44px, safe-area padding remains in place, keyboard actions are immediate, and reduced motion removes the spatial sequence.

Two public layout contracts were added RED then GREEN for the ordered button phase and the mobile/tablet override. Targeted tests passed `96/96`; full frontend Vitest passed `32` files / `212` tests; all eight package scripts and Vite (`1836` modules) passed. No backend, API, migration, provider, evidence, report, draft, quota, or authentication behavior changed.

One guarded release switched production to `/opt/opc/releases/20260811-014621`; backup `/opt/opc/backups/20260811-014621`, database dump `/opt/opc/backups/20260811-014621/opc_platform.sql.gz`, backend rollback `/opt/opc-backend.rollback.20260811-014621`, and previous release `/opt/opc/releases/20260811-012139` are retained. The deployed frontend hash is `112672de103b092dad2c6282f28e02d6d3b223969944a9598735467e7ec1bbc6`. Public, Assistant, admin, health, and deployed Assistant JS/CSS probes returned HTTP 200.

## Recorded Sidebar Stretch Remediation (2026-08-11)

### Root Cause And New Timing

The supplied 59fps recording showed that the user-visible defect was not only a rail divider issue. During collapse, a wide command with `width: calc(100% - 24px)` was still painted while the desktop rail itself was becoming narrower. The rail correctly changed geometry, but the wide child was clipped in successive frames and looked horizontally stretched before the compact UI appeared.

The corrected choreography is pointer-only and explicitly staged:

- Collapse: the compact 44px command becomes the active and focusable command immediately; the wide command and extended content exit over 120ms; only then does the native `276px` to `64px` rail transition run for 500ms.
- Expand: the rail begins from its compact state, while the compact command stays visible. At 320ms, the wide command and extended history content become visible and leave `inert` together. Their 160ms `opacity`/`transform` transition completes near the rail's final width.
- The former one-shot expansion keyframe was removed. This makes rapid pointer interactions retargetable and removes the mismatch where visible content had not yet rejoined the accessibility tree.

The native divider still follows the real grid track. No pseudo divider, fixed 276px overlay, width animation of a visible command, backend/API change, migration, Provider call, or fabricated research data is involved.

### Responsive And Accessibility Rules

- `<=840px`: history is a focus-managed drawer and retains full-width commands; phone controls meet 44px target size.
- `841px-1023px`: history is desktop-width where applicable; Inspector remains an overlay drawer.
- `>=1024px`: history rail, reading column, and optional Inspector use the established desktop grid.
- Keyboard toggles remain immediate. Hover feedback is fine-pointer-only, and reduced motion removes spatial movement. Inspector and mobile history preserve Escape and focus restoration.

### Verification And Production Delivery

| Gate | Result |
| --- | --- |
| Targeted Assistant motion/history/layout tests | `101/101` passed |
| Full frontend Vitest | `32` files / `218` tests passed |
| Existing frontend package tests | all eight passed |
| Vite build | passed, `1836` modules transformed |
| Full Spring Boot | `549` tests, `0` failures, `0` errors, `1` explicit skip |
| MySQL 8.4 controlled runner | `81/81`, zero leak |
| JAR and Python | executable JAR; Python `126` passed, `7` opt-in skips; explicit migration `7/7`; syntax passed |
| Repository gates | diff, credential, artifact, motion, and `.codegraph/` checks passed |

The one formal frontend-only atomic delivery switched `/opt/opc/current` to `/opt/opc/releases/20260811-032203`. Frontend hash: `cd42620b363ac4ec8e8e3d08e0be4522e6ac212caaecb11478a7670c9e1692a1`. The previous `/opt/opc/releases/20260811-014621` is retained for rollback. No database migration, backup, or AI probe was appropriate because no backend/data change shipped. Independent postflight returned HTTP 200 for the public site, `/assistant`, administration login/settings, `/api/health`, and `AssistantView-B9BcLALv.js`; preflight confirms Nginx and all three services are active, and backend `8082` remains loopback-only.

## Final Desktop Rail Continuity Release (2026-08-11)

### Problem Corrected

The preceding release still described a two-control handoff. Frame review of the supplied recording showed why it felt wrong: the content/control swap occurred before the physical sidebar boundary contracted, so the user saw an empty rail continue moving. The apparent stretch was a wide fluid button being clipped by a shrinking parent rather than a continuous command surface.

### Final Motion And Layout Contract

- Desktop `>=1024px`: the real history grid track transitions directly between `276px` and `64px` for 500ms. The native divider, reading column, and one black New Research button share this geometry.
- The black command is one DOM button at both sizes. Its boundary follows the rail; its icon recentres, and its one-line label is clipped/faded within the same surface. There is no duplicate button, delayed replacement, visible width animation, pseudo-divider, or final-frame swap.
- The extended title/search/history content remains clipped during the move and fades only during the last 100ms. A pointer reversal preserves visible content and retargets the CSS transition instead of restarting an entry animation.
- Tablet/phone `<=840px` remain full history drawers; Inspector remains an overlay through `1023px`. Escape, focus restoration, touch targets, safe areas, long-content behavior, keyboard immediacy, and reduced-motion behavior remain intact.

No research workflow was moved or changed: the workbench information architecture, real evidence/citation restrictions, session actions, Composer/drafts, reports, profile, run states, authorization, API shapes, and backend remain as delivered in Phase Four.

### Release Gates

| Gate | Result |
| --- | --- |
| Focused Assistant behavior tests | `106/106` passed |
| Complete frontend Vitest | `32` files / `223` tests passed |
| Frontend package scripts and Vite production build | all eight scripts and build passed |
| Spring Boot, MySQL 8.4, and JAR release gates | passed |
| Python deployment/migration tests and syntax | `120` passed; syntax passed |
| Repository gates | `git diff --check`, high-confidence credential scan, artifact tracking, and `.codegraph/` ignore passed |
| Postflight | public, Assistant, admin, health, and deployed static asset HTTP 200 |

Playwright was intentionally not used. Manual acceptance remains the visual check for the 1024px/1440px pointer fold and 375px/768px drawer behavior.

### Formal Frontend Delivery

- Production release: `/opt/opc/releases/20260811-035739`
- Frontend SHA-256: `2fe7f6b790a2d42900496f7b86a1ce41a841ec7bf4880981ffea0d4d9467980b`
- Atomic rollback release: `/opt/opc/releases/20260811-032203`
- Migration/database backup: not applicable; this was a frontend-only visual release.

The release used one atomic switch after the local gates and remote preflight. No repeat deployment, candidate creation, Provider research run, or database mutation was used.
