package com.firstin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstin.dashboard.ingest.IngestionService;
import com.firstin.dashboard.ingest.LeadEvent;
import com.firstin.dashboard.ingest.TextNormalizer;
import com.firstin.dashboard.model.Engagement;
import com.firstin.dashboard.model.JobPosting;
import com.firstin.dashboard.model.JobSource;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * QA: only reported:true stored; cross-source duplicates collapse with merged
 * contacts; stable id deterministic; re-seen records keep first_seen;
 * restricted-visa records excluded; replay is idempotent.
 */
class IngestionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private IngestionService ingestion;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void reportedFalseIsNeverStored() {
        LeadEvent e = TestFixtures.lead("Sr. Java Developer", "Keylent", "Reading, PA", "C2C");
        e.setReported(false);

        long rejectedBefore = ingestion.rejectedCount();
        ingestion.ingestRaw(toJson(e), "job-leads.raw");

        assertThat(postings.count()).isZero();
        assertThat(ingestion.rejectedCount()).isEqualTo(rejectedBefore + 1);
    }

    @Test
    void restrictedVisaIsExcluded() {
        LeadEvent e = TestFixtures.lead("Java Developer", "Initech", "Austin, TX", "W2");
        e.setVisa(TestFixtures.visa("restricted", "matched phrase: \"no sponsorship\""));

        long rejectedBefore = ingestion.rejectedCount();
        ingestion.ingestRaw(toJson(e), "job-leads.raw");

        assertThat(postings.count()).isZero();
        assertThat(ingestion.rejectedCount()).isEqualTo(rejectedBefore + 1);
    }

    @Test
    void restrictedVisaDetectedFromNoteKeywords() {
        LeadEvent e = TestFixtures.lead("Java Developer", "Initech", "Austin, TX", "W2");
        e.setNote("US citizens only, no sponsorship available.");

        ingestion.ingestRaw(toJson(e), "job-leads.raw");

        assertThat(postings.count()).isZero();
    }

    @Test
    void crossSourceDuplicatesCollapseAndMergeContacts() {
        LeadEvent dice = TestFixtures.lead("Sr. Java Developer", "Keylent", "Reading, PA", "C2C");
        dice.setSource("Dice");
        dice.setUrl("https://dice.example/jobs/1");
        dice.setContact(TestFixtures.contact("Anil Rao", "anil.rao@example.com", null));

        // Same posting, different casing/whitespace, different source + contact.
        LeadEvent linkedin = TestFixtures.lead("  sr. java developer ", "KEYLENT", "reading, pa", "C2C");
        linkedin.setSource("LinkedIn");
        linkedin.setUrl("https://linkedin.example/posts/9");
        linkedin.setContact(TestFixtures.contact(null, null, "555-0100"));

        ingestion.ingestRaw(toJson(dice), "job-leads.raw");
        long duplicatesBefore = ingestion.duplicateCount();
        ingestion.ingestRaw(toJson(linkedin), "job-leads.raw");

        assertThat(postings.count()).isEqualTo(1);
        JobPosting posting = postings.findAll().get(0);
        assertThat(posting.getId())
                .isEqualTo(TextNormalizer.stableId("Sr. Java Developer", "Keylent", "Reading, PA"));
        assertThat(sources.findByPostingOrderBySeenAtDesc(posting)).hasSize(2);
        assertThat(ingestion.duplicateCount()).isEqualTo(duplicatesBefore + 1);
    }

    @Test
    void reSeenSightingRefreshesSeenAtAndMergesContactsWithoutErasure() {
        LeadEvent e1 = TestFixtures.lead("Java Developer", "Globex", "Dallas, TX", "W2");
        e1.setContact(TestFixtures.contact("Anil Rao", "anil.rao@example.com", "555-0100"));
        ingestion.ingestRaw(toJson(e1), "job-leads.raw");

        JobPosting posting = postings.findAll().get(0);
        Instant firstSeenAt = sources.findByPostingOrderBySeenAtDesc(posting).get(0).getSeenAt();

        // Same source+url re-seen, but the duplicate carries only a phone —
        // null name/email must not erase what is already stored.
        LeadEvent e2 = TestFixtures.lead("Java Developer", "Globex", "Dallas, TX", "W2");
        e2.setContact(TestFixtures.contact(null, null, "555-9999"));
        ingestion.ingestRaw(toJson(e2), "job-leads.raw");

        List<JobSource> rows = sources.findByPostingOrderBySeenAtDesc(posting);
        assertThat(rows).hasSize(1); // same sighting: no duplicate row
        JobSource row = rows.get(0);
        assertThat(row.getSeenAt()).isAfterOrEqualTo(firstSeenAt); // re-seen refreshes seenAt
        assertThat(row.getContactName()).isEqualTo("Anil Rao"); // preserved, not nulled
        assertThat(row.getContactEmail()).isEqualTo("anil.rao@example.com"); // preserved
        assertThat(row.getContactPhone()).isEqualTo("555-9999"); // newly supplied value merged in
    }

    @Test
    void stableIdIsDeterministic() {
        String a = TextNormalizer.stableId("Sr. Java Developer", "Keylent", "Reading, PA");
        String b = TextNormalizer.stableId("  SR. JAVA DEVELOPER ", "keylent", "reading,  pa");
        assertThat(a).isEqualTo(b).hasSize(40);
    }

    @Test
    void reSeenRecordKeepsFirstSeen() {
        Instant first = Instant.parse("2026-09-24T08:05:00Z");
        LeadEvent e1 = TestFixtures.lead("Java Developer", "Globex", "Dallas, TX", "W2");
        e1.setFirstSeen(first);
        ingestion.ingestRaw(toJson(e1), "job-leads.raw");

        LeadEvent e2 = TestFixtures.lead("Java Developer", "Globex", "Dallas, TX", "W2");
        e2.setFirstSeen(Instant.parse("2026-09-25T08:05:00Z")); // re-seen a day later
        e2.setSource("LinkedIn");
        e2.setUrl("https://linkedin.example/posts/5");
        ingestion.ingestRaw(toJson(e2), "job-leads.raw");

        JobPosting posting = postings.findAll().get(0);
        assertThat(posting.getFirstSeen()).isEqualTo(first); // truthful anchor, never rewritten
        assertThat(posting.getUpdatedAt()).isAfter(posting.getCreatedAt());
        assertThat(sources.findByPostingOrderBySeenAtDesc(posting)).hasSize(2);
    }

    @Test
    void replayIsIdempotent() {
        LeadEvent e = TestFixtures.lead("Java Developer", "Umbrella", "NYC", "FULLTIME");
        e.setPay("$120K");
        byte[] raw = toJson(e);

        ingestion.ingestRaw(raw, "job-leads.raw");
        ingestion.ingestRaw(raw, "job-leads.raw");
        ingestion.ingestRaw(raw, "job-leads.raw");

        assertThat(postings.count()).isEqualTo(1);
        assertThat(sources.count()).isEqualTo(1); // identical sighting not duplicated
        JobPosting posting = postings.findAll().get(0);
        assertThat(posting.getPayUnit()).isEqualTo("year");
    }

    @Test
    void engagementTagsStoredButCanonicalStaysSingle() {
        LeadEvent e = TestFixtures.withTags(
                TestFixtures.lead("Java Developer", "Hooli", "Remote", "C2C"), "W2");
        ingestion.ingestRaw(toJson(e), "job-leads.raw");

        JobPosting posting = postings.findAll().get(0);
        assertThat(posting.getEngagement()).isEqualTo(Engagement.C2C);
        assertThat(posting.getEngagementTags()).isEqualTo("W2");
    }

    @Test
    void minimalEdgeFixtureIngests() {
        LeadEvent e = TestFixtures.minimal("Dev", "Acme", "Remote", "W2");
        ingestion.ingestRaw(toJson(e), "job-leads.raw");

        assertThat(postings.count()).isEqualTo(1);
        JobPosting posting = postings.findAll().get(0);
        assertThat(posting.getPayMin()).isNull();
        assertThat(posting.getPostedMinutes()).isNull(); // unknown age: null, sorts oldest
        assertThat(posting.getFirstSeen()).isNotNull();
    }

    private byte[] toJson(LeadEvent event) {
        try {
            return objectMapper.writeValueAsBytes(event);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
