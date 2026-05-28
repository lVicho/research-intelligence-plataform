package com.researchintelligence.platform.portal.api;

public record PortalGraphSummaryResponse(
    int totalNodes,
    int totalEdges,
    int displayedNodes,
    int displayedEdges,
    boolean truncated,
    String visibilityScope,
    boolean validationFilterApplied
) {
}
