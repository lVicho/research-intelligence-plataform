package com.researchintelligence.platform.researchers.api;

import com.researchintelligence.platform.researchers.domain.AffiliationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ResearcherAffiliationRequest(
    @NotNull Long researchUnitId,
    @Size(max = 255) String role,
    @NotNull AffiliationType affiliationType,
    LocalDate startDate,
    LocalDate endDate,
    Boolean primaryAffiliation
) {
}
