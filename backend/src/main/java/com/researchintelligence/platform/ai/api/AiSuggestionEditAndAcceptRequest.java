package com.researchintelligence.platform.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiSuggestionEditAndAcceptRequest(
    @NotBlank
    String proposedDataJson,

    @Size(max = 2000)
    String reviewComment
) {
}
