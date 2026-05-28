package com.researchintelligence.platform.auth.api;

import java.util.List;

public record MeDashboardResponse(
    MeResearcherProfileResponse profile,
    long validatedActivitiesCount,
    long draftActivitiesCount,
    long pendingValidationCount,
    long changesRequestedCount,
    long rejectedCount,
    List<MePublicationYearCountResponse> publicationsByYear,
    List<MeTopicCountResponse> mainTopics,
    List<MeActivityResponse> recentActivities,
    List<String> dataQualityReminders
) {
}
