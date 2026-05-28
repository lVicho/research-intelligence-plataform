package com.researchintelligence.platform.reports.api;

import com.researchintelligence.platform.reports.application.ReportTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report-templates")
public class ReportTemplateController {

    private final ReportTemplateService service;

    public ReportTemplateController(ReportTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReportTemplateResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ReportTemplateResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportTemplateResponse create(@Valid @RequestBody ReportTemplateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ReportTemplateResponse update(@PathVariable Long id, @Valid @RequestBody ReportTemplateRequest request) {
        return service.update(id, request);
    }
}
