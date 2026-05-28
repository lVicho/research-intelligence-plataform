package com.researchintelligence.platform.portal.api;

import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;

public record PortalResearchUnitSummaryResponse(
    Long id,
    String name,
    String shortName,
    ResearchUnitType type,
    Long parentId,
    String country,
    String city,
    String website,
    boolean active
) {
}
