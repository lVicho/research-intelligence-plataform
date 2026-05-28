package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.shared.application.BusinessRuleException;

public class UnsupportedLlmService implements LlmService {

    private final String provider;
    private final String model;

    public UnsupportedLlmService(String provider, String model) {
        this.provider = provider;
        this.model = model;
    }

    @Override
    public LlmResponse answer(LlmPrompt prompt) {
        throw new BusinessRuleException("AI provider '" + provider + "' is configured but is not implemented yet.");
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
