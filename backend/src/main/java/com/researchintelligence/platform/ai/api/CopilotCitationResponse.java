package com.researchintelligence.platform.ai.api;

import java.util.List;

public record CopilotCitationResponse(
    Long id,
    int citationIndex,
    String title,
    Integer year,
    List<String> authors,
    List<String> topics,
    String doi,
    String source,
    String url,
    Double similarityScore
) {
}
