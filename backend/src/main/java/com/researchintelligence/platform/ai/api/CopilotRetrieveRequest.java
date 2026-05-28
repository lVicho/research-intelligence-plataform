package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.application.RetrievalMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CopilotRetrieveRequest(
    @NotBlank String question,
    @Min(1) Integer limit,
    @DecimalMin("0.0") @DecimalMax("1.0") Double minSimilarity,
    RetrievalMode retrievalMode,
    Boolean includeNonValidated
) {

    public CopilotRetrieveRequest(String question, Integer limit, Double minSimilarity, RetrievalMode retrievalMode) {
        this(question, limit, minSimilarity, retrievalMode, null);
    }
}
