package com.researchintelligence.platform.reports.api;

import com.researchintelligence.platform.reports.domain.ReportOutputFormat;
import com.researchintelligence.platform.reports.domain.ReportType;
import java.time.Instant;
import java.util.List;

public record ReportTemplateResponse(
    Long id,
    String name,
    String description,
    ReportType targetType,
    List<String> sections,
    Integer defaultYearFrom,
    Integer defaultYearTo,
    ReportOutputFormat outputFormat,
    boolean active,
    Instant createdAt,
    Instant updatedAt,
    Long createdByUserId,
    Long updatedByUserId
) {
}
