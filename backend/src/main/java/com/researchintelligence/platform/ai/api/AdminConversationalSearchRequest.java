package com.researchintelligence.platform.ai.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminConversationalSearchRequest(
    @NotBlank @Size(max = 1000) String question,
    ConversationalSearchEntityScope entityScope,
    @Min(1) @Max(50) Integer limit
) {
}
