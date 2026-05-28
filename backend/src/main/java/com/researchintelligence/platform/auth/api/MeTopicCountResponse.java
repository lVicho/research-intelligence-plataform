package com.researchintelligence.platform.auth.api;

public record MeTopicCountResponse(
    Long id,
    String name,
    long count
) {
}
