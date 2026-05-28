package com.researchintelligence.platform.portal.api;

import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import java.util.List;

public record PortalSearchResponse(
    String query,
    PortalPageResponse<PortalPublicationSummaryResponse> publications,
    List<PortalResearcherSummaryResponse> researchers,
    List<PortalResearchUnitSummaryResponse> researchUnits,
    VisibilityScope visibilityScope,
    boolean validationFilterApplied
) {
}
