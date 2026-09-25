package com.firstin.dashboard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One row per canonical posting. The primary key is the stable id:
 * {@code sha1(normalized title | normalized company | normalized location)},
 * so replays and re-seen records are idempotent by construction.
 *
 * Only plain column types are used (no JSONB etc.) so H2-based CI tests
 * stay honest with the PostgreSQL production schema.
 */
@Entity
@Table(name = "job_postings")
public class JobPosting {

    @Id
    @Column(name = "id", length = 40, nullable = false, updatable = false)
    private String id;

    @Column(name = "title", length = 300, nullable = false)
    private String title;

    @Column(name = "company", length = 200, nullable = false)
    private String company;

    @Column(name = "location", length = 200, nullable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "engagement", length = 20, nullable = false)
    private Engagement engagement;

    /** Secondary engagement categories, comma-separated. Never a second canonical category. */
    @Column(name = "engagement_tags", length = 200)
    private String engagementTags;

    @Column(name = "pay_min", precision = 12, scale = 2)
    private BigDecimal payMin;

    @Column(name = "pay_max", precision = 12, scale = 2)
    private BigDecimal payMax;

    @Column(name = "pay_unit", length = 10)
    private String payUnit; // "hour" | "year"

    @Column(name = "pay_hourly_equiv", precision = 12, scale = 2)
    private BigDecimal payHourlyEquiv;

    @Column(name = "pay_raw", length = 200)
    private String payRaw;

    /** Truthful freshness anchor. Never null, never rewritten on re-seen records. */
    @Column(name = "first_seen", nullable = false, updatable = false)
    private Instant firstSeen;

    /** Kept only when the source age parsed precisely; otherwise null (sorts oldest). */
    @Column(name = "posted_minutes")
    private Integer postedMinutes;

    @Column(name = "posted_minutes_confidence", length = 10)
    private String postedMinutesConfidence; // "precise" | null

    @Column(name = "raw_posted", length = 200)
    private String rawPosted;

    @Column(name = "note", length = 2000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "visa_status", length = 20, nullable = false)
    private VisaStatus visaStatus = VisaStatus.UNKNOWN;

    @Column(name = "visa_reason", length = 500)
    private String visaReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "posting")
    private List<JobSource> sources = new ArrayList<>();

    protected JobPosting() {
        // JPA
    }

    public JobPosting(String id) {
        this.id = id;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // --- getters / setters ---

    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Engagement getEngagement() { return engagement; }
    public void setEngagement(Engagement engagement) { this.engagement = engagement; }
    public String getEngagementTags() { return engagementTags; }
    public void setEngagementTags(String engagementTags) { this.engagementTags = engagementTags; }
    public BigDecimal getPayMin() { return payMin; }
    public void setPayMin(BigDecimal payMin) { this.payMin = payMin; }
    public BigDecimal getPayMax() { return payMax; }
    public void setPayMax(BigDecimal payMax) { this.payMax = payMax; }
    public String getPayUnit() { return payUnit; }
    public void setPayUnit(String payUnit) { this.payUnit = payUnit; }
    public BigDecimal getPayHourlyEquiv() { return payHourlyEquiv; }
    public void setPayHourlyEquiv(BigDecimal payHourlyEquiv) { this.payHourlyEquiv = payHourlyEquiv; }
    public String getPayRaw() { return payRaw; }
    public void setPayRaw(String payRaw) { this.payRaw = payRaw; }
    public Instant getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Instant firstSeen) { this.firstSeen = firstSeen; }
    public Integer getPostedMinutes() { return postedMinutes; }
    public void setPostedMinutes(Integer postedMinutes) { this.postedMinutes = postedMinutes; }
    public String getPostedMinutesConfidence() { return postedMinutesConfidence; }
    public void setPostedMinutesConfidence(String postedMinutesConfidence) { this.postedMinutesConfidence = postedMinutesConfidence; }
    public String getRawPosted() { return rawPosted; }
    public void setRawPosted(String rawPosted) { this.rawPosted = rawPosted; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public VisaStatus getVisaStatus() { return visaStatus; }
    public void setVisaStatus(VisaStatus visaStatus) { this.visaStatus = visaStatus; }
    public String getVisaReason() { return visaReason; }
    public void setVisaReason(String visaReason) { this.visaReason = visaReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = Instant.now(); }
    public List<JobSource> getSources() { return sources; }
}
