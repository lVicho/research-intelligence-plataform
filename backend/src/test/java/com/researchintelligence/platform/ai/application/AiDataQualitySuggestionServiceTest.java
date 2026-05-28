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
import com.researchintelligence.platform.ai.api.DataQualityFixSuggestionRequest;
import com.researchintelligence.platform.ai.api.DataQualityFixSuggestionResponse;
import com.researchintelligence.platform.ai.api.DataQualitySuggestionScope;
import com.researchintelligence.platform.ai.api.TopicRecommendationResponse;
import com.researchintelligence.platform.ai.api.TopicRecommendationTopicResponse;
import com.researchintelligence.platform.ai.domain.AiSuggestionStatus;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.dataquality.domain.DataQualityEntityType;
import com.researchintelligence.platform.dataquality.domain.DataQualityIssueType;
import com.researchintelligence.platform.dataquality.domain.DataQualitySeverity;
import com.researchintelligence.platform.dataquality.persistence.DataQualityIssueRow;
import com.researchintelligence.platform.dataquality.persistence.DataQualityRepository;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AiDataQualitySuggestionServiceTest {

    @Mock
    private DataQualityRepository dataQualityRepository;

    @Mock
    private AiSuggestionService aiSuggestionService;

    @Mock
    private TopicRecommendationService topicRecommendationService;

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
    private ResearcherRepository researcherRepository;

    @Mock
    private ResearchUnitRepository researchUnitRepository;

    @Mock
    private VisibilityContext visibilityContext;

    private AiDataQualitySuggestionService service;

    @BeforeEach
    void setUp() {
        service = new AiDataQualitySuggestionService(
            dataQualityRepository,
            aiSuggestionService,
            topicRecommendationService,
            llmService,
            publicationRepository,
            publicationAuthorRepository,
            publicationTopicRepository,
            topicRepository,
            researcherRepository,
            researchUnitRepository,
            visibilityContext,
            new ObjectMapper()
        );
        lenient().when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(1L, null, "ADMIN")));
        lenient().when(llmService.provider()).thenReturn("mock");
        lenient().when(llmService.model()).thenReturn("mock-llm");
        lenient().when(aiSuggestionService.create(any())).thenReturn(aiSuggestion(900L));
    }

    @Test
    void missingAbstractCreatesDataQualityFixSuggestion() {
        PublicationEntity publication = publication(10L, "Clinical AI for hospital triage", null, null);
        when(publicationRepository.findById(10L)).thenReturn(Optional.of(publication));
        when(publicationTopicRepository.findByPublicationId(10L)).thenReturn(List.of());
        mockIssues(issue(DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT, DataQualityEntityType.PUBLICATION, 10L));

        List<DataQualityFixSuggestionResponse> response = service.suggestFixes(request(DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT));

        assertEquals(1, response.size());
        assertEquals(900L, response.getFirst().createdSuggestionId());
        assertEquals("draft_abstract", response.getFirst().suggestedFix().get("action"));

        ArgumentCaptor<AiSuggestionCreateCommand> commandCaptor = ArgumentCaptor.forClass(AiSuggestionCreateCommand.class);
        verify(aiSuggestionService).create(commandCaptor.capture());
        assertEquals(AiSuggestionType.DATA_QUALITY_FIX, commandCaptor.getValue().suggestionType());
        assertTrue(commandCaptor.getValue().proposedDataJson().contains("draftAbstract"));
    }

    @Test
    void missingTopicsCallsTopicRecommendationLogicAndCreatesDataQualityFix() {
        PublicationEntity publication = publication(11L, "Clinical AI for hospital triage", "Predicts deterioration in emergency care.", null);
        when(publicationRepository.findById(11L)).thenReturn(Optional.of(publication));
        when(topicRecommendationService.recommend(any())).thenReturn(new TopicRecommendationResponse(
            701L,
            List.of(new TopicRecommendationTopicResponse("Clinical AI", 30L, 0.82, "Found in similar publications.", List.of(1L, 2L))),
            List.of()
        ));
        mockIssues(issue(DataQualityIssueType.PUBLICATIONS_WITHOUT_TOPICS, DataQualityEntityType.PUBLICATION, 11L));

        List<DataQualityFixSuggestionResponse> response = service.suggestFixes(request(DataQualityIssueType.PUBLICATIONS_WITHOUT_TOPICS));

        assertEquals("recommend_topics", response.getFirst().suggestedFix().get("action"));
        assertEquals(0.82, response.getFirst().confidence());
        verify(topicRecommendationService).recommend(any());

        ArgumentCaptor<AiSuggestionCreateCommand> commandCaptor = ArgumentCaptor.forClass(AiSuggestionCreateCommand.class);
        verify(aiSuggestionService).create(commandCaptor.capture());
        assertEquals(AiSuggestionType.DATA_QUALITY_FIX, commandCaptor.getValue().suggestionType());
        assertTrue(commandCaptor.getValue().proposedDataJson().contains("Clinical AI"));
        assertTrue(commandCaptor.getValue().proposedDataJson().contains("topicRecommendationSuggestionId"));
    }

    @Test
    void duplicatePublicationCandidateCreatesSuggestionWithCandidates() {
        PublicationEntity source = publication(12L, "Clinical AI for hospital triage", "Abstract", null);
        PublicationEntity duplicate = publication(13L, "Clinical AI for hospital triage", "Other", "10.123/example");
        when(publicationRepository.findById(12L)).thenReturn(Optional.of(source));
        when(publicationRepository.findDuplicateCandidates(eq(12L), eq(source.getTitle()), eq(source.getPublicationYear()), any(PageRequest.class)))
            .thenReturn(List.of(duplicate));
        mockIssues(issue(DataQualityIssueType.DUPLICATE_PUBLICATION_CANDIDATES, DataQualityEntityType.PUBLICATION, 12L));

        List<DataQualityFixSuggestionResponse> response = service.suggestFixes(request(DataQualityIssueType.DUPLICATE_PUBLICATION_CANDIDATES));

        assertEquals("review_duplicate_publication_candidates", response.getFirst().suggestedFix().get("action"));
        assertTrue(response.getFirst().confidence() > 0.8);
        ArgumentCaptor<AiSuggestionCreateCommand> commandCaptor = ArgumentCaptor.forClass(AiSuggestionCreateCommand.class);
        verify(aiSuggestionService).create(commandCaptor.capture());
        assertTrue(commandCaptor.getValue().proposedDataJson().contains("\"publicationId\":13"));
    }

    @Test
    void missingDoiDoesNotGenerateFakeDoi() {
        PublicationEntity publication = publication(14L, "Clinical AI for hospital triage", "Abstract", null);
        when(publicationRepository.findById(14L)).thenReturn(Optional.of(publication));
        mockIssues(issue(DataQualityIssueType.PUBLICATIONS_WITHOUT_DOI, DataQualityEntityType.PUBLICATION, 14L));

        service.suggestFixes(request(DataQualityIssueType.PUBLICATIONS_WITHOUT_DOI));

        ArgumentCaptor<AiSuggestionCreateCommand> commandCaptor = ArgumentCaptor.forClass(AiSuggestionCreateCommand.class);
        verify(aiSuggestionService).create(commandCaptor.capture());
        assertTrue(commandCaptor.getValue().proposedDataJson().contains("\"suggestedDoi\":null"));
        assertFalse(commandCaptor.getValue().proposedDataJson().contains("10.123"));
        assertTrue(commandCaptor.getValue().evidenceJson().contains("No DOI was generated"));
    }

    @Test
    void permissionsAreEnforced() {
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(4L, null, "PUBLIC_USER")));

        ResponseStatusException publicException = assertThrows(
            ResponseStatusException.class,
            () -> service.suggestFixes(request(DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT))
        );

        assertEquals(403, publicException.getStatusCode().value());
        verify(dataQualityRepository, never()).search(any(), any(), any(), any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    void researcherCannotUseAdminAllScope() {
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(2L, 7L, "RESEARCHER")));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.suggestFixes(new DataQualityFixSuggestionRequest(
                null,
                null,
                DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT,
                DataQualitySuggestionScope.ADMIN_ALL,
                10
            ))
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void ollamaUnavailableFallsBackGracefully() {
        PublicationEntity publication = publication(15L, "Clinical AI for hospital triage", null, null);
        when(llmService.provider()).thenReturn("ollama");
        when(llmService.model()).thenReturn("qwen2.5:14b");
        when(llmService.answer(any())).thenThrow(new BusinessRuleException("Ollama LLM request failed at http://localhost:11434/api/generate"));
        when(publicationRepository.findById(15L)).thenReturn(Optional.of(publication));
        when(publicationTopicRepository.findByPublicationId(15L)).thenReturn(List.of());
        mockIssues(issue(DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT, DataQualityEntityType.PUBLICATION, 15L));

        List<DataQualityFixSuggestionResponse> response = service.suggestFixes(request(DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT));

        assertEquals(1, response.size());
        assertEquals("draft_abstract", response.getFirst().suggestedFix().get("action"));
        assertTrue(response.getFirst().evidence().toString().contains("Ollama is unavailable"));
    }

    private void mockIssues(DataQualityIssueRow issue) {
        when(dataQualityRepository.search(
            eq(issue.issueType()),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(0),
            eq(10),
            isNull()
        )).thenReturn(new PageImpl<>(List.of(issue), PageRequest.of(0, 10), 1));
    }

    private DataQualityFixSuggestionRequest request(DataQualityIssueType issueType) {
        return new DataQualityFixSuggestionRequest(null, null, issueType, DataQualitySuggestionScope.ADMIN_ALL, 10);
    }

    private DataQualityIssueRow issue(DataQualityIssueType issueType, DataQualityEntityType entityType, Long entityId) {
        return new DataQualityIssueRow(
            issueType,
            DataQualitySeverity.WARNING,
            entityType,
            entityId,
            "Clinical AI for hospital triage",
            "Quality issue.",
            "Review metadata.",
            ValidationStatus.PENDING_VALIDATION
        );
    }

    private PublicationEntity publication(Long id, String title, String abstractText, String doi) {
        PublicationEntity publication = new PublicationEntity(
            title,
            abstractText,
            2025,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            doi,
            "Demo Journal",
            null
        );
        publication.setId(id);
        return publication;
    }

    private AiSuggestionResponse aiSuggestion(Long id) {
        return new AiSuggestionResponse(
            id,
            "PUBLICATION",
            10L,
            AiSuggestionType.DATA_QUALITY_FIX,
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
