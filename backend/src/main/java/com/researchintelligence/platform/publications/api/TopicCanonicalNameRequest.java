package com.researchintelligence.platform.publications.api;

import java.util.List;

public record TopicCanonicalNameRequest(
    List<Long> topicIds,
    List<String> topicNames
) {
}
