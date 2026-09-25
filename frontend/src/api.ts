import type { ListingsEnvelope, TabKey } from './types';

/** One fetch per (tab, query) state change — search is server-side (Phase 1 `q`). */
export async function fetchListings(
  tab: TabKey,
  query: string,
  signal: AbortSignal,
): Promise<ListingsEnvelope> {
  const params = new URLSearchParams({ tab });
  const q = query.trim();
  if (q.length > 0) {
    params.set('q', q);
  }
  const res = await fetch(`/api/listings?${params.toString()}`, { signal });
  if (!res.ok) {
    // UI shows a generic message; details stay server-side (UI-UX.md §6).
    throw new Error(`listings request failed with status ${res.status}`);
  }
  const data = (await res.json()) as Partial<ListingsEnvelope>;
  return {
    listings: Array.isArray(data.listings) ? data.listings : [],
    indexedToday: typeof data.indexedToday === 'number' ? data.indexedToday : 0,
    lastPull: typeof data.lastPull === 'string' ? data.lastPull : null,
  };
}
