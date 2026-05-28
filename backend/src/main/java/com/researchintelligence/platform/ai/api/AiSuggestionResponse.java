package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.domain.AiSuggestionStatus;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import java.time.Instant;

public record AiSuggestionResponse(
    Long id,
    String targetType,
    Long targetId,
    AiSuggestionType suggestionType,
    AiSuggestionStatus status,
    String proposedDataJson,
    String explanation,
    String evidenceJson,
    String modelProvider,
    String modelName,
    Instant createdAt,
    Long createdByUserId,
    Instant reviewedAt,
    Long reviewedByUserId,
    String reviewComment
) {
}
