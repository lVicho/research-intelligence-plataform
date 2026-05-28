package com.researchintelligence.platform.ai.api;

import jakarta.validation.constraints.NotNull;

public record PublicSummaryGenerateRequest(
    @NotNull PublicSummaryTargetType targetType,
    @NotNull Long targetId,
    @NotNull PublicSummaryStyle style,
    PublicSummaryAudience audience
) {
}
