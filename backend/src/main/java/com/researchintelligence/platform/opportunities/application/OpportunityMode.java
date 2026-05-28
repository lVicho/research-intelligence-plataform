package com.researchintelligence.platform.opportunities.application;

public enum OpportunityMode {
    STRICT(0.60, 0.45),
    BALANCED(0.40, 0.30),
    BROAD(0.20, 0.15);

    private final double minimumScore;
    private final double adjacentTopicThreshold;

    OpportunityMode(double minimumScore, double adjacentTopicThreshold) {
        this.minimumScore = minimumScore;
        this.adjacentTopicThreshold = adjacentTopicThreshold;
    }

    public double minimumScore() {
        return minimumScore;
    }

    public double adjacentTopicThreshold() {
        return adjacentTopicThreshold;
    }
}
