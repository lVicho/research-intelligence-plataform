package com.researchintelligence.platform.expertfinder.api;

import java.util.List;

public record ExpertFinderSearchResponse(
    List<ExpertFinderResultResponse> results,
    List<String> warnings,
    String rankingMethod,
    String visibilityScope,
    boolean validationFilterApplied
) {
}
