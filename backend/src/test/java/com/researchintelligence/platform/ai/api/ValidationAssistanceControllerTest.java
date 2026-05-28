package com.researchintelligence.platform.ai.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchintelligence.platform.ai.application.ValidationAssistanceReviewService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ValidationAssistanceControllerTest {

    @Test
    void exposesValidationReviewEndpoint() throws Exception {
        ValidationAssistanceReviewService service = mock(ValidationAssistanceReviewService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ValidationAssistanceController(service)).build();
        when(service.review(any(ValidationAssistanceReviewRequest.class))).thenReturn(new ValidationAssistanceReviewResponse(
            ValidationAssistanceRecommendation.REQUEST_CHANGES,
            0.78,
            List.of(new ValidationAssistanceCheckResponse(
                "MISSING_TOPICS",
                ValidationAssistanceSeverity.BLOCKER,
                "Faltan temas",
                "La publicacion no tiene temas asociados.",
                "Solicite agregar temas validados.",
                Map.of("topicCount", 0)
            )),
            "Solicitar cambios: Faltan temas.",
            901L
        ));

        mockMvc.perform(post("/api/ai/validation/review")
                .contentType("application/json")
                .content("""
                    {
                      "entityType": "PUBLICATION",
                      "entityId": 10
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recommendation").value("REQUEST_CHANGES"))
            .andExpect(jsonPath("$.confidence").value(0.78))
            .andExpect(jsonPath("$.checks[0].code").value("MISSING_TOPICS"))
            .andExpect(jsonPath("$.createdSuggestionId").value(901));

        verify(service).review(any(ValidationAssistanceReviewRequest.class));
    }
}
