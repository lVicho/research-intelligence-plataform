package com.researchintelligence.platform.graph.api;

import java.util.Map;

public record GraphEdge(
    String id,
    String source,
    String target,
    String type,
    double weight,
    Map<String, Object> metadata
) {
}
