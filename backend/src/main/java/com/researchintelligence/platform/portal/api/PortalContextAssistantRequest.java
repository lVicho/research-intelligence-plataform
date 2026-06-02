package com.researchintelligence.platform.portal.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PortalContextAssistantRequest(
    @NotNull PortalContextAssistantScope contextScope,
    Long targetId,
    @NotBlank String question,
    @Valid PortalContextAssistantSearchRequest searchRequest,
    @Min(1) @Max(30) Integer maxEvidence
) {
}
