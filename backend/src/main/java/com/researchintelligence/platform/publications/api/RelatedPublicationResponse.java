package com.researchintelligence.platform.publications.api;

import java.util.List;

public record RelatedPublicationResponse(
    PublicationSummaryResponse publication,
    double finalScore,
    Double semanticScore,
    double metadataScore,
    List<String> sharedTopicNames,
    List<String> sharedAuthorNames,
    List<String> relatedResearchUnitNames,
    Integer yearDistance,
    List<String> explanationReasons,
    String warning
) {
}
