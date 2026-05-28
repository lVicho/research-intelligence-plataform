package com.researchintelligence.platform.ai.application;

public record LlmPrompt(
    String question,
    String context
) {
}
