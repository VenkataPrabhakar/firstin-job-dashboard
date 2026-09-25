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
4. Owner's explicit merge approval received 2026-09-25 ("Merge and start next phase") → squash merge completed as commit `2cb0fb1`. Feature branch deleted after verification. Phase 2 closed.

### Implementation evidence

Stack (locked in `frontend/package-lock.json`):
- react / react-dom 18.3.1 · vite 6.4.3 · typescript 5.7.3 (strict)
- vitest 3.2.7 · @testing-library/react 16.3.3 · jsdom 25.0.1
- eslint 9.39.5 + eslint-plugin-react 7.37.5 (`react/no-danger` = error)

Local verification (2026-09-25, Node 24 / npm 10):
- `npm ci` — clean install from the committed lockfile: OK (386 packages)
- `npm test -- --run` — **31/31 pass** (18 unit in `format.test.ts`, 13
  component in `App.test.tsx`)
- `npm test -- --watchAll=false` (the exact CI invocation) — **31/31 pass**
  via `frontend/scripts/test.mjs`, which strips the Jest-ism `--watchAll`
  flag Vitest rejects (see defect 3)
- `npm run build` (`tsc -b && vite build`) — OK, 41 modules, no type errors
- `npx eslint .` — clean (no `dangerouslySetInnerHTML` anywhere)

Files added under `frontend/`: 29 (components, utils, styles, tests,
configs, README). No build output, no secrets committed; `frontend/.gitignore`
covers `node_modules/`, `dist/`, and tsc byproducts.

### Defects found during the build and their fixes

1. **RTL auto-cleanup never ran (10 test failures).** `@testing-library/react`
   registers its auto-cleanup on the *global* `afterEach`; the Vitest config
   had `globals: false`, so every test's DOM leaked into the next and queries
   matched multiple elements. Fix: `globals: true` in `vite.config.ts`
   (test-only; no production impact).
2. **Footer exact-string assertion vs. multi-node `<p>`.** The footer renders
   `Last updated … · <source line>` as sibling text nodes; `getByText` with an
   exact string requires full equality, so the assertion failed even though the
   text was correct. Fix: regex matcher (test-only).
3. **CI's `npm test -- --watchAll=false` crashes Vitest** (`CACError: Unknown
   option --watchAll`) — the workflow's Jest-ism would fail the frontend job.
   Fix: `frontend/scripts/test.mjs` strips `--watchAll`/`--watchAll=*` and
   delegates to `vitest run`; `package.json` `test` script uses the shim.
   Verified with the exact CI invocation locally.
4. **ESLint `no-undef` on the Node shim.** The flat config had no Node env for
   `scripts/*.mjs`. Fix: config block granting `process`/`URL` globals for
   that path only.
5. **Invalid helper name caught before commit** (`kFormat yearly` — a typo
   with a space, caught while writing `format.ts`, never executed).
6. **`javascript:` URLs in source data.** React escapes attribute values but
   does not neutralize `javascript:` hrefs — a hostile `url` from the API
   would be an XSS vector. Fix: `safeHttpUrl()` allow-lists `http(s)` only;
   anything else renders as plain text (covered by the hostile-HTML test).

### Deviations from the design (recorded, none user-visible)

- **Expandable card shows the merged per-source list only.** UI-UX.md §4 says
  "tapping the card reveals the full description and the merged source list",
  but the Phase 1 API has no description/note field, so there is no
  description to reveal. The details button reveals the per-source "View post"
  links + verified labels. No backend change (frozen for this phase).
- **Timezone abbreviation renders CDT/CST** (via `Intl`), where UI-UX.md's
  example shows "CT". CDT/CST is the correct abbreviation; the format
  otherwise matches.
- **Card expansion is a real button** ("Show details"/"Hide details",
  `aria-expanded`), not tapping the card body — the accessible equivalent of
  the design's tap interaction.
- **`npm audit` could not run in this sandbox** (registry policy denies the
  audit endpoint). CI runs `npm audit --audit-level=high` on GitHub runners;
  dependency versions chosen are current stable releases of maintained
  packages.


