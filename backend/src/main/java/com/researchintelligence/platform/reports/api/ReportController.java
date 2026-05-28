package com.researchintelligence.platform.reports.api;

import com.researchintelligence.platform.reports.application.ReportGenerationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportGenerationService service;

    public ReportController(ReportGenerationService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public GenerateReportResponse generate(@Valid @RequestBody GenerateReportRequest request) {
        return service.generate(request);
    }
}
