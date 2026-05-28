package com.researchintelligence.platform.publications.api;

import java.util.List;

public record RelatedPublicationsResponse(
    Long publicationId,
    int limit,
    double minScore,
    boolean metadataOnly,
    List<String> warnings,
    String visibilityScope,
    boolean validationFilterApplied,
    List<RelatedPublicationResponse> relatedPublications
) {
}
