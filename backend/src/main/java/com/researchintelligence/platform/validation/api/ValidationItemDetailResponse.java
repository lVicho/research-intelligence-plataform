package com.researchintelligence.platform.validation.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ValidationItemDetailResponse(
    ValidationItemResponse item,
    Map<String, String> fields,
    String validationComment,
    String validatedBy,
    Instant validatedAt,
    List<String> warnings,
    List<String> dataQualityFlags
) {
}
