# Phase 3 — Deploy runbook ($0/month)

Everything below is the owner's clicks. No step here creates anything
automatically: you create the Render, Supabase, and Upstash accounts, paste
the credentials, and confirm the live URL. Nothing secret goes into the repo
— secrets live only in GitHub Secrets and Render env vars.

**Cost: $0/month.** Render free web service · Supabase free Postgres ·
Upstash free Kafka · GitHub Actions free tier. One version of the app with
all features unlocked — no subscriptions, no paywalls, no Pro badges.

## Architecture

```
GitHub Actions (daily 08:05 CDT / 07:05 CST)
  │  extract ledgers → POST batches to Upstash REST → run-complete record
  │  → wake the service (plain HTTPS) → verify in Postgres
  ▼
Upstash Kafka ──job-leads.raw (1 partition)──▶ Render (Spring Boot + consumer)
                                                    │  serves UI / and API /api/**
                                                    ▼
                                              Supabase Postgres
```

The pipeline publishes; the consumer (embedded in the web service) stores.
The workflow then polls the read-only `ingest_runs` row until the run is
`COMPLETED` and fails loudly if published records produced no stored rows.

---

## Step 1 — Supabase (Postgres)

1. Sign up at supabase.com (free, no card) → create a project.
2. Open **SQL Editor** and run the schema below. Production runs with
   `ddl-auto: validate`, so the tables must exist exactly as the entities
   expect — run this DDL once.

```sql
create table job_postings (
  id                 varchar(40)  not null primary key,
  title              varchar(300) not null,
  company            varchar(200) not null,
  location           varchar(200) not null,
  engagement         varchar(20)  not null,
  engagement_tags    varchar(200),
  pay_min            numeric(12, 2),
  pay_max            numeric(12, 2),
  pay_unit           varchar(10),
  pay_hourly_equiv   numeric(12, 2),
  pay_raw            varchar(200),
  first_seen         timestamptz  not null,
  posted_minutes     integer,
  posted_minutes_confidence varchar(10),
  raw_posted         varchar(200),
  note               varchar(2000),
  visa_status        varchar(20)  not null,
  visa_reason        varchar(500),
  created_at         timestamptz  not null,
  updated_at         timestamptz  not null
);

create table job_sources (
  id            bigint generated always as identity primary key,
  posting_id    varchar(40)   not null,
  source        varchar(100),
  url           varchar(2000),
  url_verified  boolean,
  contact_name  varchar(200),
  contact_email varchar(320),
  contact_phone varchar(50),
  seen_at       timestamptz   not null
);
alter table job_sources
  add constraint fk_job_sources_posting foreign key (posting_id)
  references job_postings (id);

create table ingest_runs (
  run_id       varchar(64) not null primary key,
  started_at   timestamptz not null,
  completed_at timestamptz,
  published    bigint      not null,
  stored       bigint      not null,
  dlq          bigint      not null,
  rejected     bigint      not null,
  status       varchar(20) not null
);
```

3. Create a **read-only role** for the pipeline's verify step (least
   privilege — the workflow never writes to the DB):

```sql
create role pipeline_ro login password '<choose-a-password>';
grant connect on database postgres to pipeline_ro;
grant usage on schema public to pipeline_ro;
grant select on job_postings, job_sources, ingest_runs to pipeline_ro;
```

4. The pipeline secret is then:

```
postgresql://pipeline_ro:<password>@<host>:5432/postgres?sslmode=require
```

Take `<host>` from the direct connection string in Project Settings →
Database. If the password contains special characters, URL-encode them
(`@` → `%40`, `/` → `%2F`, etc.).

---

## Step 2 — Upstash (Kafka)

1. Sign up at upstash.com (free, no card) → **Kafka** → create a cluster
   (pick the region closest to your Render region).
2. In the cluster, open **Topics** and create:
   - `job-leads.raw` — with **1 partition**. The pipeline relies on
     per-partition ordering: the run-complete control record must be consumed
     after every data record of the run, which a single partition guarantees.
   - `job-leads.dlq`
3. Copy the **Kafka endpoint** and the **username / password** (SCRAM-SHA-256)
   from the cluster's connect panel — these become the Render env vars
   `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_SASL_USERNAME`, `KAFKA_SASL_PASSWORD`.
4. In the same cluster, open the **REST API** panel and copy the **REST URL**,
   **REST username**, and **REST password** — these become the GitHub Secrets
   `KAFKA_REST_URL`, `KAFKA_REST_USERNAME`, `KAFKA_REST_PASSWORD` used by the
   daily workflow to publish. The workflow only ever produces to
   `job-leads.raw` (never consumes or administers topics); Upstash does not
   offer produce-only-scoped Kafka credentials on the free tier, so the
   credential is stored as a GitHub Secret and used solely by this workflow.

No code changes are needed for SASL: when `KAFKA_SASL_USERNAME` is set the app
uses SASL_SSL with SCRAM-SHA-256 automatically (see
`backend/.../config/KafkaConfig.java`).

---

## Step 3 — Render (the app)

1. Sign up at render.com (free, no card; GitHub sign-in is easiest).
2. Dashboard → **New + → Blueprint** → connect and select the
   `firstin-job-dashboard` repo. Render reads `render.yaml` at the repo root.
   (Render has no native Java runtime — the Blueprint uses `runtime: docker`
   and builds the `Dockerfile` at the repo root.)
3. When prompted for env vars, fill them from the table below. Values marked
   *paste* come from Steps 1–2.

| Render env var | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (pre-filled) |
| `SPRING_DATASOURCE_URL` | *paste* the Supabase **direct** connection string, e.g. `jdbc:postgresql://db.<ref>.supabase.co:5432/postgres` — `?sslmode=require` is appended automatically when missing |
| `DB_USERNAME` | *paste* `postgres` (or your Supabase DB user) |
| `DB_PASSWORD` | *paste* your Supabase database password |
| `KAFKA_BOOTSTRAP_SERVERS` | *paste* the Upstash Kafka endpoint |
| `KAFKA_SASL_USERNAME` | *paste* the Upstash Kafka username |
| `KAFKA_SASL_PASSWORD` | *paste* the Upstash Kafka password |

4. Click **Apply**. Render builds the Docker image (multi-stage: Maven builds
   the Spring Boot jar and the React SPA is bundled into it) and starts the
   service. The first build takes several minutes (npm + Maven downloads).
5. Note the service URL (`https://<name>.onrender.com`) — the daily workflow
   wakes the service with a plain HTTPS request to it (free-tier services
   sleep after 15 min idle; no rebuild involved). You will enter this URL
   as a GitHub Actions *variable* in Step 4.

---

## Step 4 — GitHub Secrets (daily pipeline)

Repo → **Settings → Secrets and variables → Actions** → add these
**secrets**:

| Secret | Value |
|---|---|
| `KAFKA_REST_URL` | Upstash REST URL (Step 2.4) |
| `KAFKA_REST_USERNAME` | Upstash REST username |
| `KAFKA_REST_PASSWORD` | Upstash REST password |
| `SUPABASE_DB_URL` | `postgresql://pipeline_ro:…` (Step 1.4) |

Then, on the same page, switch to the **Variables** tab and add:

| Variable | Value |
|---|---|
| `SERVICE_URL` | the Render service URL from Step 3.5 (e.g. `https://firstin-dashboard.onrender.com`) — public, not secret |

Until these exist, the daily workflow prints
`pipeline not configured — see docs/RENDER.md` and exits successfully.

---

## Step 5 — Verify end to end

1. Open the service URL — the dashboard UI loads (the SPA is bundled in the jar).
2. `GET https://<name>.onrender.com/api/health` → `{"status":"UP"}`.
3. Trigger the workflow manually: **Actions → Daily ingest pipeline →
   Run workflow**. Watch it extract, publish, wake the service, wait
   for the run to reach `COMPLETED`, and verify counts.
4. The UI footer ("last pull") now shows the latest completed run time.

## Daily operation

- The workflow runs at 13:05 UTC (08:05 CDT / 07:05 CST — GitHub cron is
  UTC-fixed; the doc comment in `daily-ingest.yml` explains the DST shift).
- Free-tier notes: the Render service spins down after 15 min idle (the
  workflow wakes it with a plain HTTPS request — no rebuild); the first
  request after idle is slow. Upstash and
  Supabase free tiers have throughput/storage limits adequate for a personal
  dashboard; ledgers stay in the repo, only normalized postings are stored.
- The single-partition topic preserves publish order, so the run-complete
  record is always consumed last and `COMPLETED` means the whole run is
  stored.

---

## Troubleshooting

| Symptom | Likely cause → fix |
|---|---|
| Docker build fails: `release version 21 not supported` | The build image's JDK drifted. The Dockerfile pins `maven:3.9-eclipse-temurin-21` (build) and `eclipse-temurin:21-jre` (runtime) — confirm those tags; bump them together if a newer LTS is ever wanted. |
| App starts but `/api/health` fails / DB errors in logs | `SPRING_DATASOURCE_URL` wrong or Supabase project paused. Check the URL includes the host and `?sslmode=require`; verify the DDL from Step 1 ran. |
| Pipeline fails at "Wait for the run to complete" (timeout) | Service didn't wake or the consumer can't reach Upstash. Check Render **Logs** for Kafka auth errors; verify the `SERVICE_URL` variable. |
| Pipeline fails: "published N but stored none" | The consumer saw the batch but stored nothing — check Render logs for deserialization/validation errors and the `job-leads.dlq` topic. |
| Workflow prints "pipeline not configured" | A secret in Step 4 is missing — add it and re-run. |
| UI loads but `/api/*` 404s | The jar didn't bundle the SPA or the API profile is wrong — confirm `SPRING_PROFILES_ACTIVE=prod` and that the build log shows the frontend build ran. |

---

## Security notes

- No public ingestion endpoint: records enter only through the Kafka
  consumer. The workflow publishes via Upstash REST; the app never exposes a
  write path.
- The pipeline's Postgres credential is the read-only `pipeline_ro` role —
  it can `SELECT` the three tables and nothing else.
- Secrets flow only through GitHub Secrets / Render env vars; the
  security-scan and gitleaks CI checks stay green.
- `?sslmode=require` is enforced on the JDBC URL (appended automatically
  when missing).
