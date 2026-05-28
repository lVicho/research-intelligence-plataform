package com.researchintelligence.platform.publications.api;

import com.researchintelligence.platform.publications.application.PublisherService;
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
@RequestMapping("/api/publishers")
public class PublisherController {

    private final PublisherService service;

    public PublisherController(PublisherService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PublisherResponse> search(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(required = false) String text,
        @RequestParam(required = false) Boolean active
    ) {
        return service.search(page, size, text, active);
    }

    @GetMapping("/{id}")
    public PublisherResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublisherResponse create(@Valid @RequestBody PublisherRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public PublisherResponse update(@PathVariable Long id, @Valid @RequestBody PublisherRequest request) {
        return service.update(id, request);
    }
}
