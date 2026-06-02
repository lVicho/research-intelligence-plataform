package com.researchintelligence.platform.portal.api;

import java.util.List;

public record PortalContextAssistantPublicationEvidenceResponse(
    Long id,
    int citationIndex,
    String title,
    Integer year,
    List<String> authors,
    List<String> topics,
    String doi,
    String source,
    String url,
    Double relevanceScore,
    String path
) {
}
