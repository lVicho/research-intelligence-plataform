package com.researchintelligence.platform.researchunits.application;

import com.researchintelligence.platform.researchunits.api.ResearchUnitListResponse;
import com.researchintelligence.platform.researchunits.api.ResearchUnitRequest;
import com.researchintelligence.platform.researchunits.api.ResearchUnitResponse;
import com.researchintelligence.platform.researchunits.domain.OrganizationScope;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;

final class ResearchUnitMapper {

    private ResearchUnitMapper() {
    }

    static ResearchUnitResponse toResponse(ResearchUnitEntity entity) {
        return toResponse(entity, true, null, null, false, false, false);
    }

    static ResearchUnitListResponse toListResponse(ResearchUnitEntity entity) {
        return toListResponse(entity, null, null, false, false, false);
    }

    static ResearchUnitListResponse toListResponse(
        ResearchUnitEntity entity,
        String submittedBy,
        String validatedBy,
        boolean canEdit,
        boolean canSubmit,
        boolean canValidate
    ) {
        return new ResearchUnitListResponse(
            entity.getId(),
            entity.getName(),
            entity.getShortName(),
            entity.getType(),
            entity.getParentId(),
            entity.getCountry(),
            entity.getCity(),
            entity.getWebsite(),
            entity.isActive(),
            entity.isVisibleInPortal(),
            entity.getOrganizationScope(),
            entity.getPublicDescription(),
            entity.getInternalDescription(),
            entity.getResponsibleResearcherId(),
            entity.getFeatured(),
            entity.getSortOrder(),
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
            entity.getUpdatedAt()
        );
    }

    static ResearchUnitResponse toResponse(
        ResearchUnitEntity entity,
        boolean includeAuditUsers,
        String submittedBy,
        String validatedBy,
        boolean canEdit,
        boolean canSubmit,
        boolean canValidate
    ) {
        return new ResearchUnitResponse(
            entity.getId(),
            entity.getName(),
            entity.getShortName(),
            entity.getType(),
            entity.getParentId(),
            entity.getCountry(),
            entity.getCity(),
            entity.getWebsite(),
            entity.isActive(),
            entity.isVisibleInPortal(),
            entity.getOrganizationScope(),
            entity.getPublicDescription(),
            entity.getInternalDescription(),
            entity.getResponsibleResearcherId(),
            entity.getFeatured(),
            entity.getSortOrder(),
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
            includeAuditUsers ? entity.getCreatedByUserId() : null,
            includeAuditUsers ? entity.getUpdatedByUserId() : null
        );
    }

    static ResearchUnitEntity toEntity(ResearchUnitRequest request) {
        ResearchUnitEntity entity = new ResearchUnitEntity(
            request.name(),
            request.shortName(),
            request.type(),
            request.parentId(),
            request.country(),
            request.city(),
            request.website(),
            request.active() == null || request.active()
        );
        entity.setOrganizationScope(request.organizationScope());
        entity.setVisibleInPortal(visibleInPortal(request));
        entity.setPublicDescription(blankToNull(request.publicDescription()));
        entity.setInternalDescription(blankToNull(request.internalDescription()));
        entity.setResponsibleResearcherId(request.responsibleResearcherId());
        entity.setFeatured(request.featured() == null ? Boolean.FALSE : request.featured());
        entity.setSortOrder(request.sortOrder());
        return entity;
    }

    static void updateEntity(ResearchUnitEntity entity, ResearchUnitRequest request) {
        entity.setName(request.name());
        entity.setShortName(request.shortName());
        entity.setType(request.type());
        entity.setParentId(request.parentId());
        entity.setCountry(request.country());
        entity.setCity(request.city());
        entity.setWebsite(request.website());
        entity.setActive(request.active() == null || request.active());
        entity.setOrganizationScope(request.organizationScope());
        entity.setVisibleInPortal(visibleInPortal(request));
        entity.setPublicDescription(blankToNull(request.publicDescription()));
        entity.setInternalDescription(blankToNull(request.internalDescription()));
        entity.setResponsibleResearcherId(request.responsibleResearcherId());
        entity.setFeatured(request.featured() == null ? Boolean.FALSE : request.featured());
        entity.setSortOrder(request.sortOrder());
    }

    private static boolean visibleInPortal(ResearchUnitRequest request) {
        if (request.organizationScope() == OrganizationScope.EXTERNAL) {
            return false;
        }
        return request.visibleInPortal() == null || request.visibleInPortal();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
