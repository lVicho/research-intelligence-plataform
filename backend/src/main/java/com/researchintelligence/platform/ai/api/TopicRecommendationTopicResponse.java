package com.researchintelligence.platform.ai.api;

import java.util.List;

public record TopicRecommendationTopicResponse(
    String label,
    Long existingTopicId,
    double confidence,
    String reason,
    List<Long> evidencePublicationIds
) {
}
