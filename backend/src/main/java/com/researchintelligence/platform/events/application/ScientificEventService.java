package com.researchintelligence.platform.events.application;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.audit.application.AuditFieldChange;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.events.api.ScientificEventRequest;
import com.researchintelligence.platform.events.api.ScientificEventResponse;
import com.researchintelligence.platform.events.persistence.ScientificEventEntity;
import com.researchintelligence.platform.events.persistence.ScientificEventRepository;
import com.researchintelligence.platform.publications.persistence.VenueRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.shared.visibility.ValidableVisibilitySpecifications;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ScientificEventService {

    private final ScientificEventRepository repository;
    private final VenueRepository venueRepository;
    private final VisibilityContext visibilityContext;
    private final ActivityAuditService auditService;

    public ScientificEventService(
        ScientificEventRepository repository,
        VenueRepository venueRepository,
        VisibilityContext visibilityContext,
        ActivityAuditService auditService
    ) {
        this.repository = repository;
        this.venueRepository = venueRepository;
        this.visibilityContext = visibilityContext;
        this.auditService = auditService;
    }

    public PageResponse<ScientificEventResponse> search(
        int page,
        int size,
        String text,
        String eventTypeCode,
        Long venueId,
        Boolean active,
        ValidationStatus validationStatus
    ) {
        Specification<ScientificEventEntity> specification = matches(text, eventTypeCode, venueId, active, validationStatus)
            .and(visibleSpecification());
        PageRequest pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.DESC, "startDate").and(Sort.by(Sort.Direction.ASC, "name"))
        );
        return PageResponse.from(repository.findAll(specification, pageable).map(this::toResponse));
    }

    public ScientificEventResponse findById(Long id) {
        ScientificEventEntity entity = repository.findOne(hasId(id).and(visibleSpecification()))
            .orElseThrow(() -> new ResourceNotFoundException("ScientificEvent", id));
        return toResponse(entity);
    }

    @Transactional
    public ScientificEventResponse create(ScientificEventRequest request) {
        validateRequest(request);
        ScientificEventEntity entity = repository.save(new ScientificEventEntity(
            request.name(),
            blankToNull(request.edition()),
            request.eventTypeCode(),
            request.startDate(),
            request.endDate(),
            blankToNull(request.city()),
            blankToNull(request.country()),
            blankToNull(request.organizer()),
            blankToNull(request.website()),
            blankToNull(request.description()),
            blankToNull(request.evidenceUrl()),
            request.venueId(),
            request.active() == null || request.active(),
            request.validationStatus()
        ));
        auditService.recordCreated(ValidationEntityType.SCIENTIFIC_EVENT, entity.getId(), entity.getValidationStatus());
        return toResponse(entity);
    }

    @Transactional
    public ScientificEventResponse update(Long id, ScientificEventRequest request) {
        validateRequest(request);
        ScientificEventEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ScientificEvent", id));
        boolean wasActive = entity.isActive();
        ValidationStatus previousStatus = entity.getValidationStatus();
        ValidationStatus nextStatus = request.validationStatus() == null ? entity.getValidationStatus() : request.validationStatus();
        Map<String, AuditFieldChange> changes = changes(entity, request, nextStatus);
        entity.setName(request.name());
        entity.setEdition(blankToNull(request.edition()));
        entity.setEventTypeCode(request.eventTypeCode());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setCity(blankToNull(request.city()));
        entity.setCountry(blankToNull(request.country()));
        entity.setOrganizer(blankToNull(request.organizer()));
        entity.setWebsite(blankToNull(request.website()));
        entity.setDescription(blankToNull(request.description()));
        entity.setEvidenceUrl(blankToNull(request.evidenceUrl()));
        entity.setVenueId(request.venueId());
        entity.setActive(request.active() == null || request.active());
        entity.setValidationStatus(nextStatus);
        auditService.recordUpdated(ValidationEntityType.SCIENTIFIC_EVENT, id, previousStatus, nextStatus, changes);
        if (wasActive && !entity.isActive()) {
            auditService.recordArchived(ValidationEntityType.SCIENTIFIC_EVENT, id, entity.getValidationStatus());
        }
        return toResponse(entity);
    }

    private void validateRequest(ScientificEventRequest request) {
        validateDates(request.startDate(), request.endDate());
        if (request.venueId() != null && !venueRepository.existsById(request.venueId())) {
            throw new ResourceNotFoundException("Venue", request.venueId());
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessRuleException("Scientific event end date cannot be before start date.");
        }
    }

    private Specification<ScientificEventEntity> matches(
        String text,
        String eventTypeCode,
        Long venueId,
        Boolean active,
        ValidationStatus validationStatus
    ) {
        String normalizedText = blankToNull(text);
        String textPattern = normalizedText == null ? null : "%" + normalizedText.toLowerCase() + "%";
        String normalizedType = blankToNull(eventTypeCode);
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (textPattern != null) {
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root.get("edition"), criteriaBuilder), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root.get("city"), criteriaBuilder), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root.get("country"), criteriaBuilder), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root.get("organizer"), criteriaBuilder), textPattern)
                ));
            }
            if (normalizedType != null) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("eventTypeCode")), normalizedType.toLowerCase()));
            }
            if (venueId != null) {
                predicates.add(criteriaBuilder.equal(root.get("venueId"), venueId));
            }
            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }
            if (validationStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("validationStatus"), validationStatus));
            }
            return predicates.isEmpty() ? criteriaBuilder.conjunction() : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<ScientificEventEntity> visibleSpecification() {
        return ValidableVisibilitySpecifications.<ScientificEventEntity>visibleTo(
            visibilityContext.defaultScope(),
            visibilityContext.linkedResearcherId().orElse(null),
            null
        ).and(activeVisibilitySpecification());
    }

    private Specification<ScientificEventEntity> activeVisibilitySpecification() {
        return (root, query, criteriaBuilder) -> {
            if (visibilityContext.defaultScope() == VisibilityScope.ADMIN_ALL) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.isTrue(root.get("active"));
        };
    }

    private Specification<ScientificEventEntity> hasId(Long id) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("id"), id);
    }

    private Expression<String> lowerCoalesced(Expression<String> expression, jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.lower(criteriaBuilder.coalesce(expression, ""));
    }

    private ScientificEventResponse toResponse(ScientificEventEntity entity) {
        return new ScientificEventResponse(
            entity.getId(),
            entity.getName(),
            entity.getEdition(),
            entity.getEventTypeCode(),
            entity.getStartDate(),
            entity.getEndDate(),
            entity.getCity(),
            entity.getCountry(),
            entity.getOrganizer(),
            entity.getWebsite(),
            entity.getDescription(),
            entity.getEvidenceUrl(),
            entity.getVenueId(),
            entity.isActive(),
            entity.getValidationStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private Map<String, AuditFieldChange> changes(
        ScientificEventEntity entity,
        ScientificEventRequest request,
        ValidationStatus nextStatus
    ) {
        Map<String, AuditFieldChange> changes = auditService.changes();
        auditService.addChange(changes, "name", entity.getName(), request.name());
        auditService.addChange(changes, "edition", entity.getEdition(), blankToNull(request.edition()));
        auditService.addChange(changes, "eventTypeCode", entity.getEventTypeCode(), request.eventTypeCode());
        auditService.addChange(changes, "startDate", entity.getStartDate(), request.startDate());
        auditService.addChange(changes, "endDate", entity.getEndDate(), request.endDate());
        auditService.addChange(changes, "city", entity.getCity(), blankToNull(request.city()));
        auditService.addChange(changes, "country", entity.getCountry(), blankToNull(request.country()));
        auditService.addChange(changes, "organizer", entity.getOrganizer(), blankToNull(request.organizer()));
        auditService.addChange(changes, "website", entity.getWebsite(), blankToNull(request.website()));
        auditService.addChange(changes, "description", entity.getDescription(), blankToNull(request.description()));
        auditService.addChange(changes, "evidenceUrl", entity.getEvidenceUrl(), blankToNull(request.evidenceUrl()));
        auditService.addChange(changes, "venueId", entity.getVenueId(), request.venueId());
        auditService.addChange(changes, "active", entity.isActive(), request.active() == null || request.active());
        auditService.addChange(changes, "validationStatus", entity.getValidationStatus(), nextStatus);
        return changes;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
