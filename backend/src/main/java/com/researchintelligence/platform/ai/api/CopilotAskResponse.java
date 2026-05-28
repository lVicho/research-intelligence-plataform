package com.researchintelligence.platform.ai.api;

import java.util.List;

public record CopilotAskResponse(
    String answerRaw,
    String answer,
    List<CopilotRetrievedPublicationResponse> retrievedPublications,
    List<CopilotCitationResponse> citedPublications,
    String provider,
    String model,
    String embeddingProvider,
    String embeddingModel,
    String retrievalMethod,
    String retrievalMode,
    double minSimilarity,
    List<CopilotSignalResponse> detectedTopics,
    List<CopilotSignalResponse> bridgingAuthors,
    List<String> warnings,
    CopilotAnswerEvaluationResponse evaluation,
    String visibilityScope,
    boolean validationFilterApplied
) {

    public CopilotAskResponse(
        String answerRaw,
        String answer,
        List<CopilotRetrievedPublicationResponse> retrievedPublications,
        List<CopilotCitationResponse> citedPublications,
        String provider,
        String model,
        String embeddingProvider,
        String embeddingModel,
        String retrievalMethod,
        String retrievalMode,
        double minSimilarity,
        List<CopilotSignalResponse> detectedTopics,
        List<CopilotSignalResponse> bridgingAuthors,
        List<String> warnings,
        String visibilityScope,
        boolean validationFilterApplied
    ) {
        this(
            answerRaw,
            answer,
            retrievedPublications,
            citedPublications,
            provider,
            model,
            embeddingProvider,
            embeddingModel,
            retrievalMethod,
            retrievalMode,
            minSimilarity,
            detectedTopics,
            bridgingAuthors,
            warnings,
            null,
            visibilityScope,
            validationFilterApplied
        );
    }
}
