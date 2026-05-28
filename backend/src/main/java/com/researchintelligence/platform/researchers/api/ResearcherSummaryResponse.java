package com.researchintelligence.platform.researchers.api;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;

public record ResearcherSummaryResponse(
    Long id,
    String fullName,
    String displayName,
    String email,
    String orcid,
    boolean active,
    String primaryAffiliationName,
    ValidationStatus validationStatus,
    String validationComment,
    Instant submittedAt,
    String submittedBy,
    Instant validatedAt,
    String validatedBy,
    boolean canEdit,
    boolean canSubmit,
    boolean canValidate
) {
}
