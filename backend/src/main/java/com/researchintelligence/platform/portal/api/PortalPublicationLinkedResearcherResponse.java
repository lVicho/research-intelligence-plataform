package com.researchintelligence.platform.portal.api;

public record PortalPublicationLinkedResearcherResponse(
    Long id,
    String fullName,
    String displayName,
    String primaryAffiliationName
) {
}
