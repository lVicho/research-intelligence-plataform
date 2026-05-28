package com.researchintelligence.platform.analytics.api;

import java.util.List;

public record TopicTrendsResponse(
    Integer latestPublicationYear,
    Integer recentWindowStartYear,
    Integer previousWindowStartYear,
    Integer previousWindowEndYear,
    List<NamedCountResponse> topTopics,
    List<TopicTrendResponse> emergingTopics
) {
}
