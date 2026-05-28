package com.researchintelligence.platform.masterdata.api;

import java.time.Instant;

public record MasterDataItemResponse(
    Long id,
    String code,
    String labelEs,
    String descriptionEs,
    boolean active,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt
) {
}
