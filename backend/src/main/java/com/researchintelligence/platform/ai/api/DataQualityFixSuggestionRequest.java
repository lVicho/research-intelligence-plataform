package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.dataquality.domain.DataQualityEntityType;
import com.researchintelligence.platform.dataquality.domain.DataQualityIssueType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DataQualityFixSuggestionRequest(
    DataQualityEntityType entityType,
    Long entityId,
    DataQualityIssueType issueType,
    @NotNull DataQualitySuggestionScope scope,
    @Min(1) @Max(50) Integer limit
) {
}
