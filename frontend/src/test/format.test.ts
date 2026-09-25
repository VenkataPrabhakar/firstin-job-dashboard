import { describe, expect, it } from 'vitest';
import {
  displayTitle,
  engagementLabel,
  formatAge,
  formatLastUpdated,
  formatPay,
  isNewToday,
  safeHttpUrl,
  visaShield,
} from '../utils/format';
import { listing } from './fixtures';

describe('formatLastUpdated', () => {
  it('renders an explicit Chicago datetime', () => {
    // 18:24 UTC = 1:24 PM CDT (America/Chicago, late September).
    expect(formatLastUpdated('2026-09-25T18:24:00Z')).toBe(
      'Last updated Sep 25, 2026, 1:24 PM CDT',
    );
  });

  it('is explicit when the pull has never happened', () => {
    expect(formatLastUpdated(null)).toBe('Last updated — pending first pull');
    expect(formatLastUpdated('not-a-date')).toBe('Last updated — pending first pull');
  });
});

describe('formatAge', () => {
  const now = new Date('2026-09-25T18:00:00Z');

  it('renders minutes, hours, days', () => {
    expect(formatAge('2026-09-25T17:30:00Z', now)).toBe('30m ago');
    expect(formatAge('2026-09-25T15:00:00Z', now)).toBe('3h ago');
    expect(formatAge('2026-09-23T18:00:00Z', now)).toBe('2d ago');
  });

  it('returns null for unknown age — the caller omits the segment', () => {
    expect(formatAge(null, now)).toBeNull();
    expect(formatAge('garbage', now)).toBeNull();
  });
});

describe('isNewToday', () => {
  // Fixed clock: 2026-09-25 18:00 UTC = 1:00 PM CDT.
  const now = new Date('2026-09-25T18:00:00Z');

  it('is true for a firstSeen earlier today in Chicago', () => {
    // 05:30 UTC = 12:30 AM CDT, same Chicago day.
    expect(isNewToday('2026-09-25T05:30:00Z', now)).toBe(true);
  });

  it('is false for a firstSeen on the previous Chicago day', () => {
    // 04:30 UTC = 11:30 PM CDT on Sep 24.
    expect(isNewToday('2026-09-25T04:30:00Z', now)).toBe(false);
  });

  it('is false for unknown firstSeen', () => {
    expect(isNewToday(null, now)).toBe(false);
  });
});

describe('formatPay', () => {
  it('renders an hourly range with a yearly equivalent', () => {
    expect(formatPay(listing({ payMin: 70, payMax: 80, payUnit: 'hour' }))).toBe(
      '$70–$80/hr (≈ $146k–$166k/yr)',
    );
  });

  it('renders yearly pay with an hourly equivalent', () => {
    expect(
      formatPay(listing({ payMin: 120000, payMax: 150000, payUnit: 'year', payHourlyEquiv: 57.69 })),
    ).toBe('$120000–$150000/yr (≈ $57.69/hr)');
  });

  it('renders open-ended ranges honestly', () => {
    expect(formatPay(listing({ payMin: 70, payMax: null, payUnit: 'hour' }))).toBe(
      '$70+/hr (≈ $146k/yr)',
    );
  });

  it('renders "Pay not listed" when pay is missing', () => {
    expect(formatPay(listing({ payMin: null, payMax: null }))).toBe('Pay not listed');
  });
});

describe('engagementLabel', () => {
  it('maps canonical values to short labels', () => {
    expect(engagementLabel('C2C')).toBe('C2C');
    expect(engagementLabel('W2')).toBe('W2');
    expect(engagementLabel('FULLTIME')).toBe('FT');
  });

  it('never renders blank', () => {
    expect(engagementLabel(null)).toBe('Listing');
  });
});

describe('visaShield', () => {
  it('maps wire values to labeled shields', () => {
    expect(visaShield('confirmed')).toBe('Visa: confirmed');
    expect(visaShield('open')).toBe('Visa: open');
    expect(visaShield('unknown')).toBe('Visa: unknown — ask');
  });

  it('returns null when there is no visa info', () => {
    expect(visaShield(null)).toBeNull();
    expect(visaShield('bogus')).toBeNull();
  });
});

describe('displayTitle', () => {
  it('falls back instead of rendering blank', () => {
    expect(displayTitle(null)).toBe('Untitled posting');
    expect(displayTitle('   ')).toBe('Untitled posting');
    expect(displayTitle('Real Title')).toBe('Real Title');
  });
});

describe('safeHttpUrl', () => {
  it('allows http(s) links', () => {
    expect(safeHttpUrl('https://example.com/post')).toBe('https://example.com/post');
    expect(safeHttpUrl('HTTP://example.com')).toBe('HTTP://example.com');
  });

  it('rejects javascript: and other schemes — they render as text', () => {
    expect(safeHttpUrl(`javascript:alert('pwned')`)).toBeNull();
    expect(safeHttpUrl('data:text/html,<h1>x</h1>')).toBeNull();
    expect(safeHttpUrl(null)).toBeNull();
  });
});
