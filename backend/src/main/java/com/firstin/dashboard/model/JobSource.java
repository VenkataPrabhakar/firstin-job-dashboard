package com.firstin.dashboard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One row per sighting of a posting. Cross-source duplicates collapse into a
 * single {@link JobPosting}; their recruiter/source rows accumulate here.
 */
@Entity
@Table(name = "job_sources")
public class JobSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posting_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_job_sources_posting"))
    private JobPosting posting;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "url", length = 2000)
    private String url;

    @Column(name = "url_verified")
    private Boolean urlVerified;

    @Column(name = "contact_name", length = 200)
    private String contactName;

    @Column(name = "contact_email", length = 320)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;

    protected JobSource() {
        // JPA
    }

    public JobSource(JobPosting posting, Instant seenAt) {
        this.posting = posting;
        this.seenAt = seenAt;
    }

    public Long getId() { return id; }
    public JobPosting getPosting() { return posting; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Boolean getUrlVerified() { return urlVerified; }
    public void setUrlVerified(Boolean urlVerified) { this.urlVerified = urlVerified; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public Instant getSeenAt() { return seenAt; }
    public void setSeenAt(Instant seenAt) { this.seenAt = seenAt; }

    /** A source row counts as carrying contact info when any contact field is set. */
    public boolean hasContact() {
        return contactName != null || contactEmail != null || contactPhone != null;
    }
}
