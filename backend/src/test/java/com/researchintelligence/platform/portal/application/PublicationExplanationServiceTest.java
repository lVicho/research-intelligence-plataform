package com.researchintelligence.platform.portal.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.application.LlmPrompt;
import com.researchintelligence.platform.ai.application.LlmResponse;
import com.researchintelligence.platform.ai.application.LlmService;
import com.researchintelligence.platform.portal.api.PublicationExplanationRequest;
import com.researchintelligence.platform.portal.api.PublicationExplanationResponse;
import com.researchintelligence.platform.portal.api.PublicationExplanationStyle;
import com.researchintelligence.platform.publications.api.PublicationSummaryResponse;
import com.researchintelligence.platform.publications.api.RelatedPublicationResponse;
import com.researchintelligence.platform.publications.api.RelatedPublicationsResponse;
import com.researchintelligence.platform.publications.application.RelatedPublicationService;
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
import com.researchintelligence.platform.researchunits.domain.OrganizationScope;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PublicationExplanationServiceTest {

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
    private RelatedPublicationService relatedPublicationService;

    private FixedLlmService llmService;
    private PublicationExplanationService service;

    @BeforeEach
    void setUp() {
        llmService = new FixedLlmService("mock", "mock-llm", null);
        service = service(llmService);
        lenient().when(authorRepository.findByPublicationIdOrderByAuthorOrderAsc(any())).thenReturn(List.of());
        lenient().when(publicationTopicRepository.findByPublicationId(any())).thenReturn(List.of());
        lenient().when(topicRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(researcherRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(affiliationRepository.findByResearcherIdIn(any())).thenReturn(List.of());
        lenient().when(researchUnitRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(relatedPublicationService.findRelated(any(), eq(5), eq(0.25), eq(false)))
            .thenReturn(new RelatedPublicationsResponse(10L, 5, 0.25, true, List.of(), "PUBLIC_VALIDATED", true, List.of()));
    }

    @Test
    void explanationForValidatedPublicationUsesOnlyPublicValidatedEvidence() {
        PublicationEntity publication = publication(10L, "IA local para triaje hospitalario", "Estudio sobre modelos locales para apoyar el triaje en hospitales.");
        ResearcherEntity researcher = researcher(7L, "Maya Chen");
        ResearchUnitEntity unit = unit(3L, "Grupo de IA Clinica");
        TopicEntity topic = topic(50L, "IA local");
        when(publicationRepository.findById(10L)).thenReturn(Optional.of(publication));
        when(publicationTopicRepository.findByPublicationId(10L)).thenReturn(List.of(new PublicationTopicEntity(10L, 50L)));
        when(topicRepository.findAllById(List.of(50L))).thenReturn(List.of(topic));
        when(authorRepository.findByPublicationIdOrderByAuthorOrderAsc(10L))
            .thenReturn(List.of(new PublicationAuthorEntity(10L, 7L, null, null, 1, false)));
        when(researcherRepository.findAllById(List.of(7L))).thenReturn(List.of(researcher));
        when(affiliationRepository.findByResearcherIdIn(List.of(7L))).thenReturn(List.of(affiliation(7L, 3L)));
        when(researchUnitRepository.findAllById(List.of(3L))).thenReturn(List.of(unit));
        when(relatedPublicationService.findRelated(10L, 5, 0.25, false)).thenReturn(new RelatedPublicationsResponse(
            10L,
            5,
            0.25,
            true,
            List.of(),
            "PUBLIC_VALIDATED",
            true,
            List.of(relatedPublication(11L, "Privacidad en IA clinica"))
        ));

        PublicationExplanationResponse response = service.explain(10L, new PublicationExplanationRequest(PublicationExplanationStyle.PLAIN, null));

        assertEquals("IA local para triaje hospitalario", response.title());
        assertTrue(response.plainSummary().contains("El resumen validado"));
        assertEquals(List.of("IA local"), response.relatedTopics().stream().map(item -> item.label()).toList());
        assertEquals(List.of("Maya Chen"), response.relatedResearchers().stream().map(item -> item.label()).toList());
        assertEquals(List.of("Grupo de IA Clinica"), response.relatedUnits().stream().map(item -> item.label()).toList());
        assertEquals(List.of("Privacidad en IA clinica"), response.relatedPublications().stream().map(item -> item.label()).toList());
        assertEquals("mock", response.provider());
        assertEquals("mock-llm", response.model());
    }

    @Test
    void nonValidatedPublicationIsNotExplainablePublicly() {
        PublicationEntity publication = publication(10L, "Borrador no publico", "Resumen interno.");
        publication.setValidationStatus(ValidationStatus.PENDING_VALIDATION);
        when(publicationRepository.findById(10L)).thenReturn(Optional.of(publication));

        assertThrows(ResourceNotFoundException.class, () -> service.explain(10L, new PublicationExplanationRequest(PublicationExplanationStyle.PLAIN, "Spanish")));
    }

    @Test
    void weakContextReturnsWarnings() {
        PublicationEntity publication = publication(10L, "Ficha sin resumen", null);
        when(publicationRepository.findById(10L)).thenReturn(Optional.of(publication));

        PublicationExplanationResponse response = service.explain(10L, new PublicationExplanationRequest(PublicationExplanationStyle.PLAIN, null));

        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("no tiene resumen validado")));
        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("contexto publico validado es debil")));
        assertTrue(response.approach().contains("No hay informacion metodologica separada"));
    }

    @Test
    void ollamaUnavailableIsHandledGracefully() {
        llmService = new FixedLlmService("ollama", "qwen2.5:14b", null);
        llmService.failure = new BusinessRuleException("Ollama LLM request failed at http://localhost:11434/api/generate");
        service = service(llmService);
        PublicationEntity publication = publication(10L, "IA local para triaje hospitalario", "Estudio sobre modelos locales para apoyar el triaje en hospitales.");
        when(publicationRepository.findById(10L)).thenReturn(Optional.of(publication));

        PublicationExplanationResponse response = service.explain(10L, new PublicationExplanationRequest(PublicationExplanationStyle.TECHNICAL, null));

        assertTrue(response.plainSummary().contains("IA local para triaje hospitalario"));
        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("Ollama")));
        assertEquals("ollama", response.provider());
        assertEquals("qwen2.5:14b", response.model());
    }

    @Test
    void spanishOutputStructureIsReturned() {
        PublicationEntity publication = publication(10L, "Panteras y conservacion", "Analiza seguimiento de habitats y biodiversidad en un caso de conservacion.");
        when(publicationRepository.findById(10L)).thenReturn(Optional.of(publication));

        PublicationExplanationResponse response = service.explain(10L, new PublicationExplanationRequest(null, null));

        assertFalse(response.plainSummary().isBlank());
        assertFalse(response.problemAddressed().isBlank());
        assertFalse(response.whyItMatters().isBlank());
        assertFalse(response.approach().isBlank());
        assertTrue(response.plainSummary().contains("publicacion validada"));
        assertTrue(response.whyItMatters().contains("evidencia publica"));
    }

    private PublicationExplanationService service(LlmService llmService) {
        return new PublicationExplanationService(
            publicationRepository,
            authorRepository,
            publicationTopicRepository,
            topicRepository,
            researcherRepository,
            affiliationRepository,
            researchUnitRepository,
            relatedPublicationService,
            llmService,
            new ObjectMapper()
        );
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

    private RelatedPublicationResponse relatedPublication(Long id, String title) {
        return new RelatedPublicationResponse(
            new PublicationSummaryResponse(
                id,
                title,
                2024,
                PublicationType.ARTICLE,
                PublicationStatus.PUBLISHED,
                null,
                "Demo Journal",
                null,
                null,
                null,
                null,
                "es",
                ValidationStatus.VALIDATED,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                null,
                List.of()
            ),
            0.8,
            null,
            0.8,
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            null
        );
    }

    private ResearcherEntity researcher(Long id, String name) {
        ResearcherEntity researcher = new ResearcherEntity(name, name, name.toLowerCase().replace(" ", ".") + "@demo.local", null, true);
        researcher.setId(id);
        researcher.setValidationStatus(ValidationStatus.VALIDATED);
        return researcher;
    }

    private ResearcherAffiliationEntity affiliation(Long researcherId, Long unitId) {
        ResearcherAffiliationEntity affiliation = new ResearcherAffiliationEntity(
            researcherId,
            unitId,
            "Investigadora",
            AffiliationType.MEMBER,
            LocalDate.now().minusYears(1),
            null,
            true
        );
        affiliation.setValidationStatus(ValidationStatus.VALIDATED);
        return affiliation;
    }

    private ResearchUnitEntity unit(Long id, String name) {
        ResearchUnitEntity unit = new ResearchUnitEntity(name, null, ResearchUnitType.RESEARCH_GROUP, null, "Espana", "Madrid", null, true);
        unit.setId(id);
        unit.setValidationStatus(ValidationStatus.VALIDATED);
        unit.setOrganizationScope(OrganizationScope.INTERNAL);
        unit.setVisibleInPortal(true);
        return unit;
    }

    private TopicEntity topic(Long id, String name) {
        TopicEntity topic = new TopicEntity(name, name.toLowerCase());
        ReflectionTestUtils.setField(topic, "id", id);
        return topic;
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
