package com.researchintelligence.platform.reports.api;

import com.researchintelligence.platform.reports.domain.ReportType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GenerateReportRequest(
    @NotNull ReportType reportType,
    Long templateId,
    Long targetId,
    @Size(max = 500) String query,
    @Min(1500) @Max(2200) Integer yearFrom,
    @Min(1500) @Max(2200) Integer yearTo,
    List<@Size(max = 120) String> includeSections,
    Boolean onlyValidated,
    @Size(max = 1000) String additionalInstructions
) {
}
