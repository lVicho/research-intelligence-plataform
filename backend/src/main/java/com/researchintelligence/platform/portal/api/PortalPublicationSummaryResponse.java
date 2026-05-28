package com.researchintelligence.platform.portal.api;

import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import java.time.Instant;
import java.util.List;

public record PortalPublicationSummaryResponse(
    Long id,
    String title,
    Integer year,
    PublicationType type,
    PublicationStatus status,
    String doi,
    String source,
    Long venueId,
    Long publisherId,
    String isbn,
    String issn,
    String languageCode,
    Instant createdAt,
    List<String> topics
) {
}
