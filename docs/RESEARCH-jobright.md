# Research: jobright.ai (full public site tour)

Date: 2026-09-25. Reviewed read-only (no sign-in) at the owner's request, to
identify ideas worth borrowing for FirstIn.

## Access outcome (honest)

- `/jobs/recommend` redirects to the homepage without login; `/jobs/info/<id>`
  and `/jobs/*` SEO listing pages sit behind a Cloudflare security check (not
  attempted). The live personalized feed could not be observed.
- Two **public listing pages** WERE fully observable and are the most valuable
  source: `/remote-jobs` ("Top U.S. 100% Remote Jobs (Updated Hourly)") and
  `/entry-level-jobs` ("Top 2026 U.S. Entry Level Jobs for New Grad").
- Also toured: `/ai-job-match`, `/ai-agent`, `/ai-resume-builder`,
  `/job-referral`, `/orion-copilot`, `/job-autofill`, `/h1b-jobs`,
  `/interview-landing`, `/coach-landing`, `/employers`, `/employers/pricing`,
  `/tnt`, `/blog/is-jobright-legit/`.

## Observed UX patterns (public listing pages)

**Job card anatomy** (`/remote-jobs`): company logo + "Company · N minutes ago"
+ job title + location · remote flag + level pill ("Lead/Staff") + salary-range
pill ("$250K/yr - $349K/yr", sometimes hourly "$70/hr") + "View →" link +
one-line company blurb + industry tags + company size ("1000+ Employees") +
growth-stage pill ("Early Stage" / "Growth Stage" / "Public"). Contract type is
sometimes embedded in the title ("DevSecOps Engineer (W2 Contract only/
No 3rd Parties)").

**Freshness displays:** "311,222 Total Openings" / "22,674 New Openings Today"
counters; `/entry-level-jobs` shows an explicit
**"Last Updated: September 25, 2026, 10:40 AM PDT"** line — the most honest
freshness pattern on the site.

**Filters:** Job Type (Full-time / Contract / Part-time / Internship),
Experience Level dropdowns; a "Browse by Category" index with expandable
subcategories.

**Segment pages:** each segment gets a headline ("Top U.S. 100% Remote Jobs
(Updated Hourly)") plus a source line ("Updated hourly from major job boards
and 200K+ company career sites"). `/h1b-jobs` runs a dedicated visa-sponsorship
segment with per-title × location index pages.

**Homepage search widget:** Job Title / Work Model / Country / City /
Experience Level + GO — structured facets, not just free text.

**Empty/404 state:** plain-language explanation + grouped navigation links
instead of a dead end — a good model.

## Worth borrowing for FirstIn (ranked)

1. **Explicit "Last Updated: <date, time + timezone>" line** — adopted into
   UI-UX.md footer (was "Last pull: <time>", now with timezone).
2. **"N New Openings Today" / "M Total Openings" counters** — our "Indexed
   today: N" is the same instinct; consider adding a total-openings counter.
3. **Relative timestamp directly under the company name** on every card —
   validates our "First seen Xh ago" placement.
4. **Contract type in pills/title** ("W2 Contract only") — maps 1:1 onto our
   C2C / W2 / Full-Time tabs and badges.
5. **Per-tab source lines** — each tab gets a one-line source note, e.g.
   "Aggregated from morning agent sweeps · LinkedIn posts, Google, Dice,
   Indeed, ZipRecruiter, recruiter emails." (Adopted into UI-UX.md.)
6. **Dedicated visa segment** (`/h1b-jobs`) — validates our always-visible Visa
   tab as a first-class segment, not a filter.
7. **Structured search facets** (work model, location, experience level) —
   future enhancement, not v1.
8. **Helpful empty states** with navigation — matches our empty-tab spec.

## Not worth copying

- AI agent / auto-apply / Orion copilot, resume tailoring, match-score breakdowns
  (all need a logged-in user profile + paid AI spend — meaningless for a free
  no-login feed).
- Insider Connections contact discovery (account-gated, monetized) — our
  recruiter contacts come from free public sources instead.
- TNT network, career coaching, autofill extension, employer pricing ($499/mo)
  — irrelevant to a free discovery dashboard.
- Vanity counters ("8,000,000+ jobs") at face value — a personal dashboard uses
  its own crawl counts.
- Their broken subcategory links ("undefined/...") and dead footer tool links —
  copy the look, not the rot.

## Data-sourcing context

Per their blog (2025-06-18): ~8M live postings, ~400K fresh/day, aggregated
from LinkedIn, Indeed, and company career sites, with screening that removes
"fake" and outdated listings. Launched 2023, $7.7M raised, freemium
($30/mo Premium mentioned for AI features). Job-seeker pricing page not found
publicly.

## Decisions for FirstIn

- Adopted: timezone-explicit "Last updated" footer line; per-tab source lines
  (UI-UX.md updated).
- Backlog: total-openings counter; work-model/location search facets;
  seniority pill on cards; per-card "why surfaced" line (from the earlier
  recommendations-page pass).
- No architecture changes.
