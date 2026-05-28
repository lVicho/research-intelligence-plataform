package com.researchintelligence.platform.portal.api;

import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import java.util.List;

public record PortalSummaryResponse(
    long totalValidatedPublications,
    long totalValidatedActivities,
    long totalPublicResearchers,
    long totalPublicResearchUnits,
    List<PortalCountResponse> topTopics,
    List<PortalPublicationSummaryResponse> recentValidatedPublications,
    List<PortalResearchUnitSummaryResponse> featuredResearchUnits,
    VisibilityScope visibilityScope,
    boolean validationFilterApplied
) {
}
