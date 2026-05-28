package com.researchintelligence.platform.dataquality.api;

import com.researchintelligence.platform.dataquality.application.DataQualityService;
import com.researchintelligence.platform.dataquality.domain.DataQualityEntityType;
import com.researchintelligence.platform.dataquality.domain.DataQualityIssueType;
import com.researchintelligence.platform.dataquality.domain.DataQualitySeverity;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/data-quality")
public class DataQualityController {

    private final DataQualityService service;

    public DataQualityController(DataQualityService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public DataQualitySummaryResponse summary() {
        return service.summary();
    }

    @GetMapping("/issues")
    public PageResponse<DataQualityIssueResponse> issues(
        @RequestParam(required = false) DataQualityIssueType issueType,
        @RequestParam(required = false) DataQualitySeverity severity,
        @RequestParam(required = false) DataQualityEntityType entityType,
        @RequestParam(required = false) ValidationStatus validationStatus,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.issues(issueType, severity, entityType, validationStatus, page, size);
    }
}
