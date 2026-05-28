package com.researchintelligence.platform.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.api.AiSuggestionResponse;
import com.researchintelligence.platform.ai.api.TopicRecommendationRequest;
import com.researchintelligence.platform.ai.api.TopicRecommendationResponse;
import com.researchintelligence.platform.ai.api.TopicRecommendationTargetType;
import com.researchintelligence.platform.ai.domain.AiSuggestionStatus;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicEntity;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TopicRecommendationServiceTest {

    @Mock
    private PublicationRetrievalService retrievalService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private LlmService llmService;

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private PublicationAuthorRepository publicationAuthorRepository;

    @Mock
    private PublicationTopicRepository publicationTopicRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private EventParticipationRepository eventParticipationRepository;

    @Mock
    private ResearcherRepository researcherRepository;

    @Mock
    private AiSuggestionService aiSuggestionService;

    @Mock
    private VisibilityContext visibilityContext;

    private TopicRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new TopicRecommendationService(
            retrievalService,
            embeddingService,
            llmService,
            publicationRepository,
            publicationAuthorRepository,
            publicationTopicRepository,
            topicRepository,
            eventParticipationRepository,
            researcherRepository,
            aiSuggestionService,
            visibilityContext,
            new ObjectMapper()
        );

        lenient().when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(1L, null, "ADMIN")));
        lenient().when(embeddingService.provider()).thenReturn("ollama");
        lenient().when(embeddingService.model()).thenReturn("bge-m3");
        lenient().when(llmService.provider()).thenReturn("mock");
        lenient().when(llmService.model()).thenReturn("mock-llm");
        lenient().when(topicRepository.findByNormalizedNameIn(any())).thenReturn(List.of());
        lenient().when(aiSuggestionService.create(any())).thenReturn(aiSuggestion(500L));
    }

    @Test
    void recommendsTopicsForClinicalAiPublicationAndCreatesSuggestion() {
        PublicationEntity first = publication(1L, "Clinical AI for triage", "Machine learning models for hospital triage.");
        PublicationEntity second = publication(2L, "Predictive clinical models", "AI models for risk prediction in care pathways.");
        mockRetrieval(List.of(
            context(first, 0.86, List.of("Clinical AI", "Machine Learning")),
            context(second, 0.78, List.of("Clinical AI"))
        ));
        TopicEntity clinicalAi = topic(10L, "Clinical AI", "clinical ai");
        TopicEntity machineLearning = topic(11L, "Machine Learning", "machine learning");
        when(publicationTopicRepository.findByPublicationIdIn(any())).thenReturn(List.of(
            new PublicationTopicEntity(1L, 10L),
            new PublicationTopicEntity(1L, 11L),
            new PublicationTopicEntity(2L, 10L)
        ));
        when(topicRepository.findAllById(any())).thenReturn(List.of(clinicalAi, machineLearning));
        when(topicRepository.findByNormalizedNameIn(any())).thenReturn(List.of(clinicalAi));

        TopicRecommendationResponse response = service.recommend(new TopicRecommendationRequest(
            null,
            null,
            "Clinical AI for emergency triage",
            "A clinical artificial intelligence model predicts deterioration and supports hospital triage decisions.",
            List.of("Clinical AI"),
            8
        ));

        assertEquals(500L, response.aiSuggestionId());
        assertFalse(response.suggestedTopics().isEmpty());
        assertEquals("Clinical AI", response.suggestedTopics().getFirst().label());
        assertEquals(10L, response.suggestedTopics().getFirst().existingTopicId());
        assertEquals(List.of(1L, 2L), response.suggestedTopics().getFirst().evidencePublicationIds());

        ArgumentCaptor<AiSuggestionCreateCommand> commandCaptor = ArgumentCaptor.forClass(AiSuggestionCreateCommand.class);
        verify(aiSuggestionService).create(commandCaptor.capture());
        assertEquals(AiSuggestionType.TOPIC_RECOMMENDATION, commandCaptor.getValue().suggestionType());
        assertTrue(commandCaptor.getValue().proposedDataJson().contains("Clinical AI"));
    }

    @Test
    void recommendsConservationTopicsForPantherPublication() {
        PublicationEntity panther = publication(20L, "Camera traps for panther corridors", "Habitat connectivity for endangered panthers.");
        mockRetrieval(List.of(context(panther, 0.88, List.of("Panther Conservation", "Habitat Connectivity"))));
        TopicEntity pantherConservation = topic(30L, "Panther Conservation", "panther conservation");
        TopicEntity habitatConnectivity = topic(31L, "Habitat Connectivity", "habitat connectivity");
        when(publicationTopicRepository.findByPublicationIdIn(any())).thenReturn(List.of(
            new PublicationTopicEntity(20L, 30L),
            new PublicationTopicEntity(20L, 31L)
        ));
        when(topicRepository.findAllById(any())).thenReturn(List.of(pantherConservation, habitatConnectivity));

        TopicRecommendationResponse response = service.recommend(new TopicRecommendationRequest(
            null,
            null,
            "Landscape genetics and camera traps for Iberian panther conservation",
            "We identify ecological corridors and habitat fragmentation risks for endangered panther populations.",
            List.of("conservation", "habitat connectivity"),
            5
        ));

        assertTrue(response.suggestedTopics().stream().anyMatch(topic -> "Panther Conservation".equals(topic.label())));
        assertTrue(response.suggestedTopics().stream().anyMatch(topic -> "Habitat Connectivity".equals(topic.label())));
    }

    @Test
    void doesNotInventUnrelatedTopicsWhenInputIsWeak() {
        TopicRecommendationResponse response = service.recommend(new TopicRecommendationRequest(
            null,
            null,
            "AI",
            null,
            List.of(),
            8
        ));

        assertTrue(response.suggestedTopics().isEmpty());
        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("Input is weak")));
        verify(retrievalService, never()).retrieveBest(any(), any(), any(), any());
    }

    @Test
    void handlesOllamaUnavailableGracefully() {
        PublicationEntity fallbackPublication = publication(40L, "Clinical AI fallback", "Validated publication found by text.");
        when(retrievalService.retrieveBest(any(), any(), eq(VisibilityScope.PUBLIC_VALIDATED), isNull()))
            .thenThrow(new BusinessRuleException("Ollama embedding request failed at http://localhost:11434/api/embed"));
        when(retrievalService.textSearch(any(), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), isNull()))
            .thenReturn(List.of(context(fallbackPublication, null, List.of("Clinical AI"))));
        TopicEntity clinicalAi = topic(10L, "Clinical AI", "clinical ai");
        when(publicationTopicRepository.findByPublicationIdIn(any())).thenReturn(List.of(new PublicationTopicEntity(40L, 10L)));
        when(topicRepository.findAllById(any())).thenReturn(List.of(clinicalAi));

        TopicRecommendationResponse response = service.recommend(new TopicRecommendationRequest(
            null,
            null,
            "Clinical AI decision support for hospital triage",
            "The study evaluates clinical artificial intelligence decision support in emergency departments.",
            List.of("clinical ai"),
            8
        ));

        assertFalse(response.suggestedTopics().isEmpty());
        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("Ollama is unavailable")));
    }

    @Test
    void publicUserCannotCreateSuggestion() {
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(4L, null, "PUBLIC_USER")));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.recommend(new TopicRecommendationRequest(
                null,
                null,
                "Clinical AI decision support for hospital triage",
                "The study evaluates clinical artificial intelligence decision support in emergency departments.",
                List.of("clinical ai"),
                8
            ))
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void researcherCannotRequestRecommendationForOtherPublication() {
        PublicationEntity draft = publication(77L, "Draft publication", "Draft abstract.");
        draft.setValidationStatus(ValidationStatus.DRAFT);
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(2L, 7L, "RESEARCHER")));
        when(publicationAuthorRepository.existsByPublicationIdAndResearcherId(77L, 7L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.recommend(new TopicRecommendationRequest(
                TopicRecommendationTargetType.PUBLICATION,
                77L,
                "Clinical AI decision support for hospital triage",
                "The study evaluates clinical artificial intelligence decision support in emergency departments.",
                List.of("clinical ai"),
                8
            ))
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    private void mockRetrieval(List<RetrievedPublicationContext> contexts) {
        when(retrievalService.retrieveBest(any(), any(), eq(VisibilityScope.PUBLIC_VALIDATED), isNull()))
            .thenReturn(new PublicationRetrievalResult(
                contexts,
                RetrievalMethod.SEMANTIC,
                RetrievalMode.BALANCED,
                0.35,
                List.of(),
                VisibilityScope.PUBLIC_VALIDATED,
                true
            ));
    }

    private RetrievedPublicationContext context(PublicationEntity publication, Double similarityScore, List<String> topics) {
        return new RetrievedPublicationContext(
            publication,
            List.of(),
            topics,
            similarityScore,
            true,
            false,
            "Similitud semantica por encima del umbral configurado."
        );
    }

    private PublicationEntity publication(Long id, String title, String abstractText) {
        PublicationEntity publication = new PublicationEntity(
            title,
            abstractText,
            2025,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Demo Journal",
            null
        );
        publication.setId(id);
        publication.setValidationStatus(ValidationStatus.VALIDATED);
        return publication;
    }

    private TopicEntity topic(Long id, String name, String normalizedName) {
        TopicEntity topic = new TopicEntity(name, normalizedName);
        ReflectionTestUtils.setField(topic, "id", id);
        return topic;
    }

    private AiSuggestionResponse aiSuggestion(Long id) {
        return new AiSuggestionResponse(
            id,
            null,
            null,
            AiSuggestionType.TOPIC_RECOMMENDATION,
            AiSuggestionStatus.GENERATED,
            "{}",
            "Generated.",
            "{}",
            "embedding:ollama,llm:mock",
            "embedding:bge-m3,llm:mock-llm",
            Instant.parse("2026-05-25T10:15:00Z"),
            1L,
            null,
            null,
            null
        );
    }

    private PlatformUserPrincipal principal(Long userId, Long researcherId, String role) {
        UserEntity user = new UserEntity("user@example.test", "Test User", "{noop}password", true, researcherId);
        user.setId(userId);
        user.setRoles(new LinkedHashSet<>(List.of(new RoleEntity(role, role, role))));
        return new PlatformUserPrincipal(user);
    }
}
