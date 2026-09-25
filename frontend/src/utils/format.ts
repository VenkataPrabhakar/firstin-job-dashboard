import type { ListingDto } from '../types';

/** Owner's timezone — the truthful anchor for "today" and all datetimes. */
export const CHICAGO_TZ = 'America/Chicago';

const lastUpdatedFmt = new Intl.DateTimeFormat('en-US', {
  timeZone: CHICAGO_TZ,
  month: 'short',
  day: 'numeric',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  timeZoneName: 'short',
});

const dayFmt = new Intl.DateTimeFormat('en-CA', {
  timeZone: CHICAGO_TZ,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** "Last updated Sep 25, 2026, 1:24 PM CDT" — explicit when no pull has happened. */
export function formatLastUpdated(lastPull: string | null): string {
  if (!lastPull) {
    return 'Last updated — pending first pull';
  }
  const d = new Date(lastPull);
  if (Number.isNaN(d.getTime())) {
    return 'Last updated — pending first pull';
  }
  return `Last updated ${lastUpdatedFmt.format(d)}`;
}

/**
 * Honest age from first_seen. Unknown/unparseable age returns null and the
 * caller omits the age segment entirely (never "just now" — UI-UX.md §4).
 */
export function formatAge(firstSeen: string | null, now: Date = new Date()): string | null {
  if (!firstSeen) {
    return null;
  }
  const seen = new Date(firstSeen);
  if (Number.isNaN(seen.getTime())) {
    return null;
  }
  const minutes = Math.max(0, Math.floor((now.getTime() - seen.getTime()) / 60000));
  if (minutes < 60) {
    return `${minutes}m ago`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours}h ago`;
  }
  return `${Math.floor(hours / 24)}d ago`;
}

/** NEW badge: firstSeen falls on today in America/Chicago. */
export function isNewToday(firstSeen: string | null, now: Date = new Date()): boolean {
  if (!firstSeen) {
    return false;
  }
  const seen = new Date(firstSeen);
  if (Number.isNaN(seen.getTime())) {
    return false;
  }
  return dayFmt.format(seen) === dayFmt.format(now);
}

/** "Showing cached data from Sep 25, 1:24 PM CDT" (shorter than the footer line). */
export function formatCachedTime(savedAt: string): string {
  const d = new Date(savedAt);
  if (Number.isNaN(d.getTime())) {
    return 'an earlier visit';
  }
  return lastUpdatedFmt.format(d);
}

function trimNum(n: number): string {
  return Number.isInteger(n) ? String(n) : String(Math.round(n * 100) / 100);
}

/** $145600 -> "$146k". */
function kFormatYearly(n: number): string {
  return `$${Math.round(n / 1000)}k`;
}

/**
 * "$70–$80/hr (≈ $146k–$166k/yr)"; missing pay -> "Pay not listed".
 * Hourly equivalent: yearly ÷ 2080 (Phase 1 contract); yearly range from
 * hourly min/max when the unit is hourly.
 */
export function formatPay(l: ListingDto): string {
  const { payMin, payMax, payUnit } = l;
  if (payMin == null && payMax == null) {
    return 'Pay not listed';
  }
  const unit = payUnit === 'year' ? '/yr' : '/hr';
  const money = (n: number): string => `$${trimNum(n)}`;
  let range: string;
  if (payMin != null && payMax != null) {
    range = payMin === payMax ? `${money(payMin)}${unit}` : `${money(payMin)}–${money(payMax)}${unit}`;
  } else if (payMin != null) {
    range = `${money(payMin)}+${unit}`;
  } else {
    range = `up to ${money(payMax as number)}${unit}`;
  }

  let equiv: string | null = null;
  if (payUnit === 'hour' || (payUnit == null && payMin != null)) {
    const lo = (payMin ?? payMax) as number;
    const hi = (payMax ?? payMin) as number;
    equiv = `${kFormatYearly(lo * 2080)}–${kFormatYearly(hi * 2080)}/yr`;
    if (lo === hi) {
      equiv = `${kFormatYearly(lo * 2080)}/yr`;
    }
  } else if (payUnit === 'year' && l.payHourlyEquiv != null) {
    equiv = `$${trimNum(l.payHourlyEquiv)}/hr`;
  }
  return equiv ? `${range} (≈ ${equiv})` : range;
}

/** Canonical engagement -> short badge label. */
export function engagementLabel(engagement: string | null): string {
  switch (engagement) {
    case 'C2C':
      return 'C2C';
    case 'W2':
      return 'W2';
    case 'FULLTIME':
      return 'FT';
    default:
      return engagement && engagement.trim().length > 0 ? engagement : 'Listing';
  }
}

/**
 * Visa shield text (always carries a text label — UI-UX.md §1.6).
 * Returns null when there is no visa info to show.
 */
export function visaShield(status: string | null): string | null {
  switch (status) {
    case 'confirmed':
      return 'Visa: confirmed';
    case 'open':
      return 'Visa: open';
    case 'unknown':
      return 'Visa: unknown — ask';
    default:
      return null;
  }
}

/** "Senior Java Developer" — never blank (UI-UX.md §4). */
export function displayTitle(title: string | null): string {
  return title && title.trim().length > 0 ? title : 'Untitled posting';
}

/**
 * Allow-list for source URLs before they become <a href>s. React escapes
 * attribute values but does not stop `javascript:` URLs — only http(s)
 * links are rendered as links; anything else renders as plain text.
 */
export function safeHttpUrl(url: string | null): string | null {
  if (!url) {
    return null;
  }
  const trimmed = url.trim();
  return /^https?:\/\//i.test(trimmed) ? trimmed : null;
}
