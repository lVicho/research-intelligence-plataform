package com.researchintelligence.platform.ai.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchintelligence.platform.ai.application.AiSuggestionService;
import com.researchintelligence.platform.ai.domain.AiSuggestionStatus;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.shared.api.PageResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiSuggestionControllerTest {

    private static final Instant NOW = Instant.parse("2026-05-25T10:15:00Z");

    @Test
    void listsSuggestionsWithFilters() throws Exception {
        AiSuggestionService service = mock(AiSuggestionService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiSuggestionController(service)).build();
        when(service.findSuggestions(
            "PUBLICATION",
            10L,
            AiSuggestionType.PUBLIC_SUMMARY,
            AiSuggestionStatus.GENERATED,
            0,
            10
        )).thenReturn(new PageResponse<>(
            List.of(response(20L, AiSuggestionStatus.GENERATED)),
            0,
            10,
            1,
            1,
            true
        ));

        mockMvc.perform(get("/api/ai-suggestions")
                .param("targetType", "PUBLICATION")
                .param("targetId", "10")
                .param("suggestionType", "PUBLIC_SUMMARY")
                .param("status", "GENERATED")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(20))
            .andExpect(jsonPath("$.content[0].suggestionType").value("PUBLIC_SUMMARY"))
            .andExpect(jsonPath("$.content[0].status").value("GENERATED"));

        verify(service).findSuggestions(
            "PUBLICATION",
            10L,
            AiSuggestionType.PUBLIC_SUMMARY,
            AiSuggestionStatus.GENERATED,
            0,
            10
        );
    }

    @Test
    void exposesAcceptRejectAndEditTransitions() throws Exception {
        AiSuggestionService service = mock(AiSuggestionService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiSuggestionController(service)).build();
        when(service.accept(eq(20L), any(AiSuggestionReviewRequest.class)))
            .thenReturn(response(20L, AiSuggestionStatus.ACCEPTED));
        when(service.reject(eq(21L), any(AiSuggestionReviewRequest.class)))
            .thenReturn(response(21L, AiSuggestionStatus.REJECTED));
        when(service.editAndAccept(eq(22L), any(AiSuggestionEditAndAcceptRequest.class)))
            .thenReturn(response(22L, AiSuggestionStatus.EDITED));

        mockMvc.perform(post("/api/ai-suggestions/20/accept")
                .contentType("application/json")
                .content("{\"reviewComment\":\"Ok\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(post("/api/ai-suggestions/21/reject")
                .contentType("application/json")
                .content("{\"reviewComment\":\"No\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(post("/api/ai-suggestions/22/edit-and-accept")
                .contentType("application/json")
                .content("{\"proposedDataJson\":\"{\\\"summary\\\":\\\"Texto revisado\\\"}\",\"reviewComment\":\"Editado\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("EDITED"));

        verify(service).accept(eq(20L), any(AiSuggestionReviewRequest.class));
        verify(service).reject(eq(21L), any(AiSuggestionReviewRequest.class));
        verify(service).editAndAccept(eq(22L), any(AiSuggestionEditAndAcceptRequest.class));
    }

    @Test
    void exposesSuggestionDetail() throws Exception {
        AiSuggestionService service = mock(AiSuggestionService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiSuggestionController(service)).build();
        when(service.findById(20L)).thenReturn(response(20L, AiSuggestionStatus.GENERATED));

        mockMvc.perform(get("/api/ai-suggestions/20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(20))
            .andExpect(jsonPath("$.targetType").value("PUBLICATION"));

        verify(service).findById(20L);
    }

    private AiSuggestionResponse response(Long id, AiSuggestionStatus status) {
        return new AiSuggestionResponse(
            id,
            "PUBLICATION",
            10L,
            AiSuggestionType.PUBLIC_SUMMARY,
            status,
            "{\"summary\":\"Texto propuesto\"}",
            "Suggested from evidence.",
            null,
            "mock",
            "mock-llm",
            NOW,
            1L,
            status == AiSuggestionStatus.GENERATED ? null : NOW,
            status == AiSuggestionStatus.GENERATED ? null : 1L,
            null
        );
    }
}
