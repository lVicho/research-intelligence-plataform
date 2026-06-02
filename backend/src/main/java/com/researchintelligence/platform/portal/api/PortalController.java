package com.researchintelligence.platform.portal.api;

import com.researchintelligence.platform.portal.application.PortalService;
import com.researchintelligence.platform.portal.application.PortalContextAssistantService;
import com.researchintelligence.platform.portal.application.PortalDemoQueryService;
import com.researchintelligence.platform.portal.application.PublicationExplanationService;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/portal")
public class PortalController {

    private final PortalService service;
    private final PortalContextAssistantService contextAssistantService;
    private final PortalDemoQueryService demoQueryService;
    private final PublicationExplanationService publicationExplanationService;

    public PortalController(
        PortalService service,
        PortalContextAssistantService contextAssistantService,
        PortalDemoQueryService demoQueryService,
        PublicationExplanationService publicationExplanationService
    ) {
        this.service = service;
        this.contextAssistantService = contextAssistantService;
        this.demoQueryService = demoQueryService;
        this.publicationExplanationService = publicationExplanationService;
    }

    @GetMapping("/summary")
    public PortalSummaryResponse summary() {
        return service.summary();
    }

    @GetMapping("/research-units")
    public PortalPageResponse<PortalResearchUnitSummaryResponse> researchUnits(
        @RequestParam(required = false) String text,
        @RequestParam(required = false) ResearchUnitType type,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.researchUnits(page, size, text, type);
    }

    @GetMapping("/research-units/{id}")
    public PortalResearchUnitDetailResponse researchUnitDetail(@PathVariable Long id) {
        return service.researchUnitDetail(id);
    }

    @GetMapping("/researchers")
    public PortalPageResponse<PortalResearcherSummaryResponse> researchers(
        @RequestParam(required = false) String text,
        @RequestParam(required = false) Long researchUnitId,
        @RequestParam(required = false) String topic,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.researchers(page, size, text, researchUnitId, topic);
    }

    @GetMapping("/researchers/{id}")
    public PortalResearcherDetailResponse researcherDetail(@PathVariable Long id) {
        return service.researcherDetail(id);
    }

    @GetMapping("/publications")
    public PortalPageResponse<PortalPublicationSummaryResponse> publications(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(required = false) String text,
        @RequestParam(required = false) Integer yearFrom,
        @RequestParam(required = false) Integer yearTo,
        @RequestParam(required = false) PublicationType type,
        @RequestParam(required = false) PublicationStatus status,
        @RequestParam(required = false) Long researchUnitId,
        @RequestParam(required = false) Long researcherId,
        @RequestParam(required = false) String topic,
        @RequestParam(defaultValue = "year") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        return service.publications(
            page,
            size,
            text,
            yearFrom,
            yearTo,
            type,
            status,
            researchUnitId,
            researcherId,
            topic,
            sortBy,
            sortDirection
        );
    }

    @GetMapping("/publications/{id}")
    public PortalPublicationDetailResponse publicationDetail(@PathVariable Long id) {
        return service.publicationDetail(id);
    }

    @PostMapping("/publications/{id}/explain")
    public PublicationExplanationResponse explainPublication(
        @PathVariable Long id,
        @RequestBody(required = false) PublicationExplanationRequest request
    ) {
        return publicationExplanationService.explain(id, request);
    }

    @PostMapping("/context-assistant/ask")
    public PortalContextAssistantResponse askContextAssistant(@jakarta.validation.Valid @RequestBody PortalContextAssistantRequest request) {
        return contextAssistantService.ask(request);
    }

    @GetMapping("/search")
    public PortalSearchResponse search(
        @RequestParam(defaultValue = "") String query,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
    ) {
        return service.search(query, page, size);
    }

    @GetMapping("/demo-queries")
    public java.util.List<PortalDemoQueryResponse> demoQueries(
        @RequestParam(defaultValue = "GENERAL") PortalDemoQueryContext context,
        @RequestParam(defaultValue = "10") @Min(1) @Max(30) int limit,
        @RequestParam(defaultValue = "true") boolean onlyValidated
    ) {
        return demoQueryService.generate(context, limit, onlyValidated);
    }
}
