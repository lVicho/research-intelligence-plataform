package com.researchintelligence.platform.news.api;

import java.time.Instant;
import java.util.List;

public record PortalNewsArticleSummaryResponse(
    Long id,
    String title,
    String summary,
    String imageUrl,
    String imageAlt,
    Instant publishedAt,
    List<Long> relatedPublicationIds,
    List<Long> relatedResearcherIds,
    List<Long> relatedUnitIds
) {
}
