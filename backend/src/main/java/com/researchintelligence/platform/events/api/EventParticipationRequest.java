package com.researchintelligence.platform.events.api;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record EventParticipationRequest(
    @NotNull Long eventId,
    @NotNull Long researcherId,
    Long researchUnitId,
    @NotBlank @Size(max = 80) String participationTypeCode,
    @NotBlank @Size(max = 500) String title,
    String description,
    @Size(max = 500) String evidenceUrl,
    LocalDate participationDate,
    Long relatedPublicationId,
    ValidationStatus validationStatus
) {
}
