package com.researchintelligence.platform.ai.api;

import jakarta.validation.constraints.Size;

public record AiSuggestionReviewRequest(
    @Size(max = 2000)
    String reviewComment
) {
}
