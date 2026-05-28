package com.researchintelligence.platform.ai.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchintelligence.platform.ai.application.PublicSummaryGenerationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicSummaryControllerTest {

    @Test
    void exposesPublicSummaryGenerateEndpoint() throws Exception {
        PublicSummaryGenerationService service = mock(PublicSummaryGenerationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PublicSummaryController(service)).build();
        when(service.generate(any(PublicSummaryGenerateRequest.class))).thenReturn(new PublicSummaryGenerateResponse(
            "Resumen publico propuesto.",
            List.of(new PublicSummaryEvidenceResponse("publication:20", "Titulo validado", "Titulo")),
            900L,
            List.of(),
            "mock",
            "mock-llm"
        ));

        mockMvc.perform(post("/api/ai/public-summary/generate")
                .contentType("application/json")
                .content("""
                    {
                      "targetType": "PUBLICATION",
                      "targetId": 20,
                      "style": "STANDARD",
                      "audience": "PUBLIC"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary").value("Resumen publico propuesto."))
            .andExpect(jsonPath("$.createdSuggestionId").value(900))
            .andExpect(jsonPath("$.evidence[0].reference").value("publication:20"))
            .andExpect(jsonPath("$.provider").value("mock"))
            .andExpect(jsonPath("$.model").value("mock-llm"));

        verify(service).generate(any(PublicSummaryGenerateRequest.class));
    }
}
