package com.researchintelligence.platform.auth.api;

import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MeActivityDetailResponse(
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
    String validatedBy,
    Instant validatedAt,
    Map<String, String> fields,
    List<String> warnings,
    List<String> dataQualityReminders,
    boolean editable,
    boolean submittable
) {
}
