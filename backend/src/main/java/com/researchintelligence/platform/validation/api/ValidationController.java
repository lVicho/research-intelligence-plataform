package com.researchintelligence.platform.validation.api;

import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.validation.application.ValidationInboxService;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/validation")
public class ValidationController {

    private final ValidationInboxService service;

    public ValidationController(ValidationInboxService service) {
        this.service = service;
    }

    @GetMapping("/inbox")
    public PageResponse<ValidationItemResponse> inbox(
        @RequestParam(required = false) ValidationStatus status,
        @RequestParam(required = false) ValidationEntityType entityType,
        @RequestParam(required = false) Long researcherId,
        @RequestParam(required = false) Long researchUnitId,
        @RequestParam(required = false) Instant submittedFrom,
        @RequestParam(required = false) Instant submittedTo,
        @RequestParam(required = false) String text,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue = "submittedAt,desc") String sort
    ) {
        return service.search(status, entityType, researcherId, researchUnitId, submittedFrom, submittedTo, text, page, size, sort);
    }

    @GetMapping("/items/{entityType}/{entityId}")
    public ValidationItemDetailResponse findItem(
        @PathVariable ValidationEntityType entityType,
        @PathVariable Long entityId
    ) {
        return service.findDetail(entityType, entityId);
    }

    @PostMapping("/items/{entityType}/{entityId}/validate")
    public ValidationItemDetailResponse validate(
        @PathVariable ValidationEntityType entityType,
        @PathVariable Long entityId,
        @Valid @RequestBody(required = false) ValidationCommentRequest request,
        Authentication authentication
    ) {
        return service.validate(entityType, entityId, comment(request), currentUserId(authentication));
    }

    @PostMapping("/items/{entityType}/{entityId}/reject")
    public ValidationItemDetailResponse reject(
        @PathVariable ValidationEntityType entityType,
        @PathVariable Long entityId,
        @Valid @RequestBody(required = false) ValidationCommentRequest request,
        Authentication authentication
    ) {
        return service.reject(entityType, entityId, comment(request), currentUserId(authentication));
    }

    @PostMapping("/items/{entityType}/{entityId}/request-changes")
    public ValidationItemDetailResponse requestChanges(
        @PathVariable ValidationEntityType entityType,
        @PathVariable Long entityId,
        @Valid @RequestBody(required = false) ValidationCommentRequest request,
        Authentication authentication
    ) {
        return service.requestChanges(entityType, entityId, comment(request), currentUserId(authentication));
    }

    private String comment(ValidationCommentRequest request) {
        return request == null ? null : request.comment();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof PlatformUserPrincipal principal) {
            return principal.id();
        }
        return null;
    }
}
