package com.researchintelligence.platform.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingRepository;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingSearchRow;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicationRetrievalServiceTest {

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

    private AiProperties properties;
    private PublicationRetrievalService service;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEmbeddingDimension(3);
        service = new PublicationRetrievalService(
            properties,
            embeddingService,
            embeddingRepository,
            publicationRepository,
            authorRepository,
            publicationTopicRepository,
            topicRepository,
            researcherRepository
        );

        when(embeddingService.provider()).thenReturn("ollama");
        when(embeddingService.model()).thenReturn("bge-m3");
        when(embeddingService.embed(anyString())).thenReturn(new EmbeddingResponse(List.of(0.1, 0.2, 0.3), List.of()));
        lenient().when(embeddingRepository.hasEmbeddings("ollama", "bge-m3", 3)).thenReturn(true);
        when(authorRepository.findByPublicationIdIn(any())).thenReturn(List.of());
        when(publicationTopicRepository.findByPublicationIdIn(any())).thenReturn(List.of());
        when(researcherRepository.findAllById(any())).thenReturn(List.of());
        when(topicRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(publicationRepository.findAll(anySpecification())).thenReturn(publicationsFor(List.of(1L, 2L, 3L, 4L)));
    }

    @Test
    void returnsFewerThanLimitWhenThresholdFiltersResults() {
        when(embeddingRepository.searchNearest(anyString(), eq("ollama"), eq("bge-m3"), eq(3), eq(3), eq(VisibilityScope.PUBLIC_VALIDATED), isNull()))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(1L, 0.80),
                new PublicationEmbeddingSearchRow(2L, 0.36),
                new PublicationEmbeddingSearchRow(3L, 0.20)
            ));

        PublicationRetrievalResult result = service.retrieveBest("clinical ai", new RetrievalOptions(3, 0.35, RetrievalMode.BALANCED));

        assertEquals(2, result.publications().size());
        assertEquals(List.of(1L, 2L), result.publications().stream().map(context -> context.publication().getId()).toList());
    }

    @Test
    void returnsEmptyWhenNoResultPassesThreshold() {
        when(embeddingRepository.searchNearest(anyString(), eq("ollama"), eq("bge-m3"), eq(3), eq(3), eq(VisibilityScope.PUBLIC_VALIDATED), isNull()))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(1L, 0.20),
                new PublicationEmbeddingSearchRow(2L, 0.18)
            ));

        PublicationRetrievalResult result = service.retrieveBest("unrelated query", new RetrievalOptions(3, 0.35, RetrievalMode.BALANCED));

        assertTrue(result.publications().isEmpty());
        assertTrue(result.warnings().contains("No se han encontrado publicaciones suficientemente relacionadas."));
    }

    @Test
    void broadModeUsesMaxLimitAndAddsWarning() {
        when(embeddingRepository.searchNearest(anyString(), eq("ollama"), eq("bge-m3"), eq(3), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), isNull()))
            .thenReturn(List.of(new PublicationEmbeddingSearchRow(1L, 0.30)));

        PublicationRetrievalResult result = service.retrieveBest("exploratory query", new RetrievalOptions(null, null, RetrievalMode.BROAD));

        assertEquals(1, result.publications().size());
        assertTrue(result.warnings().contains("La recuperacion se ha realizado en modo amplio."));
        assertTrue(result.warnings().contains("Algunos resultados tienen baja similitud; interpreta la respuesta con cautela."));
        verify(embeddingRepository).searchNearest(anyString(), eq("ollama"), eq("bge-m3"), eq(3), eq(20), eq(VisibilityScope.PUBLIC_VALIDATED), isNull());
    }

    @Test
    void strictModeFiltersWeakResults() {
        when(embeddingRepository.searchNearest(anyString(), eq("ollama"), eq("bge-m3"), eq(3), eq(4), eq(VisibilityScope.PUBLIC_VALIDATED), isNull()))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(1L, 0.44),
                new PublicationEmbeddingSearchRow(2L, 0.46)
            ));

        PublicationRetrievalResult result = service.retrieveBest("specific query", new RetrievalOptions(4, null, RetrievalMode.STRICT));

        assertEquals(1, result.publications().size());
        assertEquals(2L, result.publications().getFirst().publication().getId());
        assertEquals(0.45, result.minSimilarity());
    }

    @Test
    void publicSemanticSearchExcludesRejectedPublication() {
        when(publicationRepository.findAll(anySpecification())).thenReturn(List.of(
            publication(1L, ValidationStatus.VALIDATED),
            publication(2L, ValidationStatus.REJECTED)
        ));
        when(embeddingRepository.searchNearest(anyString(), eq("ollama"), eq("bge-m3"), eq(3), eq(3), eq(VisibilityScope.PUBLIC_VALIDATED), isNull()))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(1L, 0.80),
                new PublicationEmbeddingSearchRow(2L, 0.88)
            ));

        List<SemanticPublicationMatch> matches = service.semanticSearch("clinical ai", 3, 0.35);

        assertEquals(List.of(1L), matches.stream().map(match -> match.context().publication().getId()).toList());
    }

    @SuppressWarnings("unchecked")
    private Specification<PublicationEntity> anySpecification() {
        return any(Specification.class);
    }

    private List<PublicationEntity> publicationsFor(Iterable<Long> ids) {
        List<PublicationEntity> publications = new ArrayList<>();
        for (Long id : ids) {
            publications.add(publication(id, ValidationStatus.VALIDATED));
        }
        return publications;
    }

    private PublicationEntity publication(Long id, ValidationStatus validationStatus) {
        PublicationEntity publication = new PublicationEntity(
            "Publication " + id,
            "Abstract " + id,
            2025,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Demo Source",
            null
        );
        publication.setId(id);
        publication.setValidationStatus(validationStatus);
        return publication;
    }
}
