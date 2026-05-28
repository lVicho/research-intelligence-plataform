package com.researchintelligence.platform.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResearcherAssistantAskRequest(
    @NotBlank @Size(max = 1000) String question,
    @NotNull ResearcherAssistantMode mode
) {
}
