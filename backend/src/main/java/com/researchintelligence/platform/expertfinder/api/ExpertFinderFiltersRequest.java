package com.researchintelligence.platform.expertfinder.api;

public record ExpertFinderFiltersRequest(
    Long researchUnitId,
    String topic,
    Boolean onlyValidated
) {
}
