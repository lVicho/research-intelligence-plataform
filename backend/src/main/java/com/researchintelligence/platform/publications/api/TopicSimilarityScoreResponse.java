package com.researchintelligence.platform.publications.api;

public record TopicSimilarityScoreResponse(
    Long sourceTopicId,
    Long targetTopicId,
    double score,
    String method
) {
}
