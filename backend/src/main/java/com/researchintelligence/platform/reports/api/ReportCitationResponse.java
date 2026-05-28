package com.researchintelligence.platform.reports.api;

import java.util.List;

public record ReportCitationResponse(
    Long id,
    int citationIndex,
    String title,
    Integer year,
    List<String> authors,
    List<String> topics,
    List<String> researchUnits,
    String doi,
    String source,
    String url
) {
}
