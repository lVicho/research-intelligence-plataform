package com.researchintelligence.platform.publications.api;

public record PublicationAuthorResponse(
    Long id,
    Long researcherId,
    String researcherName,
    String externalAuthorName,
    String externalAffiliation,
    Integer authorOrder,
    boolean correspondingAuthor
) {
}
