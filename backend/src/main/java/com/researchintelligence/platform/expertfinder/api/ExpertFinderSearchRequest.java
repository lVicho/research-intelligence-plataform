package com.researchintelligence.platform.expertfinder.api;

import com.researchintelligence.platform.ai.application.RetrievalMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ExpertFinderSearchRequest(
    @NotBlank String query,
    @Min(1) @Max(50) Integer limit,
    RetrievalMode mode,
    @Valid ExpertFinderFiltersRequest filters
) {
}
