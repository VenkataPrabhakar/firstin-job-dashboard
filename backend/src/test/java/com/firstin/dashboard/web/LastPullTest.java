package com.firstin.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.firstin.dashboard.AbstractIntegrationTest;
import com.firstin.dashboard.TestFixtures;
import com.firstin.dashboard.ingest.IngestOutcome;
import com.firstin.dashboard.ingest.IngestRunRecorder;
import com.firstin.dashboard.ingest.IngestionService;
import com.firstin.dashboard.model.Engagement;
import com.firstin.dashboard.model.IngestRun;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * QA: lastPull is run history — the latest COMPLETED run's completedAt —
 * falling back to max(first_seen) when no run has completed, null when the
 * DB is empty. RUNNING rows never count.
 */
class LastPullTest extends AbstractIntegrationTest {

    @Autowired
    private ListingController controller;

    @Autowired
    private IngestionService ingestion;

    @Autowired
    private IngestRunRecorder recorder;

    /** Complete a run, then pin its completedAt to a deterministic instant. */
    private void backdateCompleted(String runId, String completedAt) {
        recorder.completeRun(runId);
        IngestRun run = runs.findById(runId).orElseThrow();
        run.setCompletedAt(Instant.parse(completedAt));
        runs.save(run);
    }

    @Test
    void returnsLatestCompletedRun() {
        backdateCompleted("2026-09-20", "2026-09-20T08:05:00Z");
        backdateCompleted("2026-09-21", "2026-09-21T08:05:00Z");

        assertThat(controller.lastPull()).isEqualTo(Instant.parse("2026-09-21T08:05:00Z"));
    }

    @Test
    void prefersLatestCompletedRunOverNewerFirstSeen() {
        // A completed run from the pipeline, backdated before the posting below.
        backdateCompleted("2026-09-20", "2026-09-20T08:05:00Z");

        // ...then a newer posting arrives outside any run (backfill/manual).
        ingestion.ingest(TestFixtures.lead("Java Dev", "Acme", "Austin, TX", "C2C"),
                Engagement.C2C);

        // lastPull is run history: the older completed run still wins over the
        // newer first_seen (2026-09-25T08:05:00Z in the fixture).
        assertThat(controller.lastPull()).isEqualTo(Instant.parse("2026-09-20T08:05:00Z"));
    }

    @Test
    void fallsBackToMaxFirstSeenWithNoRuns() {
        ingestion.ingest(TestFixtures.lead("Java Dev", "Acme", "Austin, TX", "C2C"),
                Engagement.C2C);
        ingestion.ingest(TestFixtures.lead("Python Dev", "Acme", "Austin, TX", "W2"),
                Engagement.W2);

        // The fixture stamps first_seen 2026-09-25T08:05:00Z on both.
        assertThat(controller.lastPull()).isEqualTo(Instant.parse("2026-09-25T08:05:00Z"));
    }

    @Test
    void runningRunsDoNotCount() {
        ingestion.ingest(TestFixtures.lead("Java Dev", "Acme", "Austin, TX", "C2C"),
                Engagement.C2C);
        // A run that started (RUNNING) but never completed is ignored.
        recorder.record("2026-09-22", IngestOutcome.DUPLICATE);

        assertThat(runs.findById("2026-09-22")).isPresent();
        assertThat(controller.lastPull()).isEqualTo(Instant.parse("2026-09-25T08:05:00Z"));
    }

    @Test
    void nullWhenNoRunsAndNoPostings() {
        assertThat(controller.lastPull()).isNull();
    }
}
