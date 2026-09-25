# FirstIn backend — Phase 1

Spring Boot 3 (Java 21) API + Kafka ingest for the FirstIn job-discovery
dashboard. This phase delivers the serving API and the ingestion pipeline;
the React frontend (Phase 2) and Render deployment (Phase 3) are separate.

Design authority: [`docs/PHASE-1.md`](../docs/PHASE-1.md) and
[`docs/DESIGN.md`](../docs/DESIGN.md).

## Prerequisites

- Java 21 (Eclipse Temurin)
- Docker + Docker Compose (for local Kafka + PostgreSQL)
- No system Maven needed — the Maven wrapper (`mvnw`) is committed

## Quick start (local)

```bash
cd backend

# 1. Start Kafka (KRaft, single node) + PostgreSQL 16
docker compose up -d

# 2. Run with the local profile (docker-compose defaults)
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`.

## Build & test

```bash
cd backend
./mvnw -B verify
```

This compiles, runs the full test suite (H2 for JPA, embedded Kafka via
`spring-kafka-test` for the consumer/DLQ path), and packages the jar.

## Configuration — environment only

Nothing secret lives in this repo. All configuration comes from the
environment:

| Variable | Used for | Default (local) |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/firstin` |
| `DB_USERNAME` / `DB_PASSWORD` | DB credentials | `firstin` / `firstin` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | `localhost:9092` |
| `KAFKA_SASL_USERNAME` / `KAFKA_SASL_PASSWORD` | SASL/SCRAM credentials (prod/Upstash). When set, clients use `SASL_SSL`; the JAAS config is assembled in code so the password never lands in a file or log. | _(unset — PLAINTEXT)_ |
| `KAFKA_SASL_MECHANISM` | SASL mechanism | `SCRAM-SHA-256` |
| `KAFKA_SECURITY_PROTOCOL` | Overrides the default (`SASL_SSL` when SASL is on, `PLAINTEXT` otherwise) | _(unset)_ |
| `SPRING_PROFILES_ACTIVE` | `local` / `test` / `prod` | _(none)_ |

Profiles:

- **`local`** (`application-local.yml`) — docker-compose defaults:
  Postgres at `localhost:5432`, Kafka at `localhost:9092`, `ddl-auto: create-drop`.
- **`test`** (`application-test.yml`, active in tests) — H2 in-memory,
  embedded Kafka, `ddl-auto: create-drop`.
- **`prod`** (`application-prod.yml`) — everything from the environment
  (Supabase Postgres + Upstash Kafka, wired in Phase 3). The prod profile
  **appends `?sslmode=require` to the JDBC URL in code** when the operator
  did not already request an sslmode — Postgres TLS is enforced, not just
  documented.

## API

Public, read-only. There is intentionally **no HTTP ingestion endpoint** —
records enter only through the Kafka consumer (threat model: no public
ingestion trigger).

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/health` | `{ "status": "UP" }` |
| `GET` | `/api/listings?tab=&q=` | Listing envelope (see below) |

`tab` (default `today`): `today` · `c2c` · `w2` · `fulltime` · `visa`.
Anything else → `400` with a generic `{ "error": "…" }` body.

`q`: optional case-insensitive title/company search, max 200 chars.

Envelope shape:

```jsonc
{
  "listings": [ {
    "id": "sha1-of-normalized-title-company-location",
    "title": "…", "company": "…", "location": "…",
    "payMin": 70, "payMax": 75, "payUnit": "hour", "payHourlyEquiv": 72.5,
    "engagement": "C2C", "engagementTags": ["contract"],
    "visaStatus": "open", "visaReason": "…",
    "contact": { "name": "…", "email": "…", "phone": "…" },
    "sources": [ { "source": "Dice", "url": "…", "urlVerified": true } ],
    "firstSeen": "2026-09-25T13:05:00Z",
    "postedMinutes": 60, "postedMinutesConfidence": "precise"
  } ],
  "indexedToday": 12,   // first_seen on today's America/Chicago calendar day
  "lastPull": "2026-09-25T13:05:00Z"  // max(first_seen); explicit null when empty
}
```

Null fields are omitted from listings, except top-level `lastPull`, which is
always present (explicit `null` means "no sightings yet").

Tabs: `today` = `first_seen` within the last 24h, newest first. Category tabs
filter by engagement. `visa` lists postings with known visa status
(`open` / `confirmed`). Ordering is `firstSeen` descending everywhere.

## Kafka

Topics (configurable via `app.kafka.raw-topic` / `app.kafka.dlq-topic`):

- **`job-leads.raw`** — inbound lead events (JSON). Consumed by group
  `firstin-ingest`; values are read as raw bytes so malformed records can be
  quarantined instead of failing the batch.
- **`job-leads.dlq`** — poison records (bad JSON, validation failures,
  unknown engagement) with `failure-reason` and `original-topic` headers.

Ingestion policy (see `IngestionService`):

- Only `reported: true` records are stored; `reported: false` is rejected
  (counted, logged, never stored, never DLQ — it is a valid event).
- Restricted-sponsorship records are excluded at ingestion.
- Postings upsert idempotently by stable SHA-1 id
  (normalized title + company + location). Re-seen postings keep their
  original `first_seen`; the source sighting row gains a fresh `seenAt` and
  contact fields merge without erasing existing values.
- Pay is parsed to hourly equivalents (`$70-75/hr`, `$120K`, `90k`…);
  garbage → nulls. Posted age is kept only when it parses precisely
  (`postedMinutes`, confidence `precise`); unparseable ages are null and sort
  oldest, never rendering as "just now".

## Security (maps to `docs/DESIGN.md` §9)

- JPA/Hibernate only — no native SQL, no string-concatenated queries. All
  dynamic filters are JPA Specifications.
- Bean Validation on every inbound record + length caps on all text fields.
- Per-IP rate limiting on `/api/**` (Bucket4j, in-memory, 120 req/min).
- Security headers: CSP (`default-src 'self'`), HSTS (with preload),
  `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Referrer-Policy: no-referrer`.
- Generic error bodies — stack traces and SQL errors stay in server logs.
- Config from environment only; Postgres `sslmode=require` enforced in the
  prod profile; Kafka TLS + SASL/SCRAM in prod, PLAINTEXT only inside
  docker-compose.

## Project layout

```
backend/
├── pom.xml                      # Spring Boot 3.5.x, Java 21
├── mvnw / mvnw.cmd / .mvn/      # committed Maven wrapper
├── docker-compose.yml           # local Kafka (KRaft) + PostgreSQL 16
└── src/
    ├── main/java/com/firstin/dashboard/
    │   ├── FirstInApplication.java
    │   ├── config/              # Kafka, Security, rate limiting, prod datasource
    │   ├── ingest/              # listener, service, parsers, DLQ publisher
    │   ├── model/               # JobPosting, JobSource (+ VisaStatus, Engagement)
    │   ├── repo/                # Spring Data JPA repositories + specifications
    │   └── web/                # REST controller, envelope DTOs, error handler
    └── main/resources/
        ├── application.yml      # base config (env-driven)
        ├── application-local.yml
        ├── application-test.yml
        └── application-prod.yml
```

## Notes for later phases

- Phase 2 (React) consumes `GET /api/listings` exactly as shaped above.
- Phase 3 (Render deploy) provides the prod env vars (Supabase +
  Upstash) and the daily ingest scheduler that publishes to `job-leads.raw`.
