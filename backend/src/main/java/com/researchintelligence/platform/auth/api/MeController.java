package com.researchintelligence.platform.auth.api;

import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.ResearcherWorkspaceService;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/me")
public class MeController {

    private final ResearcherWorkspaceService service;

    public MeController(ResearcherWorkspaceService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public MeDashboardResponse dashboard(Authentication authentication) {
        return service.dashboard(currentUser(authentication));
    }

    @GetMapping("/activities")
    public PageResponse<MeActivityResponse> activities(
        @RequestParam(required = false) ValidationStatus status,
        @RequestParam(required = false) ValidationEntityType type,
        @RequestParam(required = false) String text,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        Authentication authentication
    ) {
        return service.activities(currentUser(authentication), status, type, text, page, size);
    }

    @GetMapping("/activities/{entityType}/{entityId}")
    public MeActivityDetailResponse activityDetail(
        @PathVariable ValidationEntityType entityType,
        @PathVariable Long entityId,
        Authentication authentication
    ) {
        return service.activityDetail(currentUser(authentication), entityType, entityId);
    }

    @PostMapping("/activities/{entityType}/{entityId}/submit")
    public MeActivityDetailResponse submitActivity(
        @PathVariable ValidationEntityType entityType,
        @PathVariable Long entityId,
        Authentication authentication
    ) {
        return service.submitActivity(currentUser(authentication), entityType, entityId);
    }

    private PlatformUserPrincipal currentUser(Authentication authentication) {
        return (PlatformUserPrincipal) authentication.getPrincipal();
    }
}
