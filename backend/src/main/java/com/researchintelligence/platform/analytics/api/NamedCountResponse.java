package com.researchintelligence.platform.analytics.api;

public record NamedCountResponse(
    Long id,
    String name,
    long count
) {
}
