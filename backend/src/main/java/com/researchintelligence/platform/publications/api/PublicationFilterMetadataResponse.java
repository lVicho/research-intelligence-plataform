package com.researchintelligence.platform.publications.api;

import java.util.List;

public record PublicationFilterMetadataResponse(
    Integer minYear,
    Integer maxYear,
    List<FilterCountResponse> availableTypes,
    List<FilterCountResponse> availableStatuses,
    List<FilterCountResponse> researchUnits,
    List<FilterCountResponse> topics
) {
}
