package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.util.List;

public record ResearcherAssistantActivityResponse(
    ValidationEntityType entityType,
    Long entityId,
    String title,
    Instant submittedAt,
    ValidationStatus validationStatus,
    String validationComment,
    List<String> dataQualityReminders,
    boolean canEdit,
    boolean canSubmit
) {
}
