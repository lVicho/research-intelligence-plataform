package com.researchintelligence.platform.ai.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CopilotAnswerRequest(
    @NotBlank String question,
    List<CopilotRetrievedPublicationResponse> retrievedPublications,
    Boolean includeNonValidated
) {

    public CopilotAnswerRequest(String question, List<CopilotRetrievedPublicationResponse> retrievedPublications) {
        this(question, retrievedPublications, null);
    }
}
