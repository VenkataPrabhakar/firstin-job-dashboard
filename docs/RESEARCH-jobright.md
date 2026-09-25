# Research: jobright.ai recommendations page

Date: 2026-09-25. Reviewed read-only (no sign-in) at the owner's request, to
identify ideas worth borrowing for FirstIn.

## Access outcome (honest)

- `https://jobright.ai/jobs/recommend` **redirects to the marketing homepage**
  without login — the real recommendations feed is account-gated and could not
  be observed.
- Job-detail pages (`/jobs/info/<id>`) are behind a bot-verification challenge,
  which was not attempted.
- All observations below come from the public homepage's product demos, live
  ticker, and feature descriptions — **not** from the live feed UX.

## Observed UX patterns

1. **Fresh-jobs ticker** under the hero: multi-lane auto-scrolling cards showing
   company logo, company name, **relative timestamp** ("4 minutes ago",
   "13 minutes ago", "1 hour ago") as the primary freshness signal, then the
   job title. Aggregate counters above it: "Today's New Jobs 400,000+".
2. **Match-score cards:** circular "Overall" percentage badge (e.g. 95%),
   timestamp pill ("1 hour ago"), logo, title, company. A "Why You Are A Good
   Fit" panel breaks the score into sub-dimensions ("95% Exp. Level",
   "93% Skill", "96% Industry Exp.") with a ✓/✕ requirement checklist.
3. **Application tracking** as a first-class dashboard view ("track every
   application from one dashboard").
4. **Pre-application field checklist:** "Required (10/12 filled)" with per-field
   ✓/− status before auto-apply.
5. **Faceted search:** Job Title, Work Model, Country, City, Experience Level.

## Worth borrowing for FirstIn

- **Relative timestamps as the primary freshness signal** — validates our
  honest-timestamp rule (§4 UI-UX.md). Keep "First seen Xh ago" prominent.
- **Header aggregate counter** — our "Indexed today: N" is the same instinct;
  Jobright confirms it works as a motivating dashboard stat.
- **"Why this job surfaced" transparency** — a small per-card line (source +
  matched signal, e.g. "via LinkedIn · visa: confirmed") mirrors their
  "Why You Are A Good Fit" panel without any ML.
- **Compact card anatomy** (logo/name/title scannable at a glance) — our card
  spec already follows this; no change needed.
- **Faceted search** — validates our tab + search structure; work-model and
  location facets are a sensible future enhancement, not v1 scope.

## Not worth copying

- The gated feed itself (unobservable); Orion auto-apply and resume tailoring
  (paid value prop, irrelevant to a free discovery feed); recruiter-network and
  interview-prep features (out of scope); vanity aggregate stats.
- Note: Jobright does **not** expose recruiter contact info publicly — confirms
  we need our own sourcing for the "recruiter always visible" rule.

## Decision

No design changes required. The research **validates** the current UI-UX.md
(timestamp prominence, header counter, card anatomy, tab + search structure)
and adds two future enhancements to the backlog: per-card "why surfaced" line,
work-model/location facets.
