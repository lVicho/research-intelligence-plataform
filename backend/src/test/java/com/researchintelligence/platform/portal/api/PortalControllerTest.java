package com.researchintelligence.platform.portal.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchintelligence.platform.portal.application.PortalDemoQueryService;
import com.researchintelligence.platform.portal.application.PortalService;
import com.researchintelligence.platform.portal.application.PublicationExplanationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PortalControllerTest {

    @Test
    void publicationExplanationEndpointReturnsStructuredResponse() throws Exception {
        PortalService portalService = mock(PortalService.class);
        PortalDemoQueryService demoQueryService = mock(PortalDemoQueryService.class);
        PublicationExplanationService explanationService = mock(PublicationExplanationService.class);
        when(explanationService.explain(eq(10L), org.mockito.ArgumentMatchers.any(PublicationExplanationRequest.class)))
            .thenReturn(new PublicationExplanationResponse(
                "IA local para triaje hospitalario",
                "Resumen publico en espanol.",
                "Problema descrito desde evidencia validada.",
                "Relevancia sin sobreafirmar impacto.",
                "Enfoque limitado al resumen y metadatos.",
                List.of(new PortalPublicationExplanationReferenceResponse(50L, "IA local")),
                List.of(new PortalPublicationExplanationReferenceResponse(7L, "Maya Chen")),
                List.of(new PortalPublicationExplanationReferenceResponse(3L, "Grupo de IA Clinica")),
                List.of(new PortalPublicationExplanationReferenceResponse(11L, "Privacidad en IA clinica")),
                List.of(),
                "mock",
                "mock-llm"
            ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new PortalController(portalService, demoQueryService, explanationService)
        ).build();

        mockMvc.perform(post("/api/portal/publications/10/explain")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"style\":\"PLAIN\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("IA local para triaje hospitalario"))
            .andExpect(jsonPath("$.plainSummary").value("Resumen publico en espanol."))
            .andExpect(jsonPath("$.relatedTopics[0].label").value("IA local"))
            .andExpect(jsonPath("$.relatedResearchers[0].label").value("Maya Chen"))
            .andExpect(jsonPath("$.relatedUnits[0].label").value("Grupo de IA Clinica"))
            .andExpect(jsonPath("$.relatedPublications[0].label").value("Privacidad en IA clinica"))
            .andExpect(jsonPath("$.provider").value("mock"))
            .andExpect(jsonPath("$.model").value("mock-llm"));
    }

    @Test
    void demoQueriesEndpointDelegatesToService() throws Exception {
        PortalService portalService = mock(PortalService.class);
        PortalDemoQueryService demoQueryService = mock(PortalDemoQueryService.class);
        PublicationExplanationService explanationService = mock(PublicationExplanationService.class);
        when(demoQueryService.generate(PortalDemoQueryContext.PUBLICATIONS, 3, true))
            .thenReturn(List.of(new PortalDemoQueryResponse(
                "IA local en hospitales",
                PortalDemoQueryContext.PUBLICATIONS,
                "Frase anclada en evidencia actual del dataset.",
                List.of("PUBLICATION", "TOPIC"),
                List.of("pub:10", "topic:7"),
                new PortalDemoQueryFreshnessResponse(Instant.parse("2026-05-25T10:15:30Z"), Instant.parse("2026-01-01T08:00:00Z"), 2),
                Instant.parse("2026-05-26T09:00:00Z")
            )));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new PortalController(portalService, demoQueryService, explanationService)
        ).build();

        mockMvc.perform(get("/api/portal/demo-queries")
                .param("context", "PUBLICATIONS")
                .param("limit", "3")
                .param("onlyValidated", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].query").value("IA local en hospitales"))
            .andExpect(jsonPath("$[0].context").value("PUBLICATIONS"))
            .andExpect(jsonPath("$[0].expectedEntityTypes[0]").value("PUBLICATION"))
            .andExpect(jsonPath("$[0].evidenceIds[0]").value("pub:10"));
    }
}
