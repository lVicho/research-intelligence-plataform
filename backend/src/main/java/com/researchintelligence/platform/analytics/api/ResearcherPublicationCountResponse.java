package com.researchintelligence.platform.analytics.api;

public record ResearcherPublicationCountResponse(
    Long researcherId,
    String researcherName,
    long publicationCount
) {
}
