package com.firstin.dashboard.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * One candidate job-lead event as published to {@code job-leads.raw} by the
 * ingest producer. Bean Validation runs before any business logic; failures
 * quarantine the raw bytes to the DLQ (never the batch).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeadEvent {

    /** Only {@code true} records may enter the database. */
    @NotNull
    private Boolean reported;

    @NotBlank
    @Size(max = 300)
    private String title;

    @NotBlank
    @Size(max = 200)
    private String company;

    @NotBlank
    @Size(max = 200)
    private String location;

    /** Raw engagement label; normalized to the canonical enum during ingest. */
    @NotBlank
    @Size(max = 50)
    private String engagement;

    @JsonProperty("engagement_tags")
    @Size(max = 10)
    private List<@Size(max = 50) String> engagementTags;

    /** Raw pay string, e.g. "$70-75/hr", "$120K". Parsed by {@link PayParser}. */
    @Size(max = 200)
    private String pay;

    @JsonProperty("first_seen")
    private Instant firstSeen;

    /** Raw posted-age string, e.g. "1 hour ago". Kept only when precisely parseable. */
    @Size(max = 200)
    private String posted;

    @Size(max = 2000)
    private String note;

    @Size(max = 100)
    private String source;

    @Size(max = 2000)
    private String url;

    @JsonProperty("url_verified")
    private Boolean urlVerified;

    @Valid
    private Contact contact;

    @Valid
    private Visa visa;

    // --- getters / setters ---

    public Boolean getReported() { return reported; }
    public void setReported(Boolean reported) { this.reported = reported; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getEngagement() { return engagement; }
    public void setEngagement(String engagement) { this.engagement = engagement; }
    public List<String> getEngagementTags() { return engagementTags; }
    public void setEngagementTags(List<String> engagementTags) { this.engagementTags = engagementTags; }
    public String getPay() { return pay; }
    public void setPay(String pay) { this.pay = pay; }
    public Instant getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Instant firstSeen) { this.firstSeen = firstSeen; }
    public String getPosted() { return posted; }
    public void setPosted(String posted) { this.posted = posted; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Boolean getUrlVerified() { return urlVerified; }
    public void setUrlVerified(Boolean urlVerified) { this.urlVerified = urlVerified; }
    public Contact getContact() { return contact; }
    public void setContact(Contact contact) { this.contact = contact; }
    public Visa getVisa() { return visa; }
    public void setVisa(Visa visa) { this.visa = visa; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Contact {
        @Size(max = 200)
        private String name;
        @Size(max = 320)
        private String email;
        @Size(max = 50)
        private String phone;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Visa {
        /** confirmed | open | unknown | restricted */
        @Size(max = 20)
        private String status;
        /** Exact matched phrase, kept verbatim. */
        @Size(max = 500)
        private String reason;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
