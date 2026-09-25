/** Skeleton cards shown while loading — no layout shift when data arrives (UI-UX.md §6). */
export function SkeletonList() {
  return (
    <div className="skeleton-list" role="status" aria-label="Loading listings">
      {[0, 1, 2].map((i) => (
        <div key={i} className="skeleton-card" aria-hidden="true">
          <div className="skeleton-line skeleton-title" />
          <div className="skeleton-line skeleton-sub" />
          <div className="skeleton-line skeleton-pay" />
          <div className="skeleton-badges">
            <div className="skeleton-badge" />
            <div className="skeleton-badge" />
          </div>
        </div>
      ))}
    </div>
  );
}
