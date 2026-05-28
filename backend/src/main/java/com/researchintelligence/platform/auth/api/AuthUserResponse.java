package com.researchintelligence.platform.auth.api;

import java.util.List;

public record AuthUserResponse(
    Long id,
    String email,
    String displayName,
    List<String> roles,
    Long researcherId
) {
}
