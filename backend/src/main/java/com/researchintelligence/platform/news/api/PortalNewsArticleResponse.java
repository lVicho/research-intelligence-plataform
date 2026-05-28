package com.researchintelligence.platform.news.api;

import java.time.Instant;
import java.util.List;

public record PortalNewsArticleResponse(
    Long id,
    String title,
    String summary,
    String body,
    String imageUrl,
    String imageAlt,
    Instant publishedAt,
    List<Long> relatedPublicationIds,
    List<Long> relatedResearcherIds,
    List<Long> relatedUnitIds
) {
}
