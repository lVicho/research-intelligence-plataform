package com.researchintelligence.platform.ingestion.application;

import java.text.Normalizer;
import java.util.Locale;

public final class IngestionNormalizer {

    private IngestionNormalizer() {
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return withoutAccents
            .replaceAll("[^\\p{Alnum}]+", " ")
            .trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    public static String normalizeDoi(String value) {
        String normalized = blankToEmpty(value).trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceFirst("^https?://(dx\\.)?doi\\.org/", "");
        normalized = normalized.replaceFirst("^doi:", "");
        return normalized.trim();
    }

    public static String normalizeOrcid(String value) {
        String normalized = blankToEmpty(value).trim();
        normalized = normalized.replaceFirst("^https?://orcid\\.org/", "");
        return normalized.replaceAll("\\s+", "");
    }

    public static String displayText(String value) {
        return blankToEmpty(value).trim().replaceAll("\\s+", " ");
    }

    public static String blankToNull(String value) {
        String text = displayText(value);
        return text.isBlank() ? null : text;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
