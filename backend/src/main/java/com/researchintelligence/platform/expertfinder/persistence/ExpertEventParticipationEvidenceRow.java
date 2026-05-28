package com.researchintelligence.platform.expertfinder.persistence;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.LocalDate;

public record ExpertEventParticipationEvidenceRow(
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
    Long participationId,
    Long eventId,
    String eventName,
    ValidationStatus eventValidationStatus,
    Long researchUnitId,
    String researchUnitName,
    ValidationStatus researchUnitValidationStatus,
    String participationTypeCode,
    String title,
    String description,
    LocalDate participationDate,
    Long relatedPublicationId,
    ValidationStatus participationValidationStatus
) {
}
