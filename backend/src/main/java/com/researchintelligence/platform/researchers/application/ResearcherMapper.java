package com.researchintelligence.platform.researchers.application;

import com.researchintelligence.platform.researchers.api.ResearcherAffiliationRequest;
import com.researchintelligence.platform.researchers.api.ResearcherAffiliationPublicResponse;
import com.researchintelligence.platform.researchers.api.ResearcherAffiliationResponse;
import com.researchintelligence.platform.researchers.api.ResearcherRequest;
import com.researchintelligence.platform.researchers.api.ResearcherResponse;
import com.researchintelligence.platform.researchers.api.ResearcherSummaryResponse;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

final class ResearcherMapper {

    private ResearcherMapper() {
    }

    static ResearcherSummaryResponse toSummary(ResearcherEntity entity, String primaryAffiliationName) {
        return toSummary(entity, primaryAffiliationName, null, null, false, false, false);
    }

    static ResearcherSummaryResponse toSummary(
        ResearcherEntity entity,
        String primaryAffiliationName,
        String submittedBy,
        String validatedBy,
        boolean canEdit,
        boolean canSubmit,
        boolean canValidate
    ) {
        return new ResearcherSummaryResponse(
            entity.getId(),
            entity.getFullName(),
            entity.getDisplayName(),
            entity.getEmail(),
            entity.getOrcid(),
            entity.isActive(),
            primaryAffiliationName,
            entity.getValidationStatus(),
            entity.getValidationComment(),
            entity.getCreatedAt(),
            submittedBy,
            entity.getValidatedAt(),
            validatedBy,
            canEdit,
            canSubmit,
            canValidate
        );
    }

    static ResearcherResponse toResponse(ResearcherEntity entity, List<ResearcherAffiliationResponse> affiliations) {
        return new ResearcherResponse(
            entity.getId(),
            entity.getFullName(),
            entity.getDisplayName(),
            entity.getEmail(),
            entity.getOrcid(),
            entity.isActive(),
            entity.getValidationStatus(),
            entity.getValidationComment(),
            entity.getCreatedAt(),
            null,
            entity.getValidatedAt(),
            null,
            false,
            false,
            false,
            affiliations,
            affiliations,
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getCreatedByUserId(),
            entity.getUpdatedByUserId()
        );
    }

    static ResearcherEntity toEntity(ResearcherRequest request) {
        return new ResearcherEntity(
            request.fullName(),
            request.displayName(),
            request.email(),
            blankToNull(request.orcid()),
            request.active() == null || request.active()
        );
    }

    static void updateEntity(ResearcherEntity entity, ResearcherRequest request) {
        entity.setFullName(request.fullName());
        entity.setDisplayName(request.displayName());
        entity.setEmail(request.email());
        entity.setOrcid(blankToNull(request.orcid()));
        entity.setActive(request.active() == null || request.active());
    }

    static void updateOwnProfileEntity(ResearcherEntity entity, ResearcherRequest request) {
        entity.setFullName(request.fullName());
        entity.setDisplayName(request.displayName());
        entity.setEmail(request.email());
        entity.setOrcid(blankToNull(request.orcid()));
    }

    static ResearcherAffiliationEntity toAffiliationEntity(Long researcherId, ResearcherAffiliationRequest request) {
        return new ResearcherAffiliationEntity(
            researcherId,
            request.researchUnitId(),
            request.role(),
            request.affiliationType(),
            request.startDate(),
            request.endDate(),
            request.primaryAffiliation() != null && request.primaryAffiliation()
        );
    }

    static void updateAffiliationEntity(ResearcherAffiliationEntity entity, ResearcherAffiliationRequest request) {
        entity.setResearchUnitId(request.researchUnitId());
        entity.setRole(request.role());
        entity.setAffiliationType(request.affiliationType());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setPrimaryAffiliation(request.primaryAffiliation() != null && request.primaryAffiliation());
    }

    static ResearcherAffiliationResponse toAffiliationResponse(
        ResearcherAffiliationEntity entity,
        Map<Long, String> researchUnitNames,
        LocalDate today
    ) {
        return toAffiliationResponse(entity, researchUnitNames, today, null, null, false, false, false);
    }

    static ResearcherAffiliationResponse toAffiliationResponse(
        ResearcherAffiliationEntity entity,
        Map<Long, String> researchUnitNames,
        LocalDate today,
        String submittedBy,
        String validatedBy,
        boolean canEdit,
        boolean canSubmit,
        boolean canValidate
    ) {
        return new ResearcherAffiliationResponse(
            entity.getId(),
            entity.getResearcherId(),
            entity.getResearchUnitId(),
            researchUnitNames.get(entity.getResearchUnitId()),
            entity.getRole(),
            entity.getAffiliationType(),
            entity.getStartDate(),
            entity.getEndDate(),
            entity.isPrimaryAffiliation(),
            isCurrent(entity, today),
            entity.getValidationStatus(),
            entity.getValidationComment(),
            entity.getCreatedAt(),
            submittedBy,
            entity.getValidatedAt(),
            validatedBy,
            canEdit,
            canSubmit,
            canValidate,
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getCreatedByUserId(),
            entity.getUpdatedByUserId()
        );
    }

    static ResearcherAffiliationPublicResponse toPublicAffiliationResponse(
        ResearcherAffiliationEntity entity,
        Map<Long, String> researchUnitNames,
        LocalDate today
    ) {
        return new ResearcherAffiliationPublicResponse(
            entity.getId(),
            entity.getResearcherId(),
            entity.getResearchUnitId(),
            researchUnitNames.get(entity.getResearchUnitId()),
            entity.getRole(),
            entity.getAffiliationType(),
            entity.getStartDate(),
            entity.getEndDate(),
            entity.isPrimaryAffiliation(),
            isCurrent(entity, today),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    static boolean isCurrent(ResearcherAffiliationEntity entity, LocalDate today) {
        return entity.getEndDate() == null || !entity.getEndDate().isBefore(today);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
