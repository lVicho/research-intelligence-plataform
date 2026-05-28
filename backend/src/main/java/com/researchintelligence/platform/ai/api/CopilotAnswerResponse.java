package com.researchintelligence.platform.ai.api;

import java.util.List;

public record CopilotAnswerResponse(
    String answerRaw,
    String answer,
    List<CopilotRetrievedPublicationResponse> retrievedPublications,
    List<CopilotCitationResponse> citedPublications,
    String provider,
    String model,
    List<String> warnings,
    CopilotAnswerEvaluationResponse evaluation,
    String visibilityScope,
    boolean validationFilterApplied
) {

    public CopilotAnswerResponse(
        String answerRaw,
        String answer,
        List<CopilotRetrievedPublicationResponse> retrievedPublications,
        List<CopilotCitationResponse> citedPublications,
        String provider,
        String model,
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
            warnings,
            null,
            visibilityScope,
            validationFilterApplied
        );
    }
}
