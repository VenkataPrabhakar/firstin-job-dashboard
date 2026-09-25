interface ErrorStateProps {
  onRetry: () => void;
}

/** Generic error — details stay server-side (docs/DESIGN.md §9, UI-UX.md §6). */
export function ErrorState({ onRetry }: ErrorStateProps) {
  return (
    <section className="error" role="alert" aria-label="Loading failed">
      <p className="error-message">Something went wrong. Try again.</p>
      <button type="button" className="retry-button" onClick={onRetry}>
        Try again
      </button>
    </section>
  );
}
