package com.researchintelligence.platform.publications.api;

import java.util.List;

public record TopicNormalizationCandidateGroupResponse(
    String canonicalSuggestion,
    List<TopicNormalizationTopicResponse> topics,
    List<TopicSimilarityScoreResponse> similarityScores,
    String reason,
    double confidence,
    long affectedPublicationsCount
) {
}
