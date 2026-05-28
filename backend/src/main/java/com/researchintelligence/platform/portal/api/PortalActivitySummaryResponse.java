package com.researchintelligence.platform.portal.api;

import java.time.LocalDate;

public record PortalActivitySummaryResponse(
    Long id,
    Long eventId,
    String eventName,
    Long researcherId,
    String researcherName,
    Long researchUnitId,
    String researchUnitName,
    String participationTypeCode,
    String title,
    String description,
    LocalDate participationDate,
    Long relatedPublicationId,
    String relatedPublicationTitle
) {
}
