package com.researchintelligence.platform.graph.api;

public record GraphMetadata(
    int totalNodes,
    int totalEdges,
    int displayedNodes,
    int displayedEdges,
    boolean truncated,
    String visibilityScope,
    boolean validationFilterApplied
) {
}
