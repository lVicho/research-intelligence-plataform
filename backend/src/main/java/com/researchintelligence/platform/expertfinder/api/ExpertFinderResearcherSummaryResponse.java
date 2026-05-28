package com.researchintelligence.platform.expertfinder.api;

public record ExpertFinderResearcherSummaryResponse(
    Long id,
    String fullName,
    String displayName,
    String orcid,
    Long primaryResearchUnitId,
    String primaryResearchUnitName
) {
}
