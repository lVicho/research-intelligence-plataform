package com.researchintelligence.platform.masterdata.persistence;

import java.time.Instant;

public record MasterDataRow(
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
