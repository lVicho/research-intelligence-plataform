package com.researchintelligence.platform.opportunities.api;

public record ComplementaryTopicResponse(
    String unitATopic,
    String unitBTopic,
    double adjacencyScore
) {
}
