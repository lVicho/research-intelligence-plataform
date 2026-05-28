package com.researchintelligence.platform.portal.api;

import com.researchintelligence.platform.researchers.domain.AffiliationType;
import java.time.LocalDate;

public record PortalAffiliationResponse(
    Long id,
    Long researcherId,
    Long researchUnitId,
    String researchUnitName,
    String role,
    AffiliationType affiliationType,
    LocalDate startDate,
    LocalDate endDate,
    boolean primaryAffiliation,
    boolean current
) {
}
