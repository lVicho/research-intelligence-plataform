package com.researchintelligence.platform.ai.application;

public record AiSuggestionApplyResult(
    boolean applied,
    String handler,
    String message
) {
    public static AiSuggestionApplyResult noOp() {
        return new AiSuggestionApplyResult(false, "NO_OP", "No concrete apply handler is registered for this suggestion.");
    }
}
