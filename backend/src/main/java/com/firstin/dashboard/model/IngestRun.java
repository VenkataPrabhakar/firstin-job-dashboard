package com.firstin.dashboard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One row per daily ingest run. The daily pipeline publishes every record of a
 * run with the same {@code run_id} Kafka header; the consumer upserts this row
 * idempotently (the run id is the primary key) and counts outcomes per run.
 *
 * <p>Counters are consumer-observed: {@code published} = records the consumer
 * received carrying this run id; {@code stored} = new postings created;
 * {@code dlq} = poison records; {@code rejected} = policy rejections.
 * Duplicates are derivable: {@code published - stored - dlq - rejected}.
 *
 * <p>{@code completedAt} is refreshed on every record (last activity) and the
 * row flips to {@code COMPLETED} when the pipeline's run-complete control
 * record arrives. {@code lastPull} reads the latest {@code COMPLETED} row.
 *
 * <p>Only plain column types — no Postgres-only DDL — so H2-based CI tests
 * stay honest with the Supabase production schema (see docs/RENDER.md for the
 * table DDL; prod runs with {@code ddl-auto: validate}).
 */
@Entity
@Table(name = "ingest_runs")
public class IngestRun {

    @Id
    @Column(name = "run_id", length = 64, nullable = false, updatable = false)
    private String runId;

    /** First record observed for this run. Never rewritten. */
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    /** Last activity for this run; the completion instant once COMPLETED. */
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "published", nullable = false)
    private long published;

    @Column(name = "stored", nullable = false)
    private long stored;

    @Column(name = "dlq", nullable = false)
    private long dlq;

    @Column(name = "rejected", nullable = false)
    private long rejected;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private RunStatus status = RunStatus.RUNNING;

    protected IngestRun() {
        // JPA
    }

    public IngestRun(String runId) {
        this.runId = runId;
        Instant now = Instant.now();
        this.startedAt = now;
        this.completedAt = now;
    }

    public String getRunId() { return runId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public long getPublished() { return published; }
    public void setPublished(long published) { this.published = published; }
    public long getStored() { return stored; }
    public void setStored(long stored) { this.stored = stored; }
    public long getDlq() { return dlq; }
    public void setDlq(long dlq) { this.dlq = dlq; }
    public long getRejected() { return rejected; }
    public void setRejected(long rejected) { this.rejected = rejected; }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
}
