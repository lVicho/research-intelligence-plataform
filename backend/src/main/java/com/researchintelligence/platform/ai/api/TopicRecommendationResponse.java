package com.researchintelligence.platform.ai.api;

import java.util.List;

public record TopicRecommendationResponse(
    Long aiSuggestionId,
    List<TopicRecommendationTopicResponse> suggestedTopics,
    List<String> warnings
) {
}
