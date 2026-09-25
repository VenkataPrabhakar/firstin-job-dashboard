# FirstIn — UI/UX Design

Companion to `docs/DESIGN.md`. Every user-visible change must implement an
approved section of this document — no feature without a design (§7,
design-before-code).

## 1. Design principles

1. **Glanceable in 60 seconds.** The owner checks this over morning coffee.
   Freshness first, everything else second.
2. **Recruiter info is never hidden.** Name, company, email, phone are always
   visible on the card — never behind a click, a tab, or a paywall (there is none).
3. **Honest time.** Ages are computed from `first_seen`. Unknown age renders as
   no age — never "just now".
4. **Empty states inform.** An empty tab explains why and shows the last pull time.
5. **Mobile-first.** 360px wide is the baseline, not an afterthought.
6. **No color-only signals.** Every badge/shield carries a text label (accessibility).

## 2. Global layout

```
┌─────────────────────────────────────────────┐
│ FirstIn        🔍 search…     Indexed today: N│  ← header (sticky)
├─────────────────────────────────────────────┤
│ Today │ C2C │ W2 │ Full-Time │ Visa          │  ← tab bar (sticky)
├─────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────┐ │
│ │ Listing card                            │ │
│ └─────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────┐ │
│ │ Listing card                            │ │
│ └─────────────────────────────────────────┘ │
├─────────────────────────────────────────────┤
│ Last pull: <time> · <source note>           │  ← footer
└─────────────────────────────────────────────┘
```

- **Header (sticky):** wordmark, search box, "Indexed today: N" counter.
- **Tab bar (sticky, horizontally scrollable on mobile):**
  Today · C2C · W2 · Full-Time · Visa.
- **Footer:** last pull time + one-line source note.

## 3. Pages

### 3.1 Today (default landing)
- Mixed feed of **all** fresh records from the last 24h, newest first by `first_seen`.
- Each card carries a small category tag (C2C / W2 / FT) so the mix stays readable.
- Header counter "Indexed today: N" counts records first seen today only.

### 3.2 C2C · W2 · Full-Time tabs
- Same card component, filtered to the engagement type. Newest first.
- Empty state: "No {type} postings in the last 24h. Last pull: {time}."

### 3.3 Visa tab
- Always visible, even when empty.
- Empty state: "No visa-friendly postings in the last 24h. Last pull: {time}."
- Cards here always show the visa shield with its state
  (`confirmed` / `open` / `unknown — ask`).

## 4. Listing card anatomy

```
┌──────────────────────────────────────────────────┐
│ Senior Java Developer                       [NEW]│  ← title (h3)
│ TechNova Solutions · Remote, USA                 │  ← company · location
│ $70–80/hr C2C  (≈ $145k–166k/yr)   [C2C] [🛡 visa]│  ← pay + badges
│ ──────────────────────────────────────────────── │
│ Recruiter: Jane Rao, Wise Solutions              │
│ jane.rao@wise.com · (555) 010-2030               │  ← always visible
│ ──────────────────────────────────────────────── │
│ First seen 3h ago · Sources: LinkedIn, Dice      │  ← honest age + sources
│ [View post]                                      │  ← per-source links
└──────────────────────────────────────────────────┘
```

Rules:
- **Title** is the card heading — never blank (fallback: "Untitled posting").
- **Pay line:** parsed pay + hourly equivalent; missing → "Pay not listed".
- **Badges:** engagement type always; visa shield when visa info exists;
  `NEW` only when `first_seen` is today.
- **Recruiter block:** name, company, email, phone — plain text, always visible.
- **Age line:** "First seen Xh/Xd ago". Unparseable age → the age segment is
  omitted entirely (never "just now", never blank heading).
- **Sources:** one "View post" link per merged source.
- **Expandable:** tapping the card reveals the full description and the merged
  source list. Collapsed by default.

## 5. Search
- Lives in the header; filters the current tab.
- Case-insensitive; matches title and company.
- Shows "N results", with a clear (×) button. Empty result: "No matches for
  '{q}' in this tab."

## 6. States
- **Loading:** skeleton cards (no layout shift when data arrives).
- **Empty tab:** message + last pull time (§3).
- **Error:** generic "Something went wrong. Try again." — details stay
  server-side (§9 of DESIGN.md).
- **Offline/stale:** if the API is unreachable, show the last successfully
  loaded data with a "Showing cached data from {time}" banner.

## 7. Mobile (360px baseline)
- Single column; cards full-width.
- Sticky tab bar scrolls horizontally; no clipped badges or controls.
- Recruiter block wraps cleanly; phone numbers tap-to-call.

## 8. React component map

```
App
├── Header (wordmark, SearchBox, TodayCounter)
├── TabBar (Today, C2C, W2, FullTime, Visa)
├── ListingList
│   └── ListingCard
│       ├── PayLine
│       ├── BadgeRow (EngagementBadge, VisaShield, NewBadge)
│       ├── RecruiterBlock
│       └── SourceLinks
├── EmptyState
└── Footer (last pull time)
```

- Data: `fetch('/api/listings?tab={tab}&q={query}')`.
- No client-side routing library needed — tab state only.

## 9. Accessibility
- Semantic headings (`h1` page, `h3` card titles); tab bar is keyboard-navigable.
- Contrast ≥ 4.5:1 for text; badges never rely on color alone.
- `alt`/aria labels on icon-only controls (search clear button).
