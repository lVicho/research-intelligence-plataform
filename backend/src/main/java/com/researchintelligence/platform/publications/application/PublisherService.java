package com.researchintelligence.platform.publications.application;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.audit.application.AuditFieldChange;
import com.researchintelligence.platform.publications.api.PublisherRequest;
import com.researchintelligence.platform.publications.api.PublisherResponse;
import com.researchintelligence.platform.publications.persistence.PublisherEntity;
import com.researchintelligence.platform.publications.persistence.PublisherRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
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
public class PublisherService {

    private final PublisherRepository repository;
    private final ActivityAuditService auditService;

    public PublisherService(PublisherRepository repository, ActivityAuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public PageResponse<PublisherResponse> search(int page, int size, String text, Boolean active) {
        PageRequest pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"))
        );
        return PageResponse.from(repository.findAll(matches(text, active), pageable).map(this::toResponse));
    }

    public PublisherResponse findById(Long id) {
        return repository.findById(id)
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Publisher", id));
    }

    @Transactional
    public PublisherResponse create(PublisherRequest request) {
        PublisherEntity entity = repository.save(new PublisherEntity(
            request.name(),
            blankToNull(request.country()),
            blankToNull(request.website()),
            request.active() == null || request.active()
        ));
        entity.setDescription(blankToNull(request.description()));
        auditService.recordCreated(ValidationEntityType.PUBLISHER, entity.getId(), null);
        return toResponse(entity);
    }

    @Transactional
    public PublisherResponse update(Long id, PublisherRequest request) {
        PublisherEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Publisher", id));
        boolean wasActive = entity.isActive();
        Map<String, AuditFieldChange> changes = auditService.changes();
        auditService.addChange(changes, "name", entity.getName(), request.name());
        auditService.addChange(changes, "country", entity.getCountry(), blankToNull(request.country()));
        auditService.addChange(changes, "website", entity.getWebsite(), blankToNull(request.website()));
        auditService.addChange(changes, "description", entity.getDescription(), blankToNull(request.description()));
        auditService.addChange(changes, "active", entity.isActive(), request.active() == null || request.active());
        entity.setName(request.name());
        entity.setCountry(blankToNull(request.country()));
        entity.setWebsite(blankToNull(request.website()));
        entity.setDescription(blankToNull(request.description()));
        entity.setActive(request.active() == null || request.active());
        auditService.recordUpdated(ValidationEntityType.PUBLISHER, id, null, null, changes);
        if (wasActive && !entity.isActive()) {
            auditService.recordArchived(ValidationEntityType.PUBLISHER, id, null);
        }
        return toResponse(entity);
    }

    private Specification<PublisherEntity> matches(String text, Boolean active) {
        String normalizedText = blankToNull(text);
        String textPattern = normalizedText == null ? null : "%" + normalizedText.toLowerCase() + "%";
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (textPattern != null) {
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root.get("country"), criteriaBuilder), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root.get("description"), criteriaBuilder), textPattern)
                ));
            }
            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }
            return predicates.isEmpty() ? criteriaBuilder.conjunction() : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Expression<String> lowerCoalesced(Expression<String> expression, jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.lower(criteriaBuilder.coalesce(expression, ""));
    }

    private PublisherResponse toResponse(PublisherEntity entity) {
        return new PublisherResponse(
            entity.getId(),
            entity.getName(),
            entity.getCountry(),
            entity.getWebsite(),
            entity.getDescription(),
            entity.isActive(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
