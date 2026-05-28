package com.researchintelligence.platform.ai.application;

import java.util.List;

public record EmbeddingResponse(
    List<Double> vector,
    List<String> warnings
) {
}
