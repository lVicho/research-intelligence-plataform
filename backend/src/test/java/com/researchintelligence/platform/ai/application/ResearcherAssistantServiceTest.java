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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.api.AiSuggestionResponse;
import com.researchintelligence.platform.ai.api.PublicSummaryGenerateRequest;
import com.researchintelligence.platform.ai.api.PublicSummaryGenerateResponse;
import com.researchintelligence.platform.ai.api.PublicSummaryEvidenceResponse;
import com.researchintelligence.platform.ai.api.ResearcherAssistantAskRequest;
import com.researchintelligence.platform.ai.api.ResearcherAssistantMode;
import com.researchintelligence.platform.ai.api.ResearcherAssistantResponse;
import com.researchintelligence.platform.ai.api.TopicRecommendationRequest;
import com.researchintelligence.platform.ai.api.TopicRecommendationResponse;
import com.researchintelligence.platform.ai.api.TopicRecommendationTopicResponse;
import com.researchintelligence.platform.ai.domain.AiSuggestionStatus;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.persistence.ResearcherActivityRow;
import com.researchintelligence.platform.auth.persistence.ResearcherWorkspaceRepository;
import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.dataquality.domain.DataQualityEntityType;
import com.researchintelligence.platform.dataquality.domain.DataQualityIssueType;
import com.researchintelligence.platform.dataquality.domain.DataQualitySeverity;
import com.researchintelligence.platform.dataquality.persistence.DataQualityIssueRow;
import com.researchintelligence.platform.dataquality.persistence.DataQualityRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ResearcherAssistantServiceTest {

    @Mock
    private ResearcherWorkspaceRepository workspaceRepository;

    @Mock
    private DataQualityRepository dataQualityRepository;

    @Mock
    private PublicationRetrievalService retrievalService;

    @Mock
    private PublicSummaryGenerationService publicSummaryGenerationService;

    @Mock
    private TopicRecommendationService topicRecommendationService;

    @Mock
    private AiSuggestionService aiSuggestionService;

    @Mock
    private LlmService llmService;

    private ResearcherAssistantService service;

    @BeforeEach
    void setUp() {
        service = new ResearcherAssistantService(
            workspaceRepository,
            dataQualityRepository,
            retrievalService,
            publicSummaryGenerationService,
            topicRecommendationService,
            aiSuggestionService,
            llmService,
            new ObjectMapper()
        );
        lenient().when(llmService.provider()).thenReturn("mock");
        lenient().when(llmService.model()).thenReturn("mock-llm");
        lenient().when(aiSuggestionService.create(any())).thenReturn(aiSuggestion(900L, AiSuggestionType.DATA_QUALITY_FIX));
        lenient().when(retrievalService.retrieveBest(any(), any(), eq(VisibilityScope.PUBLIC_VALIDATED), isNull()))
            .thenReturn(new PublicationRetrievalResult(
                List.of(),
                RetrievalMethod.TEXT,
                RetrievalMode.BALANCED,
                0.35,
                List.of(),
                VisibilityScope.PUBLIC_VALIDATED,
                true
            ));
        lenient().when(retrievalService.textSearch(any(), anyInt(), eq(VisibilityScope.PUBLIC_VALIDATED), isNull()))
            .thenReturn(List.of());
    }

    @Test
    void researcherSeesOwnPendingActivities() {
        Long researcherId = 7L;
        mockWorkspace(
            researcherId,
            List.of(activity(ValidationEntityType.PUBLICATION, 20L, researcherId, ValidationStatus.PENDING_VALIDATION, "Pending publication", null)),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );

        ResearcherAssistantResponse response = service.ask(
            principal(11L, researcherId, "RESEARCHER"),
            new ResearcherAssistantAskRequest("What do I have pending?", ResearcherAssistantMode.TASKS)
        );

        assertEquals(1, response.relatedActivities().size());
        assertEquals(ValidationStatus.PENDING_VALIDATION, response.relatedActivities().getFirst().validationStatus());
        assertTrue(response.answer().contains("1 actividad"));
    }

    @Test
    void researcherSeesOwnChangesRequestedComments() {
        Long researcherId = 7L;
        mockWorkspace(
            researcherId,
            List.of(),
            List.of(activity(ValidationEntityType.PUBLICATION, 21L, researcherId, ValidationStatus.CHANGES_REQUESTED, "Changes requested publication", "Please add DOI.")),
            List.of(),
            List.of(),
            List.of()
        );

        ResearcherAssistantResponse response = service.ask(
            principal(11L, researcherId, "RESEARCHER"),
            new ResearcherAssistantAskRequest("List activities requiring changes.", ResearcherAssistantMode.TASKS)
        );

        assertEquals("Please add DOI.", response.relatedActivities().getFirst().validationComment());
        assertTrue(response.answer().contains("Please add DOI."));
    }

    @Test
    void researcherCannotSeeOtherResearchersPrivateActivitiesEvenIfRepositoryReturnsOne() {
        Long researcherId = 7L;
        ResearcherActivityRow otherResearcherActivity = activity(
            ValidationEntityType.PUBLICATION,
            30L,
            8L,
            ValidationStatus.PENDING_VALIDATION,
            "Other private publication",
            null
        );
        mockWorkspace(researcherId, List.of(otherResearcherActivity), List.of(), List.of(), List.of(), List.of());

        ResearcherAssistantResponse response = service.ask(
            principal(11L, researcherId, "RESEARCHER"),
            new ResearcherAssistantAskRequest("What do I have pending?", ResearcherAssistantMode.TASKS)
        );

        assertTrue(response.relatedActivities().isEmpty());
        assertFalse(response.answer().contains("Other private publication"));
    }

    @Test
    void qualityModeCreatesAiSuggestionsForOwnIssues() {
        Long researcherId = 7L;
        DataQualityIssueRow issue = issue(DataQualityIssueType.PUBLICATIONS_WITHOUT_DOI, DataQualityEntityType.PUBLICATION, 20L);
        mockWorkspace(researcherId, List.of(), List.of(), List.of(), List.of(), List.of(issue));

        ResearcherAssistantResponse response = service.ask(
            principal(11L, researcherId, "RESEARCHER"),
            new ResearcherAssistantAskRequest("Explain my data quality issues.", ResearcherAssistantMode.QUALITY)
        );

        assertEquals(1, response.suggestions().size());
        assertEquals(AiSuggestionType.DATA_QUALITY_FIX, response.suggestions().getFirst().suggestionType());
        ArgumentCaptor<AiSuggestionCreateCommand> commandCaptor = ArgumentCaptor.forClass(AiSuggestionCreateCommand.class);
        verify(aiSuggestionService).create(commandCaptor.capture());
        assertEquals("PUBLICATION", commandCaptor.getValue().targetType());
        assertEquals(20L, commandCaptor.getValue().targetId());
    }

    @Test
    void profileModeCreatesPublicSummarySuggestion() {
        Long researcherId = 7L;
        mockWorkspace(researcherId, List.of(), List.of(), List.of(), List.of(), List.of());
        when(publicSummaryGenerationService.generate(any(PublicSummaryGenerateRequest.class)))
            .thenReturn(new PublicSummaryGenerateResponse(
                "Resumen publico sugerido.",
                List.of(new PublicSummaryEvidenceResponse("researcher:7", "Nombre validado", "Researcher")),
                701L,
                List.of(),
                "mock",
                "mock-llm"
            ));

        ResearcherAssistantResponse response = service.ask(
            principal(11L, researcherId, "RESEARCHER"),
            new ResearcherAssistantAskRequest("Generate my public profile summary.", ResearcherAssistantMode.PROFILE)
        );

        assertEquals(1, response.suggestions().size());
        assertEquals(701L, response.suggestions().getFirst().suggestionId());
        assertEquals(AiSuggestionType.PUBLIC_SUMMARY, response.suggestions().getFirst().suggestionType());
    }

    @Test
    void topicQuestionCreatesTopicRecommendationForOwnDraftPublication() {
        Long researcherId = 7L;
        mockWorkspace(
            researcherId,
            List.of(),
            List.of(),
            List.of(activity(ValidationEntityType.PUBLICATION, 22L, researcherId, ValidationStatus.DRAFT, "Draft AI publication", null)),
            List.of(),
            List.of()
        );
        when(topicRecommendationService.recommend(any(TopicRecommendationRequest.class)))
            .thenReturn(new TopicRecommendationResponse(
                777L,
                List.of(new TopicRecommendationTopicResponse("Clinical AI", 10L, 0.82, "Found in similar publications.", List.of(1L))),
                List.of()
            ));

        ResearcherAssistantResponse response = service.ask(
            principal(11L, researcherId, "RESEARCHER"),
            new ResearcherAssistantAskRequest("Recommend topics for my draft publication.", ResearcherAssistantMode.GENERAL)
        );

        assertEquals(1, response.suggestions().size());
        assertEquals(AiSuggestionType.TOPIC_RECOMMENDATION, response.suggestions().getFirst().suggestionType());
        assertTrue(response.suggestions().getFirst().detail().contains("Clinical AI"));
    }

    @Test
    void ollamaUnavailableHandledGracefully() {
        Long researcherId = 7L;
        mockWorkspace(researcherId, List.of(), List.of(), List.of(), List.of(), List.of());
        when(llmService.provider()).thenReturn("ollama");
        when(llmService.model()).thenReturn("qwen2.5:14b");
        when(llmService.answer(any())).thenThrow(new BusinessRuleException("Ollama LLM request failed at http://localhost:11434/api/generate"));

        ResearcherAssistantResponse response = service.ask(
            principal(11L, researcherId, "RESEARCHER"),
            new ResearcherAssistantAskRequest("Help me understand my workspace.", ResearcherAssistantMode.GENERAL)
        );

        assertTrue(response.answer().contains("Puedo ayudarte"));
        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("Ollama no esta disponible")));
    }

    @Test
    void adminActingAsLinkedResearcherGetsExplicitWarning() {
        Long researcherId = 7L;
        mockWorkspace(researcherId, List.of(), List.of(), List.of(), List.of(), List.of());

        ResearcherAssistantResponse response = service.ask(
            principal(1L, researcherId, "ADMIN"),
            new ResearcherAssistantAskRequest("What is my status?", ResearcherAssistantMode.TASKS)
        );

        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("Admin acting as linked researcher")));
    }

    @Test
    void adminWithoutLinkedResearcherGetsClearError() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.ask(
                principal(1L, null, "ADMIN"),
                new ResearcherAssistantAskRequest("What is my status?", ResearcherAssistantMode.TASKS)
            )
        );

        assertEquals(403, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("linked to a researcher profile"));
    }

    private void mockWorkspace(
        Long researcherId,
        List<ResearcherActivityRow> pending,
        List<ResearcherActivityRow> changesRequested,
        List<ResearcherActivityRow> drafts,
        List<ResearcherActivityRow> rejected,
        List<DataQualityIssueRow> qualityIssues
    ) {
        when(workspaceRepository.findOwnedActivity(researcherId, ValidationEntityType.RESEARCHER, researcherId))
            .thenReturn(Optional.of(activity(ValidationEntityType.RESEARCHER, researcherId, researcherId, ValidationStatus.VALIDATED, "Researcher", null)));
        mockActivities(researcherId, ValidationStatus.PENDING_VALIDATION, pending);
        mockActivities(researcherId, ValidationStatus.CHANGES_REQUESTED, changesRequested);
        mockActivities(researcherId, ValidationStatus.DRAFT, drafts);
        mockActivities(researcherId, ValidationStatus.REJECTED, rejected);
        when(dataQualityRepository.search(isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10), eq(researcherId)))
            .thenReturn(new PageImpl<>(qualityIssues, PageRequest.of(0, 10), qualityIssues.size()));
    }

    private void mockActivities(Long researcherId, ValidationStatus status, List<ResearcherActivityRow> rows) {
        when(workspaceRepository.activities(researcherId, status, null, null, 0, 20))
            .thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), rows.size()));
    }

    private ResearcherActivityRow activity(
        ValidationEntityType entityType,
        Long entityId,
        Long researcherId,
        ValidationStatus status,
        String title,
        String validationComment
    ) {
        return new ResearcherActivityRow(
            entityType,
            entityId,
            title,
            "Subtitle",
            researcherId,
            "Researcher",
            10L,
            "Research Unit",
            Instant.parse("2026-05-16T09:00:00Z"),
            status,
            validationComment,
            99L,
            "Validator",
            Instant.parse("2026-05-16T10:00:00Z"),
            entityType == ValidationEntityType.PUBLICATION ? "ARTICLE" : null,
            null,
            entityType == ValidationEntityType.PUBLICATION ? 2026 : null,
            null,
            entityType == ValidationEntityType.PUBLICATION ? "Journal" : null,
            "researcher@example.test",
            null,
            true,
            "Researcher",
            true,
            null,
            null,
            true,
            1L,
            entityType == ValidationEntityType.PUBLICATION ? 0L : null
        );
    }

    private DataQualityIssueRow issue(DataQualityIssueType issueType, DataQualityEntityType entityType, Long entityId) {
        return new DataQualityIssueRow(
            issueType,
            DataQualitySeverity.WARNING,
            entityType,
            entityId,
            "Quality issue target",
            "Issue description.",
            "Review metadata.",
            ValidationStatus.DRAFT
        );
    }

    private AiSuggestionResponse aiSuggestion(Long id, AiSuggestionType type) {
        return new AiSuggestionResponse(
            id,
            "PUBLICATION",
            20L,
            type,
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
