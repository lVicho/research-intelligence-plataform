package com.researchintelligence.platform.ai.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CopilotAnswerEvaluationRequest(
    @NotBlank String answer,
    List<CopilotCitationResponse> citedPublications,
    List<CopilotRetrievedPublicationResponse> retrievedPublications
) {
}
