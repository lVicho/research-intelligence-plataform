package com.researchintelligence.platform.events.application;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.audit.application.AuditFieldChange;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.events.api.EventParticipationRequest;
import com.researchintelligence.platform.events.api.EventParticipationResponse;
import com.researchintelligence.platform.events.persistence.EventParticipationEntity;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.events.persistence.ScientificEventRepository;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.shared.visibility.ValidableVisibilitySpecifications;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class EventParticipationService {

    private final EventParticipationRepository repository;
    private final ScientificEventRepository eventRepository;
    private final ResearcherRepository researcherRepository;
    private final ResearchUnitRepository researchUnitRepository;
    private final PublicationRepository publicationRepository;
    private final PublicationAuthorRepository publicationAuthorRepository;
    private final VisibilityContext visibilityContext;
    private final ActivityAuditService auditService;

    public EventParticipationService(
        EventParticipationRepository repository,
        ScientificEventRepository eventRepository,
        ResearcherRepository researcherRepository,
        ResearchUnitRepository researchUnitRepository,
        PublicationRepository publicationRepository,
        PublicationAuthorRepository publicationAuthorRepository,
        VisibilityContext visibilityContext,
        ActivityAuditService auditService
    ) {
        this.repository = repository;
        this.eventRepository = eventRepository;
        this.researcherRepository = researcherRepository;
        this.researchUnitRepository = researchUnitRepository;
        this.publicationRepository = publicationRepository;
        this.publicationAuthorRepository = publicationAuthorRepository;
        this.visibilityContext = visibilityContext;
        this.auditService = auditService;
    }

    public PageResponse<EventParticipationResponse> search(
        int page,
        int size,
        String text,
        Long eventId,
        Long researcherId,
        Long researchUnitId,
        ValidationStatus validationStatus
    ) {
        Specification<EventParticipationEntity> specification = matches(text, eventId, researcherId, researchUnitId, validationStatus)
            .and(visibleSpecification());
        PageRequest pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.DESC, "participationDate").and(Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResponse.from(repository.findAll(specification, pageable).map(this::toResponse));
    }

    public EventParticipationResponse findById(Long id) {
        EventParticipationEntity entity = repository.findOne(hasId(id).and(visibleSpecification()))
            .orElseThrow(() -> new ResourceNotFoundException("EventParticipation", id));
        return toResponse(entity);
    }

    @Transactional
    public EventParticipationResponse create(EventParticipationRequest request, PlatformUserPrincipal user) {
        validateRequest(request, user);
        ValidationStatus initialStatus = initialStatus(request.validationStatus(), user);
        EventParticipationEntity entity = repository.save(new EventParticipationEntity(
            request.eventId(),
            request.researcherId(),
            request.researchUnitId(),
            request.participationTypeCode(),
            request.title(),
            blankToNull(request.description()),
            blankToNull(request.evidenceUrl()),
            request.participationDate(),
            request.relatedPublicationId(),
            initialStatus
        ));
        applyLifecycleDates(entity, null, initialStatus);
        auditService.recordCreated(ValidationEntityType.EVENT_PARTICIPATION, entity.getId(), entity.getValidationStatus());
        return toResponse(entity);
    }

    @Transactional
    public EventParticipationResponse update(Long id, EventParticipationRequest request, PlatformUserPrincipal user) {
        EventParticipationEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("EventParticipation", id));
        authorizeWrite(entity, user);
        validateRequest(request, user);
        if (!isAdmin(user) && !isResearcherEditable(entity.getValidationStatus())) {
            throw new BusinessRuleException("Only draft event participations or participations with requested changes can be edited.");
        }

        ValidationStatus previousStatus = entity.getValidationStatus();
        ValidationStatus nextStatus = nextStatusForUpdate(entity, request, user);
        Map<String, AuditFieldChange> changes = changes(entity, request, nextStatus);
        entity.setEventId(request.eventId());
        entity.setResearcherId(request.researcherId());
        entity.setResearchUnitId(request.researchUnitId());
        entity.setParticipationTypeCode(request.participationTypeCode());
        entity.setTitle(request.title());
        entity.setDescription(blankToNull(request.description()));
        entity.setEvidenceUrl(blankToNull(request.evidenceUrl()));
        entity.setParticipationDate(request.participationDate());
        entity.setRelatedPublicationId(request.relatedPublicationId());
        entity.setValidationStatus(nextStatus);
        applyLifecycleDates(entity, previousStatus, nextStatus);
        auditService.recordUpdated(ValidationEntityType.EVENT_PARTICIPATION, id, previousStatus, nextStatus, changes);
        return toResponse(entity);
    }

    @Transactional
    public EventParticipationResponse submit(Long id, PlatformUserPrincipal user) {
        EventParticipationEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("EventParticipation", id));
        authorizeWrite(entity, user);
        if (!isResearcherEditable(entity.getValidationStatus())) {
            throw new BusinessRuleException("Only draft event participations or participations with requested changes can be submitted.");
        }
        ValidationStatus previousStatus = entity.getValidationStatus();
        entity.setValidationStatus(ValidationStatus.PENDING_VALIDATION);
        entity.setSubmittedAt(Instant.now());
        entity.setValidatedAt(null);
        auditService.recordStatusChange(
            ValidationEntityType.EVENT_PARTICIPATION,
            entity.getId(),
            previousStatus,
            ValidationStatus.PENDING_VALIDATION,
            null
        );
        return toResponse(entity);
    }

    private void validateRequest(EventParticipationRequest request, PlatformUserPrincipal user) {
        requireAuthenticatedWriter(user);
        if (!eventRepository.existsById(request.eventId())) {
            throw new ResourceNotFoundException("ScientificEvent", request.eventId());
        }
        if (!researcherRepository.existsById(request.researcherId())) {
            throw new ResourceNotFoundException("Researcher", request.researcherId());
        }
        if (request.researchUnitId() != null && !researchUnitRepository.existsById(request.researchUnitId())) {
            throw new ResourceNotFoundException("ResearchUnit", request.researchUnitId());
        }
        if (request.relatedPublicationId() != null && !publicationRepository.existsById(request.relatedPublicationId())) {
            throw new ResourceNotFoundException("Publication", request.relatedPublicationId());
        }
        if (!isAdmin(user)) {
            Long linkedResearcherId = requireLinkedResearcher(user);
            if (!linkedResearcherId.equals(request.researcherId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Event participation does not belong to the current researcher.");
            }
            if (request.relatedPublicationId() != null
                && !publicationAuthorRepository.existsByPublicationIdAndResearcherId(request.relatedPublicationId(), linkedResearcherId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Related publication does not belong to the current researcher.");
            }
        }
    }

    private void authorizeWrite(EventParticipationEntity entity, PlatformUserPrincipal user) {
        requireAuthenticatedWriter(user);
        if (isAdmin(user)) {
            return;
        }
        Long linkedResearcherId = requireLinkedResearcher(user);
        if (!linkedResearcherId.equals(entity.getResearcherId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Event participation does not belong to the current researcher.");
        }
    }

    private ValidationStatus initialStatus(ValidationStatus requestedStatus, PlatformUserPrincipal user) {
        if (isAdmin(user) && requestedStatus != null) {
            return requestedStatus;
        }
        return ValidationStatus.DRAFT;
    }

    private ValidationStatus nextStatusForUpdate(
        EventParticipationEntity entity,
        EventParticipationRequest request,
        PlatformUserPrincipal user
    ) {
        if (isAdmin(user) && request.validationStatus() != null) {
            return request.validationStatus();
        }
        if (isAdmin(user)) {
            return entity.getValidationStatus();
        }
        return ValidationStatus.DRAFT;
    }

    private void applyLifecycleDates(EventParticipationEntity entity, ValidationStatus previousStatus, ValidationStatus nextStatus) {
        if (nextStatus == ValidationStatus.PENDING_VALIDATION && entity.getSubmittedAt() == null) {
            entity.setSubmittedAt(Instant.now());
        }
        if (nextStatus == ValidationStatus.VALIDATED && entity.getValidatedAt() == null) {
            if (entity.getSubmittedAt() == null) {
                entity.setSubmittedAt(Instant.now());
            }
            entity.setValidatedAt(Instant.now());
        }
        if (previousStatus != null && nextStatus == ValidationStatus.DRAFT && previousStatus != ValidationStatus.DRAFT) {
            entity.setSubmittedAt(null);
            entity.setValidatedAt(null);
        }
    }

    private boolean isResearcherEditable(ValidationStatus status) {
        return status == ValidationStatus.DRAFT || status == ValidationStatus.CHANGES_REQUESTED;
    }

    private Specification<EventParticipationEntity> matches(
        String text,
        Long eventId,
        Long researcherId,
        Long researchUnitId,
        ValidationStatus validationStatus
    ) {
        String normalizedText = blankToNull(text);
        String textPattern = normalizedText == null ? null : "%" + normalizedText.toLowerCase() + "%";
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (textPattern != null) {
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root.get("description"), criteriaBuilder), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root.get("participationTypeCode"), criteriaBuilder), textPattern)
                ));
            }
            if (eventId != null) {
                predicates.add(criteriaBuilder.equal(root.get("eventId"), eventId));
            }
            if (researcherId != null) {
                predicates.add(criteriaBuilder.equal(root.get("researcherId"), researcherId));
            }
            if (researchUnitId != null) {
                predicates.add(criteriaBuilder.equal(root.get("researchUnitId"), researchUnitId));
            }
            if (validationStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("validationStatus"), validationStatus));
            }
            return predicates.isEmpty() ? criteriaBuilder.conjunction() : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<EventParticipationEntity> visibleSpecification() {
        return ValidableVisibilitySpecifications.visibleTo(
            visibilityContext.defaultScope(),
            visibilityContext.linkedResearcherId().orElse(null),
            (root, query, criteriaBuilder, researcherId) -> criteriaBuilder.equal(root.get("researcherId"), researcherId)
        );
    }

    private Specification<EventParticipationEntity> hasId(Long id) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("id"), id);
    }

    private Expression<String> lowerCoalesced(Expression<String> expression, jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.lower(criteriaBuilder.coalesce(expression, ""));
    }

    private Map<String, AuditFieldChange> changes(
        EventParticipationEntity entity,
        EventParticipationRequest request,
        ValidationStatus nextStatus
    ) {
        Map<String, AuditFieldChange> changes = auditService.changes();
        auditService.addChange(changes, "eventId", entity.getEventId(), request.eventId());
        auditService.addChange(changes, "researcherId", entity.getResearcherId(), request.researcherId());
        auditService.addChange(changes, "researchUnitId", entity.getResearchUnitId(), request.researchUnitId());
        auditService.addChange(changes, "participationTypeCode", entity.getParticipationTypeCode(), request.participationTypeCode());
        auditService.addChange(changes, "title", entity.getTitle(), request.title());
        auditService.addChange(changes, "description", entity.getDescription(), blankToNull(request.description()));
        auditService.addChange(changes, "evidenceUrl", entity.getEvidenceUrl(), blankToNull(request.evidenceUrl()));
        auditService.addChange(changes, "participationDate", entity.getParticipationDate(), request.participationDate());
        auditService.addChange(changes, "relatedPublicationId", entity.getRelatedPublicationId(), request.relatedPublicationId());
        auditService.addChange(changes, "validationStatus", entity.getValidationStatus(), nextStatus);
        return changes;
    }

    private EventParticipationResponse toResponse(EventParticipationEntity entity) {
        String researcherName = researcherRepository.findById(entity.getResearcherId()).map(ResearcherEntity::getFullName).orElse(null);
        return new EventParticipationResponse(
            entity.getId(),
            entity.getEventId(),
            eventRepository.findById(entity.getEventId()).map(event -> event.getName()).orElse(null),
            entity.getResearcherId(),
            researcherName,
            entity.getResearchUnitId(),
            entity.getResearchUnitId() == null ? null : researchUnitRepository.findById(entity.getResearchUnitId()).map(ResearchUnitEntity::getName).orElse(null),
            entity.getParticipationTypeCode(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getEvidenceUrl(),
            entity.getParticipationDate(),
            entity.getRelatedPublicationId(),
            entity.getRelatedPublicationId() == null ? null : publicationRepository.findById(entity.getRelatedPublicationId()).map(PublicationEntity::getTitle).orElse(null),
            entity.getValidationStatus(),
            entity.getSubmittedAt(),
            canReadWorkflowActors(entity) ? researcherName : null,
            entity.getValidatedAt(),
            null,
            entity.getValidationComment(),
            canEdit(entity),
            canSubmit(entity),
            canValidate(entity.getValidationStatus()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private void requireAuthenticatedWriter(PlatformUserPrincipal user) {
        if (user == null || (!isAdmin(user) && !user.roles().contains("RESEARCHER"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is required to manage event participations.");
        }
    }

    private boolean isAdmin(PlatformUserPrincipal user) {
        return user != null && user.roles().contains("ADMIN");
    }

    private boolean canEdit(EventParticipationEntity entity) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN")) {
            return true;
        }
        return roles.contains("RESEARCHER")
            && isResearcherEditable(entity.getValidationStatus())
            && visibilityContext.linkedResearcherId()
                .map(researcherId -> researcherId.equals(entity.getResearcherId()))
                .orElse(false);
    }

    private boolean canSubmit(EventParticipationEntity entity) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN")) {
            return isResearcherEditable(entity.getValidationStatus());
        }
        return roles.contains("RESEARCHER")
            && isResearcherEditable(entity.getValidationStatus())
            && visibilityContext.linkedResearcherId()
                .map(researcherId -> researcherId.equals(entity.getResearcherId()))
                .orElse(false);
    }

    private boolean canValidate(ValidationStatus validationStatus) {
        Set<String> roles = currentRoles();
        return validationStatus == ValidationStatus.PENDING_VALIDATION
            && (roles.contains("ADMIN") || roles.contains("VALIDATOR"));
    }

    private boolean canReadWorkflowActors(EventParticipationEntity entity) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN") || roles.contains("VALIDATOR")) {
            return true;
        }
        return roles.contains("RESEARCHER")
            && visibilityContext.linkedResearcherId()
                .map(researcherId -> researcherId.equals(entity.getResearcherId()))
                .orElse(false);
    }

    private Set<String> currentRoles() {
        Set<String> roles = visibilityContext.currentRoles();
        return roles == null ? Set.of() : roles;
    }

    private Long requireLinkedResearcher(PlatformUserPrincipal user) {
        if (user == null || user.researcherId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current user is not linked to a researcher.");
        }
        return user.researcherId();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
