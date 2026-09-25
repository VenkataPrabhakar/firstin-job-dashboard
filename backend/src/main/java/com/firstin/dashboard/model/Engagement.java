package com.firstin.dashboard.model;

/** Canonical engagement category — exactly one per posting. */
public enum Engagement {
    C2C,
    W2,
    FULLTIME;

    /**
     * Normalizes the many spellings producers may send ("Full-Time", "full time",
     * "w2", "C2C/1099"…) to the canonical enum. Secondary categories belong in
     * {@code engagementTags}, never here.
     */
    public static Engagement parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("engagement is required");
        }
        String n = raw.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        return switch (n) {
            case "C2C", "C2C1099", "1099" -> C2C;
            case "W2" -> W2;
            case "FULLTIME", "FT" -> FULLTIME;
            default -> throw new IllegalArgumentException("unknown engagement: " + raw);
        };
    }
}
