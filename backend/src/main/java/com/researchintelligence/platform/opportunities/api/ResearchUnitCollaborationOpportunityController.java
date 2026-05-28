package com.researchintelligence.platform.opportunities.api;

import com.researchintelligence.platform.opportunities.application.OpportunityMode;
import com.researchintelligence.platform.opportunities.application.ResearchUnitCollaborationOpportunityService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/opportunities")
public class ResearchUnitCollaborationOpportunityController {

    private final ResearchUnitCollaborationOpportunityService service;

    public ResearchUnitCollaborationOpportunityController(ResearchUnitCollaborationOpportunityService service) {
        this.service = service;
    }

    @GetMapping("/research-unit-collaborations")
    public List<ResearchUnitCollaborationOpportunityResponse> researchUnitCollaborations(
        @RequestParam(required = false) @Min(1500) @Max(2200) Integer yearFrom,
        @RequestParam(required = false) @Min(1500) @Max(2200) Integer yearTo,
        @RequestParam(required = false) OpportunityMode mode,
        @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
        @RequestParam(defaultValue = "true") Boolean onlyValidated
    ) {
        return service.findResearchUnitCollaborations(yearFrom, yearTo, mode, limit, onlyValidated);
    }
}
