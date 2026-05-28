package com.researchintelligence.platform.reports.api;

import java.time.Instant;
import java.util.List;

public record GenerateReportResponse(
    String reportTitle,
    String markdownContent,
    List<ReportCitationResponse> citedPublications,
    List<String> warnings,
    Instant generatedAt,
    String provider,
    String model
) {
}
