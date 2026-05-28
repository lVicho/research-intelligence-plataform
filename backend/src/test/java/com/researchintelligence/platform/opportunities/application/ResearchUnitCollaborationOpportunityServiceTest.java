package com.researchintelligence.platform.opportunities.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.ai.application.AiProperties;
import com.researchintelligence.platform.ai.application.EmbeddingService;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingRepository;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingSearchRow;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.opportunities.api.ResearchUnitCollaborationOpportunityResponse;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorEntity;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicEntity;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.domain.AffiliationType;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ResearchUnitCollaborationOpportunityServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private PublicationEmbeddingRepository embeddingRepository;

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private PublicationAuthorRepository authorRepository;

    @Mock
    private PublicationTopicRepository publicationTopicRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private ResearcherRepository researcherRepository;

    @Mock
    private ResearcherAffiliationRepository affiliationRepository;

    @Mock
    private ResearchUnitRepository researchUnitRepository;

    @Mock
    private VisibilityContext visibilityContext;

    private ResearchUnitCollaborationOpportunityService service;
    private List<PublicationEntity> publications;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setEmbeddingDimension(3);
        service = new ResearchUnitCollaborationOpportunityService(
            properties,
            embeddingService,
            embeddingRepository,
            publicationRepository,
            authorRepository,
            publicationTopicRepository,
            topicRepository,
            researcherRepository,
            affiliationRepository,
            researchUnitRepository,
            visibilityContext
        );

        lenient().when(embeddingService.provider()).thenReturn("ollama");
        lenient().when(embeddingService.model()).thenReturn("bge-m3");
        publications = List.of(
            publication(1L, "IA clinica para triaje hospitalario", 2026, ValidationStatus.VALIDATED),
            publication(2L, "Aprendizaje automatico para riesgo hospitalario", 2025, ValidationStatus.VALIDATED),
            publication(3L, "IA clinica para imagen medica", 2026, ValidationStatus.VALIDATED),
            publication(4L, "Aprendizaje automatico para soporte diagnostico", 2025, ValidationStatus.VALIDATED),
            publication(5L, "Optica cuantica en materiales fotonicos", 2026, ValidationStatus.VALIDATED),
            publication(6L, "Cristales fotonicos para sensores", 2025, ValidationStatus.VALIDATED),
            publication(7L, "Borrador pendiente sobre IA clinica", 2026, ValidationStatus.PENDING_VALIDATION)
        );

        when(publicationRepository.findAll(anySpecification())).thenReturn(publications);
        when(publicationTopicRepository.findByPublicationIdIn(any())).thenReturn(List.of(
            new PublicationTopicEntity(1L, 10L),
            new PublicationTopicEntity(1L, 11L),
            new PublicationTopicEntity(2L, 12L),
            new PublicationTopicEntity(2L, 13L),
            new PublicationTopicEntity(3L, 10L),
            new PublicationTopicEntity(3L, 14L),
            new PublicationTopicEntity(4L, 12L),
            new PublicationTopicEntity(4L, 15L),
            new PublicationTopicEntity(5L, 20L),
            new PublicationTopicEntity(6L, 21L),
            new PublicationTopicEntity(7L, 10L)
        ));
        when(topicRepository.findAllById(any())).thenReturn(List.of(
            topic(10L, "IA clinica"),
            topic(11L, "Triaje hospitalario"),
            topic(12L, "Aprendizaje automatico"),
            topic(13L, "Riesgo hospitalario"),
            topic(14L, "Imagen medica"),
            topic(15L, "Soporte diagnostico"),
            topic(20L, "Optica cuantica"),
            topic(21L, "Cristales fotonicos")
        ));
        when(authorRepository.findByPublicationIdIn(any())).thenReturn(List.of(
            new PublicationAuthorEntity(1L, 100L, null, null, 1, true),
            new PublicationAuthorEntity(2L, 100L, null, null, 1, true),
            new PublicationAuthorEntity(3L, 200L, null, null, 1, true),
            new PublicationAuthorEntity(4L, 200L, null, null, 1, true),
            new PublicationAuthorEntity(5L, 300L, null, null, 1, true),
            new PublicationAuthorEntity(6L, 300L, null, null, 1, true),
            new PublicationAuthorEntity(7L, 200L, null, null, 1, true)
        ));
        when(researcherRepository.findAllById(any())).thenReturn(List.of(
            researcher(100L, "Lucia Herrera"),
            researcher(200L, "Mateo Rios"),
            researcher(300L, "Valeria Campos")
        ));
        when(affiliationRepository.findByResearcherIdIn(any())).thenReturn(List.of(
            affiliation(100L, 500L),
            affiliation(200L, 600L),
            affiliation(300L, 700L)
        ));
        when(researchUnitRepository.findAllById(any())).thenReturn(List.of(
            researchUnit(500L, "Hospital Universitario Central"),
            researchUnit(600L, "Instituto de Imagen Medica"),
            researchUnit(700L, "Laboratorio de Fotonica Cuantica")
        ));
        stubEmbeddings();
    }

    @Test
    void closeLowCollaborationUnitsRankHigh() {
        List<ResearchUnitCollaborationOpportunityResponse> opportunities = service.findResearchUnitCollaborations(
            2025,
            2026,
            OpportunityMode.BALANCED,
            10,
            true
        );

        assertFalse(opportunities.isEmpty());
        ResearchUnitCollaborationOpportunityResponse first = opportunities.getFirst();
        assertEquals("Hospital Universitario Central", first.unitA().name());
        assertEquals("Instituto de Imagen Medica", first.unitB().name());
        assertEquals(0, first.existingCollaborationCount());
        assertTrue(first.score() >= 0.70);
        assertTrue(first.sharedTopics().contains("IA clinica"));
        assertTrue(first.sharedTopics().contains("Aprendizaje automatico"));
    }

    @Test
    void unrelatedUnitsRankLowInBroadMode() {
        List<ResearchUnitCollaborationOpportunityResponse> opportunities = service.findResearchUnitCollaborations(
            2025,
            2026,
            OpportunityMode.BROAD,
            10,
            true
        );

        ResearchUnitCollaborationOpportunityResponse closeUnits = opportunity(opportunities, 500L, 600L);
        ResearchUnitCollaborationOpportunityResponse unrelatedUnits = opportunity(opportunities, 500L, 700L);

        assertNotNull(closeUnits);
        assertNotNull(unrelatedUnits);
        assertTrue(closeUnits.score() > unrelatedUnits.score());
        assertTrue(unrelatedUnits.score() < 0.40);
        assertTrue(unrelatedUnits.sharedTopics().isEmpty());
        assertTrue(unrelatedUnits.warnings().stream().anyMatch(warning -> warning.contains("No se detectaron temas")));
    }

    @Test
    void validatedOnlyIsAppliedByDefault() {
        List<ResearchUnitCollaborationOpportunityResponse> opportunities = service.findResearchUnitCollaborations(
            null,
            null,
            OpportunityMode.BROAD,
            10,
            null
        );

        assertFalse(opportunities.stream()
            .flatMap(opportunity -> opportunity.representativePublicationsA().stream())
            .anyMatch(publication -> publication.id().equals(7L)));
        assertFalse(opportunities.stream()
            .flatMap(opportunity -> opportunity.representativePublicationsB().stream())
            .anyMatch(publication -> publication.id().equals(7L)));
        verify(embeddingRepository, never()).searchNearestToPublication(
            eq(7L),
            eq("ollama"),
            eq("bge-m3"),
            eq(3),
            anyInt(),
            eq(VisibilityScope.PUBLIC_VALIDATED),
            eq(null)
        );
    }

    private ResearchUnitCollaborationOpportunityResponse opportunity(
        List<ResearchUnitCollaborationOpportunityResponse> opportunities,
        Long unitAId,
        Long unitBId
    ) {
        return opportunities.stream()
            .filter(opportunity -> opportunity.unitA().id().equals(unitAId) && opportunity.unitB().id().equals(unitBId))
            .findFirst()
            .orElse(null);
    }

    private void stubEmbeddings() {
        for (PublicationEntity publication : publications) {
            lenient().when(embeddingRepository.hasEmbeddingForPublication(
                eq(publication.getId()),
                eq("ollama"),
                eq("bge-m3"),
                eq(3)
            )).thenReturn(publication.getValidationStatus() == ValidationStatus.VALIDATED);
        }
        lenient().when(embeddingRepository.searchNearestToPublication(eq(1L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(3L, 0.88),
                new PublicationEmbeddingSearchRow(4L, 0.78),
                new PublicationEmbeddingSearchRow(5L, 0.06),
                new PublicationEmbeddingSearchRow(6L, 0.05)
            ));
        lenient().when(embeddingRepository.searchNearestToPublication(eq(2L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(4L, 0.86),
                new PublicationEmbeddingSearchRow(3L, 0.75),
                new PublicationEmbeddingSearchRow(5L, 0.05),
                new PublicationEmbeddingSearchRow(6L, 0.05)
            ));
        lenient().when(embeddingRepository.searchNearestToPublication(eq(3L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(1L, 0.88),
                new PublicationEmbeddingSearchRow(2L, 0.75),
                new PublicationEmbeddingSearchRow(5L, 0.04),
                new PublicationEmbeddingSearchRow(6L, 0.04)
            ));
        lenient().when(embeddingRepository.searchNearestToPublication(eq(4L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(2L, 0.86),
                new PublicationEmbeddingSearchRow(1L, 0.78),
                new PublicationEmbeddingSearchRow(5L, 0.05),
                new PublicationEmbeddingSearchRow(6L, 0.04)
            ));
        lenient().when(embeddingRepository.searchNearestToPublication(eq(5L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(6L, 0.80),
                new PublicationEmbeddingSearchRow(1L, 0.06),
                new PublicationEmbeddingSearchRow(2L, 0.05)
            ));
        lenient().when(embeddingRepository.searchNearestToPublication(eq(6L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(5L, 0.80),
                new PublicationEmbeddingSearchRow(1L, 0.05),
                new PublicationEmbeddingSearchRow(2L, 0.05)
            ));
    }

    private PublicationEntity publication(Long id, String title, Integer year, ValidationStatus validationStatus) {
        PublicationEntity publication = new PublicationEntity(
            title,
            "Resumen " + title,
            year,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Demo",
            null
        );
        publication.setId(id);
        publication.setValidationStatus(validationStatus);
        return publication;
    }

    private TopicEntity topic(Long id, String name) {
        TopicEntity topic = new TopicEntity(name, name.toLowerCase());
        ReflectionTestUtils.setField(topic, "id", id);
        return topic;
    }

    private ResearcherEntity researcher(Long id, String fullName) {
        ResearcherEntity researcher = new ResearcherEntity(fullName, fullName, null, null, true);
        researcher.setId(id);
        researcher.setValidationStatus(ValidationStatus.VALIDATED);
        return researcher;
    }

    private ResearcherAffiliationEntity affiliation(Long researcherId, Long researchUnitId) {
        ResearcherAffiliationEntity affiliation = new ResearcherAffiliationEntity(
            researcherId,
            researchUnitId,
            "Investigador",
            AffiliationType.MEMBER,
            null,
            null,
            true
        );
        affiliation.setValidationStatus(ValidationStatus.VALIDATED);
        return affiliation;
    }

    private ResearchUnitEntity researchUnit(Long id, String name) {
        ResearchUnitEntity researchUnit = new ResearchUnitEntity(name, null, ResearchUnitType.CENTER, null, "Espana", null, null, true);
        researchUnit.setId(id);
        researchUnit.setValidationStatus(ValidationStatus.VALIDATED);
        return researchUnit;
    }

    @SuppressWarnings("unchecked")
    private Specification<PublicationEntity> anySpecification() {
        return any(Specification.class);
    }
}
