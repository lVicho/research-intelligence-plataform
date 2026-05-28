package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.ai.persistence.AiSuggestionEntity;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;

public interface AiSuggestionApplyHandler {

    boolean supports(AiSuggestionEntity suggestion);

    AiSuggestionApplyResult apply(AiSuggestionEntity suggestion, String acceptedDataJson, PlatformUserPrincipal reviewer);
}
