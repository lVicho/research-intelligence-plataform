package com.researchintelligence.platform.portal.api;

public record PortalResearcherSummaryResponse(
    Long id,
    String fullName,
    String displayName,
    String email,
    String orcid,
    boolean active,
    String primaryAffiliationName
) {
}
