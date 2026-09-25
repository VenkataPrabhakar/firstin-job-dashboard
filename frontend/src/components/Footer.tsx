import { TAB_SOURCE_LINES, type TabKey } from '../types';
import { formatLastUpdated } from '../utils/format';

interface FooterProps {
  lastPull: string | null;
  tab: TabKey;
}

/** Explicit "Last updated <date, time + timezone>" + tab source line (UI-UX.md §2). */
export function Footer({ lastPull, tab }: FooterProps) {
  return (
    <footer className="site-footer">
      <p>
        {formatLastUpdated(lastPull)} · {TAB_SOURCE_LINES[tab]}
      </p>
    </footer>
  );
}
