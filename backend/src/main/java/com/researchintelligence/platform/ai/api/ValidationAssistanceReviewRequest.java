package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import jakarta.validation.constraints.NotNull;

public record ValidationAssistanceReviewRequest(
    @NotNull ValidationEntityType entityType,
    @NotNull Long entityId
) {
}
