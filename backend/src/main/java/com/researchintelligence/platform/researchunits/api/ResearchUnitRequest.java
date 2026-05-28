package com.researchintelligence.platform.researchunits.api;

import com.researchintelligence.platform.researchunits.domain.OrganizationScope;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResearchUnitRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 100) String shortName,
    @NotNull ResearchUnitType type,
    Long parentId,
    @Size(max = 120) String country,
    @Size(max = 120) String city,
    @Size(max = 500) String website,
    Boolean active,
    Boolean visibleInPortal,
    OrganizationScope organizationScope,
    @Size(max = 4000) String publicDescription,
    @Size(max = 4000) String internalDescription,
    Long responsibleResearcherId,
    Boolean featured,
    Integer sortOrder
) {
}
