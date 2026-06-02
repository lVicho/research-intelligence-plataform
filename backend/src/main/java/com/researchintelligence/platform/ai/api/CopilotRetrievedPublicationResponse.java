package com.researchintelligence.platform.ai.api;

import java.util.List;

public record CopilotRetrievedPublicationResponse(
    Long id,
    String title,
    String abstractText,
    Integer year,
    String doi,
    String source,
    String url,
    List<String> authors,
    List<String> researchUnits,
    List<String> externalAffiliations,
    List<String> topics,
    Double similarityScore,
    boolean passedThreshold,
    boolean lowSimilarity,
    String retrievalReason
) {
}
