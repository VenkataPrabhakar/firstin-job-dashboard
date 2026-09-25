package com.firstin.dashboard.ingest;

import com.firstin.dashboard.model.IngestRun;
import com.firstin.dashboard.model.RunStatus;
import com.firstin.dashboard.repo.IngestRunRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-run accounting for the daily pipeline. The pipeline stamps every record
 * of a run with the same {@code run_id} Kafka header; this recorder upserts
 * the {@link IngestRun} row (the run id is the primary key, so replays are
 * safe) and counts consumer-observed outcomes.
 *
 * <p>Counter semantics: {@code published} counts every record the consumer
 * received carrying the run id — a redelivered record counts again;
 * {@code stored} counts distinct postings persisted (stable posting ids make
 * a replay a duplicate, never a second stored row).
 */
@Service
public class IngestRunRecorder {

    private final IngestRunRepository runs;

    public IngestRunRecorder(IngestRunRepository runs) {
        this.runs = runs;
    }

    /** Count one consumed record's outcome against its run. Creates the row on first sight. */
    @Transactional
    public void record(String runId, IngestOutcome outcome) {
        IngestRun run = runs.findById(runId).orElseGet(() -> runs.save(new IngestRun(runId)));
        run.setPublished(run.getPublished() + 1);
        switch (outcome) {
            case STORED -> run.setStored(run.getStored() + 1);
            case DUPLICATE -> { /* published already counted; stored stays stable */ }
            case REJECTED -> run.setRejected(run.getRejected() + 1);
            case DLQ -> run.setDlq(run.getDlq() + 1);
        }
        run.setCompletedAt(Instant.now());
        runs.save(run);
    }

    /**
     * Mark a run complete. Handles the pipeline's run-complete control record;
     * also creates the row when a run completes with zero data records.
     */
    @Transactional
    public void completeRun(String runId) {
        IngestRun run = runs.findById(runId).orElseGet(() -> runs.save(new IngestRun(runId)));
        run.setStatus(RunStatus.COMPLETED);
        run.setCompletedAt(Instant.now());
        runs.save(run);
    }
}
