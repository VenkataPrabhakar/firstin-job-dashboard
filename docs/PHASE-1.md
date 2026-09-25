# Phase 1 — Backend (Spring Boot API + Kafka ingest)

Status: merged to `main` 2026-09-25 (squash `eac51e3`).
Branch: `feat/phase-1-backend`. Implements `docs/DESIGN.md` §2–§3 (backend-owned parts).

## Objective

A working backend that ingests job-lead events from Kafka, normalizes and
dedupes them into PostgreSQL, and serves the listing API the React frontend
(Phase 2) will consume. No frontend, no deploy, no scheduler in this phase.

## Design decisions

- **Build tool: Maven** (open question from Phase 0, now decided). Why: the
  owner's primary build tool across 8+ years of Spring work; Spring Boot's
  default; the widest plugin/docs ecosystem for the exact stack here
  (spring-kafka-test, Testcontainers). The Maven wrapper (`mvnw`) is committed
  so CI and any machine build identically.
- **Spring Boot 3.x (latest stable), Java 21** — per approved architecture.
- **Two tables, no exotic column types** (keeps H2-based CI tests honest):
  - `job_postings` — one row per canonical posting. Stable `id` =
    `sha1(normalized title | normalized company | normalized location)`,
    PK. Columns: title, company, location, engagement (canonical:
    C2C/W2/FULLTIME), engagement_tags (comma-separated secondary tags),
    pay_min, pay_max, pay_unit, pay_hourly_equiv, pay_raw, first_seen
    (UTC, never null), posted_minutes (nullable), posted_minutes_confidence,
    raw_posted, note, visa_status, visa_reason, created_at, updated_at.
  - `job_sources` — one row per sighting of a posting: posting_id (FK),
    source, url, url_verified, contact_name, contact_email, contact_phone,
    seen_at. Cross-source duplicates collapse into one posting; their
    recruiter/source rows accumulate here.
- **`lastPull` without a scheduler:** computed as `max(first_seen)` over
  postings (`null` when empty). The `ingest_runs` run-history table lands in
  Phase 3 with the daily scheduler that writes it.
- **No HTTP ingest endpoint — not even dev-only.** Records enter only through
  the Kafka consumer (per the threat model: no public ingestion trigger).
  Local seeding is via the console producer against docker-compose Kafka.

## Kafka wiring

- Consumer group `firstin-ingest` on topic `job-leads.raw`.
- Happy path: validate → normalize → upsert posting (idempotent by stable
  id) → append/merge source rows.
- Poison path: deserialization or validation failure → publish the raw bytes
  to `job-leads.dlq` with headers (`failure-reason`, `original-topic`);
  the batch continues. Backs QA criterion 9.
- Rejected business rules (never stored, never DLQ — they are valid events
  that fail policy): `reported:false`, visa `restricted`. Counted and logged.

## Normalization (backend owns this)

- **Dedupe:** normalized (lowercased, whitespace-collapsed, trimmed)
  title + company + location → stable id. URL is secondary evidence only.
  Re-seen postings update `updated_at` and add/refresh a source row (existing
  rows get a fresh `seen_at`; nonblank contact fields merge without erasing
  stored values); they never change `first_seen` and never inflate
  `indexedToday`.
- **Engagement:** exactly one canonical category per posting; extra categories
  become `engagement_tags`.
- **Pay parsing:** handles `$70-75/hr`, `$120K`, `$130,000–$150,000/year`,
  `70/hr`, etc. Hourly equivalent = yearly ÷ 2080. Unparseable → all pay
  fields null; the API renders "Pay not listed" (Phase 2).
- **Freshness:** `first_seen` is the truthful anchor. `posted_minutes` is kept
  only when the source age parses precisely; otherwise null → sorts oldest,
  never "just now".
- **`indexedToday`:** count of postings whose `first_seen` falls on the
  current calendar day in America/Chicago.

## REST API

`GET /api/listings?tab=today|c2c|w2|fulltime|visa&q=<text>` → one envelope:

```json
{
  "listings": [
    {
      "id": "…", "title": "…", "company": "…", "location": "…",
      "payMin": 70, "payMax": 75, "payUnit": "hour", "payHourlyEquiv": 72.5,
      "engagement": "C2C", "engagementTags": ["W2"],
      "visaStatus": "open", "visaReason": "…",
      "contact": { "name": "…", "email": "…", "phone": "…" },
      "sources": [{ "source": "Dice", "url": "https://…", "urlVerified": true }],
      "firstSeen": "2026-09-25T08:05:00Z",
      "postedMinutes": 63, "postedMinutesConfidence": "precise"
    }
  ],
  "indexedToday": 12,
  "lastPull": "2026-09-25T08:05:00Z"
}
```

- `tab=today`: `first_seen` within the last 24h, newest first. Category tabs
  filter the same dataset. `tab=visa`: postings with known visa status
  (confirmed/open) — returns the envelope with an empty list when none, never
  404 (the tab stays visible).
- `q`: case-insensitive match on title + company.
- `GET /api/health` → `{ "status": "UP" }`.

## Security implementation (maps to docs/DESIGN.md §9)

- JPA/Hibernate only; no native SQL string concatenation.
- Bean Validation on every inbound record + length caps on all text fields.
- Per-IP rate limiting on `/api/**` (Bucket4j, in-memory).
- Spring Security headers: CSP, HSTS, X-Content-Type-Options,
  Referrer-Policy, frame deny. No `dangerouslySetInnerHTML` exists yet —
  the API returns plain strings; escaping is Phase 2's contract.
- Generic error bodies (`{ "error": "…" }`); stack traces and SQL errors stay
  in server logs.
- Config from environment only (`SPRING_DATASOURCE_URL`, `DB_USERNAME`,
  `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_SASL_*`). Nothing secret
  in the repo, in logs, or in error responses. Postgres `sslmode=require`;
  Kafka TLS + SASL/SCRAM in prod profile; plaintext only inside
  docker-compose.

## Local development

- `backend/docker-compose.yml`: Kafka (KRaft, single node) + PostgreSQL 16.
- Profiles: `local` (docker-compose defaults), `prod` (env-provided Supabase +
  Upstash; wired in Phase 3).

## Test plan (maps to QA criteria)

- `IngestionServiceTest`: only `reported:true` stored; cross-source duplicates
  collapse with merged contacts; stable id is deterministic; re-seen records
  keep `first_seen`; restricted-visa records excluded.
- `PayParserTest`: hourly/yearly/K formats → hourly equivalents; garbage →
  nulls.
- `FreshnessTest`: unknown age sorts oldest; `indexedToday` counts
  first-seen-today only (America/Chicago).
- `DlqTest` (embedded Kafka): corrupt bytes → DLQ with failure headers, batch
  continues.
- `ListingApiTest` (MockMvc): envelope shape `{listings, indexedToday,
  lastPull}`; case-insensitive search; tab filters; empty visa tab → 200 with
  empty list; edge fixtures (nearly-all-fields-missing, hostile HTML) render
  without errors and without `undefined`/blank headings.
- Idempotency: replaying the same events changes nothing.

## Explicitly NOT in Phase 1

React frontend (Phase 2) · Render deploy + prod Kafka/Postgres wiring
(Phase 3) · daily scheduler GitHub Action (Phase 3) · `ingest_runs` table
(Phase 3) · any paid service.

## Process (filled in as the phase runs)

1. Design written above and pushed to the branch before any code
   (`3cc9ec0`, 2026-09-25 19:11 UTC). Implementation branch:
   `feat/phase-1-backend`.
2. `./mvnw -B verify` green locally — see evidence below.
3. PR opened with the template; CI runs; persona review loop; findings fixed.
4. Owner's explicit merge approval received 2026-09-25 ("Go head") → squash merge completed as commit `eac51e3`. Feature branch deleted after verification. Phase 1 closed.

### Implementation evidence (2026-09-25)

- **Stack versions:** Spring Boot 3.5.16, Java 21, Maven 3.9.9, Bucket4j
  8.10.1, PostgreSQL 16 (compose) / H2 (tests).
- **Final build:** `./mvnw -B verify` → `Tests run: 45, Failures: 0,
  Errors: 0, Skipped: 0` → `BUILD SUCCESS`. Per-class:
  `ProdDataSourceConfigTest` 3 · `DlqTest` 2 · `FreshnessTest` 4 ·
  `IngestionServiceTest` 10 · `ListingApiTest` 12 · `PayParserTest` 13 ·
  `RateLimitTest` 1.
- **Sandbox build setup** (local-only, not committed, irrelevant to CI):
  Maven needed the sandbox egress CA, a local TCP relay, and
  `-Djava.net.preferIPv4Stack=true` (IPv6-mapped Java connections abort in
  this sandbox) for forked JVMs.

### Defects found during the build and their fixes

1. **Embedded Kafka started in ZooKeeper mode** and ZK timed out in the
   sandbox → `@EmbeddedKafka(..., kraft = true)` (also matches the KRaft
   compose file); surefire `argLine` carries the IPv4 flag into forked JVMs.
2. **The `@KafkaListener` container never started** — the app excludes
   Boot's `KafkaAutoConfiguration`, which also removes the annotation
   processor. `DlqTest` timed out with zero consumer activity. Fix: explicit
   `@EnableKafka` on `KafkaConfig` (Boot's factories stay out of the way).
3. **`@Transactional` self-invocation:** `ingestRaw()` called
   `this.ingest()` directly, bypassing Spring's transaction proxy; the
   re-seen path then hit `LazyInitializationException` on lazy sources.
   Fix: `@Transactional` on the proxy entry point `ingestRaw()`.
4. **Order-dependent counter tests** (ingest counters are shared
   `AtomicLong`s) → assertions now measure before/after deltas.
5. **JPA Criteria rejects `Sort.NullHandling.NULLS_LAST`** ("not yet
   supported") → the unknown-age-sorts-oldest rule is asserted with an
   explicit nulls-last ordering at the application level (native null
   ordering differs between H2 and PostgreSQL anyway).
6. **`BigDecimal` → `NUMERIC(19,2)` round-trip** made `$70` serialize as
   `70.00` → API test expects `70.0`/`75.0` numerics.
7. **Compose defects:** invalid `CLUSTER_ID` (not a Kafka UUID), missing
   `KAFKA_CONTROLLER_LISTENER_NAMES` / `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP`
   → fixed; added `KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT`.
8. **Config contract:** generic `KAFKA_EXTRA_PROPS` replaced with the named
   `KAFKA_SASL_*` variables per the approved architecture; `sslmode=require`
   is now enforced in code (`ProdDataSourceConfig`, prod profile).

### Deviations from the design above

- `lastPull` serializes as explicit `null` (never omitted) via
  `@JsonInclude(ALWAYS)` on the getter, so consumers can distinguish
  "empty" from "missing".
- `docs/DESIGN.md` was not otherwise changed; this doc's process section is
  the implementation record.

