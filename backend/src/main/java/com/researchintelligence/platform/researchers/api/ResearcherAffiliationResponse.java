package com.researchintelligence.platform.researchers.api;

import com.researchintelligence.platform.researchers.domain.AffiliationType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.time.LocalDate;

public record ResearcherAffiliationResponse(
    Long id,
    Long researcherId,
    Long researchUnitId,
    String researchUnitName,
    String role,
    AffiliationType affiliationType,
    LocalDate startDate,
    LocalDate endDate,
    boolean primaryAffiliation,
    boolean current,
    ValidationStatus validationStatus,
    String validationComment,
    Instant submittedAt,
    String submittedBy,
    Instant validatedAt,
    String validatedBy,
    boolean canEdit,
    boolean canSubmit,
    boolean canValidate,
    Instant createdAt,
    Instant updatedAt,
    Long createdByUserId,
    Long updatedByUserId
) {
}
