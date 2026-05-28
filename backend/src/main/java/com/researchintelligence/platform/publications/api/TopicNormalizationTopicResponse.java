package com.researchintelligence.platform.publications.api;

public record TopicNormalizationTopicResponse(
    Long id,
    String name,
    String normalizedName,
    long publicationsCount
) {
}
