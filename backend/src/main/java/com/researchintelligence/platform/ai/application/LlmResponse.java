package com.researchintelligence.platform.ai.application;

import java.util.List;

public record LlmResponse(
    String answer,
    List<String> warnings
) {
}
