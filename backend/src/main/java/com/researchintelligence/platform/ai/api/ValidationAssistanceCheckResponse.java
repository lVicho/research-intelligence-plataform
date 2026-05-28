package com.researchintelligence.platform.ai.api;

import java.util.Map;

public record ValidationAssistanceCheckResponse(
    String code,
    ValidationAssistanceSeverity severity,
    String title,
    String description,
    String suggestedAction,
    Map<String, Object> evidence
) {
}
