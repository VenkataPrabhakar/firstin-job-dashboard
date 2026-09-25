package com.firstin.dashboard.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Single-envelope response for GET /api/listings — the only shape Phase 2 needs. */
@JsonInclude(Include.NON_NULL)
public class ListingEnvelope {

    private final List<ListingDto> listings;
    private final long indexedToday;
    private final Instant lastPull;

    public ListingEnvelope(List<ListingDto> listings, long indexedToday, Instant lastPull) {
        this.listings = listings;
        this.indexedToday = indexedToday;
        this.lastPull = lastPull;
    }

    public List<ListingDto> getListings() { return listings; }
    public long getIndexedToday() { return indexedToday; }

    // Always present — explicit null tells Phase 2 "no sightings yet",
    // distinct from a field the API forgot to send.
    @JsonInclude(Include.ALWAYS)
    public Instant getLastPull() { return lastPull; }

    @JsonInclude(Include.NON_NULL)
    public static class ListingDto {
        private String id;
        private String title;
        private String company;
        private String location;
        private BigDecimal payMin;
        private BigDecimal payMax;
        private String payUnit;
        private BigDecimal payHourlyEquiv;
        private String engagement;
        private List<String> engagementTags;
        private String visaStatus;
        private String visaReason;
        private ContactDto contact;
        private List<SourceDto> sources;
        private Instant firstSeen;
        private Integer postedMinutes;
        private String postedMinutesConfidence;

        // getters/setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCompany() { return company; }
        public void setCompany(String company) { this.company = company; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public BigDecimal getPayMin() { return payMin; }
        public void setPayMin(BigDecimal payMin) { this.payMin = payMin; }
        public BigDecimal getPayMax() { return payMax; }
        public void setPayMax(BigDecimal payMax) { this.payMax = payMax; }
        public String getPayUnit() { return payUnit; }
        public void setPayUnit(String payUnit) { this.payUnit = payUnit; }
        public BigDecimal getPayHourlyEquiv() { return payHourlyEquiv; }
        public void setPayHourlyEquiv(BigDecimal payHourlyEquiv) { this.payHourlyEquiv = payHourlyEquiv; }
        public String getEngagement() { return engagement; }
        public void setEngagement(String engagement) { this.engagement = engagement; }
        public List<String> getEngagementTags() { return engagementTags; }
        public void setEngagementTags(List<String> engagementTags) { this.engagementTags = engagementTags; }
        public String getVisaStatus() { return visaStatus; }
        public void setVisaStatus(String visaStatus) { this.visaStatus = visaStatus; }
        public String getVisaReason() { return visaReason; }
        public void setVisaReason(String visaReason) { this.visaReason = visaReason; }
        public ContactDto getContact() { return contact; }
        public void setContact(ContactDto contact) { this.contact = contact; }
        public List<SourceDto> getSources() { return sources; }
        public void setSources(List<SourceDto> sources) { this.sources = sources; }
        public Instant getFirstSeen() { return firstSeen; }
        public void setFirstSeen(Instant firstSeen) { this.firstSeen = firstSeen; }
        public Integer getPostedMinutes() { return postedMinutes; }
        public void setPostedMinutes(Integer postedMinutes) { this.postedMinutes = postedMinutes; }
        public String getPostedMinutesConfidence() { return postedMinutesConfidence; }
        public void setPostedMinutesConfidence(String postedMinutesConfidence) { this.postedMinutesConfidence = postedMinutesConfidence; }
    }

    @JsonInclude(Include.NON_NULL)
    public static class ContactDto {
        private String name;
        private String email;
        private String phone;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    @JsonInclude(Include.NON_NULL)
    public static class SourceDto {
        private String source;
        private String url;
        private Boolean urlVerified;

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public Boolean getUrlVerified() { return urlVerified; }
        public void setUrlVerified(Boolean urlVerified) { this.urlVerified = urlVerified; }
    }
}
