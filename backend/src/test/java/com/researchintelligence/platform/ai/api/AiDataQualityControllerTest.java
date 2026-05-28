package com.researchintelligence.platform.ai.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchintelligence.platform.ai.application.AiDataQualitySuggestionService;
import com.researchintelligence.platform.dataquality.api.DataQualityIssueResponse;
import com.researchintelligence.platform.dataquality.domain.DataQualityEntityType;
import com.researchintelligence.platform.dataquality.domain.DataQualityIssueType;
import com.researchintelligence.platform.dataquality.domain.DataQualitySeverity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiDataQualityControllerTest {

    @Test
    void exposesSuggestFixesEndpoint() throws Exception {
        AiDataQualitySuggestionService service = mock(AiDataQualitySuggestionService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiDataQualityController(service)).build();
        Map<String, Object> suggestedFix = new LinkedHashMap<>();
        suggestedFix.put("action", "draft_abstract");
        suggestedFix.put("requiresHumanReview", true);
        when(service.suggestFixes(any(DataQualityFixSuggestionRequest.class))).thenReturn(List.of(new DataQualityFixSuggestionResponse(
            new DataQualityIssueResponse(
                DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT,
                DataQualitySeverity.WARNING,
                DataQualityEntityType.PUBLICATION,
                10L,
                "Clinical AI",
                "Publication has no abstract text.",
                "Add abstract."
            ),
            suggestedFix,
            0.62,
            Map.of("source", "metadata"),
            900L
        )));

        mockMvc.perform(post("/api/ai/data-quality/suggest-fixes")
                .contentType("application/json")
                .content("""
                    {
                      "issueType": "PUBLICATIONS_WITHOUT_ABSTRACT",
                      "scope": "ADMIN_ALL",
                      "limit": 10
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].issue.issueType").value("PUBLICATIONS_WITHOUT_ABSTRACT"))
            .andExpect(jsonPath("$[0].suggestedFix.action").value("draft_abstract"))
            .andExpect(jsonPath("$[0].createdSuggestionId").value(900));

        verify(service).suggestFixes(any(DataQualityFixSuggestionRequest.class));
    }
}
