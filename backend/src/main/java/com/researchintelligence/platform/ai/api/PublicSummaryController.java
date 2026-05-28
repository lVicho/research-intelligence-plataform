package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.application.PublicSummaryGenerationService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/ai/public-summary")
public class PublicSummaryController {

    private final PublicSummaryGenerationService service;

    public PublicSummaryController(PublicSummaryGenerationService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public PublicSummaryGenerateResponse generate(@Valid @RequestBody PublicSummaryGenerateRequest request) {
        return service.generate(request);
    }
}
