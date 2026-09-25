package com.firstin.dashboard.model;

/** Visa posture of a posting, per docs/DESIGN.md §3. */
public enum VisaStatus {
    CONFIRMED,
    OPEN,
    UNKNOWN,
    RESTRICTED;

    public static VisaStatus parseLenient(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        return switch (raw.trim().toLowerCase()) {
            case "confirmed" -> CONFIRMED;
            case "open" -> OPEN;
            case "restricted" -> RESTRICTED;
            default -> UNKNOWN;
        };
    }

    /** Lowercase wire form used by the API. */
    public String wireValue() {
        return name().toLowerCase();
    }
}
