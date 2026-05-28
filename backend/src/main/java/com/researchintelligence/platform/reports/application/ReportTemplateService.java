package com.researchintelligence.platform.reports.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.reports.api.ReportTemplateRequest;
import com.researchintelligence.platform.reports.api.ReportTemplateResponse;
import com.researchintelligence.platform.reports.domain.ReportOutputFormat;
import com.researchintelligence.platform.reports.domain.ReportSection;
import com.researchintelligence.platform.reports.persistence.ReportTemplateEntity;
import com.researchintelligence.platform.reports.persistence.ReportTemplateRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportTemplateService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ReportTemplateRepository repository;
    private final ObjectMapper objectMapper;

    public ReportTemplateService(ReportTemplateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ReportTemplateResponse> findAll() {
        return repository.findAllByOrderByNameAsc()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ReportTemplateResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public Optional<ReportTemplateEntity> findActiveEntity(Long id) {
        return repository.findById(id).filter(ReportTemplateEntity::isActive);
    }

    @Transactional
    public ReportTemplateResponse create(ReportTemplateRequest request) {
        validateRequest(request);
        ReportTemplateEntity entity = new ReportTemplateEntity(
            clean(request.name()),
            cleanNullable(request.description()),
            request.targetType(),
            toSectionsJson(normalizeSections(request.sections())),
            request.defaultYearFrom(),
            request.defaultYearTo(),
            request.outputFormat(),
            request.active() == null || request.active()
        );
        return toResponse(repository.save(entity));
    }

    @Transactional
    public ReportTemplateResponse update(Long id, ReportTemplateRequest request) {
        validateRequest(request);
        ReportTemplateEntity entity = findEntity(id);
        entity.setName(clean(request.name()));
        entity.setDescription(cleanNullable(request.description()));
        entity.setTargetType(request.targetType());
        entity.setSectionsJson(toSectionsJson(normalizeSections(request.sections())));
        entity.setDefaultYearFrom(request.defaultYearFrom());
        entity.setDefaultYearTo(request.defaultYearTo());
        entity.setOutputFormat(request.outputFormat());
        entity.setActive(request.active() == null || request.active());
        return toResponse(entity);
    }

    public List<ReportSection> sections(ReportTemplateEntity entity) {
        return parseSections(entity.getSectionsJson());
    }

    private ReportTemplateEntity findEntity(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ReportTemplate", id));
    }

    private void validateRequest(ReportTemplateRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Report template request is required.");
        }
        if (request.defaultYearFrom() != null && request.defaultYearTo() != null && request.defaultYearFrom() > request.defaultYearTo()) {
            throw new BusinessRuleException("defaultYearFrom must be less than or equal to defaultYearTo.");
        }
        normalizeSections(request.sections());
    }

    private List<ReportSection> normalizeSections(List<String> rawSections) {
        LinkedHashSet<ReportSection> sections = new LinkedHashSet<>();
        if (rawSections != null) {
            for (String rawSection : rawSections) {
                ReportSection section = ReportSection.fromApiValue(rawSection)
                    .orElseThrow(() -> new BusinessRuleException("Unknown report section: " + rawSection));
                sections.add(section);
            }
        }
        if (sections.isEmpty()) {
            throw new BusinessRuleException("At least one report section is required.");
        }
        return List.copyOf(sections);
    }

    private String toSectionsJson(List<ReportSection> sections) {
        try {
            return objectMapper.writeValueAsString(sections.stream().map(Enum::name).toList());
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException("Could not serialize report template sections.");
        }
    }

    private List<ReportSection> parseSections(String sectionsJson) {
        try {
            List<String> values = objectMapper.readValue(sectionsJson, STRING_LIST);
            return normalizeSections(values);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException("Report template sections are not valid JSON.");
        }
    }

    private ReportTemplateResponse toResponse(ReportTemplateEntity entity) {
        return new ReportTemplateResponse(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getTargetType(),
            parseSections(entity.getSectionsJson()).stream().map(Enum::name).toList(),
            entity.getDefaultYearFrom(),
            entity.getDefaultYearTo(),
            entity.getOutputFormat() == null ? ReportOutputFormat.MARKDOWN : entity.getOutputFormat(),
            entity.isActive(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getCreatedByUserId(),
            entity.getUpdatedByUserId()
        );
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String cleanNullable(String value) {
        String cleanValue = clean(value);
        return cleanValue == null || cleanValue.isBlank() ? null : cleanValue;
    }
}
