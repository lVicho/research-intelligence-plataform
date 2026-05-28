package com.researchintelligence.platform.publications.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PublicationAuthorRequest(
    Long researcherId,
    @Size(max = 255) String externalAuthorName,
    @Size(max = 255) String externalAffiliation,
    @NotNull @Min(1) Integer authorOrder,
    Boolean correspondingAuthor
) {
}
