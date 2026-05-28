package com.researchintelligence.platform.analytics.api;

import com.researchintelligence.platform.publications.api.PublicationSummaryResponse;
import java.util.List;

public record AnalyticsSummaryResponse(
    long totalResearchUnits,
    long totalResearchers,
    long activeResearchers,
    long totalPublications,
    List<YearCountResponse> publicationsByYear,
    List<NamedCountResponse> publicationsByType,
    List<NamedCountResponse> publicationsByStatus,
    List<NamedCountResponse> publicationsByResearchUnit,
    List<ResearcherPublicationCountResponse> topResearchersByPublicationCount,
    List<NamedCountResponse> topTopicsByPublicationCount,
    List<PublicationSummaryResponse> recentPublications,
    List<NamedCountResponse> researchersByResearchUnitType
) {
}
