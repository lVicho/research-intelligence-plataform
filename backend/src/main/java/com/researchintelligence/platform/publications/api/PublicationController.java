package com.researchintelligence.platform.publications.api;

import com.researchintelligence.platform.publications.application.PublicationService;
import com.researchintelligence.platform.publications.application.RelatedPublicationService;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/publications")
public class PublicationController {

    private final PublicationService service;
    private final RelatedPublicationService relatedPublicationService;

    public PublicationController(PublicationService service, RelatedPublicationService relatedPublicationService) {
        this.service = service;
        this.relatedPublicationService = relatedPublicationService;
    }

    @GetMapping
    public PageResponse<PublicationSummaryResponse> search(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
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
        return service.search(page, size, text, yearFrom, yearTo, type, status, researchUnitId, researcherId, topic, sortBy, sortDirection);
    }

    @GetMapping("/filter-metadata")
    public PublicationFilterMetadataResponse filterMetadata() {
        return service.filterMetadata();
    }

    @GetMapping("/{id}")
    public PublicationResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/{id}/related")
    public RelatedPublicationsResponse findRelated(
        @PathVariable Long id,
        @RequestParam(defaultValue = "15") @Min(1) @Max(50) Integer limit,
        @RequestParam(required = false) @DecimalMin("0.0") @DecimalMax("1.0") Double minScore,
        @RequestParam(required = false) Boolean includeNonValidated
    ) {
        return relatedPublicationService.findRelated(id, limit, minScore, includeNonValidated);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicationResponse create(@Valid @RequestBody PublicationRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public PublicationResponse update(
        @PathVariable Long id,
        @Valid @RequestBody PublicationRequest request,
        Authentication authentication
    ) {
        PlatformUserPrincipal principal = (PlatformUserPrincipal) authentication.getPrincipal();
        if (principal.roles().contains("ADMIN")) {
            return service.update(id, request);
        }
        return service.updateOwn(principal.researcherId(), id, request);
    }

    @PostMapping("/{id}/submit")
    public PublicationResponse submit(@PathVariable Long id, Authentication authentication) {
        PlatformUserPrincipal principal = (PlatformUserPrincipal) authentication.getPrincipal();
        return service.submit(id, principal);
    }
}
