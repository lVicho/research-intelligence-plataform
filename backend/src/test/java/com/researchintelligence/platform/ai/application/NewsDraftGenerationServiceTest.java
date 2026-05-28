package com.researchintelligence.platform.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.api.AiSuggestionResponse;
import com.researchintelligence.platform.ai.api.NewsDraftGenerateRequest;
import com.researchintelligence.platform.ai.api.NewsDraftGenerateResponse;
import com.researchintelligence.platform.ai.api.NewsDraftSourceType;
import com.researchintelligence.platform.ai.api.NewsDraftTone;
import com.researchintelligence.platform.ai.domain.AiSuggestionStatus;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
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
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NewsDraftGenerationServiceTest {

    @Mock
    private AiSuggestionService aiSuggestionService;

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
    private ResearcherAffiliationRepository affiliationRepository;

    @Mock
    private ResearchUnitRepository researchUnitRepository;

    @Mock
    private VisibilityContext visibilityContext;

    private NewsDraftGenerationService service;
    private FixedLlmService llmService;

    @BeforeEach
    void setUp() {
        llmService = new FixedLlmService();
        service = new NewsDraftGenerationService(
            aiSuggestionService,
            llmService,
            publicationRepository,
            publicationAuthorRepository,
            publicationTopicRepository,
            topicRepository,
            researcherRepository,
            affiliationRepository,
            researchUnitRepository,
            visibilityContext,
            new ObjectMapper()
        );
        lenient().when(visibilityContext.currentUser()).thenReturn(Optional.of(principal("ADMIN")));
        lenient().when(aiSuggestionService.create(any())).thenReturn(aiSuggestion(900L));
        lenient().when(publicationAuthorRepository.findByPublicationIdOrderByAuthorOrderAsc(any())).thenReturn(List.of());
        lenient().when(publicationTopicRepository.findByPublicationId(any())).thenReturn(List.of());
    }

    @Test
    void publicationDraftCreatesNewsDraftSuggestionFromValidatedEvidence() {
        when(publicationRepository.findById(20L)).thenReturn(Optional.of(publication(20L, ValidationStatus.VALIDATED)));

        NewsDraftGenerateResponse response = service.generateDraft(new NewsDraftGenerateRequest(
            NewsDraftSourceType.PUBLICATION,
            20L,
            null,
            NewsDraftTone.OUTREACH,
            null
        ));

        assertEquals(900L, response.createdSuggestionId());
        assertEquals(AiSuggestionType.NEWS_DRAFT, response.createdSuggestionType());
        assertTrue(response.suggestedBody().contains("La institucion prepara esta noticia"));
        assertTrue(response.evidence().stream().anyMatch(evidence -> evidence.reference().equals("publication:20")));

        ArgumentCaptor<AiSuggestionCreateCommand> commandCaptor = ArgumentCaptor.forClass(AiSuggestionCreateCommand.class);
        verify(aiSuggestionService).create(commandCaptor.capture());
        AiSuggestionCreateCommand command = commandCaptor.getValue();
        assertEquals(AiSuggestionType.NEWS_DRAFT, command.suggestionType());
        assertEquals("PUBLICATION", command.targetType());
        assertEquals(20L, command.targetId());
        assertTrue(command.proposedDataJson().contains("\"requiresHumanReview\":true"));
        assertTrue(command.proposedDataJson().contains("\"relatedPublicationIds\":[20]"));
    }

    @Test
    void nonAdminCannotGenerateNewsDraft() {
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal("VALIDATOR")));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.generateDraft(new NewsDraftGenerateRequest(NewsDraftSourceType.PUBLICATION, 20L, null, NewsDraftTone.INSTITUTIONAL, null))
        );

        assertEquals(403, exception.getStatusCode().value());
        verify(aiSuggestionService, never()).create(any());
    }

    @Test
    void draftRejectsNonValidatedPublicationEvidence() {
        when(publicationRepository.findById(20L)).thenReturn(Optional.of(publication(20L, ValidationStatus.PENDING_VALIDATION)));

        assertThrows(
            BusinessRuleException.class,
            () -> service.generateDraft(new NewsDraftGenerateRequest(NewsDraftSourceType.PUBLICATION, 20L, null, NewsDraftTone.INSTITUTIONAL, null))
        );
        verify(aiSuggestionService, never()).create(any());
    }

    private PublicationEntity publication(Long id, ValidationStatus validationStatus) {
        PublicationEntity publication = new PublicationEntity(
            "IA local para triaje hospitalario",
            "Estudio validado sobre modelos locales en hospitales.",
            2025,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            "10.1234/demo",
            "Demo Journal",
            null
        );
        publication.setId(id);
        publication.setValidationStatus(validationStatus);
        return publication;
    }

    private AiSuggestionResponse aiSuggestion(Long id) {
        return new AiSuggestionResponse(
            id,
            "PUBLICATION",
            20L,
            AiSuggestionType.NEWS_DRAFT,
            AiSuggestionStatus.GENERATED,
            "{\"suggestedTitle\":\"Titulo\"}",
            "Generated.",
            null,
            llmService.provider(),
            llmService.model(),
            Instant.now(),
            1L,
            null,
            null,
            null
        );
    }

    private PlatformUserPrincipal principal(String role) {
        UserEntity user = new UserEntity(role.toLowerCase() + "@example.test", "Test User", "{noop}password", true, null);
        user.setId(1L);
        user.setRoles(new LinkedHashSet<>(List.of(new RoleEntity(role, role, role))));
        return new PlatformUserPrincipal(user);
    }

    private static final class FixedLlmService implements LlmService {

        @Override
        public LlmResponse answer(LlmPrompt prompt) {
            return new LlmResponse("", List.of());
        }

        @Override
        public String provider() {
            return "mock";
        }

        @Override
        public String model() {
            return "mock-llm";
        }
    }
}
