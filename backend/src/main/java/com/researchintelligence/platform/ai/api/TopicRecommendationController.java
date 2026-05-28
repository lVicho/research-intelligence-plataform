package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.application.TopicRecommendationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/topics")
public class TopicRecommendationController {

    private final TopicRecommendationService service;

    public TopicRecommendationController(TopicRecommendationService service) {
        this.service = service;
    }

    @PostMapping("/recommend")
    public TopicRecommendationResponse recommend(@Valid @RequestBody TopicRecommendationRequest request) {
        return service.recommend(request);
    }
}
