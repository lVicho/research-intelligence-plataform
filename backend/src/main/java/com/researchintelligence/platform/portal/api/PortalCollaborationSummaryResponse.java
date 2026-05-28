package com.researchintelligence.platform.portal.api;

public record PortalCollaborationSummaryResponse(
    long researcherCount,
    long publicationCount,
    long activityCount
) {
}
