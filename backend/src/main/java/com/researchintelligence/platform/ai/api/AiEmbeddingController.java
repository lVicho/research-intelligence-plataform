package com.researchintelligence.platform.ai.api;

import com.researchintelligence.platform.ai.application.PublicationEmbeddingRebuildService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/embeddings/publications")
public class AiEmbeddingController {

    private final PublicationEmbeddingRebuildService service;

    public AiEmbeddingController(PublicationEmbeddingRebuildService service) {
        this.service = service;
    }

    @PostMapping("/rebuild")
    public PublicationEmbeddingRebuildResponse rebuildPublicationEmbeddings() {
        return service.rebuild();
    }
}
