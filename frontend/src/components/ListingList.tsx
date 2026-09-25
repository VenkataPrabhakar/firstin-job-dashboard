import type { ListingDto } from '../types';
import { ListingCard } from './ListingCard';

interface ListingListProps {
  listings: ListingDto[];
  /** Active search text; when non-empty the "N results" line renders. */
  query: string;
}

export function ListingList({ listings, query }: ListingListProps) {
  return (
    <section aria-label="Job listings">
      {query.trim().length > 0 && (
        <p className="results-line" aria-live="polite">
          {listings.length} {listings.length === 1 ? 'result' : 'results'}
        </p>
      )}
      <div className="card-list">
        {listings.map((l) => (
          <ListingCard key={l.id} listing={l} />
        ))}
      </div>
    </section>
  );
}
