interface HeaderProps {
  query: string;
  onQueryChange: (q: string) => void;
  /** Null while the first payload hasn't arrived. */
  indexedToday: number | null;
}

/**
 * Sticky header: wordmark, search box, "Indexed today: N" counter
 * (docs/UI-UX.md §2).
 */
export function Header({ query, onQueryChange, indexedToday }: HeaderProps) {
  return (
    <header className="site-header">
      <h1 className="wordmark">FirstIn</h1>
      <div className="search" role="search">
        <label className="visually-hidden" htmlFor="listing-search">
          Search listings by title or company
        </label>
        <input
          id="listing-search"
          className="search-input"
          type="search"
          placeholder="Search title or company…"
          autoComplete="off"
          maxLength={200}
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
        />
        {query.length > 0 && (
          <button
            type="button"
            className="search-clear"
            aria-label="Clear search"
            onClick={() => onQueryChange('')}
          >
            ×
          </button>
        )}
      </div>
      <p className="today-counter" aria-live="polite">
        Indexed today: {indexedToday == null ? '—' : indexedToday}
      </p>
    </header>
  );
}
