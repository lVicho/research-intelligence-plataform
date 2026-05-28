package com.researchintelligence.platform.strategicmap.api;

import java.util.List;

public record ResearchLinePublicationResponse(
    Long id,
    String citationKey,
    String title,
    Integer year,
    String doi,
    String source,
    List<String> topics,
    Double relevanceScore,
    Double semanticCentrality
) {
}
