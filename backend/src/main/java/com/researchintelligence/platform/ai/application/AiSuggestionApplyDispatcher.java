package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.ai.persistence.AiSuggestionEntity;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiSuggestionApplyDispatcher {

    private final List<AiSuggestionApplyHandler> handlers;

    public AiSuggestionApplyDispatcher(List<AiSuggestionApplyHandler> handlers) {
        this.handlers = handlers;
    }

    public AiSuggestionApplyResult apply(AiSuggestionEntity suggestion, String acceptedDataJson, PlatformUserPrincipal reviewer) {
        return handlers.stream()
            .filter(handler -> handler.supports(suggestion))
            .findFirst()
            .map(handler -> handler.apply(suggestion, acceptedDataJson, reviewer))
            .orElseGet(AiSuggestionApplyResult::noOp);
    }
}
