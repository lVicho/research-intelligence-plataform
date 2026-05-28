package com.researchintelligence.platform.researchunits.api;

import com.researchintelligence.platform.researchunits.domain.OrganizationScope;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;

public record ResearchUnitListResponse(
    Long id,
    String name,
    String shortName,
    ResearchUnitType type,
    Long parentId,
    String country,
    String city,
    String website,
    boolean active,
    boolean visibleInPortal,
    OrganizationScope organizationScope,
    String publicDescription,
    String internalDescription,
    Long responsibleResearcherId,
    Boolean featured,
    Integer sortOrder,
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
    Instant updatedAt
) {
}
