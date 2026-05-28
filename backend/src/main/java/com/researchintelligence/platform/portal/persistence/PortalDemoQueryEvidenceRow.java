package com.researchintelligence.platform.portal.persistence;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;

public record PortalDemoQueryEvidenceRow(
    Long publicationId,
    String publicationTitle,
    String publicationAbstract,
    String publicationPublicSummary,
    Instant publicationUpdatedAt,
    ValidationStatus publicationValidationStatus,
    Long topicId,
    String topicName,
    String topicNormalizedName,
    Long researcherId,
    String researcherFullName,
    boolean researcherActive,
    ValidationStatus researcherValidationStatus,
    Long researchUnitId,
    String researchUnitName,
    Boolean researchUnitVisibleInPortal,
    Boolean researchUnitActive,
    String researchUnitOrganizationScope,
    ValidationStatus researchUnitValidationStatus
) {
}
