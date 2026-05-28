package com.researchintelligence.platform.expertfinder.api;

import com.researchintelligence.platform.expertfinder.application.ExpertFinderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/expert-finder")
public class ExpertFinderController {

    private final ExpertFinderService service;

    public ExpertFinderController(ExpertFinderService service) {
        this.service = service;
    }

    @PostMapping("/search")
    public ExpertFinderSearchResponse search(@Valid @RequestBody ExpertFinderSearchRequest request) {
        return service.search(request);
    }
}
