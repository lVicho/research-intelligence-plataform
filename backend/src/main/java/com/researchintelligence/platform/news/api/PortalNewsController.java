package com.researchintelligence.platform.news.api;

import com.researchintelligence.platform.news.application.NewsArticleService;
import com.researchintelligence.platform.shared.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/portal/news")
public class PortalNewsController {

    private final NewsArticleService service;

    public PortalNewsController(NewsArticleService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PortalNewsArticleSummaryResponse> search(
        @RequestParam(required = false) String text,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.searchPublic(text, page, size);
    }

    @GetMapping("/{id}")
    public PortalNewsArticleResponse findById(@PathVariable Long id) {
        return service.findPublicById(id);
    }
}
