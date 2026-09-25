import { formatCachedTime } from '../utils/format';

interface CachedBannerProps {
  savedAt: string;
}

/** Offline/stale: cached data renders with an honest banner (UI-UX.md §6). */
export function CachedBanner({ savedAt }: CachedBannerProps) {
  return (
    <p className="cached-banner" role="status">
      Showing cached data from {formatCachedTime(savedAt)}
    </p>
  );
}
