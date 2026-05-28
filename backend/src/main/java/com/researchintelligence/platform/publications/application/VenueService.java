package com.researchintelligence.platform.publications.application;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.audit.application.AuditFieldChange;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.publications.api.VenueRequest;
import com.researchintelligence.platform.publications.api.VenueResponse;
import com.researchintelligence.platform.publications.persistence.PublisherRepository;
import com.researchintelligence.platform.publications.persistence.VenueEntity;
import com.researchintelligence.platform.publications.persistence.VenueRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.shared.visibility.ValidableVisibilitySpecifications;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
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
public class VenueService {

    private final VenueRepository repository;
    private final VisibilityContext visibilityContext;
    private final PublisherRepository publisherRepository;
    private final ActivityAuditService auditService;

    public VenueService(
        VenueRepository repository,
        VisibilityContext visibilityContext,
        PublisherRepository publisherRepository,
        ActivityAuditService auditService
    ) {
        this.repository = repository;
        this.visibilityContext = visibilityContext;
        this.publisherRepository = publisherRepository;
        this.auditService = auditService;
    }

    public PageResponse<VenueResponse> search(
        int page,
        int size,
        String text,
        String typeCode,
        Boolean active,
        ValidationStatus validationStatus
    ) {
        Specification<VenueEntity> specification = matches(text, typeCode, active, validationStatus)
            .and(ValidableVisibilitySpecifications.<VenueEntity>visibleTo(
                visibilityContext.defaultScope(),
                visibilityContext.linkedResearcherId().orElse(null),
                null
            ))
            .and(activeVisibilitySpecification());
        PageRequest pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"))
        );
        return PageResponse.from(repository.findAll(specification, pageable).map(this::toResponse));
    }

    public VenueResponse findById(Long id) {
        VenueEntity entity = repository.findOne(hasId(id)
            .and(ValidableVisibilitySpecifications.<VenueEntity>visibleTo(
                visibilityContext.defaultScope(),
                visibilityContext.linkedResearcherId().orElse(null),
                null
            )))
            .orElseThrow(() -> new ResourceNotFoundException("Venue", id));
        return toResponse(entity);
    }

    @Transactional
    public VenueResponse create(VenueRequest request) {
        validateRequest(request);
        VenueEntity entity = repository.save(new VenueEntity(
            request.name(),
            blankToNull(request.shortName()),
            request.typeCode(),
            blankToNull(request.issn()),
            blankToNull(request.eissn()),
            blankToNull(request.isbn()),
            blankToNull(request.country()),
            blankToNull(request.website()),
            blankToNull(request.description()),
            request.publisherId(),
            request.active() == null || request.active(),
            request.validationStatus()
        ));
        auditService.recordCreated(ValidationEntityType.VENUE, entity.getId(), entity.getValidationStatus());
        return toResponse(entity);
    }

    @Transactional
    public VenueResponse update(Long id, VenueRequest request) {
        validateRequest(request);
        VenueEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Venue", id));
        boolean wasActive = entity.isActive();
        ValidationStatus previousStatus = entity.getValidationStatus();
        ValidationStatus nextStatus = request.validationStatus() == null ? entity.getValidationStatus() : request.validationStatus();
        Map<String, AuditFieldChange> changes = changes(entity, request, nextStatus);
        entity.setName(request.name());
        entity.setShortName(blankToNull(request.shortName()));
        entity.setTypeCode(request.typeCode());
        entity.setIssn(blankToNull(request.issn()));
        entity.setEissn(blankToNull(request.eissn()));
        entity.setIsbn(blankToNull(request.isbn()));
        entity.setCountry(blankToNull(request.country()));
        entity.setWebsite(blankToNull(request.website()));
        entity.setDescription(blankToNull(request.description()));
        entity.setPublisherId(request.publisherId());
        entity.setActive(request.active() == null || request.active());
        entity.setValidationStatus(nextStatus);
        auditService.recordUpdated(ValidationEntityType.VENUE, id, previousStatus, nextStatus, changes);
        if (wasActive && !entity.isActive()) {
            auditService.recordArchived(ValidationEntityType.VENUE, id, entity.getValidationStatus());
        }
        return toResponse(entity);
    }

    private void validateRequest(VenueRequest request) {
        if (request.publisherId() != null && !publisherRepository.existsById(request.publisherId())) {
            throw new ResourceNotFoundException("Publisher", request.publisherId());
        }
    }

    private Specification<VenueEntity> matches(
        String text,
        String typeCode,
        Boolean active,
        ValidationStatus validationStatus
    ) {
        String normalizedText = blankToNull(text);
        String textPattern = normalizedText == null ? null : "%" + normalizedText.toLowerCase() + "%";
        String normalizedType = blankToNull(typeCode);
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (textPattern != null) {
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root.get("shortName"), criteriaBuilder), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root.get("issn"), criteriaBuilder), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root.get("eissn"), criteriaBuilder), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root.get("isbn"), criteriaBuilder), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root.get("country"), criteriaBuilder), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root.get("description"), criteriaBuilder), textPattern)
                ));
            }
            if (normalizedType != null) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("typeCode")), normalizedType.toLowerCase()));
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

    private Specification<VenueEntity> activeVisibilitySpecification() {
        return (root, query, criteriaBuilder) -> {
            if (visibilityContext.defaultScope() == VisibilityScope.ADMIN_ALL) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.isTrue(root.get("active"));
        };
    }

    private Specification<VenueEntity> hasId(Long id) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("id"), id);
    }

    private Expression<String> lowerCoalesced(Expression<String> expression, jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.lower(criteriaBuilder.coalesce(expression, ""));
    }

    private VenueResponse toResponse(VenueEntity entity) {
        return new VenueResponse(
            entity.getId(),
            entity.getName(),
            entity.getShortName(),
            entity.getTypeCode(),
            entity.getIssn(),
            entity.getEissn(),
            entity.getIsbn(),
            entity.getCountry(),
            entity.getWebsite(),
            entity.getDescription(),
            entity.getPublisherId(),
            entity.isActive(),
            entity.getValidationStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getCreatedByUserId(),
            entity.getUpdatedByUserId()
        );
    }

    private Map<String, AuditFieldChange> changes(VenueEntity entity, VenueRequest request, ValidationStatus nextStatus) {
        Map<String, AuditFieldChange> changes = auditService.changes();
        auditService.addChange(changes, "name", entity.getName(), request.name());
        auditService.addChange(changes, "shortName", entity.getShortName(), blankToNull(request.shortName()));
        auditService.addChange(changes, "typeCode", entity.getTypeCode(), request.typeCode());
        auditService.addChange(changes, "issn", entity.getIssn(), blankToNull(request.issn()));
        auditService.addChange(changes, "eissn", entity.getEissn(), blankToNull(request.eissn()));
        auditService.addChange(changes, "isbn", entity.getIsbn(), blankToNull(request.isbn()));
        auditService.addChange(changes, "country", entity.getCountry(), blankToNull(request.country()));
        auditService.addChange(changes, "website", entity.getWebsite(), blankToNull(request.website()));
        auditService.addChange(changes, "description", entity.getDescription(), blankToNull(request.description()));
        auditService.addChange(changes, "publisherId", entity.getPublisherId(), request.publisherId());
        auditService.addChange(changes, "active", entity.isActive(), request.active() == null || request.active());
        auditService.addChange(changes, "validationStatus", entity.getValidationStatus(), nextStatus);
        return changes;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
