package com.researchintelligence.platform.auth.api;

public record MeResearcherProfileResponse(
    Long id,
    String fullName,
    String displayName,
    String email,
    String orcid,
    boolean active,
    String primaryAffiliationName
) {
}
