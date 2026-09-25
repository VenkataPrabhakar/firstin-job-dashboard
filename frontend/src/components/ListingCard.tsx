import { useState } from 'react';
import type { ListingDto } from '../types';
import {
  displayTitle,
  engagementLabel,
  formatAge,
  formatPay,
  isNewToday,
  safeHttpUrl,
  visaShield,
} from '../utils/format';

interface ListingCardProps {
  listing: ListingDto;
}

/**
 * Listing card (docs/UI-UX.md §4). Recruiter info is always visible —
 * never behind a click. Collapsed by default; the details button reveals
 * the merged per-source list.
 *
 * Hostile HTML: React escapes text and attribute values by default; URLs
 * are allow-listed to http(s) before becoming links (see safeHttpUrl).
 */
export function ListingCard({ listing }: ListingCardProps) {
  const [expanded, setExpanded] = useState(false);

  const title = displayTitle(listing.title);
  const companyLine = [listing.company, listing.location]
    .map((s) => (s ?? '').trim())
    .filter((s) => s.length > 0)
    .join(' · ');
  const shield = visaShield(listing.visaStatus);
  const age = formatAge(listing.firstSeen);
  const isNew = isNewToday(listing.firstSeen);
  const contact = listing.contact;
  const contactName = contact?.name?.trim();
  const sources = listing.sources ?? [];
  const sourceNames = sources.map((s) => s.source).filter((s) => s && s.trim().length > 0);
  const detailsId = `details-${listing.id}`;

  const telHref = contact?.phone ? `tel:${contact.phone.replace(/[^+\d().\- ]/g, '')}` : null;

  return (
    <article className="card" aria-labelledby={`title-${listing.id}`}>
      <div className="card-head">
        <h3 className="card-title" id={`title-${listing.id}`}>
          {title}
        </h3>
        {isNew && (
          <span className="badge badge-new" title="First seen today">
            NEW
          </span>
        )}
      </div>

      {companyLine.length > 0 && <p className="card-sub">{companyLine}</p>}
      <p className="card-pay">{formatPay(listing)}</p>

      <div className="badges" aria-label="Posting badges">
        <span className="badge">{engagementLabel(listing.engagement)}</span>
        {shield && <span className="badge badge-visa">🛡 {shield}</span>}
      </div>

      <div className="recruiter">
        <span className="recruiter-row">
          <span className="recruiter-label">Recruiter:</span>{' '}
          {contactName && contactName.length > 0 ? contactName : 'Not provided'}
        </span>
        {contact?.email && contact.email.trim().length > 0 && (
          <a className="recruiter-link" href={`mailto:${contact.email.trim()}`}>
            {contact.email.trim()}
          </a>
        )}
        {telHref && contact?.phone && contact.phone.trim().length > 0 && (
          <a className="recruiter-link" href={telHref}>
            {contact.phone.trim()}
          </a>
        )}
        {(!contact?.email || contact.email.trim().length === 0) &&
          (!contact?.phone || contact.phone.trim().length === 0) && (
            <span className="recruiter-missing">Contact details not provided</span>
          )}
      </div>

      <p className="card-meta">
        {age && (
          <>
            First seen {age}
            {sourceNames.length > 0 && ' · '}
          </>
        )}
        {sourceNames.length > 0 && <>Sources: {sourceNames.join(', ')}</>}
      </p>

      <button
        type="button"
        className="details-toggle"
        aria-expanded={expanded}
        aria-controls={detailsId}
        onClick={() => setExpanded((v) => !v)}
      >
        {expanded ? 'Hide details' : 'Show details'}
      </button>

      {expanded && (
        <div className="card-details" id={detailsId}>
          {sources.length === 0 ? (
            <p className="sources-empty">No source links recorded.</p>
          ) : (
            <ul className="sources">
              {sources.map((s, i) => {
                const href = safeHttpUrl(s.url);
                return (
                  <li key={`${s.source}-${i}`} className="source-row">
                    {href ? (
                      <a href={href} target="_blank" rel="noopener noreferrer">
                        View post
                      </a>
                    ) : (
                      <span>View post</span>
                    )}
                    <span className="source-name">{s.source || 'Unknown source'}</span>
                    {s.urlVerified && <span className="verified">✓ verified</span>}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </article>
  );
}
