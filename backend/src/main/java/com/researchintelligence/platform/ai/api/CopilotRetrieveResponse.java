package com.researchintelligence.platform.ai.api;

import java.util.List;

public record CopilotRetrieveResponse(
    String retrievalMethod,
    String retrievalMode,
    double minSimilarity,
    String embeddingProvider,
    String embeddingModel,
    String provider,
    String model,
    List<CopilotRetrievedPublicationResponse> retrievedPublications,
    List<CopilotSignalResponse> detectedTopics,
    List<CopilotSignalResponse> bridgingAuthors,
    List<String> warnings,
    String visibilityScope,
    boolean validationFilterApplied
) {
}
