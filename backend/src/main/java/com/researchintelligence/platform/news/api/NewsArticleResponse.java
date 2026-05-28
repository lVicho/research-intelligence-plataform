package com.researchintelligence.platform.news.api;

import com.researchintelligence.platform.news.domain.NewsArticleStatus;
import java.time.Instant;
import java.util.List;

public record NewsArticleResponse(
    Long id,
    String title,
    String summary,
    String body,
    NewsArticleStatus status,
    String imageUrl,
    String imageAlt,
    String imageSuggestion,
    Instant publishedAt,
    Instant createdAt,
    Instant updatedAt,
    Long createdByUserId,
    Long updatedByUserId,
    List<Long> relatedPublicationIds,
    List<Long> relatedResearcherIds,
    List<Long> relatedUnitIds
) {
}
