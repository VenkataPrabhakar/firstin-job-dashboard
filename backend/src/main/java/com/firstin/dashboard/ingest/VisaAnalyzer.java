package com.firstin.dashboard.ingest;

import com.firstin.dashboard.model.VisaStatus;
import java.util.List;

/**
 * Keyword analysis for visa posture, per docs/DESIGN.md §3. The producer
 * usually ships an explicit visa block; this is the backend-owned fallback
 * when it doesn't — and the enforcement point that excludes restricted
 * records at ingestion.
 */
public final class VisaAnalyzer {

    private static final List<String> RESTRICTED_PHRASES = List.of(
            "no sponsorship", "will not sponsor", "without sponsorship",
            "citizens only", "us citizens only", "u.s. citizens only",
            "usc only", "gc only", "green card only", "h4-ead only",
            "no h1", "no h-1b", "no opt", "no cpt", "e-verified only");

    private static final List<String> CONFIRMED_PHRASES = List.of(
            "sponsorship", "sponsor h1", "sponsor h-1b", "h1b", "h-1b",
            "visa sponsorship", "opt", "cpt", "ead", "will sponsor");

    private VisaAnalyzer() {
    }

    public record VisaAssessment(VisaStatus status, String reason) {
    }

    /**
     * @param explicitStatus status shipped by the producer, may be null
     * @param explicitReason reason shipped by the producer, may be null
     * @param haystack       title + note text to keyword-scan as fallback
     */
    public static VisaAssessment assess(String explicitStatus, String explicitReason, String haystack) {
        VisaStatus status = VisaStatus.parseLenient(explicitStatus);
        if (status != VisaStatus.UNKNOWN) {
            return new VisaAssessment(status, explicitReason);
        }
        if (haystack == null || haystack.isBlank()) {
            return new VisaAssessment(VisaStatus.UNKNOWN, null);
        }
        String text = haystack.toLowerCase();
        for (String phrase : RESTRICTED_PHRASES) {
            if (text.contains(phrase)) {
                return new VisaAssessment(VisaStatus.RESTRICTED, "matched phrase: \"" + phrase + "\"");
            }
        }
        for (String phrase : CONFIRMED_PHRASES) {
            if (text.contains(phrase)) {
                return new VisaAssessment(VisaStatus.CONFIRMED, "matched phrase: \"" + phrase + "\"");
            }
        }
        return new VisaAssessment(VisaStatus.UNKNOWN, null);
    }
}
