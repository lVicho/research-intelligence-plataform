package com.researchintelligence.platform.ai.application;

public interface LlmService {

    LlmResponse answer(LlmPrompt prompt);

    String provider();

    String model();
}
