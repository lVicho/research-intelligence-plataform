package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import java.time.Instant;
import java.util.List;

public record PublicationSemanticSearchResponse(
    Long id,
    String title,
    Integer year,
    PublicationType type,
    PublicationStatus status,
    String doi,
    String source,
    Instant createdAt,
    List<String> authors,
    List<String> topics,
    double similarityScore,
    boolean passedThreshold,
    boolean lowSimilarity,
    String retrievalReason,
    String visibilityScope,
    boolean validationFilterApplied
) {
}
