package com.researchintelligence.platform.strategicmap.api;

import com.researchintelligence.platform.strategicmap.application.StrategicResearchMapService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/strategic-map")
public class StrategicMapController {

    private final StrategicResearchMapService service;

    public StrategicMapController(StrategicResearchMapService service) {
        this.service = service;
    }

    @GetMapping("/research-lines")
    public StrategicResearchMapResponse researchLines(
        @RequestParam(required = false) @Min(1500) @Max(2200) Integer yearFrom,
        @RequestParam(required = false) @Min(1500) @Max(2200) Integer yearTo,
        @RequestParam(required = false) Long researchUnitId,
        @RequestParam(defaultValue = "true") Boolean onlyValidated
    ) {
        return service.researchLines(yearFrom, yearTo, researchUnitId, onlyValidated);
    }
}
