package com.researchintelligence.platform.publications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.ai.application.AiProperties;
import com.researchintelligence.platform.ai.application.EmbeddingService;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingRepository;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingSearchRow;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.publications.api.RelatedPublicationResponse;
import com.researchintelligence.platform.publications.api.RelatedPublicationsResponse;
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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RelatedPublicationServiceTest {

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

    private List<PublicationEntity> publications;
    private RelatedPublicationService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setEmbeddingDimension(3);
        service = new RelatedPublicationService(
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

        when(embeddingService.provider()).thenReturn("ollama");
        when(embeddingService.model()).thenReturn("bge-m3");
        publications = List.of(
            publication(1L, "IA clinica en hospitales universitarios", 2026, ValidationStatus.VALIDATED),
            publication(2L, "Modelos predictivos para riesgo hospitalario", 2025, ValidationStatus.VALIDATED),
            publication(3L, "Corredores ecologicos para panteras", 2025, ValidationStatus.VALIDATED),
            publication(4L, "Conservacion de habitats para panteras", 2024, ValidationStatus.VALIDATED),
            publication(5L, "Borrador pendiente sobre IA clinica", 2026, ValidationStatus.PENDING_VALIDATION),
            publication(6L, "Articulo rechazado sobre IA clinica", 2026, ValidationStatus.REJECTED)
        );
        when(publicationRepository.findAll(anySpecification())).thenReturn(publications);
        when(publicationRepository.findById(any())).thenAnswer(invocation -> publicationById(invocation.getArgument(0)));
        when(publicationTopicRepository.findByPublicationIdIn(any())).thenReturn(List.of(
            new PublicationTopicEntity(1L, 10L),
            new PublicationTopicEntity(1L, 11L),
            new PublicationTopicEntity(2L, 10L),
            new PublicationTopicEntity(2L, 12L),
            new PublicationTopicEntity(3L, 20L),
            new PublicationTopicEntity(3L, 21L),
            new PublicationTopicEntity(4L, 20L),
            new PublicationTopicEntity(4L, 22L),
            new PublicationTopicEntity(5L, 10L),
            new PublicationTopicEntity(6L, 10L)
        ));
        when(topicRepository.findAllById(any())).thenReturn(List.of(
            topic(10L, "IA clinica"),
            topic(11L, "Riesgo hospitalario"),
            topic(12L, "Datos clinicos"),
            topic(20L, "Panteras"),
            topic(21L, "Corredores ecologicos"),
            topic(22L, "Conservacion")
        ));
        when(authorRepository.findByPublicationIdIn(any())).thenReturn(List.of(
            new PublicationAuthorEntity(1L, 100L, null, null, 1, true),
            new PublicationAuthorEntity(1L, 101L, null, null, 2, false),
            new PublicationAuthorEntity(2L, 100L, null, null, 1, true),
            new PublicationAuthorEntity(2L, 102L, null, null, 2, false),
            new PublicationAuthorEntity(3L, 200L, null, null, 1, true),
            new PublicationAuthorEntity(3L, 201L, null, null, 2, false),
            new PublicationAuthorEntity(4L, 200L, null, null, 1, true),
            new PublicationAuthorEntity(4L, 202L, null, null, 2, false),
            new PublicationAuthorEntity(5L, 100L, null, null, 1, true),
            new PublicationAuthorEntity(6L, 100L, null, null, 1, true)
        ));
        when(researcherRepository.findAllById(any())).thenReturn(List.of(
            researcher(100L, "Lucia Herrera"),
            researcher(101L, "Omar Alvarez"),
            researcher(102L, "Carmen Rios"),
            researcher(200L, "Valeria Campos"),
            researcher(201L, "Nicolas Duarte"),
            researcher(202L, "Sofia Almeida")
        ));
        when(affiliationRepository.findByResearcherIdIn(any())).thenReturn(List.of(
            affiliation(100L, 500L),
            affiliation(101L, 500L),
            affiliation(102L, 500L),
            affiliation(200L, 600L),
            affiliation(201L, 600L),
            affiliation(202L, 600L)
        ));
        when(researchUnitRepository.findAllById(any())).thenReturn(List.of(
            researchUnit(500L, "Hospital Universitario Central"),
            researchUnit(600L, "Centro de Conservacion de Grandes Felinos")
        ));
    }

    @Test
    void clinicalPublicationRelatesToClinicalPublicationAndNotPantherPublication() {
        when(embeddingRepository.hasEmbeddingForPublication(eq(1L), eq("ollama"), eq("bge-m3"), eq(3))).thenReturn(true);
        when(embeddingRepository.searchNearestToPublication(eq(1L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(2L, 0.82),
                new PublicationEmbeddingSearchRow(3L, 0.18),
                new PublicationEmbeddingSearchRow(4L, 0.16)
            ));

        RelatedPublicationsResponse response = service.findRelated(1L, 5, 0.35);

        assertEquals(List.of(2L), response.relatedPublications().stream().map(result -> result.publication().id()).toList());
        RelatedPublicationResponse related = response.relatedPublications().getFirst();
        assertTrue(related.finalScore() > 0.70);
        assertTrue(related.sharedTopicNames().contains("IA clinica"));
        assertTrue(related.sharedAuthorNames().contains("Lucia Herrera"));
        assertTrue(related.relatedResearchUnitNames().contains("Hospital Universitario Central"));
    }

    @Test
    void pantherPublicationRelatesToConservationPublication() {
        when(embeddingRepository.hasEmbeddingForPublication(eq(3L), eq("ollama"), eq("bge-m3"), eq(3))).thenReturn(true);
        when(embeddingRepository.searchNearestToPublication(eq(3L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(4L, 0.86),
                new PublicationEmbeddingSearchRow(1L, 0.20),
                new PublicationEmbeddingSearchRow(2L, 0.19)
            ));

        RelatedPublicationsResponse response = service.findRelated(3L, 5, 0.35);

        assertEquals(4L, response.relatedPublications().getFirst().publication().id());
        assertTrue(response.relatedPublications().getFirst().sharedTopicNames().contains("Panteras"));
        assertTrue(response.relatedPublications().getFirst().relatedResearchUnitNames().contains("Centro de Conservacion de Grandes Felinos"));
    }

    @Test
    void broadModeCanIncludeUnrelatedResultsWithLowScoreWarning() {
        when(embeddingRepository.hasEmbeddingForPublication(eq(1L), eq("ollama"), eq("bge-m3"), eq(3))).thenReturn(true);
        when(embeddingRepository.searchNearestToPublication(eq(1L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(2L, 0.82),
                new PublicationEmbeddingSearchRow(3L, 0.18),
                new PublicationEmbeddingSearchRow(4L, 0.16)
            ));

        RelatedPublicationsResponse response = service.findRelated(1L, 5, 0.0);

        RelatedPublicationResponse panther = response.relatedPublications()
            .stream()
            .filter(result -> result.publication().id().equals(3L))
            .findFirst()
            .orElseThrow();
        assertTrue(panther.finalScore() < 0.25);
        assertEquals("Relacion debil; revisa los motivos antes de usarla.", panther.warning());
    }

    @Test
    void metadataOnlyFallbackReturnsWarningAndNullSemanticScore() {
        when(embeddingRepository.hasEmbeddingForPublication(eq(1L), eq("ollama"), eq("bge-m3"), eq(3))).thenReturn(false);

        RelatedPublicationsResponse response = service.findRelated(1L, 5, 0.35);

        assertTrue(response.metadataOnly());
        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("ranking solo por metadatos")));
        RelatedPublicationResponse related = response.relatedPublications().getFirst();
        assertEquals(2L, related.publication().id());
        assertNull(related.semanticScore());
        assertTrue(related.finalScore() >= 0.35);
    }

    @Test
    void publicRelatedPublicationsExcludePendingAndRejected() {
        when(embeddingRepository.hasEmbeddingForPublication(eq(1L), eq("ollama"), eq("bge-m3"), eq(3))).thenReturn(true);
        when(embeddingRepository.searchNearestToPublication(eq(1L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(5L, 0.95),
                new PublicationEmbeddingSearchRow(6L, 0.94),
                new PublicationEmbeddingSearchRow(2L, 0.82)
            ));

        RelatedPublicationsResponse response = service.findRelated(1L, 10, 0.0);

        assertEquals(List.of(2L, 3L, 4L), response.relatedPublications().stream().map(result -> result.publication().id()).toList());
        assertEquals("PUBLIC_VALIDATED", response.visibilityScope());
        assertTrue(response.validationFilterApplied());
    }

    @Test
    void adminCanIncludeNonValidatedWhenParameterIsSupported() {
        when(visibilityContext.currentRoles()).thenReturn(Set.of("ADMIN"));
        when(embeddingRepository.hasEmbeddingForPublication(eq(1L), eq("ollama"), eq("bge-m3"), eq(3))).thenReturn(true);
        when(embeddingRepository.searchNearestToPublication(eq(1L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.ADMIN_ALL), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(5L, 0.95),
                new PublicationEmbeddingSearchRow(6L, 0.94),
                new PublicationEmbeddingSearchRow(2L, 0.82)
            ));

        RelatedPublicationsResponse response = service.findRelated(1L, 10, 0.0, true);

        assertTrue(response.relatedPublications().stream().map(result -> result.publication().id()).toList().containsAll(List.of(5L, 6L)));
        assertEquals("ADMIN_ALL", response.visibilityScope());
        assertTrue(!response.validationFilterApplied());
    }

    private Optional<PublicationEntity> publicationById(Long id) {
        return publications
            .stream()
            .filter(publication -> publication.getId().equals(id))
            .findFirst();
    }

    private PublicationEntity publication(Long id, String title, Integer year) {
        return publication(id, title, year, ValidationStatus.VALIDATED);
    }

    private PublicationEntity publication(Long id, String title, Integer year, ValidationStatus validationStatus) {
        PublicationEntity publication = new PublicationEntity(
            title,
            "Abstract " + title,
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
        ResearcherAffiliationEntity affiliation = new ResearcherAffiliationEntity(researcherId, researchUnitId, "Investigador", AffiliationType.MEMBER, null, null, true);
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
