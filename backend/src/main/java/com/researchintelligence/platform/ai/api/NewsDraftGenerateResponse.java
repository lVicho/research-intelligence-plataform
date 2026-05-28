package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import java.util.List;

public record NewsDraftGenerateResponse(
    String suggestedTitle,
    String suggestedSummary,
    String suggestedBody,
    String imageSuggestion,
    String imageAltSuggestion,
    List<NewsDraftEvidenceResponse> evidence,
    Long createdSuggestionId,
    AiSuggestionType createdSuggestionType
) {
}
