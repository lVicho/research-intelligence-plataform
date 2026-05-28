package com.researchintelligence.platform.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.ai.api.CopilotRetrieveRequest;
import com.researchintelligence.platform.ai.api.CopilotRetrieveResponse;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingRepository;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingSearchRow;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class CopilotServiceVisibilityTest {

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
    private VisibilityContext visibilityContext;

    private CopilotService service;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setEmbeddingDimension(3);
        PublicationRetrievalService retrievalService = new PublicationRetrievalService(
            properties,
            embeddingService,
            embeddingRepository,
            publicationRepository,
            authorRepository,
            publicationTopicRepository,
            topicRepository,
            researcherRepository
        );
        service = new CopilotService(
            new FixedLlmService(),
            embeddingService,
            retrievalService,
            new CopilotAnswerEvaluationService(),
            properties,
            visibilityContext
        );

        when(embeddingService.provider()).thenReturn("ollama");
        when(embeddingService.model()).thenReturn("bge-m3");
        when(embeddingService.embed(anyString())).thenReturn(new EmbeddingResponse(List.of(0.1, 0.2, 0.3), List.of()));
        when(embeddingRepository.hasEmbeddings("ollama", "bge-m3", 3)).thenReturn(true);
        when(embeddingRepository.searchNearest(anyString(), any(), any(), any(Integer.class), any(Integer.class), any(), any()))
            .thenReturn(List.of(
                new PublicationEmbeddingSearchRow(1L, 0.82),
                new PublicationEmbeddingSearchRow(2L, 0.90)
            ));
        when(publicationRepository.findAll(anySpecification())).thenReturn(List.of(
            publication(1L, "Validated publication", ValidationStatus.VALIDATED),
            publication(2L, "Pending publication", ValidationStatus.PENDING_VALIDATION)
        ));
        when(authorRepository.findByPublicationIdIn(any())).thenReturn(List.of());
        when(publicationTopicRepository.findByPublicationIdIn(any())).thenReturn(List.of());
        when(researcherRepository.findAllById(any())).thenReturn(List.of());
        when(topicRepository.findAllById(any())).thenReturn(List.of());
    }

    @Test
    void publicCopilotRetrieveExcludesPendingPublication() {
        CopilotRetrieveResponse response = service.retrieve(new CopilotRetrieveRequest("clinical ai", 5, 0.35, RetrievalMode.BALANCED));

        assertEquals(List.of(1L), response.retrievedPublications().stream().map(publication -> publication.id()).toList());
        assertEquals("PUBLIC_VALIDATED", response.visibilityScope());
        assertTrue(response.validationFilterApplied());
    }

    @SuppressWarnings("unchecked")
    private Specification<PublicationEntity> anySpecification() {
        return any(Specification.class);
    }

    private PublicationEntity publication(Long id, String title, ValidationStatus validationStatus) {
        PublicationEntity publication = new PublicationEntity(
            title,
            "Abstract",
            2026,
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

    private static class FixedLlmService implements LlmService {

        @Override
        public LlmResponse answer(LlmPrompt prompt) {
            return new LlmResponse("Respuesta [pub:1].", List.of());
        }

        @Override
        public String provider() {
            return "test";
        }

        @Override
        public String model() {
            return "test-llm";
        }
    }
}
