package com.researchintelligence.platform.portal.api;

import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;

public record PortalPublicationLinkedUnitResponse(
    Long id,
    String name,
    String shortName,
    ResearchUnitType type
) {
}
