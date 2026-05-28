package com.researchintelligence.platform.analytics.api;

import com.researchintelligence.platform.dataquality.api.DataQualitySummaryResponse;
import java.util.List;

public record InstitutionalOverviewResponse(
    long totalValidatedPublications,
    long totalResearchers,
    long totalResearchUnits,
    List<YearCountResponse> publicationsByYear,
    List<NamedCountResponse> publicationsByType,
    List<NamedCountResponse> publicationsByAcademicStatus,
    List<NamedCountResponse> activitiesByValidationStatus,
    List<NamedCountResponse> publicationsByResearchUnit,
    List<NamedCountResponse> publicationsByTopic,
    List<NamedCountResponse> activeResearchersByUnit,
    List<NamedCountResponse> topTopics,
    List<TopicTrendResponse> emergingTopics,
    List<CollaborationPairResponse> collaborationPairs,
    long crossUnitCollaborations,
    DataQualitySummaryResponse dataQualitySummary
) {
}
