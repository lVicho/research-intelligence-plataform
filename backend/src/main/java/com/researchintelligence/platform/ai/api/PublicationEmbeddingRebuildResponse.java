package com.researchintelligence.platform.ai.api;

import java.util.List;

public record PublicationEmbeddingRebuildResponse(
    int totalPublications,
    int processedPublications,
    int storedPublicationEmbeddings,
    boolean storedEmbeddings,
    String provider,
    String model,
    int dimension,
    String message,
    List<String> warnings
) {
}
