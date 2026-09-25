package com.firstin.dashboard.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstin.dashboard.model.Engagement;
import com.firstin.dashboard.model.JobPosting;
import com.firstin.dashboard.model.JobSource;
import com.firstin.dashboard.model.VisaStatus;
import com.firstin.dashboard.repo.JobPostingRepository;
import com.firstin.dashboard.repo.JobSourceRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka-driven ingestion: validate → normalize → upsert (idempotent by stable
 * id) → append/merge source rows.
 *
 * <ul>
 *   <li>Poison (bad JSON / validation failure / unknown engagement) → DLQ,
 *       batch continues.</li>
 *   <li>Policy rejections ({@code reported:false}, restricted visa) → counted
 *       and logged, never stored, never DLQ — they are valid events.</li>
 *   <li>Re-seen postings update {@code updatedAt} and gain a source row; they
 *       never rewrite {@code firstSeen} and never inflate {@code indexedToday}.</li>
 * </ul>
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final JobPostingRepository postings;
    private final JobSourceRepository sources;
    private final DlqPublisher dlqPublisher;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final IngestRunRecorder runRecorder;

    private final AtomicLong stored = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();

    public IngestionService(JobPostingRepository postings,
                            JobSourceRepository sources,
                            DlqPublisher dlqPublisher,
                            ObjectMapper objectMapper,
                            Validator validator,
                            IngestRunRecorder runRecorder) {
        this.postings = postings;
        this.sources = sources;
        this.dlqPublisher = dlqPublisher;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.runRecorder = runRecorder;
    }

    /**
     * Entry point for raw Kafka bytes. Never throws — poison goes to the DLQ.
     *
     * <p>Transactional because it is the proxy entry point: {@code ingest}
     * is invoked via self-call, which would bypass Spring's transaction proxy
     * and leave lazy collections (posting sources) unusable.
     */
    @Transactional
    public void ingestRaw(byte[] raw, String originalTopic) {
        ingestRaw(raw, originalTopic, null);
    }

    /**
     * Same as {@link #ingestRaw(byte[], String)}, but records the outcome
     * against the pipeline run when the record carries a {@code run_id}
     * header. Records without a run id are plain ingestion and never touch
     * the run table.
     */
    @Transactional
    public void ingestRaw(byte[] raw, String originalTopic, String runId) {
        final LeadEvent event;
        try {
            event = objectMapper.readValue(raw, LeadEvent.class);
        } catch (Exception e) {
            dlqPublisher.publish(raw, originalTopic, "deserialization failed: " + shortMessage(e));
            recordOutcome(runId, IngestOutcome.DLQ);
            return;
        }
        Set<ConstraintViolation<LeadEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String reasons = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            dlqPublisher.publish(raw, originalTopic, "validation failed: " + reasons);
            recordOutcome(runId, IngestOutcome.DLQ);
            return;
        }
        final Engagement engagement;
        try {
            engagement = Engagement.parse(event.getEngagement());
        } catch (IllegalArgumentException e) {
            dlqPublisher.publish(raw, originalTopic, "unknown engagement: " + event.getEngagement());
            recordOutcome(runId, IngestOutcome.DLQ);
            return;
        }
        recordOutcome(runId, ingest(event, engagement));
    }

    private void recordOutcome(String runId, IngestOutcome outcome) {
        if (runId != null) {
            runRecorder.record(runId, outcome);
        }
    }

    @Transactional
    public IngestOutcome ingest(LeadEvent event, Engagement engagement) {
        // Policy: only reported:true records may enter the database.
        if (!Boolean.TRUE.equals(event.getReported())) {
            rejected.incrementAndGet();
            log.info("Rejected unreported lead (policy): title present, reported=false");
            return IngestOutcome.REJECTED;
        }

        // Policy: restricted-sponsorship records are excluded at ingestion.
        String explicitStatus = event.getVisa() == null ? null : event.getVisa().getStatus();
        String explicitReason = event.getVisa() == null ? null : event.getVisa().getReason();
        String haystack = joinNonNull(event.getTitle(), event.getNote());
        VisaAnalyzer.VisaAssessment visa = VisaAnalyzer.assess(explicitStatus, explicitReason, haystack);
        if (visa.status() == VisaStatus.RESTRICTED) {
            rejected.incrementAndGet();
            log.info("Rejected restricted-sponsorship lead (policy): {}", visa.reason());
            return IngestOutcome.REJECTED;
        }

        String id = TextNormalizer.stableId(event.getTitle(), event.getCompany(), event.getLocation());
        Optional<JobPosting> existing = postings.findById(id);
        if (existing.isPresent()) {
            JobPosting posting = existing.get();
            posting.touch();
            addSourceRow(posting, event);
            duplicates.incrementAndGet();
            log.debug("Re-seen posting {} (first_seen unchanged)", id);
            return IngestOutcome.DUPLICATE;
        }

        JobPosting posting = new JobPosting(id);
        posting.setTitle(event.getTitle().trim());
        posting.setCompany(event.getCompany().trim());
        posting.setLocation(event.getLocation().trim());
        posting.setEngagement(engagement);
        posting.setEngagementTags(joinTags(event.getEngagementTags()));
        posting.setFirstSeen(event.getFirstSeen() != null ? event.getFirstSeen() : Instant.now());

        PayParser.parse(event.getPay()).ifPresent(p -> {
            posting.setPayMin(p.min());
            posting.setPayMax(p.max());
            posting.setPayUnit(p.unit());
            posting.setPayHourlyEquiv(p.hourlyEquiv());
        });
        posting.setPayRaw(event.getPay());

        posting.setRawPosted(event.getPosted());
        PostedAgeParser.parseMinutes(event.getPosted()).ifPresent(minutes -> {
            posting.setPostedMinutes(minutes);
            posting.setPostedMinutesConfidence("precise");
        });

        posting.setNote(event.getNote());
        posting.setVisaStatus(visa.status());
        posting.setVisaReason(visa.reason());

        postings.save(posting);
        addSourceRow(posting, event);
        stored.incrementAndGet();
        return IngestOutcome.STORED;
    }

    private void addSourceRow(JobPosting posting, LeadEvent event) {
        // Avoid piling up identical sightings of the same source+url.
        Optional<JobSource> same = sources.findFirstByPostingAndSourceAndUrl(
                posting, event.getSource(), event.getUrl());
        JobSource row = same.orElseGet(() -> {
            JobSource created = new JobSource(posting, Instant.now());
            posting.getSources().add(created);
            return created;
        });
        row.setSource(event.getSource());
        row.setUrl(event.getUrl());
        row.setUrlVerified(event.getUrlVerified());
        // A re-seen sighting refreshes seenAt; first-seen bookkeeping on the
        // posting itself is never touched here.
        row.setSeenAt(Instant.now());
        if (event.getContact() != null) {
            // Merge, don't overwrite: a duplicate event that supplies only
            // some contact fields (or nulls) must not erase what is stored.
            mergeIfSupplied(row::setContactName, event.getContact().getName());
            mergeIfSupplied(row::setContactEmail, event.getContact().getEmail());
            mergeIfSupplied(row::setContactPhone, event.getContact().getPhone());
        }
        sources.save(row);
    }

    private static void mergeIfSupplied(java.util.function.Consumer<String> setter, String value) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String joined = tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    private static String joinNonNull(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                sb.append(p).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private static String shortMessage(Exception e) {
        String m = e.getMessage();
        if (m == null) {
            return e.getClass().getSimpleName();
        }
        return m.length() > 200 ? m.substring(0, 200) : m;
    }

    // Counters for observability (no PII).
    public long storedCount() { return stored.get(); }
    public long duplicateCount() { return duplicates.get(); }
    public long rejectedCount() { return rejected.get(); }
}
