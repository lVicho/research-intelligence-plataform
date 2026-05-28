package com.researchintelligence.platform.opportunities.api;

import java.util.List;

public record OpportunityPublicationResponse(
    Long id,
    String title,
    Integer year,
    List<String> topics
) {
}
