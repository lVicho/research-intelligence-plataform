package com.researchintelligence.platform.graph.api;

import java.util.Map;

public record GraphNode(
    String id,
    String type,
    String label,
    Map<String, Object> metadata
) {
}
