package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.dataquality.api.DataQualityIssueResponse;
import java.util.Map;

public record DataQualityFixSuggestionResponse(
    DataQualityIssueResponse issue,
    Map<String, Object> suggestedFix,
    double confidence,
    Map<String, Object> evidence,
    Long createdSuggestionId
) {
}
