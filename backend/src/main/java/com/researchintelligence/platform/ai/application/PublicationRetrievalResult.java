package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import java.util.List;

record PublicationRetrievalResult(
    List<RetrievedPublicationContext> publications,
    RetrievalMethod retrievalMethod,
    RetrievalMode retrievalMode,
    double minSimilarity,
    List<String> warnings,
    VisibilityScope visibilityScope,
    boolean validationFilterApplied
) {
}
