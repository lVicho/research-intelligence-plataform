package com.researchintelligence.platform.ai.application;

record SemanticPublicationMatch(
    RetrievedPublicationContext context,
    double similarityScore,
    boolean passedThreshold,
    String retrievalReason
) {
}
