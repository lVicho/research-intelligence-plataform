package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.application.ValidationAssistanceReviewService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/ai/validation")
public class ValidationAssistanceController {

    private final ValidationAssistanceReviewService service;

    public ValidationAssistanceController(ValidationAssistanceReviewService service) {
        this.service = service;
    }

    @PostMapping("/review")
    public ValidationAssistanceReviewResponse review(@Valid @RequestBody ValidationAssistanceReviewRequest request) {
        return service.review(request);
    }
}
