package com.researchintelligence.platform.ai.application;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

final class EmbeddingVectorFormatter {

    private EmbeddingVectorFormatter() {
    }

    static String toPgVector(List<Double> vector) {
        return vector.stream()
            .map(value -> String.format(Locale.ROOT, "%.10f", value))
            .collect(Collectors.joining(",", "[", "]"));
    }
}
