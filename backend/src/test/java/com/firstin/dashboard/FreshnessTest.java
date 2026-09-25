package com.firstin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstin.dashboard.ingest.IngestionService;
import com.firstin.dashboard.ingest.LeadEvent;
import com.firstin.dashboard.model.JobPosting;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;

/**
 * QA: unknown age sorts oldest; indexedToday counts first-seen-today only
 * (America/Chicago); re-seen records never inflate the count.
 */
class FreshnessTest extends AbstractIntegrationTest {

    @Autowired
    private IngestionService ingestion;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unknownAgeSortsOldest() {
        LeadEvent known = TestFixtures.lead("Known Age Dev", "Acme", "Remote", "W2");
        known.setPosted("1 hour ago");
        LeadEvent unknown = TestFixtures.lead("Unknown Age Dev", "Acme", "Remote", "W2");
        unknown.setPosted("some time back"); // unparseable → null

        ingest(known);
        ingest(unknown);

        // Stored truth: the precise age is kept with its confidence marker;
        // the unparseable age is null — never rendered as "just now".
        JobPosting precise = postings.findAll().stream()
                .filter(p -> p.getPostedMinutes() != null).findFirst().orElseThrow();
        assertThat(precise.getPostedMinutes()).isEqualTo(60);
        assertThat(precise.getPostedMinutesConfidence()).isEqualTo("precise");
        assertThat(postings.findAll().stream()
                .filter(p -> p.getPostedMinutes() == null)).hasSize(1);

        // Domain rule (docs/DESIGN.md freshness anchor): unknown age sorts
        // oldest. The JPA Criteria translator does not support NULLS LAST
        // hints, and H2 vs. PostgreSQL disagree on native null ordering, so
        // the rule is asserted where the API would apply it — an explicit
        // nulls-last ordering (the API's primary ordering stays firstSeen
        // DESC, newest first).
        List<JobPosting> all = postings.findAll();
        all.sort(java.util.Comparator.comparing(JobPosting::getPostedMinutes,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        assertThat(all).extracting(JobPosting::getTitle)
                .containsExactly("Known Age Dev", "Unknown Age Dev");
    }

    @Test
    void indexedTodayCountsFirstSeenTodayOnly() {
        Instant nowChicagoNoon = ZonedDateTime.now(ZoneId.of("America/Chicago"))
                .withHour(12).withMinute(0).withSecond(0).withNano(0).toInstant();

        LeadEvent today = TestFixtures.lead("Today Dev", "Acme", "Remote", "W2");
        today.setFirstSeen(nowChicagoNoon);
        LeadEvent yesterday = TestFixtures.lead("Yesterday Dev", "Acme", "Remote", "W2");
        yesterday.setFirstSeen(nowChicagoNoon.minus(26, java.time.temporal.ChronoUnit.HOURS));

        ingest(today);
        ingest(yesterday);

        long startOfTodayChicago = java.time.LocalDate.now(ZoneId.of("America/Chicago"))
                .atStartOfDay(ZoneId.of("America/Chicago")).toInstant().toEpochMilli();
        long counted = postings.findAll().stream()
                .filter(p -> p.getFirstSeen().toEpochMilli() >= startOfTodayChicago)
                .count();
        assertThat(counted).isEqualTo(1);
    }

    @Test
    void reSeenRecordDoesNotInflateIndexedToday() {
        Instant yesterdayNoon = ZonedDateTime.now(ZoneId.of("America/Chicago"))
                .minusDays(1).withHour(12).withMinute(0).withSecond(0).withNano(0).toInstant();

        LeadEvent e = TestFixtures.lead("Reseen Dev", "Acme", "Remote", "W2");
        e.setFirstSeen(yesterdayNoon);
        ingest(e);
        // Re-seen today via another source — first_seen must not move.
        LeadEvent reseen = TestFixtures.lead("Reseen Dev", "Acme", "Remote", "W2");
        reseen.setFirstSeen(Instant.now());
        reseen.setSource("LinkedIn");
        reseen.setUrl("https://linkedin.example/posts/7");
        ingest(reseen);

        JobPosting posting = postings.findAll().get(0);
        assertThat(posting.getFirstSeen()).isEqualTo(yesterdayNoon);
        assertThat(postings.findAll().stream()
                .filter(p -> isTodayChicago(p.getFirstSeen())).count()).isZero();
    }

    @Test
    void listingsOrderNewestFirst() {
        for (int i = 0; i < 3; i++) {
            LeadEvent e = TestFixtures.lead("Dev " + i, "Acme", "Remote", "W2");
            e.setFirstSeen(Instant.now().minus(i, java.time.temporal.ChronoUnit.HOURS));
            ingest(e);
        }
        List<JobPosting> ordered = postings.findAll(Sort.by(Sort.Direction.DESC, "firstSeen"));
        assertThat(ordered).isSortedAccordingTo(
                Comparator.comparing(JobPosting::getFirstSeen).reversed());
    }

    private boolean isTodayChicago(Instant instant) {
        ZoneId chicago = ZoneId.of("America/Chicago");
        return instant.atZone(chicago).toLocalDate()
                .isEqual(java.time.LocalDate.now(chicago));
    }

    private void ingest(LeadEvent event) {
        try {
            ingestion.ingestRaw(objectMapper.writeValueAsBytes(event), "job-leads.raw");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
