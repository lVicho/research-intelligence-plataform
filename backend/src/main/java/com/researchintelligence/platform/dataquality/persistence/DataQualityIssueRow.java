package com.researchintelligence.platform.dataquality.persistence;

import com.researchintelligence.platform.dataquality.domain.DataQualityEntityType;
import com.researchintelligence.platform.dataquality.domain.DataQualityIssueType;
import com.researchintelligence.platform.dataquality.domain.DataQualitySeverity;
import com.researchintelligence.platform.validation.domain.ValidationStatus;

public record DataQualityIssueRow(
    DataQualityIssueType issueType,
    DataQualitySeverity severity,
    DataQualityEntityType entityType,
    Long entityId,
    String title,
    String description,
    String suggestedAction,
    ValidationStatus validationStatus
) {
}
