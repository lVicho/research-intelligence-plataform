package com.researchintelligence.platform.validation.api;

import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ValidationItemResponse(
    ValidationEntityType entityType,
    Long entityId,
    String title,
    String subtitle,
    String researcherName,
    String researchUnitName,
    String submittedBy,
    Instant submittedAt,
    ValidationStatus validationStatus,
    Map<String, String> summaryFields,
    List<String> warnings,
    List<String> dataQualityFlags
) {
}
