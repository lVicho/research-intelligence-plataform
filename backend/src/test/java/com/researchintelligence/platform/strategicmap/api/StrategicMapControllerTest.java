package com.researchintelligence.platform.strategicmap.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchintelligence.platform.strategicmap.application.StrategicResearchMapService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StrategicMapControllerTest {

    @Test
    void exposesResearchLinesEndpointWithFilters() throws Exception {
        StrategicResearchMapService service = mock(StrategicResearchMapService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StrategicMapController(service)).build();
        when(service.researchLines(2024, 2026, 500L, true)).thenReturn(new StrategicResearchMapResponse(
            2024,
            2026,
            500L,
            true,
            "PUBLIC_VALIDATED",
            true,
            "hybrid_embedding_topic_connected_components",
            List.of(),
            List.of(new ResearchLineResponse(
                "line-1-demo",
                "Linea sobre IA clinica",
                "Linea identificada a partir de publicaciones validadas.",
                2,
                List.of(),
                List.of(),
                List.of(),
                List.of(new ResearchLinePublicationResponse(1L, "pub:1", "IA clinica", 2026, null, "Demo", List.of("IA clinica"), 0.9, 0.86)),
                "Actividad estable entre 2025 y 2026 (1 -> 1 publicaciones).",
                0.9,
                List.of()
            ))
        ));

        mockMvc.perform(get("/api/strategic-map/research-lines")
                .param("yearFrom", "2024")
                .param("yearTo", "2026")
                .param("researchUnitId", "500")
                .param("onlyValidated", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.researchLines[0].lineId").value("line-1-demo"))
            .andExpect(jsonPath("$.researchLines[0].representativePublications[0].citationKey").value("pub:1"));

        verify(service).researchLines(2024, 2026, 500L, true);
    }
}
