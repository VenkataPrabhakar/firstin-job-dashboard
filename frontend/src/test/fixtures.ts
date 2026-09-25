import type { ListingDto, ListingsEnvelope } from '../types';

/** Base happy-path listing; overrides punch the holes each QA fixture needs. */
export function listing(overrides: Partial<ListingDto> = {}): ListingDto {
  return {
    id: 'abc123',
    title: 'Senior Java Developer',
    company: 'TechNova Solutions',
    location: 'Remote, USA',
    payMin: 70,
    payMax: 80,
    payUnit: 'hour',
    payHourlyEquiv: 75,
    engagement: 'C2C',
    engagementTags: [],
    visaStatus: 'open',
    visaReason: null,
    contact: { name: 'Jane Rao', email: 'jane.rao@example.com', phone: '(555) 010-2030' },
    sources: [{ source: 'LinkedIn', url: 'https://example.com/post/1', urlVerified: true }],
    firstSeen: '2026-09-25T17:30:00Z',
    postedMinutes: 63,
    postedMinutesConfidence: 'precise',
    ...overrides,
  };
}

export function envelope(
  listings: ListingDto[],
  overrides: Partial<ListingsEnvelope> = {},
): ListingsEnvelope {
  return {
    listings,
    indexedToday: listings.length,
    lastPull: '2026-09-25T18:00:00Z',
    ...overrides,
  };
}

/** QA fixture: hostile HTML must render as inert text — no injection. */
export const hostileListing = listing({
  id: 'evil1',
  title: `<img src=x onerror="alert('xss')">Senior <b>Java</b> Dev`,
  company: `Evil <script>alert(1)</script> Corp`,
  contact: { name: `<svg onload="alert(1)">Hacker`, email: 'evil@example.com', phone: '123' },
  sources: [{ source: 'LinkedIn', url: `javascript:alert('pwned')`, urlVerified: false }],
});

/** QA fixture: nearly every field missing — no blanks, no "undefined". */
export const sparseListing = listing({
  id: 'sparse1',
  title: null,
  company: null,
  location: null,
  payMin: null,
  payMax: null,
  payUnit: null,
  payHourlyEquiv: null,
  engagement: null,
  engagementTags: [],
  visaStatus: null,
  visaReason: null,
  contact: null,
  sources: [],
  firstSeen: null,
  postedMinutes: null,
  postedMinutesConfidence: null,
});
