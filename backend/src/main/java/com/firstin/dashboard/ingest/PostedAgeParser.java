package com.firstin.dashboard.ingest;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the raw posted-age string ("1 hour ago", "3 days ago") into minutes.
 * Only precise numeric ages are kept; anything vague or unparseable returns
 * empty → stored as null, sorts oldest, never renders as "just now".
 */
public final class PostedAgeParser {

    private static final Pattern AGE = Pattern.compile(
            "(\\d+)\\s*(minute|min|hour|hr|day|week)s?\\s+ago", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT = Pattern.compile(
            "(\\d+)\\s*(m|h|d|w)\\b", Pattern.CASE_INSENSITIVE);

    private PostedAgeParser() {
    }

    public static Optional<Integer> parseMinutes(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String s = raw.trim();
        Matcher m = AGE.matcher(s);
        if (m.find()) {
            return Optional.of(toMinutes(m.group(1), m.group(2)));
        }
        // Short forms like "5h", "30m" — but NOT bare words like "new".
        Matcher shortM = SHORT.matcher(s);
        if (shortM.matches()) {
            return Optional.of(toMinutes(shortM.group(1), shortM.group(2)));
        }
        return Optional.empty();
    }

    private static int toMinutes(String amount, String unit) {
        int n = Integer.parseInt(amount);
        return switch (unit.toLowerCase().charAt(0)) {
            case 'm' -> n;
            case 'h' -> n * 60;
            case 'd' -> n * 24 * 60;
            case 'w' -> n * 7 * 24 * 60;
            default -> throw new IllegalArgumentException(unit);
        };
    }
}
