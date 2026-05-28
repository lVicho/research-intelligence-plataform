package com.researchintelligence.platform.analytics.api;

public record CollaborationPairResponse(
    Long researchUnitAId,
    String researchUnitAName,
    Long researchUnitBId,
    String researchUnitBName,
    long sharedPublicationCount
) {
}
