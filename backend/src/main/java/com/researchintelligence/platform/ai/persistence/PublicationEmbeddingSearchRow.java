package com.researchintelligence.platform.ai.persistence;

public record PublicationEmbeddingSearchRow(
    Long publicationId,
    double similarityScore
) {
}
