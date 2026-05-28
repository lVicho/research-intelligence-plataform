package com.researchintelligence.platform.publications.api;

import java.time.Instant;

public record PublisherResponse(
    Long id,
    String name,
    String country,
    String website,
    String description,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}
