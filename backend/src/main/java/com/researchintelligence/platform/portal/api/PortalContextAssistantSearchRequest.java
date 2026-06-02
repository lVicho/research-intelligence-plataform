package com.researchintelligence.platform.portal.api;

import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;

public record PortalContextAssistantSearchRequest(
    String query,
    String mode,
    Integer yearFrom,
    Integer yearTo,
    PublicationType type,
    PublicationStatus status,
    Long researchUnitId,
    Long researcherId,
    String topic
) {
}
