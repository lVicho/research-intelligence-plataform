package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import java.util.List;

record RetrievedPublicationContext(
    PublicationEntity publication,
    List<String> authors,
    List<String> topics,
    Double similarityScore,
    boolean passedThreshold,
    boolean lowSimilarity,
    String retrievalReason
) {
}
