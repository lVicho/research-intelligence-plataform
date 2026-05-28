package com.researchintelligence.platform.auth.persistence;

import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.time.LocalDate;

public record ResearcherActivityRow(
    ValidationEntityType entityType,
    Long entityId,
    String title,
    String subtitle,
    Long researcherId,
    String researcherName,
    Long researchUnitId,
    String researchUnitName,
    Instant submittedAt,
    ValidationStatus validationStatus,
    String validationComment,
    Long validatedByUserId,
    String validatedBy,
    Instant validatedAt,
    String primaryType,
    String secondaryStatus,
    Integer yearValue,
    String doi,
    String sourceValue,
    String email,
    String orcid,
    Boolean active,
    String roleValue,
    Boolean primaryAffiliation,
    LocalDate startDate,
    LocalDate endDate,
    Boolean abstractPresent,
    Long internalAuthorCount,
    Long topicCount
) {
}
