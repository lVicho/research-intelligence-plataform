package com.researchintelligence.platform.dataquality.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchintelligence.platform.dataquality.application.DataQualityService;
import com.researchintelligence.platform.dataquality.domain.DataQualityEntityType;
import com.researchintelligence.platform.dataquality.domain.DataQualityIssueType;
import com.researchintelligence.platform.dataquality.domain.DataQualitySeverity;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DataQualityControllerTest {

    @Test
    void exposesSummaryEndpoint() throws Exception {
        DataQualityService service = mock(DataQualityService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DataQualityController(service)).build();
        when(service.summary()).thenReturn(new DataQualitySummaryResponse(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14));

        mockMvc.perform(get("/api/data-quality/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publicationsWithoutDoi").value(1))
            .andExpect(jsonPath("$.publicationsWithoutAbstract").value(2))
            .andExpect(jsonPath("$.publicationsWithoutPublicSummary").value(3))
            .andExpect(jsonPath("$.publicationTitleCasingIssues").value(5))
            .andExpect(jsonPath("$.duplicatePublicationCandidates").value(14));

        verify(service).summary();
    }

    @Test
    void exposesIssuesEndpointWithFilters() throws Exception {
        DataQualityService service = mock(DataQualityService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DataQualityController(service)).build();
        when(service.issues(
            DataQualityIssueType.PUBLICATIONS_WITHOUT_DOI,
            DataQualitySeverity.WARNING,
            DataQualityEntityType.PUBLICATION,
            ValidationStatus.PENDING_VALIDATION,
            0,
            10
        )).thenReturn(new PageResponse<>(
            List.of(new DataQualityIssueResponse(
                DataQualityIssueType.PUBLICATIONS_WITHOUT_DOI,
                DataQualitySeverity.WARNING,
                DataQualityEntityType.PUBLICATION,
                100L,
                "Paper without DOI",
                "Publication has no DOI value.",
                "Add DOI."
            )),
            0,
            10,
            1,
            1,
            true
        ));

        mockMvc.perform(get("/api/data-quality/issues")
                .param("issueType", "PUBLICATIONS_WITHOUT_DOI")
                .param("severity", "WARNING")
                .param("entityType", "PUBLICATION")
                .param("validationStatus", "PENDING_VALIDATION")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].issueType").value("PUBLICATIONS_WITHOUT_DOI"))
            .andExpect(jsonPath("$.content[0].severity").value("WARNING"))
            .andExpect(jsonPath("$.content[0].entityType").value("PUBLICATION"))
            .andExpect(jsonPath("$.content[0].entityId").value(100));

        verify(service).issues(
            DataQualityIssueType.PUBLICATIONS_WITHOUT_DOI,
            DataQualitySeverity.WARNING,
            DataQualityEntityType.PUBLICATION,
            ValidationStatus.PENDING_VALIDATION,
            0,
            10
        );
    }
}
