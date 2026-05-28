package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.ai.api.ConversationalSearchEntityScope;
import java.util.List;

public record ConversationalSearchInterpretation(
    ConversationalSearchEntityScope entityScope,
    String interpretedIntent,
    ConversationalSearchFilters filters,
    boolean clarificationNeeded,
    List<String> clarificationOptions,
    List<String> warnings
) {
}
