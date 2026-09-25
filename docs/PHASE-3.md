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
  during the Maven build and the build copies `frontend/dist` into
  `backend/target/classes/static` (packaged in the jar as
  `BOOT-INF/classes/static`; the prebuilt copy under
  `backend/src/main/resources/static` serves local dev). Spring serves the
  SPA; `/api/**` stays the API. One artifact, one Render web service, $0.
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
- **Render (user's clicks, documented):** `render.yaml` blueprint (Docker
  runtime — Render has no native Java runtime — building the repo-root
  `Dockerfile`: Maven/Temurin 21 build stage, Temurin 21 JRE runtime stage,
  free plan) plus `docs/RENDER.md` — exact step-by-step: create account →
  New → Blueprint → connect repo → add env vars (`SPRING_DATASOURCE_URL` with
  `?sslmode=require`, `DB_USERNAME`, `DB_PASSWORD`, `KAFKA_*`) → deploy. The
  deploy hook URL goes to GitHub Secrets as `RENDER_DEPLOY_HOOK`; the daily
  workflow calls it to wake the (sleeping) free service before the consumer
  drains each batch. The owner creates the Render/Supabase/Upstash accounts
  and pastes the secrets — that part cannot be automated.
- **Secrets:** GitHub Secrets for the workflow (`KAFKA_REST_URL`,
  `KAFKA_REST_USERNAME`, `KAFKA_REST_PASSWORD`, `SUPABASE_DB_URL` read-only for
  verify, `RENDER_DEPLOY_HOOK`); Render env vars for the app. Nothing secret
  in the repo, ever.

## Security implementation

- Publisher credentials are stored as GitHub Secrets and the workflow uses
  them only to produce to `job-leads.raw` via Upstash's REST API (least
  privilege by usage — Upstash's free credentials are not produce-scoped, so
  the scoping is by the workflow's behavior, not the credential).
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
  `target/classes/static` (`StaticBundleTest`).
- Pipeline: `test_extract.sh` covers extract/validate against fixture ledgers
  (12 cases: validity filters, malformed files, dedupe, empty ledgers). The
  publish step is not unit-tested (it needs a live Kafka endpoint); the
  verify step's counter logic is exercised in CI via the deterministic
  fixtures, and end-to-end behavior is verified by the first manual
  `workflow_dispatch` run against the real services.
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

### Implementation evidence

Verified 2026-09-25 on the `feat/phase-3-deploy` branch (fresh reconstruction;
the earlier `/tmp` implementation was lost to VM replacement and rebuilt):

- **Backend** (`./mvnw -B verify`): **56 tests, 0 failures, 0 errors**
  (DlqTest 2, FreshnessTest 4, IngestRunRecorderTest 5, IngestionServiceTest 10,
  ListingApiTest 12, PayParserTest 13, RateLimitTest 1, StaticBundleTest 1,
  ProdDataSourceConfigTest 3, LastPullTest 5). BUILD SUCCESS.
- **Frontend** (`npm ci && npm run build && npm test -- --watchAll=false`):
  **31 tests passed** (App.test.tsx 13, format.test.ts 18); Vite build emits
  `dist/index.html` + hashed `dist/assets/*`.
- **Pipeline scripts** (`bash .github/scripts/test_extract.sh`): **12 passed,
  0 failed** (validity filters, malformed files, cross-ledger dedupe, empty
  ledgers). Fixture run: 4 valid records → 3 unique after dedupe, broken
  ledger logged and skipped.
- **Pin check** (`bash .github/scripts/check-pins.sh`): all workflow actions
  SHA-pinned.
- **Jar**: single executable `backend/target/firstin-dashboard-0.1.0.jar`
  containing `BOOT-INF/classes/static/index.html` and hashed
  `BOOT-INF/classes/static/assets/index-*.js` / `index-*.css`
  (copied from `frontend/dist` into `backend/target/classes/static` during
  the build; `StaticBundleTest` asserts this in CI).
- **Upstash REST format** re-verified against the official Producer API docs:
  `POST /produce/$TOPIC` accepts `{"value": ..., "headers": [{"key", "value"}]}`,
  single or array — exactly what `daily-ingest.yml` sends.
- **render.yaml** fields validated against the official Blueprint spec
  (`render.com/docs/blueprint-spec`, 2026-09-25): `type: web`,
  `runtime: docker`, `plan: free`, `healthCheckPath`, `autoDeployTrigger:
  commit`, `sync: false` placeholder env vars. `dockerfilePath` omitted on
  purpose — the spec defaults it to `./Dockerfile` (repo root).

### Defects found during the build and their fixes

1. **Wrong bundling destination in the design doc.** The doc said the SPA is
   copied into `backend/src/main/resources/static`; the build actually copies
   `frontend/dist` into `backend/target/classes/static` (packaged as
   `BOOT-INF/classes/static`). Doc corrected.
2. **Invalid Render runtime.** The first `render.yaml` used `runtime: java`;
   Render's Blueprint spec has no Java runtime (only node/python/ruby/go/
   elixir/rust + special-case `docker`/`image`/`static`). Switched to
   `runtime: docker` with a repo-root multi-stage `Dockerfile`
   (Maven/Temurin 21 build → Temurin 21 JRE runtime; tags
   `maven:3.9-eclipse-temurin-21` and `eclipse-temurin:21-jre` confirmed to
   exist via the Docker Hub API; `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`
   for the 512 MB free tier; `PORT` honored at startup).
3. **Unverifiable security claim.** "Upstash REST credentials scoped to
   produce-only" could not be confirmed in Upstash's docs, so the doc and
   runbook now say the credentials live in GitHub Secrets and the workflow
   uses them only to produce to `job-leads.raw` (least privilege by usage).
4. **Run-counter semantics needed a decision.** `published` counts every
   record the consumer receives for a run (a replayed record counts again);
   `stored` counts only newly persisted postings; storage idempotency comes
   from stable posting IDs, not from the counters. The alternative
   (deduping `published` by record identity) would need unbounded per-record
   state — rejected as over-engineering. Documented in
   `IngestRunRecorder`'s javadoc and covered by
   `IngestRunRecorderTest.sameRunIdTwiceIsOneRowWithCorrectCounters`
   (`published=4, stored=2` after a full replay).
5. **Unhandled-exception path (deliberate, no code change).** A
   `RuntimeException` escaping `ingestRaw` (e.g. DB or DLQ outage) retries
   and then stops the consumer; the run never completes and the workflow's
   30-minute wait fails loudly. Skip-and-continue was rejected: silently
   undercounting would violate the "never silent" rule. Documented in
   `docs/RENDER.md` troubleshooting.
6. **Sandbox build environment (not a product defect).** The replacement VM
   had no JDK and a stale Maven proxy setup; fixed locally with OpenJDK 21,
   the egress CA in cacerts, per-session proxy credentials in
   `~/.m2/settings.xml`, and a `/root/.m2/settings.xml` symlink (the shell
   runs as root, so Java's `user.home` is `/root`). None of this is committed.
