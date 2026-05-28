package com.researchintelligence.platform.expertfinder.api;

import java.time.LocalDate;

public record ExpertFinderEventParticipationResponse(
    Long id,
    Long eventId,
    String eventName,
    String participationTypeCode,
    String title,
    LocalDate participationDate,
    Long relatedPublicationId
) {
}
