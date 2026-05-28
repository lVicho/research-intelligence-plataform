package com.researchintelligence.platform.strategicmap.api;

import java.util.List;

public record ResearchLineResponse(
    String lineId,
    String title,
    String description,
    int publicationCount,
    List<ResearchLineNamedCountResponse> researchers,
    List<ResearchLineNamedCountResponse> researchUnits,
    List<ResearchLineNamedCountResponse> topics,
    List<ResearchLinePublicationResponse> representativePublications,
    String trendSummary,
    Double confidence,
    List<String> warnings
) {
}
