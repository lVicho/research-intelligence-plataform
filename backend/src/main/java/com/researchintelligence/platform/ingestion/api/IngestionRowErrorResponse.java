package com.researchintelligence.platform.ingestion.api;

public record IngestionRowErrorResponse(
    int rowNumber,
    String message
) {
}
