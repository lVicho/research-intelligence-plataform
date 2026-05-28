package com.researchintelligence.platform.researchers.api;

public record ResearcherCoauthorResponse(
    Long researcherId,
    String name,
    boolean internal,
    long sharedPublicationCount
) {
}
