package com.researchintelligence.platform.audit.persistence;

import com.researchintelligence.platform.audit.domain.ActivityAuditAction;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "activity_audit_events")
public class ActivityAuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private ValidationEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityAuditAction action;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_display_name")
    private String actorDisplayName;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private ValidationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status")
    private ValidationStatus newStatus;

    @Column(name = "comment")
    private String comment;

    @Column(name = "changes_json")
    private String changesJson;

    protected ActivityAuditEventEntity() {
    }

    public ActivityAuditEventEntity(
        ValidationEntityType entityType,
        Long entityId,
        ActivityAuditAction action,
        Long actorUserId,
        String actorDisplayName,
        String actorRole,
        Instant occurredAt,
        ValidationStatus previousStatus,
        ValidationStatus newStatus,
        String comment,
        String changesJson
    ) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.actorUserId = actorUserId;
        this.actorDisplayName = actorDisplayName;
        this.actorRole = actorRole;
        this.occurredAt = occurredAt;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.comment = comment;
        this.changesJson = changesJson;
    }

    public Long getId() {
        return id;
    }

    public ValidationEntityType getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public ActivityAuditAction getAction() {
        return action;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorDisplayName() {
        return actorDisplayName;
    }

    public String getActorRole() {
        return actorRole;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public ValidationStatus getPreviousStatus() {
        return previousStatus;
    }

    public ValidationStatus getNewStatus() {
        return newStatus;
    }

    public String getComment() {
        return comment;
    }

    public String getChangesJson() {
        return changesJson;
    }
}
