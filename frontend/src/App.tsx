import { useEffect, useState } from 'react';
import { fetchListings } from './api';
import type { ListingsEnvelope, TabKey } from './types';
import { CachedBanner } from './components/CachedBanner';
import { EmptyState } from './components/EmptyState';
import { ErrorState } from './components/ErrorState';
import { Footer } from './components/Footer';
import { Header } from './components/Header';
import { ListingList } from './components/ListingList';
import { SkeletonList } from './components/Skeleton';
import { TabBar } from './components/TabBar';
import { readCache, writeCache } from './utils/cache';

type Status = 'loading' | 'ready' | 'error';

/** Debounce search input so typing doesn't fire a fetch per keystroke. */
function useDebouncedValue(value: string, delayMs: number): string {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(id);
  }, [value, delayMs]);
  return debounced;
}

export default function App() {
  const [tab, setTab] = useState<TabKey>('today');
  const [query, setQuery] = useState('');
  const debouncedQuery = useDebouncedValue(query, 300);
  const [envelope, setEnvelope] = useState<ListingsEnvelope | null>(null);
  const [status, setStatus] = useState<Status>('loading');
  const [cachedAt, setCachedAt] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setStatus('loading');
    setCachedAt(null);

    fetchListings(tab, debouncedQuery, controller.signal)
      .then((env) => {
        writeCache(tab, debouncedQuery, env);
        setEnvelope(env);
        setStatus('ready');
      })
      .catch((err: unknown) => {
        if (err instanceof DOMException && err.name === 'AbortError') {
          return;
        }
        // Offline/stale: fall back to the last successful payload for this
        // (tab, query); without one, show the generic error state.
        const cached = readCache(tab, debouncedQuery);
        if (cached) {
          setEnvelope(cached.envelope);
          setCachedAt(cached.savedAt);
          setStatus('ready');
        } else {
          setStatus('error');
        }
      });

    return () => controller.abort();
  }, [tab, debouncedQuery, retryCount]);

  const trimmedQuery = debouncedQuery.trim();

  return (
    <div className="app">
      <div className="sticky-top">
        <Header
          query={query}
          onQueryChange={setQuery}
          indexedToday={envelope ? envelope.indexedToday : null}
        />
        <TabBar active={tab} onSelect={setTab} />
      </div>

      <main className="content">
        {cachedAt && <CachedBanner savedAt={cachedAt} />}

        {status === 'loading' && <SkeletonList />}

        {status === 'error' && <ErrorState onRetry={() => setRetryCount((c) => c + 1)} />}

        {status === 'ready' && envelope && (
          <>
            {envelope.listings.length === 0 ? (
              <EmptyState tab={tab} query={trimmedQuery} lastPull={envelope.lastPull} />
            ) : (
              <ListingList listings={envelope.listings} query={trimmedQuery} />
            )}
          </>
        )}
      </main>

      <Footer lastPull={envelope ? envelope.lastPull : null} tab={tab} />
    </div>
  );
}
