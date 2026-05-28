package com.researchintelligence.platform.validation.api;

import jakarta.validation.constraints.Size;

public record ValidationCommentRequest(
    @Size(max = 2000) String comment
) {
}
