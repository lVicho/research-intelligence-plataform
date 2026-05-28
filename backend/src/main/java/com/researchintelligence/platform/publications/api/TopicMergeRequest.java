package com.researchintelligence.platform.publications.api;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record TopicMergeRequest(
    Long canonicalTopicId,
    String canonicalName,
    @NotEmpty List<Long> topicIdsToMerge
) {
}
