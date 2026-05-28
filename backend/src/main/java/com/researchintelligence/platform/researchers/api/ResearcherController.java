package com.researchintelligence.platform.researchers.api;

import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.researchers.application.ResearcherService;
import com.researchintelligence.platform.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/researchers")
public class ResearcherController {

    private final ResearcherService service;

    public ResearcherController(ResearcherService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ResearcherSummaryResponse> search(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String text,
        @RequestParam(required = false) Long researchUnitId,
        @RequestParam(required = false) Boolean active
    ) {
        return service.search(page, size, text, researchUnitId, active);
    }

    @GetMapping("/{id}")
    public ResearcherResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResearcherResponse create(@Valid @RequestBody ResearcherRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ResearcherResponse update(
        @PathVariable Long id,
        @Valid @RequestBody ResearcherRequest request,
        Authentication authentication
    ) {
        PlatformUserPrincipal principal = (PlatformUserPrincipal) authentication.getPrincipal();
        if (principal.roles().contains("ADMIN")) {
            return service.update(id, request);
        }
        return service.updateOwn(principal.researcherId(), id, request);
    }

    @GetMapping("/{id}/affiliations")
    public List<ResearcherAffiliationPublicResponse> findAffiliations(@PathVariable Long id) {
        return service.findAffiliations(id);
    }

    @PostMapping("/{id}/affiliations")
    @ResponseStatus(HttpStatus.CREATED)
    public ResearcherAffiliationResponse addAffiliation(
        @PathVariable Long id,
        @Valid @RequestBody ResearcherAffiliationRequest request
    ) {
        return service.addAffiliation(id, request);
    }

    @PutMapping("/{id}/affiliations/{affiliationId}")
    public ResearcherAffiliationResponse updateAffiliation(
        @PathVariable Long id,
        @PathVariable Long affiliationId,
        @Valid @RequestBody ResearcherAffiliationRequest request,
        Authentication authentication
    ) {
        PlatformUserPrincipal principal = (PlatformUserPrincipal) authentication.getPrincipal();
        if (!principal.roles().contains("ADMIN")) {
            return service.updateOwnAffiliation(principal.researcherId(), id, affiliationId, request);
        }
        return service.updateAffiliation(id, affiliationId, request);
    }

    @DeleteMapping("/{id}/affiliations/{affiliationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAffiliation(@PathVariable Long id, @PathVariable Long affiliationId) {
        service.deleteAffiliation(id, affiliationId);
    }
}
