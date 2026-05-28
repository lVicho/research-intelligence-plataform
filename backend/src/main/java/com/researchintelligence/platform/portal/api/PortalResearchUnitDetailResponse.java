package com.researchintelligence.platform.portal.api;

import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import java.util.List;

public record PortalResearchUnitDetailResponse(
    PortalResearchUnitSummaryResponse unit,
    List<PortalResearcherSummaryResponse> researchers,
    List<PortalPublicationSummaryResponse> publications,
    List<PortalActivitySummaryResponse> activities,
    List<PortalCountResponse> topics,
    List<PortalResearchUnitSummaryResponse> childUnits,
    PortalCollaborationSummaryResponse collaborationSummary,
    VisibilityScope visibilityScope,
    boolean validationFilterApplied
) {
}
