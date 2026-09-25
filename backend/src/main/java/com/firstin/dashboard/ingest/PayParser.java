package com.firstin.dashboard.ingest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw pay strings into structured pay. Handles:
 * <ul>
 *   <li>{@code $70-75/hr}, {@code $70 - $75 / hr}, {@code 70/hr} → hourly range</li>
 *   <li>{@code $120K} → 120000/year (K without a unit implies yearly)</li>
 *   <li>{@code $130,000–$150,000/year} → yearly range (en/em dashes, commas)</li>
 *   <li>{@code $70/hr} → single hourly rate</li>
 * </ul>
 * Unparseable input → empty (all pay fields null; the API renders
 * "Pay not listed"). Hourly equivalent = yearly ÷ 2080.
 */
public final class PayParser {

    private static final BigDecimal HOURS_PER_YEAR = new BigDecimal("2080");

    private static final Pattern RANGE = Pattern.compile(
            "^\\$?([\\d.,]+)\\s*([kK])?\\s*[\\-–—]\\s*\\$?([\\d.,]+)\\s*([kK])?\\s*/?\\s*"
                    + "(hr|hour|hourly|hrs|yr|year|yearly|annual|annum|p\\.a\\.)?\\s*$");
    private static final Pattern SINGLE = Pattern.compile(
            "^\\$?([\\d.,]+)\\s*([kK])?\\s*/?\\s*"
                    + "(hr|hour|hourly|hrs|yr|year|yearly|annual|annum|p\\.a\\.)?\\s*$");

    private PayParser() {
    }

    public record ParsedPay(BigDecimal min, BigDecimal max, String unit, BigDecimal hourlyEquiv) {
    }

    public static Optional<ParsedPay> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String s = raw.trim();
        Matcher range = RANGE.matcher(s);
        if (range.matches()) {
            return build(range.group(1), range.group(2), range.group(3), range.group(4), range.group(5));
        }
        Matcher single = SINGLE.matcher(s);
        if (single.matches()) {
            return build(single.group(1), single.group(2), single.group(1), single.group(2), single.group(3));
        }
        return Optional.empty();
    }

    private static Optional<ParsedPay> build(String minRaw, String minK,
                                             String maxRaw, String maxK, String unitRaw) {
        try {
            BigDecimal min = amount(minRaw, minK);
            BigDecimal max = amount(maxRaw, maxK);
            if (min.signum() <= 0 || max.signum() <= 0 || min.compareTo(max) > 0) {
                return Optional.empty();
            }
            String unit = unit(unitRaw, minK != null || maxK != null);
            if (unit == null) {
                // Bare number with no unit and no K suffix: ambiguous — do not guess.
                return Optional.empty();
            }
            BigDecimal avg = min.add(max).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            BigDecimal hourlyEquiv = unit.equals("hour")
                    ? avg
                    : avg.divide(HOURS_PER_YEAR, 2, RoundingMode.HALF_UP);
            return Optional.of(new ParsedPay(min, max, unit, hourlyEquiv));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static BigDecimal amount(String raw, String k) {
        BigDecimal value = new BigDecimal(raw.replace(",", ""));
        if (k != null) {
            value = value.multiply(BigDecimal.valueOf(1000));
        }
        return value;
    }

    /**
     * Resolves the unit: explicit unit wins; a K suffix without a unit implies
     * yearly; otherwise unknown (null).
     */
    private static String unit(String unitRaw, boolean hasK) {
        if (unitRaw != null) {
            String u = unitRaw.toLowerCase();
            if (u.startsWith("hr") || u.startsWith("hour")) {
                return "hour";
            }
            return "year";
        }
        return hasK ? "year" : null;
    }
}
