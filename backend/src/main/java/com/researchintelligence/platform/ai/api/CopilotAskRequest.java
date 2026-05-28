package com.researchintelligence.platform.ai.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CopilotAskRequest(
    @NotBlank String question,
    @Min(1) @Max(10) Integer limit,
    Boolean includeNonValidated
) {

    public CopilotAskRequest(String question, Integer limit) {
        this(question, limit, null);
    }
}
