package com.researchintelligence.platform.audit.api;

import com.researchintelligence.platform.audit.domain.ActivityAuditAction;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;

public record ActivityAuditEventResponse(
    Long id,
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
}
