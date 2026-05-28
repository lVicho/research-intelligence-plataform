package com.researchintelligence.platform.publications.api;

import java.util.List;

public record TopicMergeResponse(
    TopicResponse canonicalTopic,
    List<TopicResponse> mergedTopics,
    long affectedPublicationsCount,
    boolean auditEventCreated
) {
}
