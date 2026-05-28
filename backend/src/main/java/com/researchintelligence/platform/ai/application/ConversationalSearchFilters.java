package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.LinkedHashMap;
import java.util.Map;

public record ConversationalSearchFilters(
    ValidationStatus validationStatus,
    String academicStatus,
    String type,
    Integer yearFrom,
    Integer yearTo,
    String topic,
    String researcher,
    String unit,
    String dataQualityIssue,
    String textQuery,
    boolean semanticQuery
) {

    public Map<String, Object> toResponseMap() {
        Map<String, Object> filters = new LinkedHashMap<>();
        putIfPresent(filters, "validationStatus", validationStatus == null ? null : validationStatus.name());
        putIfPresent(filters, "academicStatus", academicStatus);
        putIfPresent(filters, "type", type);
        if (yearFrom != null || yearTo != null) {
            Map<String, Object> yearRange = new LinkedHashMap<>();
            putIfPresent(yearRange, "from", yearFrom);
            putIfPresent(yearRange, "to", yearTo);
            filters.put("yearRange", yearRange);
        }
        putIfPresent(filters, "topic", topic);
        putIfPresent(filters, "researcher", researcher);
        putIfPresent(filters, "unit", unit);
        putIfPresent(filters, "dataQualityIssue", dataQualityIssue);
        putIfPresent(filters, "textQuery", textQuery);
        if (semanticQuery) {
            filters.put("semanticQuery", true);
        }
        return filters;
    }

    public boolean hasSearchTerm() {
        return hasText(topic) || hasText(researcher) || hasText(unit) || hasText(dataQualityIssue) || hasText(textQuery);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static void putIfPresent(Map<String, Object> values, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        values.put(key, value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static final class Builder {
        private ValidationStatus validationStatus;
        private String academicStatus;
        private String type;
        private Integer yearFrom;
        private Integer yearTo;
        private String topic;
        private String researcher;
        private String unit;
        private String dataQualityIssue;
        private String textQuery;
        private boolean semanticQuery;

        public Builder validationStatus(ValidationStatus validationStatus) {
            this.validationStatus = validationStatus;
            return this;
        }

        public Builder academicStatus(String academicStatus) {
            this.academicStatus = clean(academicStatus);
            return this;
        }

        public Builder type(String type) {
            this.type = clean(type);
            return this;
        }

        public Builder yearFrom(Integer yearFrom) {
            this.yearFrom = yearFrom;
            return this;
        }

        public Builder yearTo(Integer yearTo) {
            this.yearTo = yearTo;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = clean(topic);
            return this;
        }

        public Builder researcher(String researcher) {
            this.researcher = clean(researcher);
            return this;
        }

        public Builder unit(String unit) {
            this.unit = clean(unit);
            return this;
        }

        public Builder dataQualityIssue(String dataQualityIssue) {
            this.dataQualityIssue = clean(dataQualityIssue);
            return this;
        }

        public Builder textQuery(String textQuery) {
            this.textQuery = clean(textQuery);
            return this;
        }

        public Builder semanticQuery(boolean semanticQuery) {
            this.semanticQuery = semanticQuery;
            return this;
        }

        public ConversationalSearchFilters build() {
            return new ConversationalSearchFilters(
                validationStatus,
                academicStatus,
                type,
                yearFrom,
                yearTo,
                topic,
                researcher,
                unit,
                dataQualityIssue,
                textQuery,
                semanticQuery
            );
        }

        private String clean(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.length() > 160 ? trimmed.substring(0, 160) : trimmed;
        }
    }
}
