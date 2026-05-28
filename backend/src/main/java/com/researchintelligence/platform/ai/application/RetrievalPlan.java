package com.researchintelligence.platform.ai.application;

record RetrievalPlan(
    int limit,
    double minSimilarity,
    RetrievalMode mode
) {
}
