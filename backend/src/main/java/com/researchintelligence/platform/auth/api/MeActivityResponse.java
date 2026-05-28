package com.researchintelligence.platform.auth.api;

import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MeActivityResponse(
    ValidationEntityType entityType,
    Long entityId,
    String title,
    String subtitle,
    Long researcherId,
    String researcherName,
    Long researchUnitId,
    String researchUnitName,
    Instant submittedAt,
    ValidationStatus validationStatus,
    String validationComment,
    Map<String, String> summaryFields,
    List<String> dataQualityReminders,
    boolean editable,
    boolean submittable
) {
}
