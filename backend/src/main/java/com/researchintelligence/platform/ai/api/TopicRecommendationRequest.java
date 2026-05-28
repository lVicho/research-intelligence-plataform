package com.researchintelligence.platform.ai.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TopicRecommendationRequest(
    TopicRecommendationTargetType targetType,
    Long targetId,
    @Size(max = 500) String title,
    @Size(max = 5000) String abstractText,
    @Size(max = 20) List<@Size(max = 120) String> keywords,
    @Min(1) @Max(20) Integer maxTopics
) {
}
