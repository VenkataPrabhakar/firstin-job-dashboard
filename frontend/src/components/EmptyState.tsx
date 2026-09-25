import { TABS, type TabKey } from '../types';
import { formatLastUpdated } from '../utils/format';

interface EmptyStateProps {
  tab: TabKey;
  query: string;
  lastPull: string | null;
}

/**
 * Empty states inform (docs/UI-UX.md §1.4): why it's empty + the
 * last-updated datetime. Never a 404 (the tab stays visible).
 */
export function EmptyState({ tab, query, lastPull }: EmptyStateProps) {
  const q = query.trim();
  let message: string;
  if (q.length > 0) {
    message = `No matches for '${q}' in this tab.`;
  } else if (tab === 'visa') {
    message = 'No visa-friendly postings in the last 24h.';
  } else if (tab === 'today') {
    message = 'No postings in the last 24h.';
  } else {
    const label = TABS.find((t) => t.key === tab)?.label ?? '';
    message = `No ${label} postings in the last 24h.`;
  }

  return (
    <section className="empty" aria-label="No listings">
      <p className="empty-message">{message}</p>
      <p className="empty-updated">{formatLastUpdated(lastPull)}</p>
    </section>
  );
}
