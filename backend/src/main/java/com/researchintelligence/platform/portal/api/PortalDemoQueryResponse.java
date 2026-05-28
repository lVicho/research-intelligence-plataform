package com.researchintelligence.platform.portal.api;

import java.time.Instant;
import java.util.List;

public record PortalDemoQueryResponse(
    String query,
    PortalDemoQueryContext context,
    String reason,
    List<String> expectedEntityTypes,
    List<String> evidenceIds,
    PortalDemoQueryFreshnessResponse freshness,
    Instant generatedAt
) {
}
