package com.firstin.dashboard.web;

import com.firstin.dashboard.model.JobPosting;
import com.firstin.dashboard.model.JobSource;
import com.firstin.dashboard.repo.JobPostingRepository;
import com.firstin.dashboard.repo.JobSourceRepository;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read-only API. No POST/PUT/DELETE exists anywhere in this app —
 * records enter only through the Kafka consumer.
 */
@RestController
@RequestMapping("/api")
@Validated
public class ListingController {

    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");

    private final JobPostingRepository postings;
    private final JobSourceRepository sources;

    public ListingController(JobPostingRepository postings, JobSourceRepository sources) {
        this.postings = postings;
        this.sources = sources;
    }

    @GetMapping("/health")
    public Health health() {
        return new Health("UP");
    }

    /**
     * @param tab today | c2c | w2 | fulltime | visa (default today)
     * @param q   optional case-insensitive title/company search, length-capped
     */
    @GetMapping("/listings")
    public ListingEnvelope listings(
            @RequestParam(defaultValue = "today")
            @Pattern(regexp = "today|c2c|w2|fulltime|visa", message = "unknown tab") String tab,
            @RequestParam(required = false)
            @Size(max = 200, message = "search too long") String q) {

        Instant todayCutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        List<JobPosting> matches = postings.findAll(
                JobPostingRepository.listingFilter(tab, q, todayCutoff),
                Sort.by(Sort.Direction.DESC, "firstSeen"));

        List<ListingEnvelope.ListingDto> dtos = matches.stream().map(this::toDto).toList();
        return new ListingEnvelope(dtos, indexedToday(), lastPull());
    }

    /** Count of postings first seen on the current calendar day in America/Chicago. */
    long indexedToday() {
        Instant startOfDay = LocalDate.now(CHICAGO).atStartOfDay(CHICAGO).toInstant();
        return postings.count((root, cq, cb) ->
                cb.greaterThanOrEqualTo(root.get("firstSeen"), startOfDay));
    }

    /** lastPull without a scheduler: max(first_seen); null when the DB is empty. */
    Instant lastPull() {
        return postings.findAll(Sort.by(Sort.Direction.DESC, "firstSeen")).stream()
                .findFirst()
                .map(JobPosting::getFirstSeen)
                .orElse(null);
    }

    private ListingEnvelope.ListingDto toDto(JobPosting p) {
        ListingEnvelope.ListingDto dto = new ListingEnvelope.ListingDto();
        dto.setId(p.getId());
        dto.setTitle(p.getTitle());
        dto.setCompany(p.getCompany());
        dto.setLocation(p.getLocation());
        dto.setPayMin(p.getPayMin());
        dto.setPayMax(p.getPayMax());
        dto.setPayUnit(p.getPayUnit());
        dto.setPayHourlyEquiv(p.getPayHourlyEquiv());
        dto.setEngagement(p.getEngagement().name());
        dto.setEngagementTags(p.getEngagementTags() == null ? List.of()
                : Arrays.stream(p.getEngagementTags().split(",")).toList());
        dto.setVisaStatus(p.getVisaStatus().wireValue());
        dto.setVisaReason(p.getVisaReason());

        List<JobSource> rows = sources.findByPostingOrderBySeenAtDesc(p);
        dto.setContact(mergeContact(rows));
        dto.setSources(rows.stream().map(s -> {
            ListingEnvelope.SourceDto sd = new ListingEnvelope.SourceDto();
            sd.setSource(s.getSource());
            sd.setUrl(s.getUrl());
            sd.setUrlVerified(s.getUrlVerified());
            return sd;
        }).toList());

        dto.setFirstSeen(p.getFirstSeen());
        dto.setPostedMinutes(p.getPostedMinutes());
        dto.setPostedMinutesConfidence(p.getPostedMinutesConfidence());
        return dto;
    }

    /** Newest source row carrying any contact info wins; fields merge across rows. */
    private ListingEnvelope.ContactDto mergeContact(List<JobSource> rows) {
        ListingEnvelope.ContactDto contact = new ListingEnvelope.ContactDto();
        boolean any = false;
        for (JobSource row : rows) {
            if (!row.hasContact()) {
                continue;
            }
            any = true;
            if (contact.getName() == null) contact.setName(row.getContactName());
            if (contact.getEmail() == null) contact.setEmail(row.getContactEmail());
            if (contact.getPhone() == null) contact.setPhone(row.getContactPhone());
            if (contact.getName() != null && contact.getEmail() != null && contact.getPhone() != null) {
                break;
            }
        }
        return any ? contact : null;
    }

    public record Health(String status) {
    }
}
