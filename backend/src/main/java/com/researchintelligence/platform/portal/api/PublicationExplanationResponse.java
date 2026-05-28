package com.researchintelligence.platform.portal.api;

import java.util.List;

public record PublicationExplanationResponse(
    String title,
    String plainSummary,
    String problemAddressed,
    String whyItMatters,
    String approach,
    List<PortalPublicationExplanationReferenceResponse> relatedTopics,
    List<PortalPublicationExplanationReferenceResponse> relatedResearchers,
    List<PortalPublicationExplanationReferenceResponse> relatedUnits,
    List<PortalPublicationExplanationReferenceResponse> relatedPublications,
    List<String> warnings,
    String provider,
    String model
) {
}
