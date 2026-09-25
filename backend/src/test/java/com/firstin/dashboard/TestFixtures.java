package com.firstin.dashboard;

import com.firstin.dashboard.ingest.LeadEvent;
import java.time.Instant;
import java.util.List;

/** Builders for valid and edge-case lead events used across tests. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static LeadEvent lead(String title, String company, String location, String engagement) {
        LeadEvent e = new LeadEvent();
        e.setReported(true);
        e.setTitle(title);
        e.setCompany(company);
        e.setLocation(location);
        e.setEngagement(engagement);
        e.setSource("Dice");
        e.setUrl("https://example.com/jobs/1");
        e.setUrlVerified(true);
        e.setFirstSeen(Instant.parse("2026-09-25T08:05:00Z"));
        return e;
    }

    public static LeadEvent.Contact contact(String name, String email, String phone) {
        LeadEvent.Contact c = new LeadEvent.Contact();
        c.setName(name);
        c.setEmail(email);
        c.setPhone(phone);
        return c;
    }

    public static LeadEvent.Visa visa(String status, String reason) {
        LeadEvent.Visa v = new LeadEvent.Visa();
        v.setStatus(status);
        v.setReason(reason);
        return v;
    }

    /** Minimal event: nearly every optional field missing. Must still ingest cleanly. */
    public static LeadEvent minimal(String title, String company, String location, String engagement) {
        LeadEvent e = new LeadEvent();
        e.setReported(true);
        e.setTitle(title);
        e.setCompany(company);
        e.setLocation(location);
        e.setEngagement(engagement);
        return e;
    }

    /** Hostile HTML in text fields — the API returns plain strings; React escapes in Phase 2. */
    public static LeadEvent hostileHtml() {
        LeadEvent e = minimal("<script>alert('xss')</script> Sr. Java Dev",
                "<img src=x onerror=alert(1)>", "Austin, TX", "C2C");
        e.setNote("<b>bold</b> <iframe src='https://evil.example'></iframe>");
        return e;
    }

    public static LeadEvent withTags(LeadEvent e, String... tags) {
        e.setEngagementTags(List.of(tags));
        return e;
    }
}
