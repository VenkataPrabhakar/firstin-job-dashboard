import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import { writeCache } from '../utils/cache';
import { envelope, hostileListing, listing, sparseListing } from './fixtures';

function mockFetchSuccess(data: unknown) {
  const mock = vi
    .fn()
    .mockResolvedValue({ ok: true, json: () => Promise.resolve(data) } as unknown as Response);
  vi.stubGlobal('fetch', mock);
  return mock;
}

function mockFetchFailure() {
  const mock = vi.fn().mockRejectedValue(new Error('network down'));
  vi.stubGlobal('fetch', mock);
  return mock;
}

function mockFetchPending() {
  const mock = vi.fn().mockReturnValue(new Promise(() => {}));
  vi.stubGlobal('fetch', mock);
  return mock;
}

beforeEach(() => {
  localStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('listing rendering', () => {
  it('renders a full listing card with recruiter info always visible', async () => {
    mockFetchSuccess(envelope([listing()]));
    render(<App />);

    const card = (await screen.findByRole('heading', { name: 'Senior Java Developer' })).closest(
      'article',
    );
    expect(card).not.toBeNull();
    const cardScope = within(card as HTMLElement);
    expect(cardScope.getByText('TechNova Solutions · Remote, USA')).toBeInTheDocument();
    expect(cardScope.getByText('$70–$80/hr (≈ $146k–$166k/yr)')).toBeInTheDocument();
    expect(cardScope.getByText('C2C')).toBeInTheDocument();
    expect(cardScope.getByText(/First seen/)).toBeInTheDocument();
    // Recruiter block is visible without expanding.
    expect(cardScope.getByText('Jane Rao', { exact: false })).toBeInTheDocument();
    expect(cardScope.getByText('jane.rao@example.com')).toBeInTheDocument();
    expect(cardScope.getByText('(555) 010-2030')).toBeInTheDocument();
    // Visa shield carries a text label.
    expect(cardScope.getByText('🛡 Visa: open')).toBeInTheDocument();
    // Header counter + footer datetime.
    expect(screen.getByText('Indexed today: 1')).toBeInTheDocument();
    expect(screen.getByText(/Last updated Sep 25, 2026/)).toBeInTheDocument();
  });

  it('renders hostile HTML as inert text — no injection', async () => {
    mockFetchSuccess(envelope([hostileListing]));
    render(<App />);

    await screen.findByText(/Senior.*Java.*Dev/);
    // No live elements smuggled in.
    expect(document.querySelector('img')).toBeNull();
    expect(document.querySelector('script')).toBeNull();
    expect(document.querySelector('[onerror]')).toBeNull();
    expect(document.querySelector('[onload]')).toBeNull();

    // The javascript: "View post" URL renders as plain text, not a link.
    fireEvent.click(screen.getByRole('button', { name: 'Show details' }));
    expect(screen.getByText('View post').tagName).toBe('SPAN');
    expect(screen.queryByRole('link', { name: 'View post' })).toBeNull();
  });

  it('handles a nearly-all-fields-missing listing with no blanks or undefined', async () => {
    mockFetchSuccess(envelope([sparseListing]));
    render(<App />);

    await screen.findByRole('heading', { name: 'Untitled posting' });
    expect(screen.getByText('Pay not listed')).toBeInTheDocument();
    // Unknown age -> no age segment at all.
    expect(screen.queryByText(/First seen/)).toBeNull();
    // Nothing renders as "undefined" or leaves a blank heading.
    expect(document.body.textContent).not.toMatch(/undefined/);
    const headings = screen.getAllByRole('heading');
    for (const h of headings) {
      expect(h.textContent?.trim().length).toBeGreaterThan(0);
    }
  });

  it('expands the card to reveal the merged per-source list', async () => {
    mockFetchSuccess(envelope([listing()]));
    render(<App />);

    await screen.findByRole('heading', { name: 'Senior Java Developer' });
    const toggle = screen.getByRole('button', { name: 'Show details' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(toggle);
    expect(screen.getByRole('link', { name: 'View post' })).toHaveAttribute(
      'href',
      'https://example.com/post/1',
    );
    expect(screen.getByText('✓ verified')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Hide details' })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
  });
});

describe('tabs', () => {
  it('keeps the Visa tab visible and informs on empty (never 404)', async () => {
    const fetchMock = mockFetchSuccess(
      envelope([], { indexedToday: 0, lastPull: '2026-09-25T18:00:00Z' }),
    );
    render(<App />);

    const visaTab = await screen.findByRole('tab', { name: 'Visa' });
    fireEvent.click(visaTab);
    expect(visaTab).toHaveAttribute('aria-selected', 'true');

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('tab=visa'),
        expect.anything(),
      );
    });
    expect(await screen.findByText('No visa-friendly postings in the last 24h.')).toBeInTheDocument();
    const emptySection = screen.getByLabelText('No listings');
    expect(within(emptySection).getByText(/Last updated Sep 25, 2026/)).toBeInTheDocument();
  });

  it('switches tabs with the keyboard (arrow keys)', async () => {
    mockFetchSuccess(envelope([listing()]));
    render(<App />);

    const todayTab = await screen.findByRole('tab', { name: 'Today' });
    todayTab.focus();
    fireEvent.keyDown(todayTab, { key: 'ArrowRight' });
    await waitFor(() => {
      expect(screen.getByRole('tab', { name: 'C2C' })).toHaveAttribute('aria-selected', 'true');
    });
  });
});

describe('search', () => {
  it('searches server-side, shows N results, and clears', async () => {
    const fetchMock = mockFetchSuccess(envelope([listing()]));
    render(<App />);
    await screen.findByRole('heading', { name: 'Senior Java Developer' });

    const input = screen.getByLabelText('Search listings by title or company');
    fireEvent.change(input, { target: { value: 'acme' } });

    await waitFor(
      () => {
        expect(fetchMock).toHaveBeenCalledWith(
          expect.stringContaining('q=acme'),
          expect.anything(),
        );
      },
      { timeout: 2000 },
    );
    expect(await screen.findByText('1 result')).toBeInTheDocument();

    // Clear (×) resets the query.
    fireEvent.click(screen.getByRole('button', { name: 'Clear search' }));
    expect(input).toHaveValue('');
  });

  it('shows the empty-result message for a search with no matches', async () => {
    mockFetchSuccess(envelope([listing()]));
    render(<App />);
    await screen.findByRole('heading', { name: 'Senior Java Developer' });

    mockFetchSuccess(envelope([], { indexedToday: 0 }));
    const input = screen.getByLabelText('Search listings by title or company');
    fireEvent.change(input, { target: { value: 'zzz-no-match' } });

    expect(await screen.findByText("No matches for 'zzz-no-match' in this tab.")).toBeInTheDocument();
  });
});

describe('loading / error / offline states', () => {
  it('shows skeleton cards while loading', () => {
    mockFetchPending();
    render(<App />);
    expect(screen.getByRole('status', { name: 'Loading listings' })).toBeInTheDocument();
  });

  it('shows a generic error when the API fails with no cache', async () => {
    mockFetchFailure();
    render(<App />);
    expect(await screen.findByText('Something went wrong. Try again.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('retries after an error', async () => {
    mockFetchFailure();
    render(<App />);
    await screen.findByText('Something went wrong. Try again.');

    mockFetchSuccess(envelope([listing()]));
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    await screen.findByRole('heading', { name: 'Senior Java Developer' });
  });

  it('renders cached data with a banner when offline', async () => {
    writeCache('today', '', envelope([listing()]));
    mockFetchFailure();
    render(<App />);

    await screen.findByRole('heading', { name: 'Senior Java Developer' });
    expect(screen.getByText(/Showing cached data from/)).toBeInTheDocument();
  });
});

describe('fixed-clock timezone behavior', () => {
  it('uses America/Chicago for the footer, NEW badge, and age', async () => {
    vi.useFakeTimers();
    // 18:05 UTC = 1:05 PM CDT.
    vi.setSystemTime(new Date('2026-09-25T18:05:00Z'));

    // 17:30 UTC = 12:30 PM CDT — same Chicago day, 35 minutes ago.
    mockFetchSuccess(
      envelope([listing({ id: 'tz1', firstSeen: '2026-09-25T17:30:00Z' })], {
        lastPull: '2026-09-25T18:00:00Z',
      }),
    );
    render(<App />);
    // Advance past the 300ms search debounce so the fetch settles deterministically.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(500);
    });

    // Regex: the footer <p> also carries the tab source line after the datetime.
    expect(screen.getByText(/Last updated Sep 25, 2026, 1:00 PM CDT/)).toBeInTheDocument();
    expect(screen.getByText('NEW')).toBeInTheDocument();
    expect(screen.getByText(/First seen 35m ago/)).toBeInTheDocument();
    vi.useRealTimers();
  });
});
