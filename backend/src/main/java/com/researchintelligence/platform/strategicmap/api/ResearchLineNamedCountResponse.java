package com.researchintelligence.platform.strategicmap.api;

public record ResearchLineNamedCountResponse(
    Long id,
    String name,
    int publicationCount
) {
}
