package com.researchintelligence.platform.analytics.api;

import java.util.List;

public record CollaborationsResponse(
    long crossUnitCollaborations,
    List<CollaborationPairResponse> collaborationPairs
) {
}
