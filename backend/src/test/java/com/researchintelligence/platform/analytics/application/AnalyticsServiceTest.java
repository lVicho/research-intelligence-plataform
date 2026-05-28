package com.researchintelligence.platform.analytics.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.dataquality.api.DataQualitySummaryResponse;
import com.researchintelligence.platform.dataquality.application.DataQualityService;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private ResearchUnitRepository researchUnitRepository;

    @Mock
    private ResearcherRepository researcherRepository;

    @Mock
    private ResearcherAffiliationRepository affiliationRepository;

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private PublicationTopicRepository publicationTopicRepository;

    @Mock
    private EventParticipationRepository eventParticipationRepository;

    @Mock
    private DataQualityService dataQualityService;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(
            researchUnitRepository,
            researcherRepository,
            affiliationRepository,
            publicationRepository,
            topicRepository,
            publicationTopicRepository,
            eventParticipationRepository,
            dataQualityService
        );
    }

    @Test
    void dashboardAnalyticsDefaultsToValidatedData() {
        PublicationEntity validated = publication(1L, "Validated analytics result", ValidationStatus.VALIDATED);
        stubAnalytics(ValidationStatus.VALIDATED, List.of(validated), 3L, 4L, 2L, 5L);

        var response = service.summary(null, null);

        assertEquals(3L, response.totalResearchUnits());
        assertEquals(4L, response.totalResearchers());
        assertEquals(2L, response.activeResearchers());
        assertEquals(5L, response.totalPublications());
        assertEquals(List.of("Validated analytics result"), response.recentPublications().stream().map(publication -> publication.title()).toList());
        verify(publicationRepository).findRecentByValidationStatus(eq(ValidationStatus.VALIDATED), any(Pageable.class));
    }

    @Test
    void adminCanFilterDashboardAnalyticsByPendingValidation() {
        PublicationEntity pending = publication(2L, "Pending analytics result", ValidationStatus.PENDING_VALIDATION);
        stubAnalytics(ValidationStatus.PENDING_VALIDATION, List.of(pending), 1L, 1L, 0L, 1L);

        var response = service.summary(ValidationStatus.PENDING_VALIDATION, null);

        assertEquals(1L, response.totalPublications());
        assertEquals(List.of("Pending analytics result"), response.recentPublications().stream().map(publication -> publication.title()).toList());
        verify(publicationRepository).findRecentByValidationStatus(eq(ValidationStatus.PENDING_VALIDATION), any(Pageable.class));
    }

    @Test
    void institutionalOverviewIncludesExpectedAggregations() {
        when(publicationRepository.countByValidationStatus(ValidationStatus.VALIDATED)).thenReturn(8L);
        when(researcherRepository.countByValidationStatus(ValidationStatus.VALIDATED)).thenReturn(5L);
        when(researchUnitRepository.countByValidationStatus(ValidationStatus.VALIDATED)).thenReturn(4L);
        when(publicationRepository.countPublicationsByYear(ValidationStatus.VALIDATED)).thenReturn(rows(new Object[] {2026, 8L}));
        when(publicationRepository.countPublicationsByType(ValidationStatus.VALIDATED)).thenReturn(rows(new Object[] {"ARTICLE", 8L}));
        when(publicationRepository.countPublicationsByStatus(ValidationStatus.VALIDATED)).thenReturn(rows(new Object[] {"PUBLISHED", 8L}));
        when(eventParticipationRepository.countActivitiesByValidationStatus()).thenReturn(rows(new Object[] {"VALIDATED", 3L}));
        when(publicationRepository.countPublicationsByResearchUnit(ValidationStatus.VALIDATED))
            .thenReturn(rows(new Object[] {10L, "Unit A", 6L}));
        when(publicationRepository.countPublicationsByTopic(ValidationStatus.VALIDATED))
            .thenReturn(rows(new Object[] {20L, "AI", 4L}));
        when(affiliationRepository.countActiveResearchersByResearchUnit(eq(ValidationStatus.VALIDATED), any()))
            .thenReturn(rows(new Object[] {10L, "Unit A", 3L}));
        when(publicationRepository.findTopTopicsByPublicationCount(eq(ValidationStatus.VALIDATED), any(Pageable.class)))
            .thenReturn(rows(new Object[] {20L, "AI", 4L}));
        when(publicationRepository.findMaxPublicationYear(ValidationStatus.VALIDATED)).thenReturn(2026);
        when(publicationRepository.countPublicationsByTopicInYearRange(ValidationStatus.VALIDATED, 2024, 2026))
            .thenReturn(rows(new Object[] {20L, "AI", 4L}));
        when(publicationRepository.countPublicationsByTopicInYearRange(ValidationStatus.VALIDATED, 2021, 2023))
            .thenReturn(rows(new Object[] {20L, "AI", 1L}));
        when(publicationRepository.findCollaborationPairs(eq(ValidationStatus.VALIDATED.name()), any(Pageable.class)))
            .thenReturn(rows(new Object[] {10L, "Unit A", 11L, "Unit B", 2L}));
        when(publicationRepository.countCrossUnitCollaborations(ValidationStatus.VALIDATED.name())).thenReturn(2L);
        when(dataQualityService.summary()).thenReturn(emptyDataQualitySummary());

        var response = service.institutionalOverview(null, null);

        assertEquals(8L, response.totalValidatedPublications());
        assertEquals(5L, response.totalResearchers());
        assertEquals(4L, response.totalResearchUnits());
        assertEquals(1, response.emergingTopics().size());
        assertEquals("AI", response.emergingTopics().getFirst().topicName());
        assertEquals(3L, response.emergingTopics().getFirst().delta());
        assertEquals(2L, response.crossUnitCollaborations());
        assertEquals(1, response.collaborationPairs().size());
        assertEquals("Unit A", response.collaborationPairs().getFirst().researchUnitAName());
    }

    @Test
    void topicTrendsCalculatesEmergingTopicsFromRecentAndPreviousWindows() {
        when(publicationRepository.findTopTopicsByPublicationCount(eq((ValidationStatus) null), any(Pageable.class)))
            .thenReturn(rows(new Object[] {30L, "Graph AI", 7L}));
        when(publicationRepository.findMaxPublicationYear((ValidationStatus) null)).thenReturn(2025);
        when(publicationRepository.countPublicationsByTopicInYearRange(null, 2023, 2025))
            .thenReturn(rows(
                new Object[] {30L, "Graph AI", 4L},
                new Object[] {31L, "Health NLP", 2L}
            ));
        when(publicationRepository.countPublicationsByTopicInYearRange(null, 2020, 2022))
            .thenReturn(rows(
                new Object[] {30L, "Graph AI", 1L},
                new Object[] {32L, "Legacy Topic", 3L}
            ));

        var response = service.topicTrends(null, true, 3, 10);

        assertEquals(2025, response.latestPublicationYear());
        assertEquals(2023, response.recentWindowStartYear());
        assertEquals(2020, response.previousWindowStartYear());
        assertEquals(2022, response.previousWindowEndYear());
        assertEquals(2, response.emergingTopics().size());
        assertEquals("Graph AI", response.emergingTopics().getFirst().topicName());
        assertEquals(3L, response.emergingTopics().getFirst().delta());
        assertEquals(3.0, response.emergingTopics().getFirst().growthRate());
        verify(publicationRepository).findMaxPublicationYear((ValidationStatus) null);
    }

    @Test
    void collaborationsCanIncludeNonValidatedDataForAdminRequests() {
        when(publicationRepository.findCollaborationPairs(eq((String) null), any(Pageable.class)))
            .thenReturn(rows(new Object[] {1L, "Unit X", 2L, "Unit Y", 5L}));
        when(publicationRepository.countCrossUnitCollaborations((String) null)).thenReturn(7L);

        var response = service.collaborations(null, true, 25);

        assertEquals(7L, response.crossUnitCollaborations());
        assertEquals(1, response.collaborationPairs().size());
        assertEquals("Unit X", response.collaborationPairs().getFirst().researchUnitAName());
        assertEquals(5L, response.collaborationPairs().getFirst().sharedPublicationCount());
    }

    private void stubAnalytics(
        ValidationStatus validationStatus,
        List<PublicationEntity> recentPublications,
        long totalUnits,
        long totalResearchers,
        long activeResearchers,
        long totalPublications
    ) {
        when(researchUnitRepository.countByValidationStatus(validationStatus)).thenReturn(totalUnits);
        when(researcherRepository.countByValidationStatus(validationStatus)).thenReturn(totalResearchers);
        when(researcherRepository.countByActiveTrueAndValidationStatus(validationStatus)).thenReturn(activeResearchers);
        when(publicationRepository.countByValidationStatus(validationStatus)).thenReturn(totalPublications);
        when(publicationRepository.findRecentByValidationStatus(eq(validationStatus), any(Pageable.class))).thenReturn(recentPublications);
        when(publicationRepository.countPublicationsByYear(validationStatus)).thenReturn(List.of());
        when(publicationRepository.countPublicationsByType(validationStatus)).thenReturn(List.of());
        when(publicationRepository.countPublicationsByStatus(validationStatus)).thenReturn(List.of());
        when(publicationRepository.countPublicationsByResearchUnit(validationStatus)).thenReturn(List.of());
        when(publicationRepository.findTopResearchersByPublicationCount(eq(validationStatus), any(Pageable.class))).thenReturn(List.of());
        when(publicationRepository.findTopTopicsByPublicationCount(eq(validationStatus), any(Pageable.class))).thenReturn(List.of());
        when(affiliationRepository.countCurrentResearchersByResearchUnitType(eq(validationStatus), any())).thenReturn(List.of());
        when(publicationTopicRepository.findByPublicationIdIn(any())).thenReturn(List.of());
        when(topicRepository.findAllById(any())).thenReturn(List.of());
    }

    private PublicationEntity publication(Long id, String title, ValidationStatus validationStatus) {
        PublicationEntity publication = new PublicationEntity(
            title,
            "Abstract",
            2026,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Demo",
            null
        );
        publication.setId(id);
        publication.setValidationStatus(validationStatus);
        return publication;
    }

    private DataQualitySummaryResponse emptyDataQualitySummary() {
        return new DataQualitySummaryResponse(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private List<Object[]> rows(Object[]... rows) {
        return List.of(rows);
    }
}
