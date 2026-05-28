package com.researchintelligence.platform.publications.api;

import com.researchintelligence.platform.publications.application.TopicNormalizationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/topics")
public class TopicController {

    private final TopicNormalizationService service;

    public TopicController(TopicNormalizationService service) {
        this.service = service;
    }

    @GetMapping("/normalization-candidates")
    public List<TopicNormalizationCandidateGroupResponse> normalizationCandidates() {
        return service.findNormalizationCandidates();
    }

    @PostMapping("/merge")
    public TopicMergeResponse merge(@Valid @RequestBody TopicMergeRequest request) {
        return service.merge(request);
    }

    @PostMapping("/suggest-canonical-name")
    public TopicCanonicalNameResponse suggestCanonicalName(@RequestBody TopicCanonicalNameRequest request) {
        return service.suggestCanonicalName(request);
    }
}
