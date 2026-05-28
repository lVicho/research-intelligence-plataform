package com.researchintelligence.platform.publications.api;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;

public record VenueResponse(
    Long id,
    String name,
    String shortName,
    String typeCode,
    String issn,
    String eissn,
    String isbn,
    String country,
    String website,
    String description,
    Long publisherId,
    boolean active,
    ValidationStatus validationStatus,
    Instant createdAt,
    Instant updatedAt,
    Long createdByUserId,
    Long updatedByUserId
) {
}
