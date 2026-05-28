package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.ai.api.PublicationSemanticSearchResponse;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PublicationSemanticSearchService {

    private final PublicationRetrievalService retrievalService;
    private final VisibilityContext visibilityContext;

    public PublicationSemanticSearchService(PublicationRetrievalService retrievalService, VisibilityContext visibilityContext) {
        this.retrievalService = retrievalService;
        this.visibilityContext = visibilityContext;
    }

    public List<PublicationSemanticSearchResponse> search(String query, Integer limit, Double minSimilarity) {
        return search(query, limit, minSimilarity, null);
    }

    public List<PublicationSemanticSearchResponse> search(String query, Integer limit, Double minSimilarity, Boolean includeNonValidated) {
        VisibilityScope visibilityScope = resolvePublicScope(includeNonValidated);
        return retrievalService.semanticSearch(
                query,
                limit,
                minSimilarity,
                visibilityScope,
                null
            )
            .stream()
            .map(match -> toResponse(match, visibilityScope))
            .toList();
    }

    private PublicationSemanticSearchResponse toResponse(SemanticPublicationMatch match, VisibilityScope visibilityScope) {
        RetrievedPublicationContext context = match.context();
        PublicationEntity publication = context.publication();
        return new PublicationSemanticSearchResponse(
            publication.getId(),
            publication.getTitle(),
            publication.getPublicationYear(),
            publication.getType(),
            publication.getStatus(),
            publication.getDoi(),
            publication.getSource(),
            publication.getCreatedAt(),
            context.authors(),
            context.topics(),
            match.similarityScore(),
            match.passedThreshold(),
            context.lowSimilarity(),
            match.retrievalReason(),
            visibilityScope.name(),
            validationFilterApplied(visibilityScope)
        );
    }

    private VisibilityScope resolvePublicScope(Boolean includeNonValidated) {
        if (Boolean.TRUE.equals(includeNonValidated) && visibilityContext.currentRoles().contains("ADMIN")) {
            return VisibilityScope.ADMIN_ALL;
        }
        return VisibilityScope.PUBLIC_VALIDATED;
    }

    private boolean validationFilterApplied(VisibilityScope visibilityScope) {
        return visibilityScope != VisibilityScope.ADMIN_ALL;
    }
}
