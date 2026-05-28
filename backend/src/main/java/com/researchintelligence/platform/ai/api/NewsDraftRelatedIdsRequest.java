package com.researchintelligence.platform.ai.api;

import java.util.List;

public record NewsDraftRelatedIdsRequest(
    List<Long> publicationIds,
    List<Long> researcherIds,
    List<Long> unitIds
) {
}
