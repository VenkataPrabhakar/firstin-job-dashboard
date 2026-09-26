# Phase 4 — Daily ingest on Redpanda (Kafka protocol)

**Status:** implemented on `feat/phase-4-redpanda-ingest` — awaiting owner
approval to push, then PR review.
**Branch:** `feat/phase-4-redpanda-ingest` → PR against `main` → squash merge.

## Why this phase exists

Phase 3's daily-ingest workflow published through the **Upstash Kafka REST
API**. Upstash discontinued Kafka entirely (deprecated Sep 2024, removed by
2026), so that publish path is dead. On 2026-09-25 the owner approved the
replacement: **Redpanda Cloud Serverless** (Kafka-protocol compatible).
Redpanda exposes **no HTTP produce endpoint** — the workflow must publish
with a real Kafka client (SASL_SSL, SCRAM-SHA-256).

This is a planned adaptation to an external change, not a defect fix. (The
separate production 403 on `/` was a defect; it was fixed in PR #11 and
verified live.)

## What stays the same

- `extract_records.sh`: ledger → `records.jsonl` validation is untouched.
- The **wire format** the consumer already understands (see
  `JobLeadListener`): data records carry value = the record JSON string and
  header `run_id=<UTC date>`; the run-complete control record carries value
  `run-complete` with headers `run_id=<run>` and `run_complete=true`. The
  new producer replicates this byte-for-byte.
- Single-partition topic `job-leads.raw`: run-complete is consumed after
  every data record, so `COMPLETED` still means the whole run is stored.
- The wake-the-service step (plain HTTPS to `/api/health` — free tier
  sleeps), the 30-minute completion wait, and the counter-reconciliation
  verify step are unchanged.
- The backend consumer (`KafkaConfig`, `JobLeadListener`,
  `IngestionService`) is already generic: env-driven bootstrap + SASL, no
  code change needed. Only its comments say "Upstash".

## Design

### 1. Producer: Python + confluent-kafka in the Actions job

A new script, `.github/scripts/publish_kafka.py`:

- Reads `records.jsonl` (one JSON object per line).
- `confluent_kafka.Producer` with:
  - `bootstrap.servers` = `$KAFKA_BOOTSTRAP_SERVERS`
  - `security.protocol=SASL_SSL`, `sasl.mechanism=SCRAM-SHA-256`,
    `sasl.username` / `sasl.password` from env
  - `acks=all`, `enable.idempotence=true` (no duplicates on retry)
- Produces each record to `job-leads.raw` with header `run_id=<RUN_ID>`,
  then a final `run-complete` record with headers `run_id` +
  `run_complete=true`. If the extract produced zero records, only the
  run-complete record is sent (same as the old REST flow).
- Delivery callbacks collect failures; the script exits non-zero if any
  message is undelivered. `flush()` before the run-complete send so
  ordering on the single partition is preserved.

Why Python instead of Java: the job already shells out to
bash/jq/curl/psql; `pip install confluent-kafka` (prebuilt wheel with
librdkafka, no system deps on `ubuntu-latest`) keeps this a ~80-line
script instead of a second Maven build in CI.

### 2. Workflow changes (`daily-ingest.yml`)

- Secrets check: replace `KAFKA_REST_URL` / `KAFKA_REST_USERNAME` /
  `KAFKA_REST_PASSWORD` with `KAFKA_BOOTSTRAP_SERVERS` /
  `KAFKA_SASL_USERNAME` / `KAFKA_SASL_PASSWORD`.
- Replace the two "Publish … to Upstash Kafka REST" steps with:
  1. `pip install confluent-kafka`
  2. `python3 .github/scripts/publish_kafka.py` (env: the three secrets +
     `RUN_ID`)
- Update the header comment (Upstash REST → Redpanda Kafka client).

### 3. GitHub configuration (owner actions)

- **Secrets** (repo → Settings → Secrets → Actions): add
  `KAFKA_BOOTSTRAP_SERVERS` = `dareme0bjs0idj505v70.any.us-east-1.mpx.prd.cloud.redpanda.com:9092`,
  `KAFKA_SASL_USERNAME` = `firstin`, `KAFKA_SASL_PASSWORD` = the Redpanda
  SASL password the owner holds privately. Delete the obsolete
  `KAFKA_REST_*` secrets. (Secrets are entered by the owner; they never go
  through chat.)
- **Variable** (repo → Settings → Variables → Actions): add
  `SERVICE_URL` = `https://firstin-dashboard.onrender.com`. The workflow
  already reads it; it was never set, so the pipeline has been a no-op
  "not configured" run until now.

### 4. Render service check (owner action)

The deployed service's `KAFKA_BOOTSTRAP_SERVERS` / `KAFKA_SASL_USERNAME` /
`KAFKA_SASL_PASSWORD` env vars must point at **Redpanda**, not Upstash.
Verify in the Render dashboard → service → Logs that the consumer joined
group `firstin-ingest`; if the vars still hold Upstash values, update them
to the Redpanda bootstrap and `firstin` credentials (owner action in the
Render dashboard) and let the service redeploy. Without this, published
records sit in the topic unconsumed and the run never completes.

### 5. Comment and doc cleanup (same PR)

Replace obsolete "Upstash" wording (comments only, no behavior change) in:

- `render.yaml` (the `KAFKA_SASL_MECHANISM` comment)
- `backend/src/main/resources/application.yml` and `application-prod.yml`
- `backend/README.md`
- `docs/RENDER.md` (Step 2 becomes Redpanda: cluster `firstin`,
  topics, SASL user, ACLs — matching the 2026-09-25 setup)
- `docs/DESIGN.md`, `docs/PHASE-1.md`, `docs/PHASE-3.md` where they name
  Upstash as the Kafka provider

### 6. Testing

1. `workflow_dispatch` manual run of `daily-ingest.yml` on the PR branch.
2. Expect: extract → publish → wake → `ingest_runs.status = COMPLETED`,
   counters reconcile (`published = stored + duplicates + dlq + rejected`),
   new rows in `job_postings`, `/api/listings` non-empty,
   `lastPull` = run date.
3. Full local suite (`./mvnw verify`) green before merge, as always.

## Decisions

- **Python producer in CI, not a Java publisher:** smallest change, no new
  build, no new deployable. Accepted.
- **confluent-kafka (librdkafka) over kafka-python:** maintained, supports
  SASL_SSL/SCRAM out of the box, prebuilt wheels. Accepted.
- **Idempotent producer (`acks=all`, `enable.idempotence=true`):** the
  consumer dedupes by stable posting id anyway; this is defense in depth.
  Accepted.
- **No HTTP ingestion endpoint** (threat model, unchanged): records enter
  only through the Kafka consumer.

## Implementation notes (review findings)

- **Bounded flush.** The first draft called `producer.flush()` with no
  timeout; a smoke test against the real `confluent-kafka==2.15.1` with an
  unreachable broker hung until killed. `flush()` is now bounded at 60 s and
  undelivered messages fail the step — a dead broker fails the workflow in
  ~1 minute, not at the 60-minute job timeout.
- **Exact dependency pin.** `confluent-kafka==2.15.1` (verified 2026-09-26),
  matching the repo's pin-everything convention (SHA-pinned actions).
- **Wire format verified against `JobLeadListener`:** data records carry
  value = record JSON bytes + `run_id` header; control record value
  `run-complete` + `run_id` and `run_complete=true` headers. The listener
  keys off the headers, and the value convention is preserved anyway.
- **Zero-record runs** still send only the run-complete record (same as the
  old REST flow), so the run completes instead of hanging in "Wait for the
  run to complete".

## Deferred (not this phase)

- **Redpanda cost:** the cluster runs on a $100 trial credit — it is *not*
  a permanent free tier, which conflicts with the free-forever target. No
  cheaper alternative identified yet (Confluent needs a card; self-hosting
  Kafka has no free home). Flagged for a later cost decision, not solved
  here.
- **Morning-agent ledger sync:** still a separate proposal; out of scope.
- **PHASES.md Phase 0 row** still reads "awaiting owner's merge approval"
  from the old template — stale, pre-existing, not touched here.

## Process record

- [x] Design written before any code (this document).
- [x] Owner approved design (2026-09-26) → implementation on this branch.
- [x] Branch synced to `origin/main` at `4c50f07` (PR #11); duplicate 403-fix
      files dropped (byte-identical to merged).
- [x] Producer `publish_kafka.py` + stub-based tests (15 checks) +
      real-library smoke test (fails fast on dead broker, no hang).
- [x] Workflow adapted; `confluent-kafka==2.15.1` exact pin.
- [ ] Push branch (owner approval) → PR → candid review + CI green.
- [ ] Owner approves merge → squash merge → Render auto-deploys.
- [ ] Owner sets GitHub secrets/variable; verifies Render Kafka env vars.
- [ ] Manual `workflow_dispatch` run verified end to end (COMPLETED,
      counters, listings, lastPull).
