package com.researchintelligence.platform.events.api;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.time.LocalDate;

public record EventParticipationResponse(
    Long id,
    Long eventId,
    String eventName,
    Long researcherId,
    String researcherName,
    Long researchUnitId,
    String researchUnitName,
    String participationTypeCode,
    String title,
    String description,
    String evidenceUrl,
    LocalDate participationDate,
    Long relatedPublicationId,
    String relatedPublicationTitle,
    ValidationStatus validationStatus,
    Instant submittedAt,
    String submittedBy,
    Instant validatedAt,
    String validatedBy,
    String validationComment,
    boolean canEdit,
    boolean canSubmit,
    boolean canValidate,
    Instant createdAt,
    Instant updatedAt
) {
}
