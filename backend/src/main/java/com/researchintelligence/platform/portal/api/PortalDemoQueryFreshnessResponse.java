package com.researchintelligence.platform.portal.api;

import java.time.Instant;

public record PortalDemoQueryFreshnessResponse(
    Instant newestEvidenceAt,
    Instant oldestEvidenceAt,
    int evidenceCount
) {
}
