package com.researchintelligence.platform.reports.application;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.List;

record ReportPublicationEvidence(
    Long id,
    String title,
    String abstractText,
    Integer year,
    String doi,
    String source,
    String url,
    List<String> authors,
    List<String> topics,
    List<String> researchUnits,
    ValidationStatus validationStatus
) {
}
