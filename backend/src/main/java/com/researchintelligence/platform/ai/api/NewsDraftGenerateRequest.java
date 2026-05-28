package com.researchintelligence.platform.ai.api;

import jakarta.validation.constraints.Size;

public record NewsDraftGenerateRequest(
    NewsDraftSourceType sourceType,
    Long sourceId,
    @Size(max = 500) String query,
    NewsDraftTone tone,
    NewsDraftRelatedIdsRequest relatedIds
) {
}
