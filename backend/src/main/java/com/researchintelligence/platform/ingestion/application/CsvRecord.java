package com.researchintelligence.platform.ingestion.application;

import java.util.Map;

record CsvRecord(
    int rowNumber,
    Map<String, String> values
) {
    String get(String column) {
        return values.getOrDefault(column, "");
    }
}
