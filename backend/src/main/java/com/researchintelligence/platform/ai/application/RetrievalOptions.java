package com.researchintelligence.platform.ai.application;

public record RetrievalOptions(
    Integer limit,
    Double minSimilarity,
    RetrievalMode mode
) {
}
