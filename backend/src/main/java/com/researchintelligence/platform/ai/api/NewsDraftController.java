package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.application.NewsDraftGenerationService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/ai/news")
public class NewsDraftController {

    private final NewsDraftGenerationService service;

    public NewsDraftController(NewsDraftGenerationService service) {
        this.service = service;
    }

    @PostMapping("/generate-draft")
    public NewsDraftGenerateResponse generateDraft(@Valid @RequestBody NewsDraftGenerateRequest request) {
        return service.generateDraft(request);
    }
}
