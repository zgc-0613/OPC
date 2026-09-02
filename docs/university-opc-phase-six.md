# University OPC Public Archive Delivery

Date: 2026-09-02  
Scope: public-home high-density layout and the existing `06 高校 OPC` archive route  
Status: frontend-only release deployed under explicit user authorization; Docker/Testcontainers prerequisite remains an unexecuted residual gate

## Scope And Boundary

This is a public-frontend coherence pass. It keeps the existing Vue 3/Vite public archive, Spring Boot public API, MyBatis-Plus/MySQL data path, and Prisma Light visual language. It does not change Assistant behavior, add an AI Provider, mutate source data, create a new public API, change a migration, or alter authentication.

`UniversityOpcView` continues to fetch the existing `/api/public/university-opc` response. Its data remains explicitly labelled as a static preview for verification and is not written by this page. Source links remain the response's real `sourceUrl` values, opened as external links with `noopener noreferrer`.

## Problems Addressed

- The public home was constrained by an archive-width shell on high-resolution displays. The contact/footer combination could use viewport-oriented padding inside a capped parent, producing an undersized reading column.
- `06 高校 OPC` did not share the numbered public-navigation treatment used by `01` through `05`, despite already containing four real archive destinations.
- The first grouped-navigation pass still left `06` with a one-row trigger, a different inactive gray pair, and Chinese-only expanded rows. At a mobile breakpoint after a desktop collapse, the group could also remain visually collapsed because its desktop hide rule won the cascade.
- The university page rendered the complete filtered result set. Users could not choose a readable page size or move through a bounded result page.
- Dense, small fixed typography made the university content feel underscaled on 1440px-and-larger displays while long content still needed to remain safe on smaller screens.

## Information Architecture

The public archive keeps its original route ownership. `06 高校 OPC` is a navigation group, not a new dashboard:

| Area | Retained behavior | Delivery behavior |
| --- | --- | --- |
| Public navigation | Existing routes and active state | `06` adopts the same numbered, two-line archive visual structure and opens a compact submenu |
| University categories | `communities`, `support`, `activities`, `cases` query destinations | The group exposes these same four RouterLink targets with Chinese titles and English subtitles, and keeps the current category marked active |
| Data workspace | Existing filters, static-preview notice, source anchors | Rows are paged locally after the real API response is filtered |
| Source access | Existing real response URL | A source remains a normal external anchor; no click interception or generated citation is added |

## Pagination Behavior

The page offers `10`, `20`, and `50` rows per page. Only the selected page is rendered. Previous/next controls are disabled at the appropriate bounds, a bounded page-number window is exposed, and the current page carries `aria-current="page"`.

Changing the active category, province, evidence grade, keyword, or page size returns the user to page one. If a filtered result has no rows, the existing empty state remains and no pagination controls are presented. This preserves the existing filter semantics and avoids presenting a partial page as a complete search result.

## Layout And Responsive Rules

At `1440px` and above, the public archive can use a wider measured content rail, the University OPC reading area can grow to a bounded desktop width, and headings/body type gain physical scale without changing their Chinese font or Bookman numeric treatment. At `2000px` and above, the archive and University measures gain a second bounded increment rather than filling the monitor edge-to-edge.

At intermediate desktop widths, the university page remains inside a safe 1240px-oriented content measure. Existing narrow-screen breakpoints preserve a two-column stat/category grid before the filter controls and footer collapse to one column. Long URL, mixed-language, and record content use bounded wrapping so the page does not create horizontal viewport overflow.

The home footer uses stable `minmax(0, ...)` grid tracks on desktop and one column on narrow viewports. The University trigger and expanded rows use the same 68px two-line rhythm as the ordinary desktop archive links. At `<=900px`, they use the drawer's 58px touch-safe rhythm and explicitly restore title, Chevron, and submenu content even if the shell was collapsed at a wider viewport. The document does not claim manual visual acceptance at 375px, 768px, 1024px, or high-density desktop; those remain required before final acceptance.

## Menu, Motion, And Accessibility

The submenu uses the existing Vue `Transition` surface with a short opacity/transform entrance and exit. Pointer interaction can close it through the trigger, an outside pointer, or a submenu choice; Escape closes it from the trigger or document handler. Reduced-motion preferences disable the spatial transition.

The controls remain semantic: navigation uses `nav`, the disclosure uses a `button` with `aria-expanded` and `aria-controls`, pagination uses a labelled `nav`, page-size selection has a label, disabled bounds remain visible, and source URLs stay keyboard-accessible anchors. The desktop hover treatment is restricted to fine hover pointers.

## Component And API Compatibility

| File | Responsibility in this pass |
| --- | --- |
| `opc-frontend/src/layouts/MainLayout.vue` | Keeps the existing public shell and implements the `06 高校 OPC` grouped navigation, four real bilingual submenu labels, and close behavior |
| `opc-frontend/src/views/UniversityOpcView.vue` | Paginates already-fetched preview records and exposes accessible pagination controls |
| `opc-frontend/src/styles/prisma.css` | Adds high-density public/university proportions, the numbered submenu treatment, responsive constraints, and reduced-motion rules |
| `opc-frontend/src/styles/global.css` | Corrects home contact/footer width and padding geometry |
| `opc-frontend/src/layouts/__tests__/MainLayout.spec.js` | Covers bilingual submenu content, archive typography/state CSS contracts, mobile collapsed-to-drawer recovery, visible shell, and keyboard submenu close behavior |
| `opc-frontend/src/views/__tests__/UniversityOpcView.spec.js` | Covers page-size limits, page movement, filter reset, real source URLs, and high-density CSS contract |
| `opc-frontend/src/views/__tests__/HomeViewResponsive.spec.js` | Covers fluid home shell, stable footer tracks, and wrapping contact-link contract |

No backend controller, DTO, service, mapper, database table, migration, deployment API, or Assistant component is part of this change. Existing public API consumers remain compatible because the API response shape is untouched.

## Verification Record

Completed local evidence is:

- Focused University/navigation Vitest: `4/4` passing after the mobile rail correction.
- Complete frontend Vitest: `37` files / `244` tests passing.
- All eight existing frontend contract-script commands: passing.
- Vite production build: passing.
- Python discovery: `133` passing, `7` designed MySQL opt-in skips; Python syntax, `git diff --check`, high-confidence credential scan, build-artifact tracking, and `.codegraph/`/local-secret ignore checks: passing.
- Spring Boot JAR packaging: passing. The full suite reached `470` passing tests and `1` explicit skip before the Docker-backed class failed to initialize.

The required full Spring Boot invocation has `1` error only because `PhaseOneMySqlIntegrationTest` cannot initialize Testcontainers. `python scripts/run_phase_one_mysql_test.py --label university-opc-release` and the opt-in MySQL migration suite stop before executing because `docker.exe` is unavailable. Direct checks found no Docker CLI, Docker service, Docker Desktop installation, or registered WSL distribution. This is an external workstation prerequisite, not an application assertion or an API/data failure.

## Release Plan And Manual Review

Because the change is public frontend only, deployment uses the repository's existing frontend atomic workflow and must not create repeated candidate releases, run a migration, send a real Provider workload, or disclose credentials. The original pagination release completed on 2026-09-01. After the reported `06` visual mismatch was corrected, the initial bilingual-navigation follow-up completed on 2026-09-02; a later mobile-rail correction is recorded below:

- Initial bilingual-navigation production release: `/opt/opc/releases/20260902-003155`
- Initial retained atomic rollback release: `/opt/opc/releases/20260901-235819`
- Initial frontend index SHA-256: `2c2c68af3ba5a93b5bda7a60090a4c6cbc4d8a93f262b8a43adff5f819141b91`
- No migration, candidate loop, Provider request, database backup, backend restart, or external data write occurred.
- Postflight HTTP `200`: public home, `/assistant`, administrator login, `/api/health`, and the deployed main JavaScript asset. That asset contains all four bilingual University submenu strings. Nginx and the loopback-only `opc-backend.service` remained active.

### Mobile Rail Follow-up (2026-09-02)

The 375px drawer reproduction exposed an intrinsic-width collapse in the bilingual 06 submenu. The submenu now uses the full drawer rail after its 34px archive indent, removes the inherited pseudo-number column, and keeps Chinese/English labels on readable lines with ellipsis fallback. This follow-up was released once to `/opt/opc/releases/20260902-081330`; `/opt/opc/releases/20260902-003155` is the atomic rollback release and the deployed frontend index hash is `6235336ba392fee6dfc5f575f855835b69e535a11f75a43bdd20605c4e0deae8`. No backend, migration, AI Provider, or database state changed.

The MySQL 8.4/Testcontainers gate remains unexecuted because the workstation has no usable Docker runtime. After Docker Desktop or an equivalent compatible Docker runtime is available, rerun the existing MySQL runner to close that residual test gap; do not treat its absence as a passed test or trigger another deployment merely to close it.

### Shared Navigation Typography Follow-up (2026-09-02)

The reported `06` font mismatch came from a separate line-height declaration rather than a different downloaded font: numbered titles inherited `1.5`, while the University title forced `1.2`. Numbered archive titles and the University trigger now share one explicit `Noto Serif SC` / Songti family, `14px` size, `760` weight, and `1.5` line height. Their subtitles share the same family, `11px` size, `1.2` line height, and Prisma quiet color. No route, menu behavior, API response, or data changed.

The Red/Green typography contract test failed before the shared rule and passed afterward. Focused `MainLayout` tests passed `5/5`, complete Vitest passed `37` files / `245` tests, Vite production build passed, and `git diff --check` passed. One frontend-only atomic deployment switched production to `/opt/opc/releases/20260902-235323`; `/opt/opc/releases/20260902-081330` is the rollback target and the frontend index SHA-256 is `67c6154a427e8cb9a9566793f04dd136710f9b09e96db04de1243e88270221df`. Public home, `/university-opc`, `/assistant`, administrator login, `/api/health`, and the new CSS asset returned HTTP `200`.

The current production release is `/opt/opc/releases/20260902-235323`; its atomic rollback target is `/opt/opc/releases/20260902-081330` and its frontend index SHA-256 is `67c6154a427e8cb9a9566793f04dd136710f9b09e96db04de1243e88270221df`.

Manual acceptance still needs the public home and `/university-opc` checked at 375px, 768px, 1024px, 1440px, and a high-density desktop viewport. Check the menu disclosure/close paths, long source/title wrapping, the three page sizes, selected page focus/current-page state, filter reset, footer wrapping, and ordinary external source-link navigation.
