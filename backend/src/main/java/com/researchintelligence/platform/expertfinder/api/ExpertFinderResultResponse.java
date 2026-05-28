package com.researchintelligence.platform.expertfinder.api;

import java.util.List;

public record ExpertFinderResultResponse(
    ExpertFinderResearcherSummaryResponse researcher,
    double score,
    String confidence,
    List<String> matchedTopics,
    List<ExpertFinderPublicationResponse> representativePublications,
    List<ExpertFinderEventParticipationResponse> relevantEventParticipations,
    List<String> reasons,
    String explanation,
    List<String> warnings
) {
}
