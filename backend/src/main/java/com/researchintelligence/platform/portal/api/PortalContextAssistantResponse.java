package com.researchintelligence.platform.portal.api;

import java.util.List;

public record PortalContextAssistantResponse(
    String answer,
    List<PortalContextAssistantPublicationEvidenceResponse> citedPublications,
    List<PortalContextAssistantResearcherEvidenceResponse> citedResearchers,
    List<PortalContextAssistantUnitEvidenceResponse> citedUnits,
    List<String> evidenceSummary,
    List<String> warnings,
    String provider,
    String model,
    String visibilityScope,
    boolean validationFilterApplied
) {
}
