# SoloFirm Trusted Data And AI Baseline Audit

## Snapshot

- Audit date: 2026-07-23 (Asia/Shanghai)
- Repository: `C:\Users\ACha_\Documents\GitHub\OPC`
- Git HEAD: `b54b5e0`
- Production public URL: `https://findopc.online`
- Production administrator URL: `https://admin.findopc.online`
- Production release before Phase 13: `/opt/opc/releases/20260723-212947`
- Production backup before Phase 13: `/opt/opc/backups/20260723-212947`
- CodeGraph: 182 files, 3,423 nodes, 7,045 edges

The working tree contains intentional user and prior implementation changes. This audit does not treat Git HEAD alone as the deployable baseline and does not authorize discarding any uncommitted file.

## Runtime Baseline

| Area | Current implementation |
|---|---|
| Frontend | Vue 3, Vue Router, Vite, Axios |
| Backend | Spring Boot 3.5.15, Java 17, MyBatis-Plus |
| Database | MySQL 8 |
| Public UI | SoloFirm Prisma Light public routes and account flow |
| Administrator UI | `admin.findopc.online` with individual administrator sessions |
| Human verification | ALTCHA before registration email-code delivery |
| Model provider | Not selected or configured |
| AI routes | Not implemented at this snapshot |
| Ingestion pipeline | Offline Excel-to-SQL scripts only |

## Public And Administrator Data Matrix

| Data surface | Anonymous/public behavior | Administrator behavior | Phase 13 state |
|---|---|---|---|
| Cases list/detail | Server filters to `published`; non-published detail is not found | `/api/admin/cases/**` can read all statuses | Implemented and tested |
| Policies list/detail | Server filters to `published`; non-published detail is not found | `/api/admin/policies/**` can read all statuses | Implemented and tested |
| Sources list | Server filters to `published` | `/api/admin/sources` can read all statuses | Implemented and tested |
| Dashboard | Counts and recent updates use published records | Administrator dashboard retains operational statistics | Implemented and tested |
| Policy Excel export | `/api/public/export/policies.xlsx` exports published policies only | `/api/admin/export/policies.xlsx` exports the managed dataset and requires an administrator session | Added in Phase 13 |
| AI context | No public model access | No administrator model access | Pending verified evidence and AI boundary |

## Current Data Limitations

- Publication status and verification status are not separate.
- Historical published records do not have a claim-level evidence graph.
- Case industry, technology, and outcome metrics are not normalized.
- Source snapshots, content hashes, immutable revisions, and content review events do not yet exist.
- Existing published rows must enter governance as `legacy_unverified`; they must not be silently promoted to verified research evidence.

## AI Foundation Decision

The first visible AI capability is evidence-aware case analysis. The product exposes one `SoloFirm research assistant`; capability modes are server-side business contracts, not separate provider bots.

The required server path is:

```text
authenticated user request
-> AI task service
-> published and verified revision/evidence loader
-> versioned prompt and JSON schema
-> provider-neutral AiClient
-> structured output and evidence validator
-> persisted analysis record
-> sanitized Vue rendering
```

Statistics, rankings, coverage, and technical-assessment scores are deterministic backend calculations. A model may explain those results but cannot replace or modify them.

## Immediate Gates

1. Protect `/api/ai/**` with the existing user session source of truth.
2. Add a provider-neutral disabled/fake provider contract before selecting a real provider.
3. Introduce verification and evidence storage through versioned migrations.
4. Manually verify the initial golden set before returning case-analysis facts.
5. Keep provider keys and provider-specific identifiers in protected server configuration only.

## Verification Commands

```powershell
cd opc-backend
.\mvnw.cmd test

cd ..\opc-frontend
npm run build

cd ..
git diff --check
codegraph status
```
