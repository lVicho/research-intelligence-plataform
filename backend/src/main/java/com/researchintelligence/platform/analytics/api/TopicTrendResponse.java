package com.researchintelligence.platform.analytics.api;

public record TopicTrendResponse(
    Long topicId,
    String topicName,
    long recentPublicationCount,
    long previousPublicationCount,
    long delta,
    double growthRate
) {
}
