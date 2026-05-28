package com.researchintelligence.platform.reports.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.reports.api.ReportTemplateRequest;
import com.researchintelligence.platform.reports.api.ReportTemplateResponse;
import com.researchintelligence.platform.reports.domain.ReportOutputFormat;
import com.researchintelligence.platform.reports.domain.ReportType;
import com.researchintelligence.platform.reports.persistence.ReportTemplateEntity;
import com.researchintelligence.platform.reports.persistence.ReportTemplateRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportTemplateServiceTest {

    @Mock
    private ReportTemplateRepository repository;

    private ReportTemplateService service;

    @BeforeEach
    void setUp() {
        service = new ReportTemplateService(repository, new ObjectMapper());
    }

    @Test
    void createsTemplateWithNormalizedUniqueSections() {
        when(repository.save(any(ReportTemplateEntity.class))).thenAnswer(invocation -> {
            ReportTemplateEntity entity = invocation.getArgument(0);
            entity.setId(12L);
            return entity;
        });

        ReportTemplateResponse response = service.create(new ReportTemplateRequest(
            " Informe anual ",
            "Demo",
            ReportType.RESEARCH_UNIT,
            List.of("EXECUTIVE_SUMMARY", "resumen ejecutivo", "DATA_QUALITY"),
            2020,
            2026,
            ReportOutputFormat.MARKDOWN,
            true
        ));

        assertEquals(12L, response.id());
        assertEquals("Informe anual", response.name());
        assertEquals(List.of("EXECUTIVE_SUMMARY", "DATA_QUALITY"), response.sections());
    }

    @Test
    void rejectsUnknownSections() {
        ReportTemplateRequest request = new ReportTemplateRequest(
            "Plantilla insegura",
            null,
            ReportType.TOPIC,
            List.of("FULL_PROMPT"),
            null,
            null,
            ReportOutputFormat.MARKDOWN,
            true
        );

        assertThrows(BusinessRuleException.class, () -> service.create(request));
    }

    @Test
    void returnsOnlyActiveTemplateForGeneration() {
        ReportTemplateEntity inactive = new ReportTemplateEntity(
            "Inactiva",
            null,
            ReportType.RESEARCHER,
            "[\"EXECUTIVE_SUMMARY\"]",
            null,
            null,
            ReportOutputFormat.MARKDOWN,
            false
        );
        when(repository.findById(8L)).thenReturn(Optional.of(inactive));

        assertEquals(Optional.empty(), service.findActiveEntity(8L));
    }
}
