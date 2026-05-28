package com.researchintelligence.platform.events.api;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.time.LocalDate;

public record ScientificEventResponse(
    Long id,
    String name,
    String edition,
    String eventTypeCode,
    LocalDate startDate,
    LocalDate endDate,
    String city,
    String country,
    String organizer,
    String website,
    String description,
    String evidenceUrl,
    Long venueId,
    boolean active,
    ValidationStatus validationStatus,
    Instant createdAt,
    Instant updatedAt
) {
}
