package com.researchintelligence.platform.dataquality.api;

import com.researchintelligence.platform.dataquality.domain.DataQualityEntityType;
import com.researchintelligence.platform.dataquality.domain.DataQualityIssueType;
import com.researchintelligence.platform.dataquality.domain.DataQualitySeverity;

public record DataQualityIssueResponse(
    DataQualityIssueType issueType,
    DataQualitySeverity severity,
    DataQualityEntityType entityType,
    Long entityId,
    String title,
    String description,
    String suggestedAction
) {
}
