package com.researchintelligence.platform.portal.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.ai.api.CopilotAnswerRequest;
import com.researchintelligence.platform.ai.api.CopilotAnswerResponse;
import com.researchintelligence.platform.ai.application.CopilotService;
import com.researchintelligence.platform.ai.application.RetrievalMode;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderFiltersRequest;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderPublicationResponse;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderResearcherSummaryResponse;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderResultResponse;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderSearchRequest;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderSearchResponse;
import com.researchintelligence.platform.expertfinder.application.ExpertFinderService;
import com.researchintelligence.platform.portal.api.PortalContextAssistantRequest;
import com.researchintelligence.platform.portal.api.PortalContextAssistantResponse;
import com.researchintelligence.platform.portal.api.PortalContextAssistantScope;
import com.researchintelligence.platform.portal.api.PortalContextAssistantSearchRequest;
import com.researchintelligence.platform.publications.api.PublicationAuthorResponse;
import com.researchintelligence.platform.publications.api.PublicationResponse;
import com.researchintelligence.platform.publications.api.PublicationSummaryResponse;
import com.researchintelligence.platform.publications.api.RelatedPublicationsResponse;
import com.researchintelligence.platform.publications.api.TopicResponse;
import com.researchintelligence.platform.publications.application.PublicationService;
import com.researchintelligence.platform.publications.application.RelatedPublicationService;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.researchers.api.ResearcherAffiliationResponse;
import com.researchintelligence.platform.researchers.api.ResearcherResponse;
import com.researchintelligence.platform.researchers.application.ResearcherService;
import com.researchintelligence.platform.researchunits.api.ResearchUnitResponse;
import com.researchintelligence.platform.researchunits.application.ResearchUnitService;
import com.researchintelligence.platform.researchunits.domain.OrganizationScope;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortalContextAssistantServiceTest {

    @Mock
    private PublicationService publicationService;

    @Mock
    private ResearcherService researcherService;

    @Mock
    private ResearchUnitService researchUnitService;

    @Mock
    private RelatedPublicationService relatedPublicationService;

    @Mock
    private ExpertFinderService expertFinderService;

    @Mock
    private CopilotService copilotService;

    private PortalContextAssistantService service;

    @BeforeEach
    void setUp() {
        service = new PortalContextAssistantService(
            publicationService,
            researcherService,
            researchUnitService,
            relatedPublicationService,
            expertFinderService,
            copilotService
        );
        lenient().when(copilotService.answer(any())).thenAnswer(invocation -> {
            CopilotAnswerRequest request = invocation.getArgument(0);
            return new CopilotAnswerResponse(
                "Respuesta",
                "Respuesta",
                request.retrievedPublications(),
                List.of(),
                "mock",
                "mock-llm",
                List.of("La respuesta no contiene citas explícitas a publicaciones."),
                VisibilityScope.PUBLIC_VALIDATED.name(),
                true
            );
        });
    }

    @Test
    void researcherProfileContextPagesBeyondFirstDisplaySlice() {
        when(researcherService.findPortalVisibleValidatedById(7L)).thenReturn(researcher(7L, "Maya Chen"));
        when(researchUnitService.findPortalVisibleValidatedById(8L)).thenReturn(unit(8L, "Grupo de IA Clinica"));
        when(publicationService.searchPublicValidated(
            eq(0), eq(100), any(), any(), any(), any(), any(), any(), eq(7L), any(), eq("year"), eq("desc")
        )).thenReturn(page(List.of(summary(1L, "Primer resultado visible")), 101, false));
        when(publicationService.searchPublicValidated(
            eq(1), eq(100), any(), any(), any(), any(), any(), any(), eq(7L), any(), eq("year"), eq("desc")
        )).thenReturn(page(List.of(summary(101L, "Tema beyond cap")), 101, true));
        when(publicationService.findPublicValidatedById(1L)).thenReturn(publication(1L, "Primer resultado visible"));
        when(publicationService.findPublicValidatedById(101L)).thenReturn(publication(101L, "Tema beyond cap"));

        service.ask(new PortalContextAssistantRequest(
            PortalContextAssistantScope.RESEARCHER_PROFILE,
            7L,
            "Que trabajos hablan de beyond cap?",
            null,
            5
        ));

        ArgumentCaptor<CopilotAnswerRequest> captor = ArgumentCaptor.forClass(CopilotAnswerRequest.class);
        verify(copilotService).answer(captor.capture());
        assertTrue(captor.getValue().retrievedPublications().stream().anyMatch(publication -> publication.id().equals(101L)));
    }

    @Test
    void publicationSearchContextReconstructsFiltersServerSide() {
        when(publicationService.searchPublicValidated(
            eq(0),
            eq(100),
            eq("IA local"),
            eq(2020),
            eq(2026),
            eq(PublicationType.ARTICLE),
            eq(PublicationStatus.PUBLISHED),
            eq(8L),
            eq(7L),
            eq("IA clinica"),
            eq("year"),
            eq("desc")
        )).thenReturn(page(List.of(summary(50L, "IA local validada")), 1, true));
        when(publicationService.findPublicValidatedById(50L)).thenReturn(publication(50L, "IA local validada"));

        service.ask(new PortalContextAssistantRequest(
            PortalContextAssistantScope.PUBLICATION_SEARCH_RESULTS,
            null,
            "Que lineas aparecen?",
            new PortalContextAssistantSearchRequest(
                "IA local",
                "fields",
                2020,
                2026,
                PublicationType.ARTICLE,
                PublicationStatus.PUBLISHED,
                8L,
                7L,
                "IA clinica"
            ),
            5
        ));

        verify(publicationService).searchPublicValidated(
            eq(0),
            eq(100),
            eq("IA local"),
            eq(2020),
            eq(2026),
            eq(PublicationType.ARTICLE),
            eq(PublicationStatus.PUBLISHED),
            eq(8L),
            eq(7L),
            eq("IA clinica"),
            eq("year"),
            eq("desc")
        );
    }

    @Test
    void expertFinderContextUsesBackendExpertScope() {
        when(expertFinderService.search(any(ExpertFinderSearchRequest.class))).thenReturn(new ExpertFinderSearchResponse(
            List.of(new ExpertFinderResultResponse(
                new ExpertFinderResearcherSummaryResponse(7L, "Maya Chen", "Maya", null, 8L, "Grupo de IA Clinica"),
                0.9,
                "HIGH",
                List.of("IA clinica"),
                List.of(new ExpertFinderPublicationResponse(50L, "IA clinica local", 2026, "ARTICLE", null, "Demo", null, 0.9, List.of("IA clinica"))),
                List.of(),
                List.of("Alta afinidad."),
                "Maya aparece por publicaciones representativas.",
                List.of()
            )),
            List.of(),
            "deterministic",
            VisibilityScope.PUBLIC_VALIDATED.name(),
            true
        ));
        when(researchUnitService.findPortalVisibleValidatedById(8L)).thenReturn(unit(8L, "Grupo de IA Clinica"));
        when(publicationService.findPublicValidatedById(50L)).thenReturn(publication(50L, "IA clinica local"));

        service.ask(new PortalContextAssistantRequest(
            PortalContextAssistantScope.EXPERT_FINDER_RESULTS,
            null,
            "Quien es adecuado?",
            new PortalContextAssistantSearchRequest("IA clinica", "BALANCED", null, null, null, null, 8L, null, null),
            5
        ));

        ArgumentCaptor<ExpertFinderSearchRequest> captor = ArgumentCaptor.forClass(ExpertFinderSearchRequest.class);
        verify(expertFinderService).search(captor.capture());
        ExpertFinderFiltersRequest filters = captor.getValue().filters();
        assertEquals("IA clinica", captor.getValue().query());
        assertEquals(RetrievalMode.BALANCED, captor.getValue().mode());
        assertEquals(8L, filters.researchUnitId());
        assertEquals(true, filters.onlyValidated());
    }

    @Test
    void unitProfileContextIncludesPublicationLinkedUnits() {
        when(researchUnitService.findPortalVisibleValidatedById(8L)).thenReturn(unit(8L, "Departamento de Informatica Biomedica"));
        when(researchUnitService.findPortalVisibleValidatedById(12L)).thenReturn(unit(12L, "Unidad de Bioestadistica Clinica"));
        when(publicationService.searchPublicValidated(
            eq(0), eq(100), any(), any(), any(), any(), any(), eq(8L), any(), any(), eq("year"), eq("desc")
        )).thenReturn(page(List.of(summary(50L, "Trabajo conjunto")), 1, true));
        when(publicationService.findPublicValidatedById(50L)).thenReturn(publication(
            50L,
            "Trabajo conjunto",
            List.of(
                new PublicationAuthorResponse(1L, 7L, "Maya Chen", null, null, 1, true),
                new PublicationAuthorResponse(2L, 9L, "Luis Rojas", null, null, 2, false)
            )
        ));
        when(researcherService.findPortalVisibleValidatedById(7L)).thenReturn(researcher(7L, "Maya Chen", 8L, "Departamento de Informatica Biomedica"));
        when(researcherService.findPortalVisibleValidatedById(9L)).thenReturn(researcher(9L, "Luis Rojas", 12L, "Unidad de Bioestadistica Clinica"));

        service.ask(new PortalContextAssistantRequest(
            PortalContextAssistantScope.UNIT_PROFILE,
            8L,
            "Con que otras unidades trabaja este departamento?",
            null,
            5
        ));

        ArgumentCaptor<CopilotAnswerRequest> captor = ArgumentCaptor.forClass(CopilotAnswerRequest.class);
        verify(copilotService).answer(captor.capture());
        assertTrue(captor.getValue().retrievedPublications().getFirst().researchUnits().contains("Departamento de Informatica Biomedica"));
        assertTrue(captor.getValue().retrievedPublications().getFirst().researchUnits().contains("Unidad de Bioestadistica Clinica"));
        assertTrue(captor.getValue().question().contains("Unidad de Bioestadistica Clinica"));
    }

    @Test
    void providerFailureReturnsPublicWarning() {
        when(publicationService.findPublicValidatedById(10L)).thenReturn(publication(10L, "IA local"));
        when(relatedPublicationService.findRelated(10L, 5, null, false)).thenReturn(new RelatedPublicationsResponse(
            10L,
            5,
            0.35,
            false,
            List.of(),
            VisibilityScope.PUBLIC_VALIDATED.name(),
            true,
            List.of()
        ));
        when(researcherService.findPortalVisibleValidatedById(7L)).thenReturn(researcher(7L, "Maya Chen"));
        when(researchUnitService.findPortalVisibleValidatedById(8L)).thenReturn(unit(8L, "Grupo de IA Clinica"));
        doThrow(new BusinessRuleException("Ollama LLM request failed.")).when(copilotService).answer(any());

        PortalContextAssistantResponse response = service.ask(new PortalContextAssistantRequest(
            PortalContextAssistantScope.PUBLICATION_DETAIL,
            10L,
            "Que problema aborda?",
            null,
            5
        ));

        assertEquals("No se ha podido generar la respuesta con el proveedor de IA configurado.", response.answer());
        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("proveedor de IA local")));
    }

    private PageResponse<PublicationSummaryResponse> page(List<PublicationSummaryResponse> content, long total, boolean last) {
        return new PageResponse<>(content, 0, 100, total, (int) Math.ceil(total / 100.0), last);
    }

    private PublicationSummaryResponse summary(Long id, String title) {
        return new PublicationSummaryResponse(
            id,
            title,
            2026,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Demo",
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
            List.of("IA clinica")
        );
    }

    private PublicationResponse publication(Long id, String title) {
        return publication(
            id,
            title,
            List.of(new PublicationAuthorResponse(1L, 7L, "Maya Chen", null, null, 1, true))
        );
    }

    private PublicationResponse publication(Long id, String title, List<PublicationAuthorResponse> authors) {
        return new PublicationResponse(
            id,
            title,
            "Abstract " + title,
            "Resumen publico " + title,
            2026,
            null,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Demo",
            null,
            null,
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
            authors,
            List.of(new TopicResponse(1L, "IA clinica", "ia clinica")),
            null,
            null,
            null,
            null
        );
    }

    private ResearcherResponse researcher(Long id, String name) {
        return researcher(id, name, 8L, "Grupo de IA Clinica");
    }

    private ResearcherResponse researcher(Long id, String name, Long unitId, String unitName) {
        ResearcherAffiliationResponse affiliation = new ResearcherAffiliationResponse(
            80L,
            id,
            unitId,
            unitName,
            "Investigadora",
            null,
            null,
            null,
            true,
            true,
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
            null,
            null,
            null
        );
        return new ResearcherResponse(
            id,
            name,
            name,
            null,
            null,
            true,
            ValidationStatus.VALIDATED,
            null,
            null,
            null,
            null,
            null,
            false,
            false,
            false,
            List.of(affiliation),
            List.of(affiliation),
            List.of(),
            affiliation,
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            null
        );
    }

    private ResearchUnitResponse unit(Long id, String name) {
        return new ResearchUnitResponse(
            id,
            name,
            null,
            ResearchUnitType.RESEARCH_GROUP,
            null,
            "Spain",
            "Madrid",
            null,
            true,
            true,
            OrganizationScope.INTERNAL,
            "Unidad publica",
            null,
            null,
            null,
            null,
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
            null,
            null,
            null
        );
    }
}
