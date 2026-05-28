package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.application.AdminConversationalSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/conversational-search")
public class AdminConversationalSearchController {

    private final AdminConversationalSearchService service;

    public AdminConversationalSearchController(AdminConversationalSearchService service) {
        this.service = service;
    }

    @PostMapping
    public AdminConversationalSearchResponse search(@Valid @RequestBody AdminConversationalSearchRequest request) {
        return service.search(request);
    }
}
