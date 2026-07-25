# SoloFirm AI Readiness

## Purpose

This file records the stable prerequisites for an AI assistant without choosing a model provider or committing to a multi-agent UI. It is the handoff point for the revised product proposal.

## Data boundary completed

- Anonymous case, policy, and source APIs read `published` content only.
- Public case and policy detail APIs treat non-published records as not found.
- Administrator list and detail APIs retain access to draft, reviewed, pending, and published records.
- Public dashboard totals, covered regions, and recent updates use the same published-only rule.
- The frontend administrator pages use `/api/admin/**` reads instead of reusing public APIs.

Current administrator read endpoints:

```text
GET /api/admin/cases
GET /api/admin/cases/{id}
GET /api/admin/policies
GET /api/admin/policies/{id}
GET /api/admin/sources
```

## Existing data that can support an MVP

Cases already contain a title, region, category, actor, summary, business model, AI tools, outcome, tags, source, original URL, status, and reviewer. Policies and sources already provide traceable records that can be loaded with a case analysis request.

This is enough for a first evidence-aware case analysis prototype, provided the selected cases are manually checked before being included in the evaluation set.

## Data gaps before analytics claims

The current case schema does not contain normalized fields for:

- industry taxonomy;
- technology taxonomy;
- revenue amount, currency, period, and metric type;
- verification state separate from publication state;
- multiple evidence records and claim-level citations;
- source snapshots and content hashes;
- data quality coverage and missing-value reasons.

Industry, technology, and revenue dashboards must not infer these values from free text and present them as verified facts. AI may propose candidate values, but an administrator must approve them before they enter published analytics.

## Provider-neutral backend boundary

The backend should expose one internal provider interface instead of allowing controllers to call Coze or another model directly.

```text
AI controller
-> task orchestrator
-> published data and evidence loaders
-> deterministic calculation tools
-> AI provider adapter
-> structured-output and citation validator
-> analysis record
```

The provider adapter must own authentication, timeout handling, retries, response parsing, and provider-specific request formats. Provider secrets remain in server environment variables and must never be returned to the Vue application.

## Minimum analysis record

Every persisted AI run should retain:

- user ID;
- task type;
- target case or policy ID;
- model provider and model identifier;
- prompt version;
- input data version or content hash;
- referenced source IDs;
- structured result;
- success or failure state;
- token or cost metadata when available;
- created time and latency.

## Decisions required from the revised proposal

1. What is the single primary task for the first release?
2. Which model provider is used for the prototype and what is the monthly budget?
3. Are user prompts and generated results stored, and for how long?
4. What exact result schema and citation rules define a valid case analysis?
5. Which manually verified cases form the initial evaluation set?
6. Which normalized fields and coverage threshold allow a chart to be displayed?
7. What per-user quota and abuse limits apply?

No AI route, provider dependency, prompt, crawler, or analytics schema should be finalized until these decisions are recorded.

## Implemented Phase-One Boundary (2026-07-24)

The first evidence-aware case-analysis slice is implemented and deployed:

- provider-neutral runtime `AiClient` with an OpenAI-compatible adapter;
- AES-GCM encrypted provider configuration and administrator audit trail;
- authenticated capabilities and case-analysis APIs;
- published and explicitly verified case/policy/source evidence only;
- persisted usage, latency, request ID, status, error type, prompt version, and evidence hash;
- daily token quota and one concurrent analysis per user;
- strict JSON parsing and backend-validated citations;
- administrator evidence eligibility controls and model settings UI;
- a standalone protected analysis page with no homepage entry.

Production remains intentionally disabled until the official API Base URL, exact DeepSeek V4 Flash Model ID, and a real API Key are configured. Crawling, general chat, normalized revenue analytics, and automated publication remain outside this phase.

## Implemented Phase-Two Runtime Boundary (2026-07-25)

The local Phase-Two implementation adds an audited, bounded multi-round research runtime without replacing the existing provider or evidence governance:

- owned research sessions and ordered visible messages;
- asynchronous `202 Accepted` submission with polling, refresh recovery, cancellation, and safe retry;
- a finite run lifecycle with at most four model rounds, six tool calls, 8,000 aggregate tokens, a 12-message history window, and a 120-second default timeout;
- a provider-neutral native-tool contract plus a controlled JSON-plan compatibility mode, which remains the default until the configured production model's native tool support is verified;
- four read-only tools: `search_cases`, `search_policies`, `get_source`, and `compare_cases`;
- published-and-verified evidence filtering, current-run ID allowlists, deterministic evidence replay, legal citation validation, and stale-evidence conflicts;
- per-user and per-session active-run guards, per-user idempotency keys, shared daily-token quota reservation, aggregate multi-round settlement, and late-result rejection;
- administrator-safe runtime audit views that omit full private questions, secrets, raw provider responses, and hidden reasoning.

User content is persisted to provide conversation continuity. User messages are limited to 2,000 characters, assistant messages to 12,000 characters, and only the configured bounded history is returned to the model. Archiving prevents further messages and removes the session from the active list; it does not physically delete the data. Automated retention and purge are not yet implemented and must be resolved before claiming a complete long-term privacy lifecycle.

Local verification passes 247 backend tests, including 36 real MySQL Testcontainers tests, 13 Vitest component tests, eight frontend contract scripts, the frontend production build, 13 deployment/migration tests, and the deterministic 20-question Agent evaluation. Production deployment and the semantic real-model probe remain pending because the secure `root` SSH credential path is unavailable; Phase Two must not be declared complete until both succeed.
