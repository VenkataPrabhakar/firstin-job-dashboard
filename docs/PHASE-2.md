# Phase 2 — Frontend (React 18 + Vite SPA)

Status: design approved below; implementation in progress.
Branch: `feat/phase-2-frontend`. Implements `docs/UI-UX.md` in full (all sections),
consuming the Phase 1 API (`docs/PHASE-1.md`).

## Objective

A mobile-first single-page app rendering the five tabs (Today · C2C · W2 ·
Full-Time · Visa), listing cards with always-visible recruiter info, header
search, and honest freshness — exactly per `docs/UI-UX.md`. No backend changes,
no deploy, no scheduler in this phase.

## Design decisions

- **React 18 + Vite** (approved stack). **TypeScript, strict:** the API envelope
  has a fixed shape (listing DTO, contact, sources); TS types catch contract
  drift at build time instead of rendering runtime blanks. Strict mode on.
- **Styling: plain CSS, no framework.** One design-token file (colors, spacing,
  type scale). Mobile-first: base styles target 360px, then `min-width` media
  queries upward. No CSS-in-JS runtime.
- **No router:** tab state is a React state value (per UI-UX.md §8). Deep-linking
  deferred to a later phase.
- **Data flow:** one fetch per `(tab, query)` state change —
  `GET /api/listings?tab={tab}&q={query}` — consuming the backend envelope
  (`listings`, `indexedToday`, `lastPull`). Search is server-side via `q`
  (case-insensitive, title + company), exactly as Phase 1 implemented.
- **Dev server:** Vite proxy `/api → http://localhost:8080` so `npm run dev`
  works against the local Spring backend with zero config.
- **Freshness honesty:** footer "Last updated {date, time + timezone}" from
  `lastPull` (explicit null → "Last updated — pending first pull"), "Indexed
  today: N" from `indexedToday`. Age line "First seen Xh/Xd ago" computed from
  `firstSeen`; unknown age → the age segment is omitted (never "just now").
- **NEW badge:** only when `firstSeen` falls on today in America/Chicago.
- **Hostile HTML:** React escapes by default; `dangerouslySetInnerHTML` is
  banned and CI enforces it (`react/no-danger` ESLint rule). Title never blank —
  fallback "Untitled posting". Missing pay → "Pay not listed".
- **Expandable card:** collapsed by default; expands to the full note and the
  merged per-source list ("View post" links).
- **Offline/stale:** last successful payload cached to `localStorage` with a
  timestamp; on fetch failure, render the cached data with a "Showing cached
  data from {time}" banner.
- **Loading:** skeleton cards, no layout shift when data arrives.
- **Accessibility:** `h1` page title, `h3` card titles; tab bar = real buttons
  with `role="tab"`/`aria-selected`, keyboard-navigable; icon-only controls
  (search clear) get `aria-label`s; contrast ≥ 4.5:1; badges always carry text
  labels (no color-only signals).

## Component map (per UI-UX.md §8)

```
App
├── Header (wordmark, SearchBox, TodayCounter)
├── TabBar (Today, C2C, W2, FullTime, Visa)
├── ListingList
│   └── ListingCard
│       ├── PayLine
│       ├── BadgeRow (EngagementBadge, VisaShield, NewBadge)
│       ├── RecruiterBlock
│       └── SourceLinks (expandable)
├── EmptyState
└── Footer (last-updated datetime + tab source line)
```

Tab source lines per UI-UX.md §3.2/§3.3; visa cards always show the shield
(`confirmed` / `open` / `unknown — ask`).

## Test plan (maps to QA criteria)

- **Vitest + React Testing Library.** Fixtures: hostile-HTML posting,
  nearly-all-fields-missing posting, empty visa envelope, error response,
  stale-cache scenario.
- Hostile HTML renders as text (no injection); missing title → "Untitled
  posting"; missing pay → "Pay not listed"; unknown age → no age segment, no
  blank heading, no `undefined` anywhere.
- Visa tab always rendered; empty envelope → empty-state message + last-updated
  datetime (never 404).
- Search: "N results", clear (×) button, empty result message — all wired to
  the API `q` parameter.
- Skeleton during loading; generic error on failure; cached-data banner when
  offline.
- America/Chicago date formatting verified with a fixed clock.

## Explicitly NOT in Phase 2

Backend changes (the Phase 1 API is frozen for this phase) · Render deploy +
prod wiring (Phase 3) · daily scheduler (Phase 3) · bundling the React build
into the Spring Boot jar (Phase 3) · deep-linkable routes · any paid service.

## Process (filled in as the phase runs)

1. Design written above and pushed to the branch before any code
   (implementation branch: `feat/phase-2-frontend`).
2. `npm ci && npm test -- --run && npm run build` green locally — see evidence below.
3. PR opened with the template; CI runs; persona review loop; findings fixed.
4. Owner's explicit merge approval → squash merge. (Pending — not approved.)

### Implementation evidence (to be filled)

### Defects found during the build and their fixes (to be filled)
