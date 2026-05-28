package com.researchintelligence.platform.strategicmap.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.ai.application.AiProperties;
import com.researchintelligence.platform.ai.application.EmbeddingService;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingRepository;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingSearchRow;
import com.researchintelligence.platform.auth.application.VisibilityContext;
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
import com.researchintelligence.platform.strategicmap.api.ResearchLineResponse;
import com.researchintelligence.platform.strategicmap.api.StrategicResearchMapResponse;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StrategicResearchMapServiceTest {

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

    private StrategicResearchMapService service;
    private List<PublicationEntity> publications;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setEmbeddingDimension(3);
        service = new StrategicResearchMapService(
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
            publication(2L, "Modelos predictivos de riesgo hospitalario", 2025, ValidationStatus.VALIDATED),
            publication(3L, "Corredores ecologicos para panteras", 2025, ValidationStatus.VALIDATED),
            publication(4L, "Conservacion de habitat de panteras", 2024, ValidationStatus.VALIDATED),
            publication(5L, "Borrador pendiente sobre IA clinica", 2026, ValidationStatus.PENDING_VALIDATION)
        );
        when(publicationRepository.findAll(anySpecification())).thenReturn(publications);
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
            new PublicationTopicEntity(5L, 11L)
        ));
        when(topicRepository.findAllById(any())).thenReturn(List.of(
            topic(10L, "IA clinica"),
            topic(11L, "Triaje hospitalario"),
            topic(12L, "Riesgo hospitalario"),
            topic(20L, "Panteras"),
            topic(21L, "Corredores ecologicos"),
            topic(22L, "Conservacion")
        ));
        when(authorRepository.findByPublicationIdIn(any())).thenReturn(List.of(
            new PublicationAuthorEntity(1L, 100L, null, null, 1, true),
            new PublicationAuthorEntity(2L, 100L, null, null, 1, true),
            new PublicationAuthorEntity(3L, 200L, null, null, 1, true),
            new PublicationAuthorEntity(4L, 200L, null, null, 1, true),
            new PublicationAuthorEntity(5L, 100L, null, null, 1, true)
        ));
        when(researcherRepository.findAllById(any())).thenReturn(List.of(
            researcher(100L, "Lucia Herrera"),
            researcher(200L, "Valeria Campos")
        ));
        when(affiliationRepository.findByResearcherIdIn(any())).thenReturn(List.of(
            affiliation(100L, 500L),
            affiliation(200L, 600L)
        ));
        when(researchUnitRepository.findAllById(any())).thenReturn(List.of(
            researchUnit(500L, "Hospital Universitario Central"),
            researchUnit(600L, "Centro de Conservacion de Grandes Felinos")
        ));

        stubEmbeddings();
    }

    @Test
    void clustersSeparateHospitalAiFromPantherConservation() {
        StrategicResearchMapResponse response = service.researchLines(2024, 2026, null, true);

        assertEquals(2, response.researchLines().size());
        ResearchLineResponse hospitalLine = findLineContaining(response, 1L);
        ResearchLineResponse pantherLine = findLineContaining(response, 3L);

        assertTrue(hospitalLine.representativePublications().stream().anyMatch(publication -> publication.id().equals(2L)));
        assertFalse(hospitalLine.representativePublications().stream().anyMatch(publication -> publication.id().equals(3L)));
        assertTrue(hospitalLine.researchUnits().stream().anyMatch(unit -> unit.name().equals("Hospital Universitario Central")));
        assertTrue(pantherLine.representativePublications().stream().anyMatch(publication -> publication.id().equals(4L)));
        assertFalse(pantherLine.representativePublications().stream().anyMatch(publication -> publication.id().equals(1L)));
        assertTrue(pantherLine.researchUnits().stream().anyMatch(unit -> unit.name().equals("Centro de Conservacion de Grandes Felinos")));
    }

    @Test
    void onlyValidatedDataIsUsedByDefault() {
        StrategicResearchMapResponse response = service.researchLines(null, null, null, null);

        assertTrue(response.onlyValidated());
        assertTrue(response.validationFilterApplied());
        assertEquals("PUBLIC_VALIDATED", response.visibilityScope());
        assertEquals(4, response.researchLines().stream().mapToInt(ResearchLineResponse::publicationCount).sum());
        assertFalse(response.researchLines()
            .stream()
            .flatMap(line -> line.representativePublications().stream())
            .anyMatch(publication -> publication.id().equals(5L)));
    }

    @Test
    void adminCanExplicitlyIncludeNonValidatedData() {
        when(visibilityContext.currentRoles()).thenReturn(Set.of("ADMIN"));

        StrategicResearchMapResponse response = service.researchLines(null, null, null, false);

        assertFalse(response.onlyValidated());
        assertFalse(response.validationFilterApplied());
        assertEquals("ADMIN_ALL", response.visibilityScope());
        assertEquals(5, response.researchLines().stream().mapToInt(ResearchLineResponse::publicationCount).sum());
        assertTrue(response.researchLines()
            .stream()
            .flatMap(line -> line.representativePublications().stream())
            .anyMatch(publication -> publication.id().equals(5L)));
    }

    private void stubEmbeddings() {
        for (PublicationEntity publication : publications) {
            lenient().when(embeddingRepository.hasEmbeddingForPublication(
                eq(publication.getId()),
                eq("ollama"),
                eq("bge-m3"),
                eq(3)
            )).thenReturn(true);
        }
        lenient().when(embeddingRepository.searchNearestToPublication(eq(1L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(2L, 0.86),
                new PublicationEmbeddingSearchRow(3L, 0.18),
                new PublicationEmbeddingSearchRow(4L, 0.16),
                new PublicationEmbeddingSearchRow(5L, 0.95)
            ));
        lenient().when(embeddingRepository.searchNearestToPublication(eq(2L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(1L, 0.86),
                new PublicationEmbeddingSearchRow(3L, 0.17),
                new PublicationEmbeddingSearchRow(4L, 0.15),
                new PublicationEmbeddingSearchRow(5L, 0.88)
            ));
        lenient().when(embeddingRepository.searchNearestToPublication(eq(3L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(4L, 0.88),
                new PublicationEmbeddingSearchRow(1L, 0.18),
                new PublicationEmbeddingSearchRow(2L, 0.17)
            ));
        lenient().when(embeddingRepository.searchNearestToPublication(eq(4L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(3L, 0.88),
                new PublicationEmbeddingSearchRow(1L, 0.16),
                new PublicationEmbeddingSearchRow(2L, 0.15)
            ));
        lenient().when(embeddingRepository.searchNearestToPublication(eq(5L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.ADMIN_ALL), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(1L, 0.95),
                new PublicationEmbeddingSearchRow(2L, 0.88)
            ));
        lenient().when(embeddingRepository.searchNearestToPublication(eq(1L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.ADMIN_ALL), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(5L, 0.95),
                new PublicationEmbeddingSearchRow(2L, 0.86),
                new PublicationEmbeddingSearchRow(3L, 0.18),
                new PublicationEmbeddingSearchRow(4L, 0.16)
            ));
        lenient().when(embeddingRepository.searchNearestToPublication(eq(2L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.ADMIN_ALL), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(5L, 0.88),
                new PublicationEmbeddingSearchRow(1L, 0.86),
                new PublicationEmbeddingSearchRow(3L, 0.17),
                new PublicationEmbeddingSearchRow(4L, 0.15)
            ));
        lenient().when(embeddingRepository.searchNearestToPublication(eq(3L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.ADMIN_ALL), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(4L, 0.88),
                new PublicationEmbeddingSearchRow(1L, 0.18),
                new PublicationEmbeddingSearchRow(2L, 0.17)
            ));
        lenient().when(embeddingRepository.searchNearestToPublication(eq(4L), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.ADMIN_ALL), eq(null)))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(3L, 0.88),
                new PublicationEmbeddingSearchRow(1L, 0.16),
                new PublicationEmbeddingSearchRow(2L, 0.15)
            ));
    }

    private ResearchLineResponse findLineContaining(StrategicResearchMapResponse response, Long publicationId) {
        return response.researchLines()
            .stream()
            .filter(line -> line.representativePublications()
                .stream()
                .anyMatch(publication -> publication.id().equals(publicationId)))
            .findFirst()
            .orElseThrow();
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
