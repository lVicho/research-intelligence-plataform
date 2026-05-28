package com.researchintelligence.platform.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.api.AiSuggestionResponse;
import com.researchintelligence.platform.ai.api.ValidationAssistanceRecommendation;
import com.researchintelligence.platform.ai.api.ValidationAssistanceReviewRequest;
import com.researchintelligence.platform.ai.domain.AiSuggestionStatus;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.events.persistence.ScientificEventRepository;
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
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ValidationAssistanceReviewServiceTest {

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private PublicationAuthorRepository publicationAuthorRepository;

    @Mock
    private PublicationTopicRepository publicationTopicRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private ResearcherRepository researcherRepository;

    @Mock
    private ResearchUnitRepository researchUnitRepository;

    @Mock
    private ResearcherAffiliationRepository affiliationRepository;

    @Mock
    private EventParticipationRepository eventParticipationRepository;

    @Mock
    private ScientificEventRepository scientificEventRepository;

    @Mock
    private AiSuggestionService aiSuggestionService;

    @Mock
    private LlmService llmService;

    @Mock
    private VisibilityContext visibilityContext;

    private ValidationAssistanceReviewService service;

    @BeforeEach
    void setUp() {
        service = new ValidationAssistanceReviewService(
            publicationRepository,
            publicationAuthorRepository,
            publicationTopicRepository,
            topicRepository,
            researcherRepository,
            researchUnitRepository,
            affiliationRepository,
            eventParticipationRepository,
            scientificEventRepository,
            aiSuggestionService,
            llmService,
            visibilityContext,
            new ObjectMapper()
        );
        lenient().when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(1L, null, "ADMIN")));
        lenient().when(llmService.provider()).thenReturn("mock");
        lenient().when(llmService.model()).thenReturn("mock-llm");
        lenient().when(aiSuggestionService.create(any())).thenReturn(aiSuggestion(901L));
    }

    @Test
    void pendingPublicationWithMissingTopicsRecommendsRequestChangesAndCreatesValidationAssistanceSuggestion() {
        PublicationEntity publication = completePublication(10L);
        when(publicationRepository.findById(10L)).thenReturn(Optional.of(publication));
        when(publicationAuthorRepository.findByPublicationIdOrderByAuthorOrderAsc(10L))
            .thenReturn(List.of(new PublicationAuthorEntity(10L, 7L, null, null, 1, true)));
        when(publicationTopicRepository.findByPublicationId(10L)).thenReturn(List.of());
        when(topicRepository.findAll()).thenReturn(List.of(topic(30L, "Clinical AI", "clinical ai")));
        when(publicationRepository.findDuplicateCandidates(eq(10L), eq(publication.getTitle()), eq(publication.getPublicationYear()), any(PageRequest.class)))
            .thenReturn(List.of());

        var response = service.review(new ValidationAssistanceReviewRequest(ValidationEntityType.PUBLICATION, 10L));

        assertEquals(ValidationAssistanceRecommendation.REQUEST_CHANGES, response.recommendation());
        assertTrue(response.checks().stream().anyMatch(check -> "MISSING_TOPICS".equals(check.code())));
        assertTrue(response.checks().stream().anyMatch(check -> "TOPIC_SUGGESTIONS".equals(check.code())));
        assertEquals(ValidationStatus.PENDING_VALIDATION, publication.getValidationStatus());

        ArgumentCaptor<AiSuggestionCreateCommand> commandCaptor = ArgumentCaptor.forClass(AiSuggestionCreateCommand.class);
        verify(aiSuggestionService).create(commandCaptor.capture());
        assertEquals(AiSuggestionType.VALIDATION_ASSISTANCE, commandCaptor.getValue().suggestionType());
        assertEquals("PUBLICATION", commandCaptor.getValue().targetType());
        assertEquals(10L, commandCaptor.getValue().targetId());
        assertTrue(commandCaptor.getValue().proposedDataJson().contains("\"doesNotValidateAutomatically\":true"));
        assertTrue(commandCaptor.getValue().proposedDataJson().contains("MISSING_TOPICS"));
    }

    @Test
    void duplicateCandidateWarningRecommendsManualReview() {
        PublicationEntity publication = completePublication(11L);
        PublicationEntity duplicate = completePublication(12L);
        when(publicationRepository.findById(11L)).thenReturn(Optional.of(publication));
        when(publicationAuthorRepository.findByPublicationIdOrderByAuthorOrderAsc(11L))
            .thenReturn(List.of(new PublicationAuthorEntity(11L, 7L, null, null, 1, true)));
        when(publicationTopicRepository.findByPublicationId(11L)).thenReturn(List.of(new PublicationTopicEntity(11L, 30L)));
        when(publicationRepository.findDuplicateCandidates(eq(11L), eq(publication.getTitle()), eq(publication.getPublicationYear()), any(PageRequest.class)))
            .thenReturn(List.of(duplicate));

        var response = service.review(new ValidationAssistanceReviewRequest(ValidationEntityType.PUBLICATION, 11L));

        assertEquals(ValidationAssistanceRecommendation.REVIEW_MANUALLY, response.recommendation());
        assertTrue(response.confidence() > 0.5);
        assertTrue(response.checks().stream().anyMatch(check -> "POSSIBLE_DUPLICATE_PUBLICATION".equals(check.code())));
    }

    @Test
    void completePublicationRecommendsValidateWithConfidence() {
        PublicationEntity publication = completePublication(13L);
        when(publicationRepository.findById(13L)).thenReturn(Optional.of(publication));
        when(publicationAuthorRepository.findByPublicationIdOrderByAuthorOrderAsc(13L))
            .thenReturn(List.of(new PublicationAuthorEntity(13L, 7L, null, null, 1, true)));
        when(publicationTopicRepository.findByPublicationId(13L)).thenReturn(List.of(new PublicationTopicEntity(13L, 30L)));
        when(publicationRepository.findDuplicateCandidates(eq(13L), eq(publication.getTitle()), eq(publication.getPublicationYear()), any(PageRequest.class)))
            .thenReturn(List.of());

        var response = service.review(new ValidationAssistanceReviewRequest(ValidationEntityType.PUBLICATION, 13L));

        assertEquals(ValidationAssistanceRecommendation.VALIDATE, response.recommendation());
        assertTrue(response.confidence() >= 0.8);
        assertTrue(response.checks().stream().anyMatch(check -> "NO_BLOCKING_VALIDATION_ISSUES".equals(check.code())));
    }

    @Test
    void permissionsAreEnforcedForNonValidatorUsers() {
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(2L, 7L, "RESEARCHER")));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.review(new ValidationAssistanceReviewRequest(ValidationEntityType.PUBLICATION, 10L))
        );

        assertEquals(403, exception.getStatusCode().value());
        verify(publicationRepository, never()).findById(10L);
    }

    @Test
    void onlyPendingItemsCanBeReviewed() {
        PublicationEntity publication = completePublication(14L);
        publication.setValidationStatus(ValidationStatus.VALIDATED);
        when(publicationRepository.findById(14L)).thenReturn(Optional.of(publication));

        assertThrows(
            BusinessRuleException.class,
            () -> service.review(new ValidationAssistanceReviewRequest(ValidationEntityType.PUBLICATION, 14L))
        );

        verify(aiSuggestionService, never()).create(any());
    }

    @Test
    void ollamaUnavailableIsHandledGracefully() {
        PublicationEntity publication = completePublication(15L);
        when(llmService.provider()).thenReturn("ollama");
        when(llmService.model()).thenReturn("qwen2.5:14b");
        when(llmService.answer(any())).thenThrow(new BusinessRuleException("Ollama LLM request failed at http://localhost:11434/api/generate"));
        when(publicationRepository.findById(15L)).thenReturn(Optional.of(publication));
        when(publicationAuthorRepository.findByPublicationIdOrderByAuthorOrderAsc(15L))
            .thenReturn(List.of(new PublicationAuthorEntity(15L, 7L, null, null, 1, true)));
        when(publicationTopicRepository.findByPublicationId(15L)).thenReturn(List.of(new PublicationTopicEntity(15L, 30L)));
        when(publicationRepository.findDuplicateCandidates(eq(15L), eq(publication.getTitle()), eq(publication.getPublicationYear()), any(PageRequest.class)))
            .thenReturn(List.of());

        var response = service.review(new ValidationAssistanceReviewRequest(ValidationEntityType.PUBLICATION, 15L));

        assertEquals(ValidationAssistanceRecommendation.VALIDATE, response.recommendation());
        assertEquals(901L, response.createdSuggestionId());
        assertTrue(response.checks().stream().anyMatch(check -> "AI_REVIEW_MODEL_UNAVAILABLE".equals(check.code())));
    }

    private PublicationEntity completePublication(Long id) {
        PublicationEntity publication = new PublicationEntity(
            "Clinical AI for hospital triage",
            "A clinical artificial intelligence model supports hospital triage decisions.",
            2025,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            "10.1234/example.2025.001",
            "Demo Journal",
            "https://example.test/publications/clinical-ai"
        );
        publication.setId(id);
        publication.setPublicSummary("Plain-language summary for validators.");
        publication.setValidationStatus(ValidationStatus.PENDING_VALIDATION);
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
            "PUBLICATION",
            10L,
            AiSuggestionType.VALIDATION_ASSISTANCE,
            AiSuggestionStatus.GENERATED,
            "{}",
            "Generated.",
            "{}",
            "mock",
            "mock-llm",
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
