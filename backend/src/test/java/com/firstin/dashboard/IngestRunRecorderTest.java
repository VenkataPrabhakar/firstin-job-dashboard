package com.firstin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstin.dashboard.ingest.IngestRunRecorder;
import com.firstin.dashboard.ingest.IngestionService;
import com.firstin.dashboard.ingest.LeadEvent;
import com.firstin.dashboard.model.IngestRun;
import com.firstin.dashboard.model.RunStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * QA: the run id is the primary key, so the same run id twice yields one row;
 * counters are consumer-observed — {@code published} counts every record the
 * consumer received (a replayed record counts again), {@code stored} counts
 * distinct postings (stable posting ids make a replayed record a DUPLICATE,
 * never a second STORED); records without the header never touch the run
 * table.
 */
class IngestRunRecorderTest extends AbstractIntegrationTest {

    @Autowired
    private IngestionService ingestion;

    @Autowired
    private IngestRunRecorder recorder;

    @Autowired
    private ObjectMapper objectMapper;

    private byte[] toJson(LeadEvent e) {
        try {
            return objectMapper.writeValueAsBytes(e);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void sameRunIdTwiceIsOneRowWithCorrectCounters() {
        String runId = "2026-09-26";
        LeadEvent a = TestFixtures.lead("Java Dev", "Acme", "Austin, TX", "C2C");
        LeadEvent b = TestFixtures.lead("Python Dev", "Acme", "Austin, TX", "W2");

        ingestion.ingestRaw(toJson(a), "job-leads.raw", runId);
        ingestion.ingestRaw(toJson(b), "job-leads.raw", runId);
        // Full replay of the same run: idempotent by stable id.
        ingestion.ingestRaw(toJson(a), "job-leads.raw", runId);
        ingestion.ingestRaw(toJson(b), "job-leads.raw", runId);

        List<IngestRun> rows = runs.findAll();
        assertThat(rows).hasSize(1);
        IngestRun run = rows.get(0);
        assertThat(run.getRunId()).isEqualTo(runId);
        assertThat(run.getStatus()).isEqualTo(RunStatus.RUNNING);
        assertThat(run.getStartedAt()).isNotNull();
        assertThat(run.getCompletedAt()).isNotNull();
        assertThat(run.getPublished()).isEqualTo(4);
        assertThat(run.getStored()).isEqualTo(2); // replay stored nothing new
        assertThat(run.getDlq()).isZero();
        assertThat(run.getRejected()).isZero();
        assertThat(postings.count()).isEqualTo(2);
    }

    @Test
    void poisonAndRejectedRecordsAreCountedPerRun() {
        String runId = "2026-09-27";

        ingestion.ingestRaw("not json".getBytes(StandardCharsets.UTF_8), "job-leads.raw", runId);

        LeadEvent unreported = TestFixtures.lead("Java Dev", "Acme", "Austin, TX", "C2C");
        unreported.setReported(false);
        ingestion.ingestRaw(toJson(unreported), "job-leads.raw", runId);

        LeadEvent restricted = TestFixtures.lead("Java Dev", "Acme", "Austin, TX", "C2C");
        restricted.setNote("US citizens only, no sponsorship.");
        ingestion.ingestRaw(toJson(restricted), "job-leads.raw", runId);

        IngestRun run = runs.findById(runId).orElseThrow();
        assertThat(run.getPublished()).isEqualTo(3);
        assertThat(run.getDlq()).isEqualTo(1);
        assertThat(run.getRejected()).isEqualTo(2);
        assertThat(run.getStored()).isZero();
        assertThat(postings.count()).isZero();
    }

    @Test
    void recordsWithoutRunIdNeverTouchTheRunTable() {
        ingestion.ingestRaw(toJson(TestFixtures.lead("Java Dev", "Acme", "Austin, TX", "C2C")),
                "job-leads.raw");

        assertThat(postings.count()).isEqualTo(1);
        assertThat(runs.count()).isZero();
    }

    @Test
    void completeRunFlipsStatusAndSetsCompletedAt() {
        String runId = "2026-09-28";
        ingestion.ingestRaw(toJson(TestFixtures.lead("Java Dev", "Acme", "Austin, TX", "C2C")),
                "job-leads.raw", runId);

        recorder.completeRun(runId);

        IngestRun run = runs.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.getCompletedAt()).isNotNull();
        assertThat(run.getPublished()).isEqualTo(1);
        assertThat(run.getStored()).isEqualTo(1);
    }

    @Test
    void completeRunOnAnEmptyRunCreatesTheRow() {
        recorder.completeRun("2026-09-29");

        IngestRun run = runs.findById("2026-09-29").orElseThrow();
        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.getPublished()).isZero();
    }
}
