package com.researchintelligence.platform.publications.api;

public record FilterCountResponse(
    Long id,
    String value,
    String label,
    long count
) {
}
