import { useRef } from 'react';
import { TABS, type TabKey } from '../types';

interface TabBarProps {
  active: TabKey;
  onSelect: (tab: TabKey) => void;
}

/**
 * Sticky tab bar — real buttons with the tablist pattern (docs/UI-UX.md §9):
 * arrow keys move between tabs, Home/End jump to the ends.
 */
export function TabBar({ active, onSelect }: TabBarProps) {
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);

  const focusTab = (index: number): void => {
    const el = buttons.current[index];
    if (el) {
      el.focus();
      onSelect(TABS[index].key);
    }
  };

  const onKeyDown = (e: React.KeyboardEvent): void => {
    const current = TABS.findIndex((t) => t.key === active);
    if (e.key === 'ArrowRight') {
      e.preventDefault();
      focusTab((current + 1) % TABS.length);
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault();
      focusTab((current - 1 + TABS.length) % TABS.length);
    } else if (e.key === 'Home') {
      e.preventDefault();
      focusTab(0);
    } else if (e.key === 'End') {
      e.preventDefault();
      focusTab(TABS.length - 1);
    }
  };

  return (
    <nav className="tabbar-nav" aria-label="Listing categories">
      <div className="tabbar" role="tablist" aria-label="Listing categories" onKeyDown={onKeyDown}>
        {TABS.map((t, i) => (
          <button
            key={t.key}
            ref={(el) => {
              buttons.current[i] = el;
            }}
            type="button"
            role="tab"
            aria-selected={t.key === active}
            tabIndex={t.key === active ? 0 : -1}
            className={t.key === active ? 'tab tab-active' : 'tab'}
            onClick={() => onSelect(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>
    </nav>
  );
}
