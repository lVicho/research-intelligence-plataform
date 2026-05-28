package com.researchintelligence.platform.ai.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchintelligence.platform.ai.application.TopicRecommendationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TopicRecommendationControllerTest {

    @Test
    void exposesTopicRecommendationEndpoint() throws Exception {
        TopicRecommendationService service = mock(TopicRecommendationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TopicRecommendationController(service)).build();
        when(service.recommend(any(TopicRecommendationRequest.class))).thenReturn(new TopicRecommendationResponse(
            500L,
            List.of(new TopicRecommendationTopicResponse(
                "Clinical AI",
                10L,
                0.82,
                "Found in 2 similar validated publications.",
                List.of(1L, 2L)
            )),
            List.of()
        ));

        mockMvc.perform(post("/api/ai/topics/recommend")
                .contentType("application/json")
                .content("""
                    {
                      "title": "Clinical AI decision support",
                      "abstractText": "A model for clinical risk prediction in hospitals.",
                      "keywords": ["clinical ai"],
                      "maxTopics": 8
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aiSuggestionId").value(500))
            .andExpect(jsonPath("$.suggestedTopics[0].label").value("Clinical AI"))
            .andExpect(jsonPath("$.suggestedTopics[0].existingTopicId").value(10))
            .andExpect(jsonPath("$.suggestedTopics[0].evidencePublicationIds[0]").value(1));

        verify(service).recommend(any(TopicRecommendationRequest.class));
    }
}
