package com.researchintelligence.platform.researchers.api;

import com.researchintelligence.platform.researchers.domain.AffiliationType;
import java.time.Instant;
import java.time.LocalDate;

public record ResearcherAffiliationPublicResponse(
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
    Instant createdAt,
    Instant updatedAt
) {
}
