package com.researchintelligence.platform.portal.api;

public record PortalCoauthorResponse(
    Long researcherId,
    String name,
    boolean internal,
    long sharedPublicationCount
) {
}
