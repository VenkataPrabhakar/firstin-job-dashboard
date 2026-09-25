# FirstIn frontend (Phase 2)

React 18 + Vite + TypeScript (strict) single-page app for the FirstIn job
dashboard. Implements `docs/UI-UX.md` in full, consuming the Phase 1 API
(`GET /api/listings`, `GET /api/health`).

## Develop

```bash
npm ci
npm run dev      # Vite dev server; /api proxies to http://localhost:8080
```

## Check

```bash
npm test -- --run   # Vitest + React Testing Library (31 tests)
npm run build        # tsc -b && vite build
npx eslint .         # react/no-danger enforced: no dangerouslySetInnerHTML
```

## Notes

- No router, no CSS framework — plain CSS, mobile-first (360px baseline).
- Search is server-side via the API `q` parameter (case-insensitive,
  title + company).
- All datetimes render in America/Chicago; `first_seen` is the freshness
  anchor. Unknown age renders as no age segment, never "just now".
- Offline: the last successful payload per (tab, query) is cached in
  `localStorage` and rendered with a "Showing cached data from …" banner.
- Hostile HTML renders as inert text. Source URLs are allow-listed to
  `http(s)` before becoming links; anything else renders as plain text.
