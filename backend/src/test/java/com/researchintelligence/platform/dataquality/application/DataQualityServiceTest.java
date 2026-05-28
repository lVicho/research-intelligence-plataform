package com.researchintelligence.platform.dataquality.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.dataquality.api.DataQualityIssueResponse;
import com.researchintelligence.platform.dataquality.api.DataQualitySummaryResponse;
import com.researchintelligence.platform.dataquality.domain.DataQualityEntityType;
import com.researchintelligence.platform.dataquality.domain.DataQualityIssueType;
import com.researchintelligence.platform.dataquality.domain.DataQualitySeverity;
import com.researchintelligence.platform.dataquality.persistence.DataQualityIssueRow;
import com.researchintelligence.platform.dataquality.persistence.DataQualityRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class DataQualityServiceTest {

    @Test
    void buildsSummaryWithMissingCountersAsZero() {
        DataQualityRepository repository = mock(DataQualityRepository.class);
        DataQualityService service = new DataQualityService(repository);
        when(repository.countByIssueType()).thenReturn(Map.of(
            DataQualityIssueType.PUBLICATIONS_WITHOUT_DOI, 3L,
            DataQualityIssueType.RESEARCHERS_WITHOUT_ORCID, 5L
        ));

        DataQualitySummaryResponse summary = service.summary();

        assertEquals(3L, summary.publicationsWithoutDoi());
        assertEquals(5L, summary.researchersWithoutOrcid());
        assertEquals(0L, summary.duplicatePublicationCandidates());
        assertEquals(0L, summary.publicationsWithoutPublicSummary());
        assertEquals(0L, summary.publicationTitleCasingIssues());
        verify(repository).countByIssueType();
    }

    @Test
    void normalizesPageAndSizeAndMapsIssueRows() {
        DataQualityRepository repository = mock(DataQualityRepository.class);
        DataQualityService service = new DataQualityService(repository);
        DataQualityIssueRow issue = new DataQualityIssueRow(
            DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT,
            DataQualitySeverity.WARNING,
            DataQualityEntityType.PUBLICATION,
            77L,
            "Title",
            "Description",
            "Action",
            ValidationStatus.PENDING_VALIDATION
        );
        when(repository.search(
            DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT,
            DataQualitySeverity.WARNING,
            DataQualityEntityType.PUBLICATION,
            ValidationStatus.PENDING_VALIDATION,
            0,
            100
        )).thenReturn(new PageImpl<>(java.util.List.of(issue), PageRequest.of(0, 100), 1));

        PageResponse<DataQualityIssueResponse> page = service.issues(
            DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT,
            DataQualitySeverity.WARNING,
            DataQualityEntityType.PUBLICATION,
            ValidationStatus.PENDING_VALIDATION,
            -2,
            400
        );

        assertEquals(1, page.content().size());
        assertEquals(DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT, page.content().get(0).issueType());
        assertEquals(DataQualitySeverity.WARNING, page.content().get(0).severity());
        assertEquals(DataQualityEntityType.PUBLICATION, page.content().get(0).entityType());
        verify(repository).search(
            DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT,
            DataQualitySeverity.WARNING,
            DataQualityEntityType.PUBLICATION,
            ValidationStatus.PENDING_VALIDATION,
            0,
            100
        );
    }
}
