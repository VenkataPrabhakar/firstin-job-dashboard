package com.firstin.dashboard.ingest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Text normalization shared by dedupe and the stable id. */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** Lowercase, whitespace-collapsed, trimmed. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    /** Stable id = sha1 hex of the normalized title|company|location triple. */
    public static String stableId(String title, String company, String location) {
        String joined = normalize(title) + "|" + normalize(company) + "|" + normalize(location);
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(40);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }
}
