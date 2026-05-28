package com.researchintelligence.platform.audit.api;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.audit.domain.ActivityAuditAction;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/audit")
public class ActivityAuditController {

    private final ActivityAuditService service;

    public ActivityAuditController(ActivityAuditService service) {
        this.service = service;
    }

    @GetMapping("/events")
    public PageResponse<ActivityAuditEventResponse> events(
        @RequestParam(required = false) ValidationEntityType entityType,
        @RequestParam(required = false) Long entityId,
        @RequestParam(required = false) ActivityAuditAction action,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.findEvents(entityType, entityId, action, page, size);
    }

    @GetMapping("/entities/{entityType}/{entityId}")
    public PageResponse<ActivityAuditEventResponse> entityEvents(
        @PathVariable ValidationEntityType entityType,
        @PathVariable Long entityId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.findEntityEvents(entityType, entityId, page, size);
    }
}
