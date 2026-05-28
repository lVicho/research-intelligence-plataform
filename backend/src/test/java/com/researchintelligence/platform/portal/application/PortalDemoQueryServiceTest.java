package com.researchintelligence.platform.portal.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.portal.api.PortalDemoQueryContext;
import com.researchintelligence.platform.portal.api.PortalDemoQueryResponse;
import com.researchintelligence.platform.portal.persistence.PortalDemoQueryEvidenceRepository;
import com.researchintelligence.platform.portal.persistence.PortalDemoQueryEvidenceRow;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortalDemoQueryServiceTest {

    @Mock
    private PortalDemoQueryEvidenceRepository evidenceRepository;

    @Mock
    private VisibilityContext visibilityContext;

    private PortalDemoQueryService service;

    @BeforeEach
    void setUp() {
        service = new PortalDemoQueryService(evidenceRepository, visibilityContext);
    }

    @Test
    void returnsDiverseQueries() {
        when(evidenceRepository.findPublicationEvidence(true)).thenReturn(sampleRows());

        List<PortalDemoQueryResponse> queries = service.generate(PortalDemoQueryContext.GENERAL, 8, true);

        assertFalse(queries.isEmpty());
        assertTrue(queries.stream().anyMatch(item -> item.query().equalsIgnoreCase("IA local en hospitales")));
        assertTrue(queries.stream().anyMatch(item -> item.query().equalsIgnoreCase("panteras y conservacion")));
        assertTrue(queries.stream().anyMatch(item -> item.query().toLowerCase().contains("clima")));
    }

    @Test
    void onlyUsesValidatedDataByDefault() {
        when(visibilityContext.currentRoles()).thenReturn(Set.of());
        when(evidenceRepository.findPublicationEvidence(true)).thenReturn(sampleRows());

        service.generate(PortalDemoQueryContext.PUBLICATIONS, 5, false);

        verify(evidenceRepository).findPublicationEvidence(true);
    }

    @Test
    void adminCanDisableValidatedOnlyFilter() {
        when(visibilityContext.currentRoles()).thenReturn(Set.of("ADMIN"));
        when(evidenceRepository.findPublicationEvidence(false)).thenReturn(sampleRows());

        service.generate(PortalDemoQueryContext.PUBLICATIONS, 5, false);

        verify(evidenceRepository).findPublicationEvidence(false);
    }

    @Test
    void queriesContainEvidenceIds() {
        when(evidenceRepository.findPublicationEvidence(true)).thenReturn(sampleRows());

        List<PortalDemoQueryResponse> queries = service.generate(PortalDemoQueryContext.GENERAL, 8, true);

        assertTrue(queries.stream().allMatch(item -> !item.evidenceIds().isEmpty()));
        assertTrue(queries.stream().allMatch(item -> item.freshness().evidenceCount() > 0));
    }

    @Test
    void handlesEmptyDatasetGracefully() {
        when(evidenceRepository.findPublicationEvidence(true)).thenReturn(List.of());

        List<PortalDemoQueryResponse> queries = service.generate(PortalDemoQueryContext.GENERAL, 8, true);

        assertTrue(queries.isEmpty());
    }

    @Test
    void returnsContextSpecificSuggestions() {
        when(evidenceRepository.findPublicationEvidence(true)).thenReturn(sampleRows());

        List<PortalDemoQueryResponse> queries = service.generate(PortalDemoQueryContext.REPORTS, 6, true);

        assertFalse(queries.isEmpty());
        assertEquals(PortalDemoQueryContext.REPORTS, queries.getFirst().context());
        assertTrue(queries.stream().allMatch(item -> item.context() == PortalDemoQueryContext.REPORTS));
        assertTrue(queries.stream().allMatch(item -> item.expectedEntityTypes().contains("PUBLICATION")));
    }

    private List<PortalDemoQueryEvidenceRow> sampleRows() {
        return List.of(
            row(100L, "IA local para triaje en hospitales", "Modelos locales para hospital", "IA local en hospitales", 10L, "IA local", 20L, "Maya Chen", 30L, "Grupo de IA Clinica"),
            row(100L, "IA local para triaje en hospitales", "Modelos locales para hospital", "IA local en hospitales", 11L, "hospitales", 20L, "Maya Chen", 31L, "Hospital Universitario Central"),
            row(101L, "Conservacion de panteras en corredores ecologicos", "Panteras y biodiversidad", "Seguimiento de panteras", 12L, "panteras", 21L, "Carla Serra", 32L, "Laboratorio de Conservacion"),
            row(101L, "Conservacion de panteras en corredores ecologicos", "Panteras y biodiversidad", "Seguimiento de panteras", 13L, "conservacion", 21L, "Carla Serra", 32L, "Laboratorio de Conservacion"),
            row(102L, "Salud publica y clima urbano en barrios vulnerables", "Clima urbano y salud", "Sensores para salud publica", 14L, "salud publica", 22L, "Ines Carvalho", 33L, "Observatorio de Salud Urbana"),
            row(102L, "Salud publica y clima urbano en barrios vulnerables", "Clima urbano y salud", "Sensores para salud publica", 15L, "clima urbano", 22L, "Ines Carvalho", 33L, "Observatorio de Salud Urbana"),
            row(103L, "Grafos de conocimiento aplicados a genomica clinica", "Genomica y grafos", "Grafo de conocimiento en genomica", 16L, "grafos de conocimiento", 20L, "Maya Chen", 30L, "Grupo de IA Clinica"),
            row(103L, "Grafos de conocimiento aplicados a genomica clinica", "Genomica y grafos", "Grafo de conocimiento en genomica", 17L, "genomica", 20L, "Maya Chen", 30L, "Grupo de IA Clinica"),
            row(104L, "Calidad de datos para analitica de investigacion", "Calidad y gobernanza", "Calidad de datos en investigacion", 18L, "calidad de datos", 23L, "Luis Vega", 34L, "Unidad de Salud Digital")
        );
    }

    private PortalDemoQueryEvidenceRow row(
        Long publicationId,
        String title,
        String abstractText,
        String publicSummary,
        Long topicId,
        String topicName,
        Long researcherId,
        String researcherName,
        Long unitId,
        String unitName
    ) {
        return new PortalDemoQueryEvidenceRow(
            publicationId,
            title,
            abstractText,
            publicSummary,
            Instant.parse("2026-05-25T10:15:30Z").minusSeconds(publicationId),
            ValidationStatus.VALIDATED,
            topicId,
            topicName,
            topicName.toLowerCase(),
            researcherId,
            researcherName,
            true,
            ValidationStatus.VALIDATED,
            unitId,
            unitName,
            true,
            true,
            "INTERNAL",
            ValidationStatus.VALIDATED
        );
    }
}
