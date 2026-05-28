package com.researchintelligence.platform.events.api;

import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.events.application.EventParticipationService;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.validation.Valid;
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
@RequestMapping("/api/event-participations")
public class EventParticipationController {

    private final EventParticipationService service;

    public EventParticipationController(EventParticipationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<EventParticipationResponse> search(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(required = false) String text,
        @RequestParam(required = false) Long eventId,
        @RequestParam(required = false) Long researcherId,
        @RequestParam(required = false) Long researchUnitId,
        @RequestParam(required = false) ValidationStatus validationStatus
    ) {
        return service.search(page, size, text, eventId, researcherId, researchUnitId, validationStatus);
    }

    @GetMapping("/{id}")
    public EventParticipationResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventParticipationResponse create(
        @Valid @RequestBody EventParticipationRequest request,
        Authentication authentication
    ) {
        return service.create(request, currentUser(authentication));
    }

    @PutMapping("/{id}")
    public EventParticipationResponse update(
        @PathVariable Long id,
        @Valid @RequestBody EventParticipationRequest request,
        Authentication authentication
    ) {
        return service.update(id, request, currentUser(authentication));
    }

    @PostMapping("/{id}/submit")
    public EventParticipationResponse submit(@PathVariable Long id, Authentication authentication) {
        return service.submit(id, currentUser(authentication));
    }

    private PlatformUserPrincipal currentUser(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof PlatformUserPrincipal principal ? principal : null;
    }
}
