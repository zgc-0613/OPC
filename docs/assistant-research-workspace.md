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

Production deployment is not implied by local completion. Release, backup, rollback, and real DeepSeek probe fields remain unset until the deployment workflow actually succeeds with secure environment credentials.

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

Local implementation and verification are complete. Production deployment was not attempted because `OPC_SSH_PASSWORD`, `OPC_INITIAL_ADMIN_USERNAME`, and `OPC_INITIAL_ADMIN_PASSWORD` are absent from the current process environment. No release, backup, rollback directory, migration execution, or real DeepSeek production probe is claimed for this pass.
