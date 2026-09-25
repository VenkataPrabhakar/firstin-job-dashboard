# FirstIn — Personal Job Discovery Dashboard
### Design document · v1.0 · 2026-09-25
### Status: proposed — pending team debate + senior panel approval (see linked thread)

## 1. What this is

A personal, single-user job-discovery dashboard that collects fresh job leads every morning from the user's existing search agents and presents them like a recruiter's live feed: newest first, honest timestamps, direct recruiter contacts, one click to the listing.

**Scope of leads:** C2C contracts · W2 contracts · full-time roles · visa/sponsorship-friendly roles.

**Hard product constraint:** one version, completely free, every feature unlocked. No plans, no billing page, no paywalled features, no "Pro" badges, no upgrade prompts. This constraint is architectural, not cosmetic — nothing in the codebase may branch on a plan tier.

## 2. Architecture

```
Morning agents (existing)          Ingestion (daily 08:05)          App
┌──────────────┐   JSON ledgers    ┌──────────────────┐   Postgres   ┌─────────────────────┐
│ C2C agent    │ ───────────────▶  │  Ingest job      │ ──────────▶  │  Spring Boot 3      │
│ W2 agent     │                   │  normalize →     │              │  REST API + static  │
│ Full-time    │                   │  dedupe → load   │              │  frontend           │
│ agent        │                   │  (GitHub Action  │              └─────────────────────┘
└──────────────┘                   │   on schedule)   │                       │
                                   └──────────────────┘                       ▼
                                                                    ┌─────────────────────┐
                                                                    │  Postgres (Supabase │
                                                                    │  free tier)         │
                                                                    └─────────────────────┘
```

| Layer | Choice | Why |
|---|---|---|
| Backend | Spring Boot 3 (Java 21) REST API | Owner's own stack; portfolio value; serves API + static frontend from one deployable |
| Database | PostgreSQL on Supabase free tier | Real relational DB, zero cost, zero ops; replaces the flat-JSON idea so history, dedupe and trends are queryable |
| Frontend | Static HTML/CSS/JS served by the app | The dashboard UI below; no build step, works from the API |
| Ingestion | Scheduled GitHub Action, daily ~08:05 | Runs after the morning agents finish; reads the 4 agent ledger JSONs, normalizes, dedupes, loads Postgres |
| CI/CD | GitHub Actions | On push: Maven build → automated QA acceptance tests → deploy. Scheduled: daily ingest |
| Cloud | Render free tier | $0/month; sleeps when idle, wakes in ~30s — fine for a morning-check dashboard |

**Total running cost: $0/month** (GitHub + Supabase free tier + Render free tier).

## 3. Data contract

One canonical record per real-world posting. Only `reported:true` records may enter the database.

```json
{
  "id": "sha1(title|company|location)",
  "title": "Sr. Java Developer",
  "company": "Keylent",
  "location": "Reading, PA, US",
  "engagement": "C2C | W2 | FULLTIME",
  "pay_min": 70, "pay_max": 75, "pay_unit": "hour",
  "pay_hourly_equiv": 72.5,
  "first_seen": "2026-09-25T08:05:00Z",
  "posted_minutes": 63,
  "posted_minutes_confidence": "precise | vague | unknown",
  "raw_posted": "1 hour ago",
  "source": "Dice",
  "note": "W2/C2C terms …",
  "url": "https://… | null",
  "url_verified": true,
  "contact": { "name": "…", "email": "…", "phone": "…" },
  "visa": { "status": "confirmed | open | unknown | restricted", "reason": "matched phrase" }
}
```

### Normalization rules (backend owns these)
- **Dedupe:** cross-source, on normalized title + company + location; URL as secondary signal. Duplicates collapse into one record, merging contact details. Stable `id` = hash of the normalized triple.
- **One canonical category:** each record lives under exactly one tab (`engagement`). Secondary engagement types may appear as small tags, never as duplicate cards.
- **Pay:** backend parses listed min/max + unit and emits an hourly equivalent so filters can compare hourly and yearly figures.
- **Freshness anchor:** `first_seen` (ISO, reliable) is the source of truth. `posted_minutes` is kept only when the source age parsed precisely; unparseable ages sort into the oldest/unknown bucket and never render as "just now". The raw source string is kept for fallback display.
- **"Indexed today"** counts records whose `first_seen` is today only — re-seen/updated listings never inflate it.
- **Visa:** keyword analysis keeps the exact matched phrase as `reason`. States: `confirmed` (e.g. "sponsorship", "H-1B", "visa", "OPT") · `open` · `unknown — ask` · `restricted` ("no sponsorship", "citizens only", "USC/GC only"). Restricted records are excluded at ingestion (the full-time search already filters them).

## 4. UI / UX

### Navigation
Tabs: **Today · C2C · W2 · Full-Time · Visa**.
- **Today** (default): mixed feed of all fresh records from the last 24h, newest first.
- Category tabs: filtered views of the same deduplicated dataset.
- **Visa** tab stays visible even when empty, with guidance + an "ask" email draft; visa shields also appear inline on cards in every feed.

### Header (sticky)
Product name · "N indexed today" · "Data updated HH:MM" · dark-mode toggle.

### Job cards
1. Title + engagement badge (C2C purple · W2 blue · Full-Time green; Visa = shield icon, not a competing color)
2. Company + pay ("Pay not listed" when missing)
3. Location + honest posting age
4. Source / technology note
5. Recruiter name/email/phone + direct listing link when captured — **always visible, never hidden or paywalled**

Honesty rules:
- Missing link → show the listing unlinked with "Link not captured".
- Missing recruiter → "No recruiter email captured". Never invent, never hide the listing.
- Broken URL → "Link unverified" badge.
- Red "new / under 2 hours" urgency **only** when the source age parsed precisely — vague timestamps never get urgency styling.
- Never backfill old roles and label them as today's.

### Search & filters
Keyword (title/company/note/source) · location · minimum pay · source multi-select · sort (Newest | Highest pay). Filters collapse on mobile, one-tap clear. 50 records per page.

### Empty states
Tabs are never hidden. Empty tab: "Nothing new yet today — last checked HH:MM."

### Quality bar
Dense, professional daily-driver UI · mobile-friendly at 360px · keyboard accessible · no marketing clutter or modals. Last payload cached in the browser for offline viewing.

## 5. CI/CD pipeline

```yaml
on: push → build → test → deploy
  1. Checkout + set up Java 21
  2. Maven build
  3. Automated QA suite (acceptance criteria §6 — fail the pipeline on violation)
  4. Deploy to Render (free tier) via deploy hook

on: schedule (daily 08:05) → ingest
  1. Read the 4 agent ledger JSONs
  2. Normalize → dedupe → load Postgres (only reported:true)
  3. Record ingest run in meta table; failures fail loudly, never silently
```

## 6. QA acceptance criteria (automated in CI)

1. Zero `reported:false` records render.
2. No listing shows a fresher age than its true/known age.
3. Cross-source duplicates collapse to one card.
4. Empty tabs stay visible and show the last pull time.
5. Missing fields never produce blank headings, `undefined`, or console errors.
6. Search is case-insensitive; works by title and company.
7. 360px mobile layout: no clipped badges or controls.
8. Edge-case fixture set renders with zero console errors.
9. Daily regression: new records appear, old ages advance, duplicates stay merged, corrupt records fail safely.

## 7. Open questions for the thread

1. Restricted-visa records: excluded at ingestion (current) vs. visible outside the Visa tab with a warning?
2. `posted_minutes` for unparseable ages: `null` vs. explicit sentinel — either is fine if it sorts oldest and never renders "just now".
3. Repo visibility: private (current) vs. public.

---
*Drafted from the design-team debate (Backend, Frontend, QA, UX — two rounds). The full discussion and senior-panel approvals live in the linked thread.*
