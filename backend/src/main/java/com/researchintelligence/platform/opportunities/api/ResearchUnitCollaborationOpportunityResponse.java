package com.researchintelligence.platform.opportunities.api;

import java.util.List;

public record ResearchUnitCollaborationOpportunityResponse(
    OpportunityResearchUnitResponse unitA,
    OpportunityResearchUnitResponse unitB,
    double score,
    double confidence,
    List<String> sharedTopics,
    List<ComplementaryTopicResponse> complementaryTopics,
    List<OpportunityPublicationResponse> representativePublicationsA,
    List<OpportunityPublicationResponse> representativePublicationsB,
    int existingCollaborationCount,
    String explanation,
    List<String> warnings
) {
}
