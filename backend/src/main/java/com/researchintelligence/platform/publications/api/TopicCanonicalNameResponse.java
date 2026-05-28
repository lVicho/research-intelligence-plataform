package com.researchintelligence.platform.publications.api;

public record TopicCanonicalNameResponse(
    String canonicalSuggestion,
    String provider,
    String model,
    String reason
) {
}
