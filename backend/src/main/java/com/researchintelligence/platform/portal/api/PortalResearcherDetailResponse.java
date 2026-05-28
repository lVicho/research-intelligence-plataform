package com.researchintelligence.platform.portal.api;

import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import java.util.List;

public record PortalResearcherDetailResponse(
    Long id,
    String fullName,
    String displayName,
    String email,
    String orcid,
    boolean active,
    List<PortalAffiliationResponse> affiliations,
    List<PortalPublicationSummaryResponse> publications,
    List<PortalActivitySummaryResponse> activities,
    List<PortalCountResponse> topics,
    List<PortalCoauthorResponse> coauthors,
    PortalGraphSummaryResponse graphSummary,
    VisibilityScope visibilityScope,
    boolean validationFilterApplied
) {
}
