package com.researchintelligence.platform.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.api.AiSuggestionResponse;
import com.researchintelligence.platform.ai.api.PublicSummaryAudience;
import com.researchintelligence.platform.ai.api.PublicSummaryGenerateRequest;
import com.researchintelligence.platform.ai.api.PublicSummaryGenerateResponse;
import com.researchintelligence.platform.ai.api.PublicSummaryStyle;
import com.researchintelligence.platform.ai.api.PublicSummaryTargetType;
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
import com.researchintelligence.platform.researchers.domain.AffiliationType;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PublicSummaryGenerationServiceTest {

    @Mock
    private AiSuggestionService aiSuggestionService;

    @Mock
    private ResearcherRepository researcherRepository;

    @Mock
    private ResearcherAffiliationRepository affiliationRepository;

    @Mock
    private ResearchUnitRepository researchUnitRepository;

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private PublicationAuthorRepository publicationAuthorRepository;

    @Mock
    private PublicationTopicRepository publicationTopicRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private VisibilityContext visibilityContext;

    private FixedLlmService llmService;
    private PublicSummaryGenerationService service;

    @BeforeEach
    void setUp() {
        llmService = new FixedLlmService("mock", "mock-llm", null);
        service = new PublicSummaryGenerationService(
            aiSuggestionService,
            llmService,
            researcherRepository,
            affiliationRepository,
            researchUnitRepository,
            publicationRepository,
            publicationAuthorRepository,
            publicationTopicRepository,
            topicRepository,
            visibilityContext,
            new ObjectMapper()
        );
        lenient().when(aiSuggestionService.create(any())).thenReturn(aiSuggestion(700L));
        lenient().when(affiliationRepository.findByResearcherIdOrderByPrimaryAffiliationDescStartDateDescIdAsc(any())).thenReturn(List.of());
        lenient().when(researchUnitRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(publicationRepository.findTopValidatedTopicsByResearcher(any(), eq(ValidationStatus.VALIDATED), any(PageRequest.class))).thenReturn(List.of());
        lenient().when(publicationRepository.findValidatedByResearcherId(any(), eq(ValidationStatus.VALIDATED), any(PageRequest.class))).thenReturn(List.of());
        lenient().when(publicationRepository.findTopValidatedTopicsByResearchUnit(any(), eq(ValidationStatus.VALIDATED), any(PageRequest.class))).thenReturn(List.of());
        lenient().when(publicationRepository.findValidatedByResearchUnitId(any(), eq(ValidationStatus.VALIDATED), any(PageRequest.class))).thenReturn(List.of());
        lenient().when(publicationAuthorRepository.findByPublicationIdOrderByAuthorOrderAsc(any())).thenReturn(List.of());
        lenient().when(publicationTopicRepository.findByPublicationId(any())).thenReturn(List.of());
        lenient().when(topicRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(researcherRepository.findAllById(any())).thenReturn(List.of());
    }

    @Test
    void researcherSummaryGeneratedFromValidatedData() {
        ResearcherEntity researcher = researcher(1L, "Maya Chen");
        ResearchUnitEntity unit = researchUnit(10L, "Grupo de IA Clinica");
        ResearcherAffiliationEntity affiliation = affiliation(100L, researcher.getId(), unit.getId());
        PublicationEntity publication = publication(200L, "IA local para triaje hospitalario", "Resumen validado.");
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(1L, null, "ADMIN")));
        when(researcherRepository.findById(1L)).thenReturn(Optional.of(researcher));
        when(affiliationRepository.findByResearcherIdOrderByPrimaryAffiliationDescStartDateDescIdAsc(1L)).thenReturn(List.of(affiliation));
        when(researchUnitRepository.findAllById(List.of(unit.getId()))).thenReturn(List.of(unit));
        when(publicationRepository.findTopValidatedTopicsByResearcher(eq(1L), eq(ValidationStatus.VALIDATED), any(PageRequest.class)))
            .thenReturn(List.<Object[]>of(new Object[] { 50L, "IA local", 2L }));
        when(publicationRepository.findValidatedByResearcherId(eq(1L), eq(ValidationStatus.VALIDATED), any(PageRequest.class)))
            .thenReturn(List.of(publication));

        PublicSummaryGenerateResponse response = service.generate(request(PublicSummaryTargetType.RESEARCHER, 1L));

        assertEquals(700L, response.createdSuggestionId());
        assertTrue(response.summary().contains("Maya Chen"));
        assertTrue(response.summary().contains("Grupo de IA Clinica"));
        assertFalse(response.evidence().isEmpty());
        ArgumentCaptor<AiSuggestionCreateCommand> commandCaptor = ArgumentCaptor.forClass(AiSuggestionCreateCommand.class);
        verify(aiSuggestionService).create(commandCaptor.capture());
        assertEquals(AiSuggestionType.PUBLIC_SUMMARY, commandCaptor.getValue().suggestionType());
        assertEquals("RESEARCHER", commandCaptor.getValue().targetType());
        assertTrue(commandCaptor.getValue().proposedDataJson().contains("publicProfileSummary"));
    }

    @Test
    void unitSummaryGenerated() {
        ResearchUnitEntity unit = researchUnit(10L, "Instituto de Salud Digital");
        PublicationEntity publication = publication(201L, "Datos clinicos interoperables", "Resumen validado.");
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(1L, null, "ADMIN")));
        when(researchUnitRepository.findById(10L)).thenReturn(Optional.of(unit));
        when(publicationRepository.findTopValidatedTopicsByResearchUnit(eq(10L), eq(ValidationStatus.VALIDATED), any(PageRequest.class)))
            .thenReturn(List.<Object[]>of(new Object[] { 51L, "Salud digital", 3L }));
        when(publicationRepository.findValidatedByResearchUnitId(eq(10L), eq(ValidationStatus.VALIDATED), any(PageRequest.class)))
            .thenReturn(List.of(publication));

        PublicSummaryGenerateResponse response = service.generate(request(PublicSummaryTargetType.RESEARCH_UNIT, 10L));

        assertTrue(response.summary().contains("Instituto de Salud Digital"));
        assertTrue(response.evidence().stream().anyMatch(evidence -> evidence.reference().equals("researchUnit:10")));
    }

    @Test
    void publicationSummaryGenerated() {
        PublicationEntity publication = publication(200L, "IA local para triaje hospitalario", "Estudio sobre modelos locales en hospitales.");
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(1L, null, "ADMIN")));
        when(publicationRepository.findById(200L)).thenReturn(Optional.of(publication));

        PublicSummaryGenerateResponse response = service.generate(request(PublicSummaryTargetType.PUBLICATION, 200L));

        assertTrue(response.summary().contains("IA local para triaje hospitalario"));
        assertTrue(response.evidence().stream().anyMatch(evidence -> evidence.reference().equals("publication:200")));
    }

    @Test
    void publicCannotGenerate() {
        when(visibilityContext.currentUser()).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.generate(request(PublicSummaryTargetType.PUBLICATION, 200L))
        );

        assertEquals(403, exception.getStatusCode().value());
        verify(aiSuggestionService, never()).create(any());
    }

    @Test
    void researcherCannotGenerateForSomeoneElse() {
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(2L, 1L, "RESEARCHER")));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.generate(request(PublicSummaryTargetType.RESEARCHER, 99L))
        );

        assertEquals(403, exception.getStatusCode().value());
        verify(aiSuggestionService, never()).create(any());
    }

    @Test
    void ollamaUnavailableUsesDeterministicSummaryWithWarning() {
        llmService = new FixedLlmService("ollama", "qwen2.5:14b", null);
        llmService.failure = new BusinessRuleException("Ollama LLM request failed at http://localhost:11434/api/generate");
        service = new PublicSummaryGenerationService(
            aiSuggestionService,
            llmService,
            researcherRepository,
            affiliationRepository,
            researchUnitRepository,
            publicationRepository,
            publicationAuthorRepository,
            publicationTopicRepository,
            topicRepository,
            visibilityContext,
            new ObjectMapper()
        );
        PublicationEntity publication = publication(200L, "IA local para triaje hospitalario", "Estudio sobre modelos locales en hospitales.");
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(1L, null, "ADMIN")));
        when(publicationRepository.findById(200L)).thenReturn(Optional.of(publication));

        PublicSummaryGenerateResponse response = service.generate(request(PublicSummaryTargetType.PUBLICATION, 200L));

        assertTrue(response.summary().contains("IA local para triaje hospitalario"));
        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("Ollama")));
        assertEquals(700L, response.createdSuggestionId());
    }

    private PublicSummaryGenerateRequest request(PublicSummaryTargetType targetType, Long targetId) {
        return new PublicSummaryGenerateRequest(targetType, targetId, PublicSummaryStyle.STANDARD, PublicSummaryAudience.PUBLIC);
    }

    private ResearcherEntity researcher(Long id, String name) {
        ResearcherEntity researcher = new ResearcherEntity(name, name, name.toLowerCase().replace(" ", ".") + "@demo.local", "0000-0000-0000-000X", true);
        researcher.setId(id);
        researcher.setValidationStatus(ValidationStatus.VALIDATED);
        return researcher;
    }

    private ResearchUnitEntity researchUnit(Long id, String name) {
        ResearchUnitEntity unit = new ResearchUnitEntity(name, null, ResearchUnitType.RESEARCH_GROUP, null, "Espana", "Madrid", null, true);
        unit.setId(id);
        unit.setValidationStatus(ValidationStatus.VALIDATED);
        unit.setVisibleInPortal(true);
        return unit;
    }

    private ResearcherAffiliationEntity affiliation(Long id, Long researcherId, Long unitId) {
        ResearcherAffiliationEntity affiliation = new ResearcherAffiliationEntity(
            researcherId,
            unitId,
            "Investigadora principal",
            AffiliationType.LEADER,
            LocalDate.now().minusYears(2),
            null,
            true
        );
        affiliation.setId(id);
        affiliation.setValidationStatus(ValidationStatus.VALIDATED);
        return affiliation;
    }

    private PublicationEntity publication(Long id, String title, String abstractText) {
        PublicationEntity publication = new PublicationEntity(
            title,
            abstractText,
            2025,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            "10.1234/demo",
            "Demo Journal",
            "https://example.test"
        );
        publication.setId(id);
        publication.setValidationStatus(ValidationStatus.VALIDATED);
        return publication;
    }

    private AiSuggestionResponse aiSuggestion(Long id) {
        return new AiSuggestionResponse(
            id,
            "PUBLICATION",
            200L,
            AiSuggestionType.PUBLIC_SUMMARY,
            AiSuggestionStatus.GENERATED,
            "{\"summary\":\"Texto\"}",
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

    private PlatformUserPrincipal principal(Long userId, Long researcherId, String role) {
        UserEntity user = new UserEntity("user@example.test", "Test User", "{noop}password", true, researcherId);
        user.setId(userId);
        user.setRoles(new LinkedHashSet<>(List.of(new RoleEntity(role, role, role))));
        return new PlatformUserPrincipal(user);
    }

    private static final class FixedLlmService implements LlmService {
        private final String provider;
        private final String model;
        private final String answer;
        private BusinessRuleException failure;

        private FixedLlmService(String provider, String model, String answer) {
            this.provider = provider;
            this.model = model;
            this.answer = answer;
        }

        @Override
        public LlmResponse answer(LlmPrompt prompt) {
            if (failure != null) {
                throw failure;
            }
            return new LlmResponse(answer, List.of());
        }

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public String model() {
            return model;
        }
    }
}
