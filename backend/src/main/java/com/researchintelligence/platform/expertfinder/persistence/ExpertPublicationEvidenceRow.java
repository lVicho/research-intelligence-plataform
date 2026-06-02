package com.researchintelligence.platform.expertfinder.persistence;

import com.researchintelligence.platform.validation.domain.ValidationStatus;

public record ExpertPublicationEvidenceRow(
    Long researcherId,
    String researcherFullName,
    String researcherDisplayName,
    String researcherOrcid,
    boolean researcherActive,
    ValidationStatus researcherValidationStatus,
    Long primaryResearchUnitId,
    String primaryResearchUnitName,
    ValidationStatus primaryAffiliationValidationStatus,
    ValidationStatus primaryResearchUnitValidationStatus,
    Long publicationId,
    String publicationTitle,
    String publicationAbstract,
    Integer publicationYear,
    String publicationType,
    String publicationDoi,
    String publicationSource,
    String publicationUrl,
    ValidationStatus publicationValidationStatus,
    Long topicId,
    String topicName,
    String topicNormalizedName
) {
}
