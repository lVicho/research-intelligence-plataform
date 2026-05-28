package com.researchintelligence.platform.reports.api;

import com.researchintelligence.platform.reports.domain.ReportOutputFormat;
import com.researchintelligence.platform.reports.domain.ReportType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReportTemplateRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 1000) String description,
    @NotNull ReportType targetType,
    @NotEmpty List<@NotBlank @Size(max = 80) String> sections,
    @Min(1500) @Max(2200) Integer defaultYearFrom,
    @Min(1500) @Max(2200) Integer defaultYearTo,
    @NotNull ReportOutputFormat outputFormat,
    Boolean active
) {
}
