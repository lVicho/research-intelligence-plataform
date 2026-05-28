package com.researchintelligence.platform.publications.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublisherRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 120) String country,
    @Size(max = 500) String website,
    @Size(max = 4000) String description,
    Boolean active
) {
}
