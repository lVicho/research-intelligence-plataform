package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.shared.application.BusinessRuleException;

public class UnsupportedEmbeddingService implements EmbeddingService {

    private final String provider;
    private final String model;

    public UnsupportedEmbeddingService(String provider, String model) {
        this.provider = provider;
        this.model = model;
    }

    @Override
    public EmbeddingResponse embed(String input) {
        throw new BusinessRuleException("AI embedding provider '" + provider + "' is configured but is not implemented yet.");
    }

    @Override
    public String provider() {
        return provider;
    }

    @Override
    public String model() {
        return model;
    }
}
