package com.researchintelligence.platform.analytics.api;

import com.researchintelligence.platform.analytics.application.AnalyticsService;
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
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public AnalyticsSummaryResponse summary(
        @RequestParam(required = false) ValidationStatus validationStatus,
        @RequestParam(required = false) Boolean includeNonValidated
    ) {
        return service.summary(validationStatus, includeNonValidated);
    }

    @GetMapping("/institutional-overview")
    public InstitutionalOverviewResponse institutionalOverview(
        @RequestParam(required = false) ValidationStatus validationStatus,
        @RequestParam(required = false) Boolean includeNonValidated
    ) {
        return service.institutionalOverview(validationStatus, includeNonValidated);
    }

    @GetMapping("/topics/trends")
    public TopicTrendsResponse topicTrends(
        @RequestParam(required = false) ValidationStatus validationStatus,
        @RequestParam(required = false) Boolean includeNonValidated,
        @RequestParam(required = false) @Min(1) @Max(10) Integer recentWindowYears,
        @RequestParam(required = false) @Min(1) @Max(50) Integer limit
    ) {
        return service.topicTrends(validationStatus, includeNonValidated, recentWindowYears, limit);
    }

    @GetMapping("/collaborations")
    public CollaborationsResponse collaborations(
        @RequestParam(required = false) ValidationStatus validationStatus,
        @RequestParam(required = false) Boolean includeNonValidated,
        @RequestParam(required = false) @Min(1) @Max(50) Integer limit
    ) {
        return service.collaborations(validationStatus, includeNonValidated, limit);
    }
}
