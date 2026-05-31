package com.researchintelligence.platform.portal.api;

public record PortalPublicationAuthorResponse(
    Long id,
    Long researcherId,
    String name,
    String externalAffiliation,
    Integer authorOrder,
    boolean correspondingAuthor,
    boolean internal,
    boolean publicProfileAvailable
) {
}
