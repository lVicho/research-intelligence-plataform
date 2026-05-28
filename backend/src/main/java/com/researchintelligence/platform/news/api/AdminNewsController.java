package com.researchintelligence.platform.news.api;

import com.researchintelligence.platform.news.application.NewsArticleService;
import com.researchintelligence.platform.news.domain.NewsArticleStatus;
import com.researchintelligence.platform.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/admin/news")
public class AdminNewsController {

    private final NewsArticleService service;

    public AdminNewsController(NewsArticleService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<NewsArticleResponse> search(
        @RequestParam(required = false) NewsArticleStatus status,
        @RequestParam(required = false) String text,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.searchAdmin(status, text, page, size);
    }

    @GetMapping("/{id}")
    public NewsArticleResponse findById(@PathVariable Long id) {
        return service.findAdminById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NewsArticleResponse create(@Valid @RequestBody NewsArticleRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public NewsArticleResponse update(@PathVariable Long id, @Valid @RequestBody NewsArticleRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/publish")
    public NewsArticleResponse publish(@PathVariable Long id) {
        return service.publish(id);
    }

    @PostMapping("/{id}/archive")
    public NewsArticleResponse archive(@PathVariable Long id) {
        return service.archive(id);
    }
}
