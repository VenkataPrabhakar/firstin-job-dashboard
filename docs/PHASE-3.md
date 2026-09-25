# Phase 3 — Integration + deploy (jar bundling, daily pipeline, Render)

Status: design approved below; implementation in progress.
Branch: `feat/phase-3-deploy`. Implements `docs/DESIGN.md` §5 (pipeline) and the
deploy commitments (§2 stack, §7 workflow). Consumes the Phase 1 API and the
Phase 2 SPA unchanged except where noted.

## Objective

One deployable artifact and one daily heartbeat: the React production build is
bundled into the Spring Boot jar, the app runs on Render's free tier against
Supabase Postgres + Upstash Kafka, and a GitHub Actions workflow publishes the
morning agents' ledgers to Kafka every day at 08:05 America/Chicago. Ingest runs
are recorded in a new `ingest_runs` table; `lastPull` becomes truthful run
history instead of `max(first_seen)`.

## Design decisions

- **Jar bundling:** `frontend-maven-plugin` runs `npm ci && npm run build`
  during the Maven build and copies the output into
  `backend/src/main/resources/static`. Spring serves the SPA; `/api/**` stays
  the API. One artifact, one Render web service, $0.
- **`ingest_runs` table** (new, Phase 1 deferred it here):
  `run_id` (unique, e.g. `2026-09-26`), `started_at`, `completed_at`,
  `published`, `stored`, `dlq`, `rejected`, `status`. The daily workflow sends
  one Kafka header `run_id` on every published record; the Spring consumer
  upserts the row idempotently as it processes (idempotent stable IDs make
  replays safe, per the design). No new HTTP endpoint — the threat model's "no
  public ingest trigger" stands.
- **`lastPull` becomes run history:** the API returns the latest completed
  `ingest_runs.completed_at`; falls back to `max(first_seen)` when no run is
  recorded (backwards compatible with Phase 1/2 behavior).
- **Daily pipeline** `.github/workflows/daily-ingest.yml` (implements the
  design's Extract → Validate → Publish → Verify):
  - Schedule: `5 13 * * *` UTC = 08:05 CDT. **DST caveat:** GitHub cron is
    UTC-fixed, so in CST (Nov–Mar) it fires at 07:05 local. Documented here;
    accepted rather than adding dual-cron complexity. `workflow_dispatch`
    supported for manual runs.
  - **Extract:** read `ledgers/*.json` from the repo (glob — works whether
    there are 3 or 4 agent ledgers). Each file: `{agent, records[]}`.
  - **Validate:** keep only `reported:true`; malformed records → logged and
    skipped, never failing the batch.
  - **Publish:** one event per valid record → Upstash Kafka topic
    `job-leads.raw` via Upstash's REST API (curl — no Kafka client needed in
    CI), with the `run_id` header.
  - **Verify:** read-only Postgres check — published count vs. stored delta
    (dedup-aware: delta ≤ published is fine; delta = 0 when published > 0 is
    not). Mismatch → workflow fails loudly and notifies (never silent).
- **Ledger supply (separate approval):** the morning job-search agents run in
  the assistant's runtime, not in GitHub — they cannot be reached by the
  workflow. A small sync automation (agents commit ledger JSONs into
  `ledgers/`) will be proposed as its own change with the owner's approval;
  until then the pipeline runs on whatever ledgers are present, and
  `workflow_dispatch` accepts a manual run. Phase 3 ships the pipeline and
  the sync contract, not the sync itself.
- **Render (user's clicks, documented):** `render.yaml` blueprint (web service:
  Java 21, `./mvnw -DskipTests package` then `java -jar`, free plan) plus
  `docs/RENDER.md` — exact step-by-step: create account → New → Blueprint →
  connect repo → add env vars (`SPRING_DATASOURCE_URL` with `?sslmode=require`,
  `DB_USERNAME`, `DB_PASSWORD`, `KAFKA_*`) → deploy. The deploy hook URL goes
  to GitHub Secrets as `RENDER_DEPLOY_HOOK` so merges to `main` redeploy.
  The owner creates the Render/Supabase/Upstash accounts and pastes the
  secrets — that part cannot be automated.
- **Secrets:** GitHub Secrets for the workflow (`KAFKA_REST_URL`,
  `KAFKA_REST_USERNAME`, `KAFKA_REST_PASSWORD`, `SUPABASE_DB_URL` read-only for
  verify, `RENDER_DEPLOY_HOOK`); Render env vars for the app. Nothing secret
  in the repo, ever.

## Security implementation

- Publisher uses Upstash REST credentials scoped to produce on
  `job-leads.raw` only (least privilege).
- Verify step uses a **read-only** Postgres role.
- No new public endpoints; the consumer path is unchanged from Phase 1.
- Secrets flow only through GitHub Secrets / Render env — the security-scan
  and gitleaks checks stay green.

## Test plan

- `IngestRunRecorderTest`: same `run_id` twice → one row, counters correct
  (idempotent upsert).
- `LastPullTest`: API returns latest completed run; falls back to
  `max(first_seen)` with no runs.
- Jar bundling: CI build asserts `index.html` + hashed assets exist under
  `target/classes/static`.
- Pipeline dry-run: workflow's extract/validate/publish steps tested against
  fixture ledgers with a mocked Kafka endpoint (counts and logging asserted).
- QA criteria §6: daily regression (criterion 9) is now exercised by the
  pipeline itself.

## Explicitly NOT in Phase 3

Creating the owner's Render/Supabase/Upstash accounts or pasting their
secrets (owner's clicks, guided by docs/RENDER.md) · the ledger-sync
automation (separate proposal + approval) · DLQ web UI (DLQ depth is checked
by the verify step; a viewer is a later phase if wanted) · any paid service.

## Process (filled in as the phase runs)

1. Design written above and pushed to the branch before any code
   (implementation branch: `feat/phase-3-deploy`).
2. `./mvnw -B verify` (backend + bundled frontend) and the pipeline dry-run
   green locally — see evidence below.
3. PR opened with the template; CI runs; persona review loop; findings fixed.
4. Owner's explicit merge approval → squash merge. (Pending — not approved.)
5. After merge: owner follows docs/RENDER.md (accounts + secrets + blueprint),
   then confirms the live URL; assistant verifies the deploy.

### Implementation evidence (to be filled)

### Defects found during the build and their fixes (to be filled)
