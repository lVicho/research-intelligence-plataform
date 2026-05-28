package com.researchintelligence.platform.validation.persistence;

import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.time.LocalDate;

public record ValidationItemRow(
    ValidationEntityType entityType,
    Long entityId,
    String title,
    String subtitle,
    Long researcherId,
    String researcherName,
    Long researchUnitId,
    String researchUnitName,
    String submittedBy,
    Instant submittedAt,
    ValidationStatus validationStatus,
    String primaryType,
    String secondaryStatus,
    Integer yearValue,
    String doi,
    String sourceValue,
    String email,
    String orcid,
    Boolean active,
    String website,
    String country,
    String city,
    Boolean abstractPresent,
    Long internalAuthorCount,
    Long topicCount,
    String roleValue,
    Boolean primaryAffiliation,
    LocalDate startDate,
    LocalDate endDate,
    String validationComment,
    Long validatedByUserId,
    String validatedBy,
    Instant validatedAt
) {
}
