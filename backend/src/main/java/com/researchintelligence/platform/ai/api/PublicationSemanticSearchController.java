package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.application.PublicationSemanticSearchService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/publications")
public class PublicationSemanticSearchController {

    private final PublicationSemanticSearchService service;

    public PublicationSemanticSearchController(PublicationSemanticSearchService service) {
        this.service = service;
    }

    @GetMapping("/semantic-search")
    public List<PublicationSemanticSearchResponse> semanticSearch(
        @RequestParam @NotBlank String query,
        @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
        @RequestParam(required = false) @DecimalMin("0.0") @DecimalMax("1.0") Double minSimilarity,
        @RequestParam(required = false) Boolean includeNonValidated
    ) {
        return service.search(query, limit, minSimilarity, includeNonValidated);
    }
}
