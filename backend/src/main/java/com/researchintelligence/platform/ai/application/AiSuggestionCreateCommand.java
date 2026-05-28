package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.ai.domain.AiSuggestionType;

public record AiSuggestionCreateCommand(
    String targetType,
    Long targetId,
    AiSuggestionType suggestionType,
    String proposedDataJson,
    String explanation,
    String evidenceJson,
    String modelProvider,
    String modelName
) {
}
