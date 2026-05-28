package com.researchintelligence.platform.ai.api;

import java.util.List;

public record ResearcherAssistantResponse(
    String answer,
    List<String> actionItems,
    List<ResearcherAssistantActivityResponse> relatedActivities,
    List<ResearcherAssistantSuggestionResponse> suggestions,
    List<String> warnings,
    String provider,
    String model
) {
}
