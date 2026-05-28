package com.researchintelligence.platform.news.api;

import com.researchintelligence.platform.news.domain.NewsArticleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NewsArticleRequest(
    @NotBlank @Size(max = 255) String title,
    @NotBlank String summary,
    @NotBlank String body,
    NewsArticleStatus status,
    @Size(max = 1000) String imageUrl,
    @Size(max = 500) String imageAlt,
    String imageSuggestion,
    List<Long> relatedPublicationIds,
    List<Long> relatedResearcherIds,
    List<Long> relatedUnitIds
) {
}
