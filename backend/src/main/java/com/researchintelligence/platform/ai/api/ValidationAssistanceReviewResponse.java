package com.researchintelligence.platform.ai.api;

import java.util.List;

public record ValidationAssistanceReviewResponse(
    ValidationAssistanceRecommendation recommendation,
    double confidence,
    List<ValidationAssistanceCheckResponse> checks,
    String suggestedValidationComment,
    Long createdSuggestionId
) {
}
