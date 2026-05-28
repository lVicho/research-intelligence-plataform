package com.researchintelligence.platform.strategicmap.api;

import java.util.List;

public record StrategicResearchMapResponse(
    Integer yearFrom,
    Integer yearTo,
    Long researchUnitId,
    boolean onlyValidated,
    String visibilityScope,
    boolean validationFilterApplied,
    String groupingApproach,
    List<String> warnings,
    List<ResearchLineResponse> researchLines
) {
}
