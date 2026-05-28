package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.application.AiDataQualitySuggestionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/ai/data-quality")
public class AiDataQualityController {

    private final AiDataQualitySuggestionService service;

    public AiDataQualityController(AiDataQualitySuggestionService service) {
        this.service = service;
    }

    @PostMapping("/suggest-fixes")
    public List<DataQualityFixSuggestionResponse> suggestFixes(@Valid @RequestBody DataQualityFixSuggestionRequest request) {
        return service.suggestFixes(request);
    }
}
