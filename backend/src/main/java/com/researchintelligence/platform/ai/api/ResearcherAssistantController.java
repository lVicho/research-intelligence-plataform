package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.application.ResearcherAssistantService;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/me/assistant")
public class ResearcherAssistantController {

    private final ResearcherAssistantService service;

    public ResearcherAssistantController(ResearcherAssistantService service) {
        this.service = service;
    }

    @PostMapping("/ask")
    public ResearcherAssistantResponse ask(
        @Valid @RequestBody ResearcherAssistantAskRequest request,
        Authentication authentication
    ) {
        return service.ask((PlatformUserPrincipal) authentication.getPrincipal(), request);
    }
}
