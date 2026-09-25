/**
 * API types mirroring the Phase 1 listing envelope
 * (backend/.../web/ListingEnvelope.java). Fields the API omits serialize as
 * absent, so everything except the envelope shell is nullable/defensive.
 */

export type TabKey = 'today' | 'c2c' | 'w2' | 'fulltime' | 'visa';

export interface ContactDto {
  name: string | null;
  email: string | null;
  phone: string | null;
}

export interface SourceDto {
  source: string;
  url: string | null;
  urlVerified: boolean | null;
}

export interface ListingDto {
  id: string;
  title: string | null;
  company: string | null;
  location: string | null;
  payMin: number | null;
  payMax: number | null;
  /** Wire values: "hour" | "year" (backend PayParser). */
  payUnit: string | null;
  payHourlyEquiv: number | null;
  /** Wire values: "C2C" | "W2" | "FULLTIME". */
  engagement: string | null;
  engagementTags: string[];
  /** Wire values: "confirmed" | "open" | "unknown" (restricted never stored). */
  visaStatus: string | null;
  visaReason: string | null;
  /** Null when no source row carries contact info. */
  contact: ContactDto | null;
  sources: SourceDto[];
  /** UTC instant; never null per backend, but guarded anyway. */
  firstSeen: string | null;
  postedMinutes: number | null;
  postedMinutesConfidence: string | null;
}

export interface ListingsEnvelope {
  listings: ListingDto[];
  indexedToday: number;
  /** Explicit null = "no sightings yet" (Phase 1 contract). */
  lastPull: string | null;
}

export const TABS: { key: TabKey; label: string }[] = [
  { key: 'today', label: 'Today' },
  { key: 'c2c', label: 'C2C' },
  { key: 'w2', label: 'W2' },
  { key: 'fulltime', label: 'Full-Time' },
  { key: 'visa', label: 'Visa' },
];

/** Tab source lines per docs/UI-UX.md §3.2 / §3.3. */
export const TAB_SOURCE_LINES: Record<TabKey, string> = {
  today:
    'Aggregated from morning agent sweeps · LinkedIn posts, Google, Dice, Indeed, ZipRecruiter, recruiter emails.',
  c2c: 'Aggregated from morning agent sweeps · LinkedIn posts, Google, Dice, Indeed, ZipRecruiter, recruiter emails.',
  w2: 'Aggregated from morning agent sweeps · LinkedIn posts, Google, Dice, Indeed, ZipRecruiter, recruiter emails.',
  fulltime:
    'Aggregated from morning agent sweeps · LinkedIn posts, Google, Dice, Indeed, ZipRecruiter, recruiter emails.',
  visa: 'Visa-friendly signals from morning agent sweeps · sponsorship keywords verified per posting.',
};
