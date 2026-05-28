package com.researchintelligence.platform.ai.application;

import java.util.List;

class RetrievalThresholdPolicy {

    private final AiProperties.Retrieval properties;

    RetrievalThresholdPolicy(AiProperties.Retrieval properties) {
        this.properties = properties;
    }

    RetrievalPlan plan(Integer requestedLimit, Double requestedMinSimilarity, RetrievalMode requestedMode) {
        RetrievalMode mode = requestedMode == null ? RetrievalMode.BALANCED : requestedMode;
        int configuredDefaultLimit = Math.max(1, properties.getDefaultLimit());
        int configuredMaxLimit = Math.max(configuredDefaultLimit, properties.getMaxLimit());
        int limit = requestedLimit == null ? defaultLimitFor(mode, configuredDefaultLimit, configuredMaxLimit) : requestedLimit;
        double minSimilarity = requestedMinSimilarity == null ? defaultMinSimilarityFor(mode) : requestedMinSimilarity;
        return new RetrievalPlan(
            Math.min(Math.max(limit, 1), configuredMaxLimit),
            clampSimilarity(minSimilarity),
            mode
        );
    }

    List<RetrievedPublicationContext> filter(List<RetrievedPublicationContext> contexts, RetrievalPlan plan) {
        return contexts.stream()
            .filter(context -> context.similarityScore() == null || context.similarityScore() >= plan.minSimilarity())
            .toList();
    }

    boolean isLowSimilarity(RetrievedPublicationContext context) {
        return context.similarityScore() != null && context.similarityScore() < properties.getMinSimilarity();
    }

    boolean hasLowSimilarity(List<RetrievedPublicationContext> contexts) {
        return contexts.stream().anyMatch(this::isLowSimilarity);
    }

    private int defaultLimitFor(RetrievalMode mode, int configuredDefaultLimit, int configuredMaxLimit) {
        return mode == RetrievalMode.BROAD ? configuredMaxLimit : configuredDefaultLimit;
    }

    private double defaultMinSimilarityFor(RetrievalMode mode) {
        return switch (mode) {
            case STRICT -> properties.getStrictMinSimilarity();
            case BROAD -> properties.getBroadMinSimilarity();
            case BALANCED -> properties.getMinSimilarity();
        };
    }

    private double clampSimilarity(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
