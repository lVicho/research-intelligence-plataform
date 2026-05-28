package com.researchintelligence.platform.events.api;

import com.researchintelligence.platform.events.application.ScientificEventService;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/scientific-events")
public class ScientificEventController {

    private final ScientificEventService service;

    public ScientificEventController(ScientificEventService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ScientificEventResponse> search(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(required = false) String text,
        @RequestParam(required = false) String eventTypeCode,
        @RequestParam(required = false) Long venueId,
        @RequestParam(required = false) Boolean active,
        @RequestParam(required = false) ValidationStatus validationStatus
    ) {
        return service.search(page, size, text, eventTypeCode, venueId, active, validationStatus);
    }

    @GetMapping("/{id}")
    public ScientificEventResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScientificEventResponse create(@Valid @RequestBody ScientificEventRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ScientificEventResponse update(@PathVariable Long id, @Valid @RequestBody ScientificEventRequest request) {
        return service.update(id, request);
    }
}
