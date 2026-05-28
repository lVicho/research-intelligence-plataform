package com.researchintelligence.platform.portal.api;

public record PublicationExplanationRequest(
    PublicationExplanationStyle style,
    String language
) {
}
