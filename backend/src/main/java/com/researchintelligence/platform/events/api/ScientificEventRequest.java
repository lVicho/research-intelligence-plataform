package com.researchintelligence.platform.events.api;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ScientificEventRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 120) String edition,
    @NotBlank @Size(max = 80) String eventTypeCode,
    LocalDate startDate,
    LocalDate endDate,
    @Size(max = 120) String city,
    @Size(max = 120) String country,
    @Size(max = 255) String organizer,
    @Size(max = 500) String website,
    String description,
    @Size(max = 500) String evidenceUrl,
    Long venueId,
    Boolean active,
    ValidationStatus validationStatus
) {
}
