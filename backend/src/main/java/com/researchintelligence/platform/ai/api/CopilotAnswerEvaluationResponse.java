package com.researchintelligence.platform.ai.api;

import java.util.List;

public record CopilotAnswerEvaluationResponse(
    CopilotAnswerSupportLevel supportLevel,
    List<String> unsupportedClaims,
    List<String> missingCitations,
    List<String> citationIssues,
    String summary,
    List<String> warnings
) {
}
