# Findings and Decisions

## Requirements
- Work in `C:\Users\ACha_\Documents\GitHub\OPC`.
- Preserve all existing core functionality, including public behavior and integrations.
- Apply the supplied Prisma visual system to the frontend user page.
- Keep the site name `SoloFirm`.
- Keep existing icon assets unchanged.
- Existing fonts may be replaced with Prisma's Almarai and Instrument Serif pairing.
- Add visual elements that Prisma requires when absent and remove conflicting presentation elements when safe; do not remove functional content or controls.
- Preserve the existing site's content while adapting it into the Prisma composition.
- Vue migration is allowed but optional and must not compromise behavior.
- Follow the Prisma component effects and responsive behavior closely.
- Use Impeccable, Using Superpowers, and Planning with Files throughout the task.
- Latest direction: convert the entire public frontend to a light color theme.
- Latest direction: make the `资料分析概览` cards use the same visual and responsive card language as the `面向创业者与研究者的资料工作流` feature grid.
- Latest direction: remove the rounded geometric font character. Use sharp, clearly structured Chinese serif/calligraphic families such as Songti and Kaiti.
- Latest direction: preserve a deliberate cinematic/editorial atmosphere after the light-theme conversion.
- Latest direction: use a stately English serif, preferably `Bookman Old Style`, for English display and brand typography.
- Latest direction: apply the Prisma component vocabulary consistently across the whole public UI, including small controls and states, not only large homepage sections.
- Latest direction: temporarily remove background imagery and background video from every public page except the login page.
- Latest direction: raise text contrast across the light theme; current hero brand/copy and controls are not visually clear enough.
- Latest direction: use the newly supplied Prisma prompt as the authoritative base for layout, components, noise, responsive behavior, and motion.
- Explicit preservation rule: do not remove any pre-existing functionality, route, control, filter, pagination behavior, export, authentication flow, analytics block, source link, or contact content.
- Explicit visual overrides over the supplied Prisma base remain active: light theme, Songti/Kaiti Chinese typography, Bookman Old Style English display, simplified button colors, static hero frame instead of autoplay, remove the About `AI + OPC` kicker, remove legacy SoloFirm gradient/shadow, and raise contrast.
- Clarified media rule: keep the Hero video autoplaying normally. Replace only the lower workflow feature-card video with a locally extracted still frame. Keep the login-page image.
- Global consistency rule: every public surface and micro-component must use the same Prisma tokens and states; no route may retain the old blue/gradient/glow component vocabulary.

## Prisma Specification Summary
- Three visual sections: full-height inset Hero, centered About treatment, and responsive Features composition.
- Dark, moody, cinematic direction with warm cream foreground colors.
- Typography: Almarai globally; Instrument Serif italic for selected expressive text.
- Palette: black global background, near-black About surface, charcoal feature surfaces, cream primary text/accent, gray supporting copy.
- Atmosphere: inline SVG turbulence noise overlays, hero media, and dark readability gradient.
- Motion: viewport-triggered word pull-up, delayed fade-up content, scroll-linked character opacity, and staggered card scale/fade entrances.
- Hero: top-center hanging pill navigation, giant bottom-aligned brand wordmark with superscript asterisk, supporting copy, and pill CTA with circular arrow affordance.
- Responsive behavior: compressed mobile navigation, fluid hero type, and feature layout changing from one to two to four columns.
- Supplied reference implementation stack is React 18, Vite, TypeScript, Tailwind CSS 3, Framer Motion, and Lucide React. The current SoloFirm stack must be inspected before selecting equivalents.

## Research Findings
- The Impeccable context loader found neither PRODUCT.md nor DESIGN.md in the target repository.
- Impeccable therefore requires the Teach flow before design edits; repository evidence must be explored before asking only genuinely unresolved strategic questions.
- The planning-with-files catch-up script reported no unsynchronized previous-session context.
- No existing task_plan.md, findings.md, or progress.md was present before this initialization.
- The repository is a multi-part project with `opc-frontend`, `opc-backend`, `opc-site-promo-video`, `data`, `docs`, and `scripts` directories plus a platform SQL dump.
- Git was clean before this task; the only current untracked files are the three planning records created by this session.
- CodeGraph tools and MCP resources are not exposed in the current tool session, so structural inspection must use repository-native files and language tooling unless availability changes.
- `opc-frontend` is already a Vue 3 + Vite application using Vue Router and Axios; no framework migration is needed.
- The Vite dev server is configured for port 5173 and proxies `/api` to the backend at `http://localhost:8082`.
- The HTML document title is `SoloFirm | OPC Platform` and the favicon is `/favicon.svg`; both are explicit preservation requirements.
- The repository README contains only the project name and `OPC network`, so product users and workflows must come from code/docs rather than the root README.
- The Vue frontend separates public user views from admin views. Public candidates are Home, policy list/detail, case list/detail, region directory, source ledger, and user login; admin screens use a separate `AdminLayout` and are outside the requested user-page visual scope unless shared CSS affects them.
- Public behavior is organized through `MainLayout.vue`, `router/index.js`, per-view Vue files, and API modules for auth, cases, policies, regions, sources, tags, visits, search logs, exports, and dashboard data.
- The frontend currently has no test, lint, or typecheck scripts in package.json; baseline verification must use the production build, backend/API contract inspection, and real-browser route/interaction checks.
- `global.css` is unusually large at about 213 KB and may contain generated or duplicated styling; it requires targeted inspection before replacement to avoid affecting admin screens or hidden states.
- Public routes are `/`, `/regions`, `/policies`, `/policies/:id`, `/cases`, `/cases/:id`, and `/sources`; `/login` is the public login, while `/admin/*` is guarded separately.
- Global public behavior includes route visit tracking (excluding login/admin and detail routes), an expandable/collapsible desktop sidebar, a mobile navigation drawer/backdrop, active route state, and automatic mobile drawer closure on navigation.
- The existing SoloFirm logo is an inline SVG reused in the home header/footer and public sidebar. It must be kept pixel-for-pixel in structure rather than replaced by Lucide or a new mark.
- Home content and actions that must remain include: SoloFirm brand, public navigation, login, enter platform, policy/case CTAs, data-view anchor, platform capability proof points, dynamic visit metrics/trend, popular policy/case, region ranking, source trace completeness, tag frequency, policy publish trend, generated insights, case category distribution, recent updates, contact details, and footer links.
- Home data is loaded from dashboard, policy, case, source, and visit APIs with tolerant fallbacks for optional dashboard/visit/source failures. Core policy/case loading errors surface through component state.
- Existing interaction behavior includes animated counts, reduced-motion checks, pointer trail, panel spotlight coordinates, and IntersectionObserver-based scroll reveals. The Prisma redesign can replace decorative pointer/network effects but must retain content/data behavior and reduced-motion support.
- MainLayout terminal output shows mojibake in several Chinese literals. This may be source encoding corruption or console decoding and requires source/browser verification before any content edits.
- Public login supports email-code send, 60-second resend cooldown, development-code display, required-field validation, code verification, redirect-after-login, existing-user display, logout, success/error notices, home return, and administrator-entry link.
- Region directory loads regions, policies, and cases; excludes the national parent row; computes coverage summaries and proportional bars; supports live cross-field keyword search; and links each region to `/policies?regionId=<id>`.
- Policy list exposes keyword, region, and policy-type filtering; query-seeded region selection; summary counts; Excel export; click counts; tag/status display; clear filters; pagination; detail navigation; keyword search logging; and loading/error/empty states.
- Case list follows the same public index pattern with category/region/keyword filters, summary counts, click rankings, pagination, detail navigation, and keyword search logging.
- Policy and case detail pages load by route id, render metadata and structured body fields, expose original-source links where available, preserve a back link, show loading/error states, and record detail visits after successful load.
- Source ledger supports keyword/type/status filters, source completeness summaries, original-link actions, local-file labels, reset, pagination, and loading/error/empty states.
- The initial multi-file public-view read exceeded output limits, so PolicyList, PolicyDetail, and CaseList require focused reads before implementation; no behavior from truncated sections will be assumed.
- Focused PolicyList inspection confirms 10-row pagination, five-page window controls, debounced 260 ms local filtering, delayed 700 ms search logging for keywords of at least two characters, type matching by either canonical field or Chinese keyword families, region selection from URL query, visit ranking display, and direct Excel export.
- Focused CaseList inspection confirms 10-row pagination, dynamic category options from live data, debounced local search plus delayed analytics logging, region/category menu mutual exclusion, visit rankings, and no export action.
- Neither list synchronizes filter changes back to the URL. The region directory's initial `regionId` handoff into PolicyList is therefore a one-way route seed that must remain functional.
- Custom filter dropdowns currently lack explicit ARIA expanded/listbox semantics. Their behavior must be preserved, and the redesign can improve semantics without changing values or filtering logic.
- Policy detail fields are title, tags, region, issuing body, document number, publish/effective dates, validity period, summary, key points, support measures, original URL, and evidence URL; both source actions and their no-link fallback must remain.
- The Axios client uses `/api`, a 15-second timeout, unwraps `{ code, message, data }` responses, rejects non-200 application codes, and attaches the user bearer token from sessionStorage to requests.
- User auth persists token/profile in sessionStorage and exposes email-code, verify, current-user, and logout API calls. This storage contract must not change during visual work.
- Public API contracts are `/public/policies`, `/public/cases`, `/public/regions`, `/public/sources`, `/public/dashboard/summary`, `/public/visits*`, `/public/search-logs*`, and `/public/tags`.
- Policy export deliberately opens `/api/admin/export/policies.xlsx` in a new tab. Although the route name is administrative, the current public button depends on it and must remain unchanged unless the backend contract proves otherwise.
- Shared API modules also contain admin CRUD/export functions, reinforcing that public visual edits must not rename or restructure API exports used elsewhere.
- The frontend has an existing lockfile, installed `node_modules`, and a previous `dist` build, so baseline build verification can run without changing dependencies first.
- Browser automation prerequisites are available: Node v24.13.0, npm/npx 11.6.2, with npx at `C:\Program Files\nodejs\npx.ps1`.
- Baseline production build passes under Vite 7.3.6: 106 modules, approximately 173.12 KB CSS and 246.30 KB JS before gzip.
- The build does not dirty tracked files; only the session planning records remain untracked.
- Backend is Spring Boot 3.5.15 on Java 17, listens on port 8082, and depends on a local MySQL `opc_platform` database on port 3306.
- Email delivery is disabled in development, matching the frontend's development verification-code display. A fully populated local browser baseline therefore requires both MySQL data and the backend service; the frontend still renders its shell and explicit error/empty states if APIs are unavailable.
- The repository includes SQL schemas/import scripts and three source spreadsheets, but the UI redesign does not require modifying those data assets.
- `main.js` has a single global stylesheet import and `App.vue` is only a RouterView, so a second public-only Prisma stylesheet can be imported after the legacy CSS without changing application boot behavior.
- Legacy `global.css` is over 10,000 lines with multiple late-appended animation/responsive blocks. Rewriting it wholesale would risk admin regressions; scoped public overrides plus focused template changes are the safer architecture.
- Existing global CSS already includes reduced-motion handling and many decorative keyframes, but the redesign should disable or supersede non-Prisma public effects rather than deleting shared/admin definitions.
- Runtime port check found 5173 free, MySQL 3306 not listening, and port 8082 occupied by a `QQ` process rather than the OPC backend. The current Vite `/api` proxy therefore cannot reach the backend until the port conflict/database state changes.
- A frontend-only baseline remains possible because Vue view shells and explicit loading/error states render independently; populated data-flow verification will require either the actual backend on another coordinated port or restoration of the expected local services.
- The baseline Vite server is running successfully at `http://localhost:5173/` under Node PID 69996.
- The Playwright skill wrapper exists but no Bash executable is available in the current PowerShell environment, so browser automation must invoke the same `@playwright/cli` package through npx directly.
- Direct npx Playwright CLI bootstrap also timed out with no output. The configured Codex in-app browser is the selected non-repeating fallback for screenshots and interaction checks.
- Impeccable product context is now established with `register: product`, evidence-backed users/purpose, cinematic-rigorous-focused brand personality, anti-references, five preservation/reveal/provenance principles, and accessibility expectations.
- The refreshed Impeccable loader confirms PRODUCT.md is active and DESIGN.md remains absent. The supplied Prisma specification will be formalized as DESIGN.md before implementation so future work retains the same visual system.

## Technical Decisions
| Decision | Rationale |
|----------|-----------|
| Preserve existing functional seams and content contracts before selecting a framework | Visual redesign must not accidentally redefine product behavior. |
| Prefer repository-native implementation patterns unless a migration is demonstrably safer and necessary | This minimizes unrelated churn while still allowing Vue if the current page is unstructured static HTML. |
| Retain the existing Vue 3 + Vite architecture | The requested optional upgrade has already occurred, so another framework change would be unrelated risk. |
| Keep externally hosted Prisma media behind graceful visual fallbacks | Remote assets may fail or be blocked; page content and controls must remain usable. |
| Add reduced-motion handling even though the reference emphasizes motion | It preserves the intended hierarchy while meeting accessibility expectations. |
| Scope the visual redesign to all public routes and the public login, while leaving admin UI behavior and presentation isolated | The user asked for the frontend user page, and the app already separates public and admin layouts. |
| Add a dedicated Prisma public stylesheet after legacy global.css | CSS source order and route/layout scoping can fully restyle public pages while leaving admin selectors intact and preserving rollback clarity. |
| Map the home page to three primary Prisma sections without dropping existing data | Hero keeps brand/navigation/CTAs; About carries the existing platform purpose and capability copy; Features opens with four Prisma cards and then contains every existing analytics/contact/footer block. |
| Implement Vue-native shared reveal components | `WordsPullUp`, `WordsPullUpMultiStyle`, and `AnimatedLetters` reproduce the required motion while retaining the existing Vue architecture and reduced-motion behavior. |
| Use `lucide-vue-next` only for new Prisma ArrowRight and Check affordances | The existing SoloFirm SVG logo and favicon remain untouched; Vue's Lucide package is the framework-equivalent icon source for newly required controls. |
| Restyle all public routes through `prisma.css` and leave admin selectors outside its scope | This satisfies the user-page scope while preserving the separate admin presentation and behavior. |

- Home mapping preserves all five public destinations in the hanging navigation, retains login as a separate action, and uses the giant `SoloFirm*` wordmark rather than Prisma replacement copy.
- The four initial feature cards map to the existing research canvas, policy index, case index, and source/region traceability capabilities; all dynamic analytics remain below them in the same Features section.
- Public list/detail/login templates can keep their current DOM and scripts because a scoped style layer can provide the Prisma palette, typography, control states, and responsive behavior without changing their data contracts.
- The former `lucide-vue-next` package is deprecated. The maintained Vue package is `@lucide/vue`, currently version 1.25.0, and it installed with no reported vulnerabilities.
- HomeView now contains the new Prisma Hero, About, and four-card Features structure while the original analytics and footer markup remains in place below the new feature grid.
- The old blue chart literals and pointer-trail script are still present after the first structural pass and must be removed/recolored before visual completion.
- The first implementation build passes after the HomeView restructure, shared reveal components, official Lucide Vue package, and scoped Prisma stylesheet. Vite transformed 1,869 modules and produced approximately 203.13 KB CSS and 254.95 KB JS before gzip.
- This repository tracks `dist` and two `node_modules` metadata files. Build/install updated those generated files; they require an explicit final diff decision rather than being mistaken for unrelated user changes.
- Home pointer-trail code and hardcoded blue trend/source colors have now been removed or replaced with the cream/neutral target palette.
- The original browser tab was cleaned up by the browser host between checks. A fresh tab was created from the existing browser binding; its empty snapshot output now requires screenshot and console inspection for a possible runtime render issue.

## Issues Encountered
| Issue | Resolution |
|-------|------------|
| Impeccable project context files are missing | Follow the mandated Teach flow, using repository evidence before writing them. |
| The Prisma prompt names a different studio and contains replacement copy | Use its visual structure/effects only; substitute SoloFirm's existing brand and content. |
| Backend port 8082 is occupied by QQ and MySQL 3306 is not running | Continue frontend shell/design work without touching those external processes; perform static/API-contract checks and report the limitation unless services become available. |
| Playwright CLI bootstrap timed out on Windows | Use the configured in-app browser runtime instead of repeating the same npx/wrapper attempt. |

## Resources
- Prisma specification: `C:\Users\ACha_\.codex\attachments\8c0ab5a1-1a96-4058-8444-828c241ea6aa\pasted-text-1.txt`
- Target repository: `C:\Users\ACha_\Documents\GitHub\OPC`
- Impeccable skill: `C:\Users\ACha_\.codex\skills\impeccable\SKILL.md`
- Planning skill: `C:\Users\ACha_\.agents\skills\planning-with-files\SKILL.md`

## Visual and Browser Findings
- Baseline desktop DOM exposes all required SoloFirm home sections, links, headings, contact information, analytics blocks, and zero/empty fallback values even while backend APIs are unavailable.
- Baseline full-page screenshot is visually dominated by a dark blue photographic hero and repeated header/background regions; most lower-page content is effectively invisible in the static full-page capture because reveal elements remain transparent until scrolled into view.
- Current palette and typography are blue/slate with a bold sans-serif wordmark, materially different from the supplied warm-cream Prisma system.
- Existing homepage uses an atmospheric remote image rather than the supplied Prisma video and does not present the required giant bottom-aligned SoloFirm wordmark, hanging pill navigation, centered editorial About treatment, or four-column feature entrance composition.
- The user-supplied screenshot shows the new four-card workflow section rendered correctly in structure: one cinematic media card followed by three numbered capability cards with image icons, checklists, and angled-arrow links.
- The requested revision is not a rollback of that structure; it is a palette inversion to a refined light theme and a request to extend the same card system to the analytics overview below.
- The typography screenshot specifically rejects the rounded Almarai appearance in the About statement. The replacement direction is a paper-archive editorial system: Songti for product reading, Kaiti/Xiaowei-style display accents, and sharp serif Latin rather than geometric sans.
- Atmosphere should come from pale paper-like neutral surfaces, visible grain, ink-dark typography, restrained pine green, and a small clay accent, while the real hero/video imagery continues to carry cinematic depth.
- `npm ls` proves only one Vue copy (3.5.39), ruling out duplicate runtime injection contexts.
- `@lucide/vue` 1.25.0 source calls `useLucideProps()` from the body of its functional `Icon` renderer; that helper calls Vue `inject()`. The client warns that inject is outside setup and returns undefined, while SSR happens to render. This package implementation is the confirmed root cause.
- The stable `lucide-vue-next` component package is therefore justified despite its deprecation notice; its runtime behavior must be browser-verified before visual work continues.
- The replacement build passes, but the long-running Vite server still served the old prebundled `@lucide/vue` chunk after the dependency swap. The next isolated variable is a controlled restart of the session-owned Vite process; old console entries are not evidence against the replacement package.
- The in-app browser also reported a Statsig telemetry timeout unrelated to localhost rendering; it is browser-host noise and not an application error.
- The session-owned Vite server was safely restarted after the lockfile change. It re-optimized dependencies and now listens on 5173 under Node PID 20684, providing a valid runtime test surface for `lucide-vue-next`.
- A clean browser tab proves `lucide-vue-next` resolves the black-screen bug: the full home DOM renders and the tab reports no application warnings or errors.
- The light-theme screenshot reveals legacy style leakage: the giant SoloFirm wordmark still has a blue gradient/shadow, several hero texts render white on a pale lower overlay, and the large media background weakens hierarchy. These require explicit high-specificity reset rules.
- The user clarified that all public background imagery/video should be removed for now, including homepage media treatments; only the login-page image remains authorized. Small feature icon images are retained as icons, not backgrounds.
- A later user message explicitly re-authorized one homepage background image if it is a carefully selected static frame extracted from the supplied hero video. This supersedes the earlier no-home-background rule while preserving no autoplay and no feature-card background video.
- Final media clarification supersedes the previous sentence: Hero retains autoplay video; the lower media card uses a static frame extracted from its own supplied video.
- Final clarified composition: the Hero's rounded inner video continues playing. A processed still from that same Hero video is used only on the outer `.prisma-hero` background visible around the inset frame. All sections below the Hero remain pure white/light paper. The lower feature-card video remains unchanged.
- The downloaded Hero reference video is H.264, 1924x1076, 10.04 seconds, 16.1 MB. It remains a runtime stream and is not added to the repository.
- Four Hero samples at 1.5s, 3.5s, 5.5s, and 7.5s show a stable composition. The 5.5s frame offers balanced subject placement and clean cloud detail; it will be locally extracted, slightly desaturated/darkened, and used as the outer Hero background.
- The final 5.5-second still was generated at 1920px width as a 107 KB WebP with modest desaturation and contrast reduction. It preserves cloud detail and gives the outer Hero padding atmospheric continuity without affecting the inner playing video.
- Button color policy is now strictly grayscale: ink-black primary, white/near-white secondary, transparent tertiary. Green is limited to checks, focus, and success state.
- Latest screenshots show four concrete defects to correct: low-contrast capability pills, low-contrast/dark analytics cards, over-colored login buttons, and the legacy gradient/drop-shadow on the giant SoloFirm wordmark.
- The About `AI + OPC` kicker is explicitly requested for removal; removing this decorative label does not remove a functional capability.
- English type will prioritize `Bookman Old Style` with Georgia fallback; Chinese product reading will use Noto Serif SC/Songti/SimSun and expressive headings will use Xiaowei/Kaiti.
- Browser console currently proves a separate runtime blocker: `@lucide/vue` 1.25.0 throws because `useLucideProps()` returns undefined during client rendering, producing a black page even though the production build passes.
- Current authoritative source still contains autoplay video in both the hero and first feature card, the decorative About `AI + OPC` kicker, and a remote Unsplash fallback. These are the first template/media changes to make.
- The current light CSS tokens are present, but late legacy selectors still win for the hero wordmark, capability pills, and several analytics internals. A final high-specificity public reset block is required rather than relying only on the earlier overrides.
- Current DESIGN.md is stale and still documents the original dark Almarai system. It must be rewritten to the light paper/ink, Songti/Kaiti/Bookman, static-frame system before completion.
- FFmpeg and FFprobe are installed locally, so the supplied hero video can be downloaded to a temporary path, sampled at multiple timestamps, and converted into a local optimized still without adding a runtime video dependency.
- Login markup and behavior remain intact. Its button palette can be simplified entirely in scoped CSS, preserving send-code, submit, logout, and navigation actions.
- Final Impeccable DESIGN.md and sidecar now encode the light paper/ink system, Bookman/Songti/Kaiti typography, grayscale commands, outer Hero still, playing videos, global public component consistency, and no-feature-removal invariant.
- The final sidecar parses as valid JSON. The current implementation production build succeeds with 1,660 transformed modules, 218.87 KB CSS, and 254.26 KB JS before gzip.
- Final desktop Hero browser check passes: all five navigation destinations, login, both CTAs, SoloFirm content, About, feature cards, all eight analytics blocks, contact details, and footer remain in the DOM with no application warnings/errors.
- Final Hero screenshot shows the intended Prisma result: playing cinematic inset video, static-frame outer gutter continuity, black hanging navigation, cream Bookman wordmark, no blue gradient, no text shadow, and grayscale CTA buttons with readable contrast.
- About browser check confirms the decorative `AI + OPC` kicker is removed, the descriptive paragraph remains, and all three capability pills now render as readable ink-on-paper controls rather than nearly invisible white labels.
- Feature workflow browser check confirms the lower Prisma video remains playing, the three icon/checklist cards share the same light paper styling, the Songti/Kaiti/Bookman typography is active, and all original feature links remain visible.
- Coordinate scroll calls did not move the in-app page from the feature viewport, so analytics verification will switch to the browser's DOM page-scroll API rather than repeating the same control path.
- A dedicated `#home-analysis` anchor enabled deterministic analytics screenshots. The first final pass replaced the old dark cards with consistent light paper surfaces, restored black headings and readable gray descriptions, and fixed source trace labels/rings.
- The follow-up analytics screenshot shows all four first-row cards now share the Prisma card vocabulary with high-contrast internal rows. The remaining dark wrapper around the three visit metrics was identified and removed with a transparent wrapper override.
- Final login DOM contains every original auth field/action and reports no warnings/errors. The page now uses one black primary button and one white/gray secondary button with matching inputs and typography.
- Login screenshot reveals the remote Unsplash image is not visibly resolving; computed CSS still references the URL but falls back to the paper-card color under the overlay. To satisfy the retained-image requirement reliably, the existing image should be downloaded and optimized as a local public asset without changing login behavior.
- The original login photo was downloaded and optimized locally to a 134 KB WebP. Visual inspection confirms it is the intended night workspace/city scene; login now references the local asset for reliable rendering.
- Fresh desktop browser inspection found four remaining public-shell defects: Vue's default prefix active class still highlighted Home beside the real section, navigation numbers and captions inherited legacy blue, topbar pseudo-elements retained the blue decorative field, and API failures used a low-contrast pale red.
- The current home analysis cards preserve their real API and computed-data paths, but the template had no explicit loading/error announcement around the analytics grid. The public audit fix adds an ARIA live state without replacing or hiding the real cards.
- Post-fix desktop route verification confirms exact active navigation on all lists and both detail routes, zero horizontal overflow, neutral captions/numbers, and readable API failure panels. The remaining blue computed values were limited to hidden legacy motion children and custom-select trigger text; both are neutralized in the scoped public layer.
- Console inspection exposed unhandled mounted-hook rejections in PolicyListView and CaseListView when the backend returns 500. Their initial Promise.all calls now feed the existing loading/error UI, preserving the API contracts while removing Vue runtime warnings; the environment failure remains visible rather than being replaced with fake data.
- The home workflow video plays when its card enters the viewport and all four workflow cards render at full contrast. An 8px page overflow was traced to the legacy full-viewport footer width/negative margins and is removed by a final public-only width reset.
- Tablet inspection exposed a responsive cascade conflict: legacy max-1080 home navigation rules reordered the Prisma grid, collapsed the hanging nav to 64px, and showed a native scrollbar. Final tablet/mobile rules now restore source order, three explicit tracks, centered links, and fixed-width login action.
- Mobile inspection found a nested page scrollbar and visible paper gutter caused by legacy `overflow-x: hidden` computing the home page's vertical overflow to `auto`. The home page now uses `overflow-x: clip` with visible vertical overflow so only the document scrolls.
- Root-level mobile measurements showed the legacy rule also promoted `body` to a second scroll container. A route-scoped `:has(.home-shell)` reset releases both body and app while leaving `html` as the sole page scroller.
- The user's expanded scope includes both administrator authentication and every `/admin` workspace. Baseline browser evidence showed the admin login still used blue gradients/glass styling; the admin shell also inherited the original color system. A dedicated Prisma admin layer now covers exact navigation, login media/forms, workspace surfaces, CRUD forms, tables, analytics, states, and responsive behavior without changing admin APIs or handlers.
- Browser scrolling confirmed all eight home analytics cards become fully visible, use the same paper/ink card language, and contain no low-opacity labels. The only remaining visible cyan accent was the legacy insight-row pseudo bullet; it is now the semantic state green with no glow.
- Final admin console inspection found unhandled initial dependency requests in PolicyAdminView and CaseAdminView. Both now load regions, sources, and records inside their existing loading/error state boundary, so backend failures remain visible without Vue mounted-hook warnings.
- User and administrator login templates now use the same `BrandMark`, the same local `/media/solofirm-login.webp` image, the same grayscale form vocabulary, and the same Xiaowei/Kaiti display type.
- The user login no longer exposes an administrator entry. Login is the default mode, registration is an explicit secondary action, and the existing auth endpoints now receive an optional `login|register` mode without changing their paths.
- The administrator login no longer renders `ADMIN WORKSPACE`, `Manage / Review / Export`, or `ADMIN ACCESS`; the equivalent user-only labels were also removed from the public login.
- Fixed-size mobile login headings were a real overflow risk at 320px. The final shared rule uses `clamp(2rem, 7vw, 3.4rem)` and a smaller registration variant, while retaining `white-space: nowrap`.
- Real-browser measurements at 320x844 confirm the administrator title text is about 196.6px wide, the user login title about 229.4px, and the registration title about 220.8px, all within the available column with no document overflow.
- Desktop, tablet, and mobile snapshots show both login pages use the same background crop, left-side font, logo treatment, surface geometry, and responsive single-column breakpoint. Browser console inspection reported zero application errors or warnings.
- MySQL remains unavailable on port 3306. The Spring Boot process is listening on 8082, but end-to-end account lookup, code delivery, and session creation still depend on starting the configured database; no fake success path was introduced.

---
Update this file after every two repository or browser inspection operations.

## Administrator Login Single-line Description (2026-07-19)
- The wrap came from the shared legacy `.login-visual p { max-width: 360px; }` rule, not from the administrator panel width.
- A scoped administrator override now uses `max-width: none`, `width: max-content`, and `white-space: nowrap`.
- A compact mobile variant preserves readable type instead of shrinking the full sentence until it becomes illegible.
- Production browser checks at 1440x900 and 320x844 show one-line heights, zero mobile overflow, and no application console errors.

## Password Authentication Completion (2026-07-19)
- The prior user interface and `/api/auth/verify` endpoint implemented email-code login because that was the earlier explicit product request. The new authoritative contract is password login with email verification reserved for registration.
- Public authentication endpoints are now `POST /api/auth/login`, `POST /api/auth/register`, and registration-only `POST /api/auth/email-code`. The old `/api/auth/verify` path returns a clear business rejection rather than a generic 500.
- `platform_users.password_hash` is nullable only for pre-migration accounts. New and upgraded accounts always receive a BCrypt hash before persistence.
- Username lookup relies on the existing case-insensitive MySQL collation plus a new unique username index. Email lookup remains normalized to lowercase.
- Production migration retained 2 accounts, added one password column and one username unique index, and found zero duplicate usernames. Both legacy hashes remain empty until their owners verify email and choose passwords.
- Stable card geometry is implemented without placing hidden fields between visible login controls: login uses a fixed minimum form height with the submit action anchored to the last grid row, while registration uses the same natural height.
- Production browser measurements: desktop login/registration forms `408px`; mobile login/registration forms `470px`; mobile panels `1194.104px`; horizontal overflow `0`.
- The first deployment health gate accidentally compared `200` with `%200`; automatic rollback restored the old backend, while the additive schema migration remained compatible. The corrected gate then completed the deployment successfully.
- The final production password-login smoke test inserted one isolated BCrypt QA account, verified username and email login paths, read `/me`, logged out, and removed all QA rows. Account count returned to 2.

## Administrator Subdomain and ALTCHA Completion (2026-07-18)
- `admin.findopc.online` is live through a direct Huawei DNS A record to `39.105.25.189`; Cloudflare orange-cloud proxying is not in use and is not required.
- Nginx isolates administrator routes on the subdomain, redirects main-domain `/admin*` traffic, and sends non-admin subdomain routes back to the public domain. HTTPS is valid through 2026-10-16.
- ALTCHA uses the official v2 contract: a top-level signature over PBKDF2/SHA-256 parameters. The production configuration is enabled with cost `5000` and a 300-second lifetime.
- The backend binds challenges to the `register` action and rejects expiry, wrong action, invalid solution, missing proof, and replay. Login and final email-code account creation remain unchanged.
- Production proof verification reached the existing-account guard without sending email or creating data; reusing the same payload was rejected.
- The administrator settings page exposes account/session, email/code, and registration-verification tabs. At 320px, tab icons are intentionally hidden and all three labels remain on one line without horizontal overflow.
- ALTCHA removes its original host attributes after initialization, so an attribute-qualified CSS selector cannot control its layout. The final rule scopes the direct `altcha-widget` child of the user login form, keeps it connected and operational, and removes it from document flow.
- Final production measurements at 320x844 show identical login/registration geometry: `1105.104px` panels, `381px` forms, zero horizontal overflow, and an empty application warning/error console while the registration widget remains present.
- The final checksum-verified frontend is `/opt/opc/releases/20260718-235925/frontend`; `/var/www/opc.rollback.20260718-235925` is the immediate rollback. The served index SHA-256 is `2c664ccb1f9f6721ed72e6004320a96329f9c45324b96ecc05afb93d4bad8ee7`.

## Production Server Inventory (2026-07-18)
- Correct production target: `39.105.25.189`; the initially supplied IP was not the target and received no deployment changes.
- SSH login succeeds as root on Alibaba Cloud Linux 3. Host key is ED25519 with SHA-256 hex fingerprint `119ae50f7f0bdb545996a90b49521db3e1404aeae327c13d64c7e67af8195672`.
- Nginx listens on 80/443 and serves `findopc.online` plus `www.findopc.online`; the frontend root reported by Nginx is `/var/www/opc` and `/api/` proxies to `127.0.0.1:8082/api/`.
- The currently deployed frontend entry is `/var/www/opc/index.html` (timestamp 2026-07-17 20:35).
- The live backend process is `java -jar /root/opc-backend.jar --spring.config.location=file:/root/application.yaml`; a second older jar exists at `/opt/opc-backend.jar`.
- MySQL 8 listens on 3306, the backend listens on 8082, and disk utilization is only 18%, leaving sufficient room for timestamped backups.
- `opc-backend.service` is installed and enabled but was not listed among running systemd services even though the Java process exists. Its unit state and launch ownership must be resolved before restart.
- Detailed service inspection confirms a split deployment: the live manual process uses `/root/opc-backend.jar` and `/root/application.yaml`, while `opc-backend.service` uses `/opt/opc-backend.jar` and `/opt/opc/application.yaml`.
- Because the manual process owns 8082, the enabled systemd service is in an auto-restart loop and had exceeded 8,400 failed restarts. Production should be normalized to one systemd-owned process during the deployment window.
- The remote database is `opc_platform` with existing business data (49 policies, 106 cases, 35 regions, 111 sources, 263 tags, and 889 visit logs). It also already contains user auth tables and two platform users; all data must be preserved.
- Existing database tables do not yet include the new administrator-session and platform-settings tables. Only idempotent additive migrations should be applied; the repository's full schema must not replace the production database.
- The current remote application config has the production datasource credentials and basic auth timing but lacks the new admin/settings/mail configuration block. The config must be extended without exposing or overwriting its existing datasource password.
- The frontend directory contains several obsolete hashed bundles from previous copies. A timestamped directory rename followed by a clean `dist` upload is safer than overlaying more hashes.
- Production MySQL 8 treats `sensitive` as a reserved word. A minimal temporary-table probe deterministically fails when unquoted and succeeds when quoted, so both DDL and MyBatis field mapping must use backticks.
- No database-backed integration-test seam exists in the current backend test suite. The correct regression loop is the real MySQL idempotent migration followed by the admin settings endpoint, not a shallow string-only unit test.
- The deployment normalized the backend to `/opt/opc-backend.jar` under `opc-backend.service`; the service is active with PID 335368 and zero restarts. The old `/root` jar/config remain untouched as a direct rollback option.
- Public HTTPS routes and all four core API collections return 200 after cutover. The served HTML references the new hashed bundle `index-BZ03F9Wn.js`.
- A real administrator login with the configured password returned 200. Authenticated reads of mail settings and registered users also returned 200, proving the new auth interceptor, `app_settings` mapping, and user-management path work against production MySQL; the temporary session was logged out.
- Production mail remains intentionally disabled and no SMTP password is configured. The settings page reports SoloFirm, `smtp.qq.com`, mail disabled, and password not configured.
- Real-browser desktop inspection of `https://findopc.online/` shows the deployed Prisma Hero, current production data, and all analysis/contact sections. The Hero video is actively playing with readyState 4, the outer background references the deployed local frame, SoloFirm has no text shadow, horizontal overflow is zero, and console warnings/errors are empty.
- Scrolling the deployed homepage into the workflow section starts the second video as designed while pausing the off-screen Hero video; both report readyState 4.
- Public user login passes desktop and 320x844 browser checks with shared Xiaowei/Kaiti typography, single-line title, no old decorative labels/admin link, no horizontal overflow, and no page-console warnings/errors.
- Browser administrator login with `opc2026` reaches the production workspace. The settings page displays both registered accounts, the complete SMTP/auth policy form, the 18x18 password-clear checkbox, and the SoloFirm email preview on desktop/mobile without page-level horizontal overflow.
- The fixed-width email artifact preview intentionally scrolls within its own iframe on a 320px viewport; the application document itself remains 305px client/scroll width. The browser administrator session was explicitly logged out after verification.
- Final production paths are `/var/www/opc` for the frontend, `/opt/opc-backend.jar` for the backend, `/opt/opc/application.yaml` for datasource/application config, `/etc/opc-backend.env` for protected runtime secrets, and `/etc/systemd/system/opc-backend.service` for process ownership.
- Release artifacts remain at `/opt/opc/releases/20260718-2037`. Full backups are at `/opt/opc/backups/20260718-2037`, including a verified compressed database dump; the immediately previous frontend is also retained at `/var/www/opc.rollback.20260718-2037`.
- Final service audit: nginx, mysqld, and opc-backend are active; exactly one backend Java process exists; systemd reports zero restarts and no post-start errors. HTTP redirects to HTTPS and all required public routes/API return 200.
- TLS certificate for `findopc.online` is valid through 2026-10-07 13:47:13 GMT. Certificate renewal remains an infrastructure responsibility outside this code deployment.
- Deployed hashes: backend jar `b939045b31efdb64d4040b9a4f4479b7a364fd117b35108fb14195bef515dd5b`; frontend index `8ea31cc71168fbdb3f5920a5cc6f2fdb88771b75f292cff2c9a76a98983603b2`.
- Rollback does not require destructive database changes because the migration was additive. Restore the previous frontend directory and either the backed-up `/opt` jar/unit/config or launch the untouched `/root` jar/config if an application rollback is required.
- The current hotfix build passes with 1,663 transformed modules and emits `index-BZrxl8ZH.css` plus `index-CLRqV0ZH.js`.
- Browser measurements at 1440x1000 prove login and registration now have identical 743.646px panels, 319px forms, and 96.854px title blocks. The reserved username input is disabled with `tabIndex=-1` in login mode and enabled in registration mode.
- At 320x844, login and registration retain identical 1105.104px panels and 381px forms with zero horizontal overflow. Copyright remains in normal flow after the card and does not overlap inputs or actions. Administrator login passes the same mobile copyright/overflow check.
- `admin.findopc.online` currently returns NXDOMAIN from AliDNS and Google DNS. The existing TLS certificate covers only `findopc.online` and `www.findopc.online`, so the administrator virtual host and certificate cannot be finalized until DNS propagates.
- Cloudflare orange-cloud proxying is optional. Using it requires moving the zone's authoritative nameservers to Cloudflare after copying all existing records; the lower-risk direct path is a Huawei DNS `admin` A record to `39.105.25.189`.
- Frontend hotfix release `20260718-213515` is live at `https://findopc.online`. The deployment verified all six files by SHA-256, preserved `/var/www/opc.rollback.20260718-213515`, reloaded only Nginx, and left `opc-backend.service` active with zero restarts.
- Production browser QA confirms desktop recent-update cards have a computed 12px separation and `18px 20px` padding; mobile cards have 10px separation and `15px 16px` padding. Screenshots show visually distinct paper cards in both layouts.
- Production `/login` reproduces exact login/register equality at 1440x1000 and the corrected copyright flow at 320x844. `/admin/login` also passes the 320x844 overflow/copyright check with an empty warning/error console.
- Anonymous command-line access to `/api/policies` returns 403 both before and after the frontend cutover, while the browser homepage loads its production analytics successfully. It was removed as a frontend release gate but remains recorded as a server access-policy behavior.
- ALTCHA renders its internal checkbox in the light DOM. The public global input rule was therefore overriding the official control with the form input height, producing a tall narrow checkbox; explicit widget-scoped dimensions correct it without modifying the library.
- The official ALTCHA checkbox may be unchecked to reset a proof. This is safe because the Vue state immediately clears the payload and disables email-code delivery, while the backend rejects missing, invalid, expired, wrong-action, and replayed proofs.
- After a successful email-code request, the proof has already been consumed and must not gate final account creation. The UI now replaces the widget with a completed state; only an explicit resend requires a fresh proof.
- The administrator notice collision came from the later `.admin-shell .settings-notice { margin: 0 !important; }` rule. A dedicated wrapper with bottom padding makes spacing independent of paragraph-margin precedence.
- Production release `20260719-033330` contains the account-deletion backend and all six requested page fixes. Frontend hotfix `20260719-034543` contains the ALTCHA geometry/workflow correction and robust administrator-notice spacing.

## Trusted Data And AI Foundation (2026-07-23)
- The revised proposal is viable as a platform roadmap, but its first visible release should be narrowed to evidence-aware case analysis.
- Public case, policy, source, detail, and dashboard reads are already isolated to `published`; administrator reads now use `/api/admin/**`.
- The remaining P0 isolation gap is `/api/admin/export/policies.xlsx`: it is excluded from `AdminAuthInterceptor`, and its service query currently exports every policy status.
- The current public analytics route is `/analysis`; future analytics work should extend it or redirect `/analytics` to it instead of creating two competing dashboards.
- Current records have publication status but no independent verification state, revision, source snapshot, or claim-level evidence. Historical published rows must bootstrap as `legacy_unverified`; only a manually reviewed golden set should enter AI context.
- CodeGraph was initialized in the requested repository on 2026-07-23: 182 indexed files, 3,423 nodes, and 7,045 edges.
- The generated `.codegraph/` directory was already covered by the repository root `.gitignore` and does not appear in Git status.
- The first implementation seams are the authenticated administrator export boundary and, subsequently, authenticated `/api/ai/**` requests backed by published and verified evidence only.
- CodeGraph traced the policy export from the Vue `PolicyListView` click handler through `src/api/export.js` to `ExcelExportController.exportPolicies` and `ExcelExportService.exportPolicies`.
- The frontend currently uses `window.open`, which cannot attach the existing `X-Admin-Token`; the correct fix requires an authenticated Axios Blob request plus removal of the backend interceptor exclusion.
- `UserAuthService.getCurrentUser(token)` already validates session expiry and active account status. A future `/api/ai/**` interceptor or argument resolver can reuse this service instead of creating a second user-session implementation.
- `SecurityConfig` does not implement bearer-token authentication; it only permits known public/auth/admin paths and requires framework authentication elsewhere. AI routes therefore need an explicit user-session interceptor backed by `UserAuthService`, not reliance on the current Spring Security default.
- The public policy export is now separated from the managed export: `/api/public/export/policies.xlsx` is published-only, while `/api/admin/export/policies.xlsx` is protected and can retain administrator dataset semantics.
- The provider-neutral AI boundary is implemented as `AiClient` with immutable request, response, and descriptor records. The default provider is disabled; only the explicit `opc.ai.provider=fake` setting enables deterministic test output.
- Disabled generation returns `SERVICE_UNAVAILABLE` (503) instead of fabricating an answer. Provider descriptors expose only provider, model, and availability; no API key or vendor credential is serialized.
- `GET /api/ai/capabilities` is protected by the existing `UserAuthInterceptor` and returns the current provider readiness plus the versioned `case-analysis-v1` capability. Anonymous access is rejected with the existing 401 result contract.
- The focused AI suite now covers provider selection, disabled failure behavior, minimal authenticated identity handling, and the capabilities response. All 6 focused tests pass.
- Full backend verification passes with 48 tests and the frontend Vite production build passes with 1,684 transformed modules. Production deployment remains intentionally pending while concurrent homepage edits are being stabilized.
- The first production probe exposed a Spring Security integration gap: `/api/ai/**` was not in the permit list and returned HTTP 403 before the user-session interceptor. The security-filter-chain regression test now covers this path, and `/api/ai/**` is explicitly permitted for interceptor-level session validation.
- After the security fix, the full backend package passed all 48 tests again. Production release `20260723-233915` is live with frontend SHA-256 `6db5afaa98e0ba06e6446836f6980cb801a4d746b1ede4844fbfe021c8b64f05` and backend SHA-256 `8d8f0dd66d58bef4182ade6ea194c35e20875945ab1eda5da983d2ab601334c2`.
- Release backups and rollback artifacts are preserved at `/opt/opc/backups/20260723-233915`, `/var/www/opc.rollback.20260723-233915`, and `/opt/opc-backend.rollback.20260723-233915`.
- Production smoke checks returned 200 for the public root, user login, administrator login, health, policies, cases, and sources. Anonymous `/api/ai/capabilities` now reaches the application and returns business code 401 as intended.

## AI Case Analysis Phase One Findings (2026-07-24)
- The official DeepSeek documentation domain was blocked by the browser security policy, so no DeepSeek V4 Flash model identifier was guessed or compiled into production.
- A persisted provider configuration must be read per request; otherwise administrators would need to restart the backend after every enable/disable or model change. `ManagedAiClient` now provides this runtime seam.
- Publication state alone is not sufficient AI evidence governance. The independent `legacy_unverified/verified/excluded` field preserves historical data while allowing a small manually reviewed golden set.
- Model-provided citation titles and URLs are not trusted. The backend accepts only known verified source IDs and replaces display metadata with database values.
- The generated `active_guard` column and unique key provide cross-process one-running-analysis-per-user protection; an in-memory lock would not protect multiple backend instances.
- Connection tests should work while the provider is disabled. They use stored credentials with a one-token, no-retry request and do not enable the production provider.
- Production contains no real model credential. The correct current state is encrypted-key infrastructure ready, provider disabled, and capabilities unavailable until an administrator supplies official values.

## Production Reverse Proxy And Service Hardening (2026-07-24)
- Nginx was already the public API boundary, but Spring listened on `*:8082` and relied on the cloud security group to prevent direct access.
- Production now sets `SERVER_ADDRESS=127.0.0.1`; this JVM/Linux combination reports the socket as `[::ffff:127.0.0.1]:8082`, an IPv4-mapped IPv6 representation of the loopback address.
- Listener validation now parses IP semantics and rejects wildcard, unspecified, wrong-port, and external addresses instead of comparing only display strings.
- `opc-backend.service` runs as the no-login `opc` user with no ambient capabilities, `NoNewPrivileges`, private temporary files, protected home/devices/system paths, SUID/SGID restrictions, and limited address families.
- `/etc/opc-backend.env` and `/opt/opc/application.yaml` are `root:opc 0640`; the executable JAR remains `root:root 0644`.
- The case-analysis endpoint has an exact Nginx location with a 64 KiB request limit, 5-second connect timeout, 15-second send timeout, 190-second response timeout, cache disabled, and an IP rate of 20 requests/minute with burst 5.
- JSON proxy buffering remains enabled because the endpoint is not SSE. Application login, daily token quota, and concurrency checks remain authoritative behind Nginx.
- Two deployment attempts were automatically rolled back because the original health assertion did not accept the mapped-loopback display form. The service itself had started successfully as `opc`; the regression is now covered by tests.
- Final release: `/opt/opc/releases/20260724-030722`; backup: `/opt/opc/backups/20260724-030722`; frontend rollback: `/var/www/opc.rollback.20260724-030722`; backend rollback: `/opt/opc-backend.rollback.20260724-030722`.
- Independent verification found one Java process, no post-release error journal entries, external port 8082 closed, both exact Nginx locations loaded, and direct-origin rate-limit responses `200,200,200,200,200,200,429,429`.

## Standalone Entrepreneurship Research Assistant Findings (2026-07-24)
- The requested independent assistant is a different product workflow from `/cases/:id/analysis`: it starts from a user's entrepreneurship profile and retrieves several local cases and policies instead of analyzing one selected case.
- A bounded profile is the appropriate first contract. Venture type, region, industry, stage, budget, goal, resources, and a 500-character question keep the capability useful without opening an unlimited general chat endpoint.
- Database case and policy matches are assembled deterministically on the backend. Model output cannot create a local match; it can only provide recommendations and cite source IDs assigned in the current evidence bundle.
- Evidence eligibility remains `status=published AND ai_evidence_status=verified`, including the underlying source. A missing eligible bundle returns `evidence_insufficient` without calling the provider.
- The existing `ai_analysis_runs` table can support both workflows by adding `task_type` and making `case_id` nullable. The generated active guard continues to enforce one running AI task per user across capabilities.
- The public page uses the existing light SoloFirm Prisma language rather than the reference skill's original dark demo palette: paper surfaces, ink typography, fine borders, restrained green evidence states, and no decorative chat bubbles.
- `/assistant` remains intentionally absent from homepage and sidebar navigation, matching the earlier requirement to build the page before choosing its public entry point.
- The user later confirmed deployment could proceed. Release `20260724-050106` is live with the provider still disabled; a stored API key is configured but is not returned by the administrator API.

## AI Evidence Stabilization Findings (2026-07-24)
- The original case-analysis and entrepreneurship-advice services each owned overlapping quota, active-run, provider-call, and settlement logic. This made timeout recovery and failed-call accounting inconsistent. `AiTaskExecutionService` now owns that lifecycle for both public AI seams.
- An evidence hash over identifiers alone does not invalidate analysis when evidence text, publication metadata, or source content changes. The stabilized hash includes case, policy, and source content/version fields.
- A model response with prose but no valid backend-assigned citation must not be treated as factual output. Both workflows now fail in a controlled way when citations are absent or invalid.
- The administrator evidence-review queue keeps verification explicit and auditable. A case or policy cannot be verified without a complete, published, verified source chain.
- Production deployment validation previously rejected all enabled configurations. The corrected validator only permits enablement when encrypted credential state, strict HTTPS endpoint policy, and Model ID are all present; it still never prints or returns secrets.
- A real golden evidence set needs human review of source credibility and content. No historic production row was auto-promoted, and the golden-set count is `0` rather than fabricated.
- Release `20260724-073422` is live with timestamped frontend/backend rollback artifacts and passed health, authorization, and secret-redaction checks.
- The final republish completed as release `20260724-074745`. Server preflight confirmed active Nginx, MySQL, and backend services, one Java process, loopback-only backend binding, and the hardened `opc` process user.
