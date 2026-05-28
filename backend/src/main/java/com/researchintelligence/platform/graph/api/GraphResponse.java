package com.researchintelligence.platform.graph.api;

import java.util.List;

public record GraphResponse(
    List<GraphNode> nodes,
    List<GraphEdge> edges,
    GraphMetadata metadata,
    List<String> warnings
) {
}
