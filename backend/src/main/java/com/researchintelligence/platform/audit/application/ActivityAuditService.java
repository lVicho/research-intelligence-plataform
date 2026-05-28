package com.researchintelligence.platform.audit.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.audit.api.ActivityAuditEventResponse;
import com.researchintelligence.platform.audit.domain.ActivityAuditAction;
import com.researchintelligence.platform.audit.persistence.ActivityAuditEventEntity;
import com.researchintelligence.platform.audit.persistence.ActivityAuditEventQueryRepository;
import com.researchintelligence.platform.audit.persistence.ActivityAuditEventRepository;
import com.researchintelligence.platform.audit.persistence.ActivityAuditEventRow;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ActivityAuditService {

    private final ActivityAuditEventRepository repository;
    private final ActivityAuditEventQueryRepository queryRepository;
    private final VisibilityContext visibilityContext;
    private final ObjectMapper objectMapper;

    public ActivityAuditService(
        ActivityAuditEventRepository repository,
        ActivityAuditEventQueryRepository queryRepository,
        VisibilityContext visibilityContext,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.queryRepository = queryRepository;
        this.visibilityContext = visibilityContext;
        this.objectMapper = objectMapper;
    }

    public PageResponse<ActivityAuditEventResponse> findEvents(
        ValidationEntityType entityType,
        Long entityId,
        ActivityAuditAction action,
        int page,
        int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PlatformUserPrincipal user = currentUser();
        Page<ActivityAuditEventRow> rows;
        if (canReadAllAudit(user)) {
            rows = queryRepository.findAllVisible(entityType, entityId, action, safePage, safeSize);
        } else if (isResearcher(user)) {
            Long researcherId = requireLinkedResearcher(user);
            rows = queryRepository.findVisibleToResearcher(researcherId, entityType, entityId, action, safePage, safeSize);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Audit events are not available for the current user.");
        }
        return PageResponse.from(rows.map(this::toResponse));
    }

    public PageResponse<ActivityAuditEventResponse> findEntityEvents(
        ValidationEntityType entityType,
        Long entityId,
        int page,
        int size
    ) {
        PlatformUserPrincipal user = currentUser();
        if (!canReadAllAudit(user) && isResearcher(user)) {
            Long researcherId = requireLinkedResearcher(user);
            if (!queryRepository.isEntityOwnedByResearcher(entityType, entityId, researcherId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Audit events do not belong to the current researcher.");
            }
        }
        return findEvents(entityType, entityId, null, page, size);
    }

    @Transactional
    public void recordCreated(ValidationEntityType entityType, Long entityId, ValidationStatus newStatus) {
        record(entityType, entityId, ActivityAuditAction.CREATED, null, newStatus, null, null);
    }

    @Transactional
    public void recordUpdated(
        ValidationEntityType entityType,
        Long entityId,
        ValidationStatus previousStatus,
        ValidationStatus newStatus,
        Map<String, AuditFieldChange> changes
    ) {
        record(entityType, entityId, ActivityAuditAction.UPDATED, previousStatus, newStatus, null, changesJson(changes));
    }

    @Transactional
    public void recordArchived(ValidationEntityType entityType, Long entityId, ValidationStatus status) {
        record(entityType, entityId, ActivityAuditAction.ARCHIVED, status, status, null, null);
    }

    @Transactional
    public void recordMerged(ValidationEntityType entityType, Long entityId, String comment, Map<String, AuditFieldChange> changes) {
        record(entityType, entityId, ActivityAuditAction.MERGED, null, null, comment, changesJson(changes));
    }

    @Transactional
    public void recordAction(
        ValidationEntityType entityType,
        Long entityId,
        ActivityAuditAction action,
        String comment,
        Map<String, AuditFieldChange> changes
    ) {
        record(entityType, entityId, action, null, null, comment, changesJson(changes));
    }

    @Transactional
    public void recordStatusChange(
        ValidationEntityType entityType,
        Long entityId,
        ValidationStatus previousStatus,
        ValidationStatus newStatus,
        String comment
    ) {
        record(entityType, entityId, actionForStatus(newStatus), previousStatus, newStatus, comment, null);
    }

    public Map<String, AuditFieldChange> changes() {
        return new LinkedHashMap<>();
    }

    public void addChange(Map<String, AuditFieldChange> changes, String field, Object previousValue, Object newValue) {
        String previous = stringValue(previousValue);
        String next = stringValue(newValue);
        if (!Objects.equals(previous, next)) {
            changes.put(field, new AuditFieldChange(previous, next));
        }
    }

    private void record(
        ValidationEntityType entityType,
        Long entityId,
        ActivityAuditAction action,
        ValidationStatus previousStatus,
        ValidationStatus newStatus,
        String comment,
        String changesJson
    ) {
        PlatformUserPrincipal user = visibilityContext.currentUser().orElse(null);
        repository.save(new ActivityAuditEventEntity(
            entityType,
            entityId,
            action,
            user == null ? null : user.id(),
            user == null ? "Sistema" : user.displayName(),
            user == null ? null : primaryRole(user.roles()),
            Instant.now(),
            previousStatus,
            newStatus,
            normalizeComment(comment),
            changesJson
        ));
    }

    private ActivityAuditAction actionForStatus(ValidationStatus status) {
        return switch (status) {
            case PENDING_VALIDATION -> ActivityAuditAction.SUBMITTED;
            case VALIDATED -> ActivityAuditAction.VALIDATED;
            case REJECTED -> ActivityAuditAction.REJECTED;
            case CHANGES_REQUESTED -> ActivityAuditAction.CHANGES_REQUESTED;
            case DRAFT -> ActivityAuditAction.UPDATED;
        };
    }

    private String changesJson(Map<String, AuditFieldChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JacksonException ex) {
            return null;
        }
    }

    private ActivityAuditEventResponse toResponse(ActivityAuditEventRow row) {
        return new ActivityAuditEventResponse(
            row.id(),
            row.entityType(),
            row.entityId(),
            row.action(),
            row.actorUserId(),
            row.actorDisplayName(),
            row.actorRole(),
            row.occurredAt(),
            row.previousStatus(),
            row.newStatus(),
            row.comment(),
            row.changesJson()
        );
    }

    private PlatformUserPrincipal currentUser() {
        return visibilityContext.currentUser()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is required to read audit events."));
    }

    private boolean canReadAllAudit(PlatformUserPrincipal user) {
        return user.roles().contains("ADMIN") || user.roles().contains("VALIDATOR");
    }

    private boolean isResearcher(PlatformUserPrincipal user) {
        return user.roles().contains("RESEARCHER");
    }

    private Long requireLinkedResearcher(PlatformUserPrincipal user) {
        if (user.researcherId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current user is not linked to a researcher.");
        }
        return user.researcherId();
    }

    private String primaryRole(List<String> roles) {
        if (roles.contains("ADMIN")) {
            return "ADMIN";
        }
        if (roles.contains("VALIDATOR")) {
            return "VALIDATOR";
        }
        if (roles.contains("RESEARCHER")) {
            return "RESEARCHER";
        }
        if (roles.contains("PUBLIC_USER")) {
            return "PUBLIC_USER";
        }
        return roles.isEmpty() ? null : roles.get(0);
    }

    private String normalizeComment(String comment) {
        return comment == null || comment.isBlank() ? null : comment.trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
