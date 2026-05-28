package com.researchintelligence.platform.ai.api;

import java.util.List;

public record PublicSummaryGenerateResponse(
    String summary,
    List<PublicSummaryEvidenceResponse> evidence,
    Long createdSuggestionId,
    List<String> warnings,
    String provider,
    String model
) {
}
