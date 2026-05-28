package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.domain.AiSuggestionType;

public record ResearcherAssistantSuggestionResponse(
    Long suggestionId,
    AiSuggestionType suggestionType,
    String targetType,
    Long targetId,
    String title,
    String detail
) {
}
