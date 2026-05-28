package com.researchintelligence.platform.researchunits.application;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.audit.application.AuditFieldChange;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.auth.persistence.UserRepository;
import com.researchintelligence.platform.researchunits.domain.OrganizationScope;
import com.researchintelligence.platform.researchunits.api.ResearchUnitRequest;
import com.researchintelligence.platform.researchunits.api.ResearchUnitListResponse;
import com.researchintelligence.platform.researchunits.api.ResearchUnitResponse;
import com.researchintelligence.platform.researchunits.api.ResearchUnitTreeNode;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ResearchUnitService {

    private final ResearchUnitRepository repository;
    private final ResearchUnitTreeBuilder treeBuilder;
    private final UserRepository userRepository;
    private final VisibilityContext visibilityContext;
    private final ActivityAuditService auditService;

    public ResearchUnitService(
        ResearchUnitRepository repository,
        ResearchUnitTreeBuilder treeBuilder,
        UserRepository userRepository,
        VisibilityContext visibilityContext,
        ActivityAuditService auditService
    ) {
        this.repository = repository;
        this.treeBuilder = treeBuilder;
        this.userRepository = userRepository;
        this.visibilityContext = visibilityContext;
        this.auditService = auditService;
    }

    public List<ResearchUnitListResponse> findAll() {
        List<ResearchUnitEntity> visibleUnits = repository.findAll(Sort.by(Sort.Direction.ASC, "name"))
            .stream()
            .filter(entity -> isVisible(entity, defaultVisibilityScope()))
            .toList();
        Map<Long, String> userDisplayNames = userDisplayNames(workflowUserIds(visibleUnits));
        return visibleUnits.stream()
            .map(entity -> toListResponse(entity, userDisplayNames))
            .toList();
    }

    public PageResponse<ResearchUnitListResponse> searchPublicValidated(int page, int size, String text, ResearchUnitType type) {
        String normalizedText = blankToNull(text);
        String textPattern = normalizedText == null ? "%" : "%" + normalizedText.toLowerCase() + "%";
        PageRequest pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.ASC, "name")
        );
        Page<ResearchUnitEntity> units = repository.searchPublicValidated(
            normalizedText,
            textPattern,
            type,
            ValidationStatus.VALIDATED,
            pageable
        );
        List<ResearchUnitListResponse> content = units.getContent().stream()
            .filter(entity -> isVisible(entity, VisibilityScope.PUBLIC_VALIDATED))
            .map(entity -> toListResponse(entity, Map.of()))
            .toList();
        return new PageResponse<>(
            content,
            units.getNumber(),
            units.getSize(),
            units.getTotalElements(),
            units.getTotalPages(),
            units.isLast()
        );
    }

    public PageResponse<ResearchUnitListResponse> searchPortalVisibleValidated(int page, int size, String text, ResearchUnitType type) {
        String normalizedText = blankToNull(text);
        String textPattern = normalizedText == null ? "%" : "%" + normalizedText.toLowerCase() + "%";
        PageRequest pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.DESC, "featured")
                .and(Sort.by(Sort.Direction.ASC, "sortOrder"))
                .and(Sort.by(Sort.Direction.ASC, "name"))
        );
        Page<ResearchUnitEntity> units = repository.searchPortalVisibleValidated(
            normalizedText,
            textPattern,
            type,
            ValidationStatus.VALIDATED,
            OrganizationScope.INTERNAL,
            pageable
        );
        List<ResearchUnitListResponse> content = units.getContent().stream()
            .filter(entity -> isVisible(entity, VisibilityScope.PUBLIC_VALIDATED))
            .filter(ResearchUnitEntity::isVisibleInPortal)
            .filter(entity -> entity.getOrganizationScope() == OrganizationScope.INTERNAL)
            .filter(ResearchUnitEntity::isActive)
            .map(entity -> toListResponse(entity, Map.of()))
            .toList();
        return new PageResponse<>(
            content,
            units.getNumber(),
            units.getSize(),
            units.getTotalElements(),
            units.getTotalPages(),
            units.isLast()
        );
    }

    public List<ResearchUnitTreeNode> findTree() {
        return treeBuilder.build(repository.findAll(Sort.by(Sort.Direction.ASC, "name"))
            .stream()
            .filter(entity -> isVisible(entity, defaultVisibilityScope()))
            .toList());
    }

    public ResearchUnitResponse findById(Long id) {
        ResearchUnitEntity entity = findEntity(id);
        if (!isVisible(entity, defaultVisibilityScope())) {
            throw new ResourceNotFoundException("Research unit", id);
        }
        return toResponse(entity);
    }

    public ResearchUnitResponse findPublicValidatedById(Long id) {
        ResearchUnitEntity entity = findEntity(id);
        if (!isVisible(entity, VisibilityScope.PUBLIC_VALIDATED) || !isPublicValidatedUnit(id)) {
            throw new ResourceNotFoundException("Research unit", id);
        }
        return toResponse(entity);
    }

    public ResearchUnitResponse findPortalVisibleValidatedById(Long id) {
        ResearchUnitEntity entity = findEntity(id);
        if (!isVisible(entity, VisibilityScope.PUBLIC_VALIDATED) || !isPortalVisibleInternalUnit(entity) || !isPortalVisibleValidatedUnit(id)) {
            throw new ResourceNotFoundException("Research unit", id);
        }
        return toResponse(entity);
    }

    public List<ResearchUnitListResponse> findPublicChildUnits(Long parentId) {
        return repository.findByParentIdAndValidationStatusOrderByNameAsc(parentId, ValidationStatus.VALIDATED)
            .stream()
            .filter(entity -> isVisible(entity, VisibilityScope.PUBLIC_VALIDATED))
            .filter(entity -> isPublicValidatedUnit(entity.getId()))
            .map(entity -> toListResponse(entity, Map.of()))
            .toList();
    }

    public List<ResearchUnitListResponse> findPortalVisibleChildUnits(Long parentId) {
        return repository.findByParentIdAndValidationStatusOrderByNameAsc(parentId, ValidationStatus.VALIDATED)
            .stream()
            .filter(entity -> isVisible(entity, VisibilityScope.PUBLIC_VALIDATED))
            .filter(ResearchUnitEntity::isVisibleInPortal)
            .filter(this::isPortalVisibleInternalUnit)
            .filter(entity -> isPortalVisibleValidatedUnit(entity.getId()))
            .map(entity -> toListResponse(entity, Map.of()))
            .toList();
    }

    public long countPortalVisibleValidated() {
        return repository.countPortalVisibleValidated(ValidationStatus.VALIDATED, OrganizationScope.INTERNAL);
    }

    @Transactional
    public ResearchUnitResponse create(ResearchUnitRequest request) {
        validateParent(request.parentId(), null);
        ResearchUnitEntity saved = repository.save(ResearchUnitMapper.toEntity(request));
        auditService.recordCreated(ValidationEntityType.RESEARCH_UNIT, saved.getId(), saved.getValidationStatus());
        return toResponse(saved);
    }

    @Transactional
    public ResearchUnitResponse update(Long id, ResearchUnitRequest request) {
        ResearchUnitEntity entity = findEntity(id);
        validateParent(request.parentId(), id);
        ValidationStatus previousStatus = entity.getValidationStatus();
        boolean wasActive = entity.isActive();
        Map<String, AuditFieldChange> changes = auditService.changes();
        auditService.addChange(changes, "name", entity.getName(), request.name());
        auditService.addChange(changes, "shortName", entity.getShortName(), request.shortName());
        auditService.addChange(changes, "type", entity.getType(), request.type());
        auditService.addChange(changes, "parentId", entity.getParentId(), request.parentId());
        auditService.addChange(changes, "country", entity.getCountry(), request.country());
        auditService.addChange(changes, "city", entity.getCity(), request.city());
        auditService.addChange(changes, "website", entity.getWebsite(), request.website());
        auditService.addChange(changes, "active", entity.isActive(), request.active() == null || request.active());
        auditService.addChange(changes, "organizationScope", entity.getOrganizationScope(), effectiveOrganizationScope(request));
        auditService.addChange(
            changes,
            "visibleInPortal",
            entity.isVisibleInPortal(),
            effectiveVisibleInPortal(request)
        );
        auditService.addChange(changes, "publicDescription", entity.getPublicDescription(), blankToNull(request.publicDescription()));
        auditService.addChange(changes, "internalDescription", entity.getInternalDescription(), blankToNull(request.internalDescription()));
        auditService.addChange(changes, "responsibleResearcherId", entity.getResponsibleResearcherId(), request.responsibleResearcherId());
        auditService.addChange(changes, "featured", entity.getFeatured(), request.featured() == null ? Boolean.FALSE : request.featured());
        auditService.addChange(changes, "sortOrder", entity.getSortOrder(), request.sortOrder());
        ResearchUnitMapper.updateEntity(entity, request);
        auditService.recordUpdated(ValidationEntityType.RESEARCH_UNIT, id, previousStatus, entity.getValidationStatus(), changes);
        if (wasActive && !entity.isActive()) {
            auditService.recordArchived(ValidationEntityType.RESEARCH_UNIT, id, entity.getValidationStatus());
        }
        return toResponse(entity);
    }

    private ResearchUnitResponse toResponse(ResearchUnitEntity entity) {
        Map<Long, String> userDisplayNames = userDisplayNames(workflowUserIds(List.of(entity)));
        return ResearchUnitMapper.toResponse(
            entity,
            true,
            submittedBy(entity, userDisplayNames),
            validatedBy(entity, userDisplayNames),
            canEdit(),
            false,
            canValidate(entity.getValidationStatus())
        );
    }

    private ResearchUnitListResponse toListResponse(ResearchUnitEntity entity, Map<Long, String> userDisplayNames) {
        return ResearchUnitMapper.toListResponse(
            entity,
            submittedBy(entity, userDisplayNames),
            validatedBy(entity, userDisplayNames),
            canEdit(),
            false,
            canValidate(entity.getValidationStatus())
        );
    }

    private String submittedBy(ResearchUnitEntity entity, Map<Long, String> userDisplayNames) {
        if (entity.getCreatedByUserId() == null) {
            return null;
        }
        return canReadWorkflowActors() ? userDisplayNames.get(entity.getCreatedByUserId()) : null;
    }

    private String validatedBy(ResearchUnitEntity entity, Map<Long, String> userDisplayNames) {
        if (entity.getValidatedByUserId() == null) {
            return null;
        }
        return canReadWorkflowActors() ? userDisplayNames.get(entity.getValidatedByUserId()) : null;
    }

    private boolean canEdit() {
        return currentRoles().contains("ADMIN");
    }

    private boolean canValidate(ValidationStatus validationStatus) {
        Set<String> roles = currentRoles();
        return validationStatus == ValidationStatus.PENDING_VALIDATION
            && (roles.contains("ADMIN") || roles.contains("VALIDATOR"));
    }

    private boolean canReadWorkflowActors() {
        Set<String> roles = currentRoles();
        return roles.contains("ADMIN") || roles.contains("VALIDATOR");
    }

    private Set<String> currentRoles() {
        Set<String> roles = visibilityContext.currentRoles();
        return roles == null ? Set.of() : roles;
    }

    private Set<Long> workflowUserIds(Collection<ResearchUnitEntity> entities) {
        return entities.stream()
            .flatMap(entity -> java.util.stream.Stream.of(entity.getCreatedByUserId(), entity.getValidatedByUserId()))
            .filter(id -> id != null)
            .collect(Collectors.toSet());
    }

    private Map<Long, String> userDisplayNames(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds)
            .stream()
            .collect(Collectors.toMap(UserEntity::getId, UserEntity::getDisplayName, (first, second) -> first));
    }

    private ResearchUnitEntity findEntity(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Research unit", id));
    }

    private VisibilityScope defaultVisibilityScope() {
        VisibilityScope scope = visibilityContext.defaultScope();
        return scope == null ? VisibilityScope.PUBLIC_VALIDATED : scope;
    }

    private boolean isVisible(ResearchUnitEntity entity, VisibilityScope visibilityScope) {
        return visibilityScope == VisibilityScope.ADMIN_ALL
            || entity.getValidationStatus() == ValidationStatus.VALIDATED;
    }

    private boolean isPublicValidatedUnit(Long id) {
        return repository.countPublicValidatedById(id, ValidationStatus.VALIDATED) > 0;
    }

    private boolean isPortalVisibleValidatedUnit(Long id) {
        return repository.countPortalVisibleValidatedById(id, ValidationStatus.VALIDATED, OrganizationScope.INTERNAL) > 0;
    }

    private boolean isPortalVisibleInternalUnit(ResearchUnitEntity entity) {
        return entity.isActive()
            && entity.isVisibleInPortal()
            && entity.getOrganizationScope() == OrganizationScope.INTERNAL;
    }

    private OrganizationScope effectiveOrganizationScope(ResearchUnitRequest request) {
        return request.organizationScope() == null ? OrganizationScope.INTERNAL : request.organizationScope();
    }

    private boolean effectiveVisibleInPortal(ResearchUnitRequest request) {
        return effectiveOrganizationScope(request) == OrganizationScope.EXTERNAL
            ? false
            : request.visibleInPortal() == null || request.visibleInPortal();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateParent(Long parentId, Long currentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(currentId)) {
            throw new BusinessRuleException("A research unit cannot be its own parent.");
        }

        ResearchUnitEntity parent = repository.findById(parentId)
            .orElseThrow(() -> new ResourceNotFoundException("Parent research unit", parentId));
        Long ancestorId = parent.getParentId();
        while (ancestorId != null) {
            if (ancestorId.equals(currentId)) {
                throw new BusinessRuleException("A research unit cannot use one of its descendants as parent.");
            }
            ancestorId = repository.findById(ancestorId)
                .map(ResearchUnitEntity::getParentId)
                .orElse(null);
        }
    }
}
