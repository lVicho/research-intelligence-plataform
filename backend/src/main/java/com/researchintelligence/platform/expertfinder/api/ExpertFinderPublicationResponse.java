package com.researchintelligence.platform.expertfinder.api;

import java.util.List;

public record ExpertFinderPublicationResponse(
    Long id,
    String title,
    Integer year,
    String doi,
    String source,
    String url,
    Double semanticSimilarity,
    List<String> matchedTopics
) {
}
