package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.application.AiSuggestionService;
import com.researchintelligence.platform.ai.domain.AiSuggestionStatus;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/ai-suggestions")
public class AiSuggestionController {

    private final AiSuggestionService service;

    public AiSuggestionController(AiSuggestionService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AiSuggestionResponse> findSuggestions(
        @RequestParam(required = false) String targetType,
        @RequestParam(required = false) Long targetId,
        @RequestParam(required = false) AiSuggestionType suggestionType,
        @RequestParam(required = false) AiSuggestionStatus status,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.findSuggestions(targetType, targetId, suggestionType, status, page, size);
    }

    @GetMapping("/{id}")
    public AiSuggestionResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping("/{id}/accept")
    public AiSuggestionResponse accept(
        @PathVariable Long id,
        @Valid @RequestBody(required = false) AiSuggestionReviewRequest request
    ) {
        return service.accept(id, request);
    }

    @PostMapping("/{id}/reject")
    public AiSuggestionResponse reject(
        @PathVariable Long id,
        @Valid @RequestBody(required = false) AiSuggestionReviewRequest request
    ) {
        return service.reject(id, request);
    }

    @PostMapping("/{id}/edit-and-accept")
    public AiSuggestionResponse editAndAccept(
        @PathVariable Long id,
        @Valid @RequestBody AiSuggestionEditAndAcceptRequest request
    ) {
        return service.editAndAccept(id, request);
    }
}
