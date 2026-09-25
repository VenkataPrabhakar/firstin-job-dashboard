import type { ListingsEnvelope, TabKey } from '../types';

/**
 * Offline/stale support (UI-UX.md §6): the last successful payload per
 * (tab, query) is cached with a timestamp. On fetch failure the app renders
 * the cached data with a "Showing cached data from {time}" banner.
 * Bounded (MAX_ENTRIES) so storage can't grow without limit; best-effort —
 * failures never break rendering.
 */

const PREFIX = 'firstin:cache:v1:';
const INDEX_KEY = 'firstin:cache:v1:index';
const MAX_ENTRIES = 20;

export interface CachedPayload {
  envelope: ListingsEnvelope;
  savedAt: string;
}

export function cacheKey(tab: TabKey, query: string): string {
  return `${PREFIX}${tab}:${query.trim().toLowerCase()}`;
}

function isEnvelope(value: unknown): value is ListingsEnvelope {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const v = value as Record<string, unknown>;
  return Array.isArray(v['listings']) && typeof v['indexedToday'] === 'number';
}

export function readCache(tab: TabKey, query: string): CachedPayload | null {
  try {
    const raw = localStorage.getItem(cacheKey(tab, query));
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<CachedPayload>;
    if (!parsed || !isEnvelope(parsed.envelope) || typeof parsed.savedAt !== 'string') {
      return null;
    }
    return { envelope: parsed.envelope, savedAt: parsed.savedAt };
  } catch {
    return null;
  }
}

export function writeCache(tab: TabKey, query: string, envelope: ListingsEnvelope): void {
  try {
    const key = cacheKey(tab, query);
    localStorage.setItem(key, JSON.stringify({ envelope, savedAt: new Date().toISOString() }));
    const rawIndex = localStorage.getItem(INDEX_KEY);
    const index = Array.isArray(JSON.parse(rawIndex ?? '[]'))
      ? (JSON.parse(rawIndex ?? '[]') as string[])
      : [];
    const next = [key, ...index.filter((k) => k !== key)].slice(0, MAX_ENTRIES);
    for (const stale of index) {
      if (!next.includes(stale)) {
        localStorage.removeItem(stale);
      }
    }
    localStorage.setItem(INDEX_KEY, JSON.stringify(next));
  } catch {
    // Storage full or blocked (private mode): caching is best-effort.
  }
}

export function clearCache(): void {
  try {
    const rawIndex = localStorage.getItem(INDEX_KEY);
    const index = Array.isArray(JSON.parse(rawIndex ?? '[]'))
      ? (JSON.parse(rawIndex ?? '[]') as string[])
      : [];
    for (const key of index) {
      localStorage.removeItem(key);
    }
    localStorage.removeItem(INDEX_KEY);
  } catch {
    // ignore
  }
}
