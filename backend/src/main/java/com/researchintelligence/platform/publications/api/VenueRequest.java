package com.researchintelligence.platform.publications.api;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VenueRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 100) String shortName,
    @NotBlank @Size(max = 80) String typeCode,
    @Size(max = 32) String issn,
    @Size(max = 32) String eissn,
    @Size(max = 32) String isbn,
    @Size(max = 120) String country,
    @Size(max = 500) String website,
    String description,
    Long publisherId,
    Boolean active,
    ValidationStatus validationStatus
) {
}
