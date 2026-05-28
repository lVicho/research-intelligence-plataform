package com.researchintelligence.platform.ai.api;

import java.util.List;
import java.util.Map;

public record AdminConversationalSearchResponse(
    String interpretedIntent,
    Map<String, Object> filters,
    String resultType,
    List<Map<String, Object>> results,
    List<String> warnings,
    String explanation,
    boolean clarificationNeeded,
    List<String> clarificationOptions
) {
}
