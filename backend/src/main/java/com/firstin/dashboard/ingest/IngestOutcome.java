package com.firstin.dashboard.ingest;

/**
 * What happened to one consumed record. Returned by
 * {@link IngestionService#ingest(LeadEvent, com.firstin.dashboard.model.Engagement)}
 * so the pipeline run recorder can count outcomes per run.
 */
public enum IngestOutcome {
    /** A new posting was persisted. */
    STORED,
    /** Re-seen posting: sources merged, nothing new stored. */
    DUPLICATE,
    /** Policy rejection (unreported, restricted sponsorship): counted, not stored. */
    REJECTED,
    /** Poison record (bad JSON / validation / unknown engagement): sent to the DLQ. */
    DLQ
}
