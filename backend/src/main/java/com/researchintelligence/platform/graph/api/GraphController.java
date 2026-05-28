package com.researchintelligence.platform.graph.api;

import com.researchintelligence.platform.graph.application.ResearchGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final ResearchGraphService service;

    public GraphController(ResearchGraphService service) {
        this.service = service;
    }

    @GetMapping("/researcher/{researcherId}")
    public GraphResponse researcherGraph(
        @PathVariable Long researcherId,
        @RequestParam(required = false) GraphDensity density,
        @RequestParam(required = false) Boolean includePublications,
        @RequestParam(required = false) Boolean includeTopics,
        @RequestParam(required = false) Boolean includeCoauthors,
        @RequestParam(required = false) Boolean includeResearchUnits,
        @RequestParam(required = false) Boolean includeExternalAuthors,
        @RequestParam(required = false) Integer maxPublications,
        @RequestParam(required = false) Integer maxTopics,
        @RequestParam(required = false) Integer maxCoauthors,
        @RequestParam(required = false) Boolean includeNonValidated
    ) {
        return service.researcherGraph(
            researcherId,
            density,
            includePublications,
            includeTopics,
            includeCoauthors,
            includeResearchUnits,
            includeExternalAuthors,
            maxPublications,
            maxTopics,
            maxCoauthors,
            includeNonValidated
        );
    }
}
