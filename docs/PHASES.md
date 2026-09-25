# Phase plan — FirstIn job dashboard

Every phase gets its own document in this folder, written step by step:
what the design is and the exact process followed. No phase starts until the
previous one is merged.

| Phase | Scope | Doc | Status |
|-------|-------|-----|--------|
| 0 | Process infrastructure: branch protection, PR template, CI, checklist bot, secret/dependency scanning, written process | [PHASE-0.md](PHASE-0.md) | Complete — PR #2 reviewed, awaiting owner's merge approval |
| 1 | Backend: Spring Boot 3 + Java 21, Kafka consumer (`firstin-ingest`), PostgreSQL schema, ingestion + dedup + pay-parsing API, rate limiting, security headers | PHASE-1.md (to be written) | Planned |
| 2 | Frontend: React 18 + Vite SPA — Today/C2C/W2/Full-Time/Visa tabs, listing cards, search, honest freshness, all UI-UX.md states | PHASE-2.md (to be written) | Planned |
| 3 | Integration + deploy: React bundled into the Spring Boot jar, Render free-tier deploy, GitHub Actions daily ingest pipeline (08:05 America/Chicago), DLQ + run history | PHASE-3.md (to be written) | Planned |

## Rules for every phase document

1. Written before the phase's implementation PR merges (drafted as the phase
   is built, finalized at its review).
2. Covers the design (what was built and why) and the exact process followed
   (branch, PR, CI results, defects found, review loop, approvals, merge).
3. Records every decision the phase made and every decision it deferred.
4. Lives in `docs/` on the phase's branch and merges with the phase's PR.

## Source documents

- Architecture: [DESIGN.md](DESIGN.md)
- UI/UX: [UI-UX.md](UI-UX.md)
- Process: [PROCESS.md](PROCESS.md)
- JobRight research: [RESEARCH-jobright.md](RESEARCH-jobright.md)
