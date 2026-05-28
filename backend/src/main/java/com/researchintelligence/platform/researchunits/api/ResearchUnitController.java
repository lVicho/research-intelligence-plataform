package com.researchintelligence.platform.researchunits.api;

import com.researchintelligence.platform.researchunits.application.ResearchUnitService;
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
@RequestMapping("/api/research-units")
public class ResearchUnitController {

    private final ResearchUnitService service;

    public ResearchUnitController(ResearchUnitService service) {
        this.service = service;
    }

    @GetMapping
    public List<ResearchUnitListResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/tree")
    public List<ResearchUnitTreeNode> findTree() {
        return service.findTree();
    }

    @GetMapping("/{id}")
    public ResearchUnitResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResearchUnitResponse create(@Valid @RequestBody ResearchUnitRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ResearchUnitResponse update(@PathVariable Long id, @Valid @RequestBody ResearchUnitRequest request) {
        return service.update(id, request);
    }
}
