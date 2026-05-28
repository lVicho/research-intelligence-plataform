package com.researchintelligence.platform.ai.api;

public record NewsDraftEvidenceResponse(
    String reference,
    String entityType,
    Long entityId,
    String label,
    String value
) {
}
