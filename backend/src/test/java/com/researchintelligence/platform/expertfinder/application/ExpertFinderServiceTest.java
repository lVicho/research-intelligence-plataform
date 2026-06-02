package com.researchintelligence.platform.expertfinder.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.ai.application.AiProperties;
import com.researchintelligence.platform.ai.application.EmbeddingResponse;
import com.researchintelligence.platform.ai.application.EmbeddingService;
import com.researchintelligence.platform.ai.application.RetrievalMode;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingRepository;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingSearchRow;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderFiltersRequest;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderResultResponse;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderSearchRequest;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderSearchResponse;
import com.researchintelligence.platform.expertfinder.persistence.ExpertEventParticipationEvidenceRow;
import com.researchintelligence.platform.expertfinder.persistence.ExpertFinderEvidenceRepository;
import com.researchintelligence.platform.expertfinder.persistence.ExpertPublicationEvidenceRow;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpertFinderServiceTest {

    @Mock
    private ExpertFinderEvidenceRepository evidenceRepository;

    @Mock
    private PublicationEmbeddingRepository embeddingRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VisibilityContext visibilityContext;

    private AiProperties properties;
    private ExpertFinderService service;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEmbeddingDimension(3);
        service = new ExpertFinderService(evidenceRepository, embeddingRepository, embeddingService, properties, visibilityContext);

        lenient().when(visibilityContext.currentRoles()).thenReturn(Set.of());
        lenient().when(embeddingService.provider()).thenReturn("ollama");
        lenient().when(embeddingService.model()).thenReturn("bge-m3");
        lenient().when(embeddingService.embed(anyString())).thenReturn(new EmbeddingResponse(List.of(0.1, 0.2, 0.3), List.of()));
        lenient().when(evidenceRepository.findEventEvidence(null, true)).thenReturn(List.of());
    }

    @Test
    void hospitalAiQueryRanksClinicalAiResearchers() {
        when(evidenceRepository.findPublicationEvidence(null, null, null, true)).thenReturn(List.of(
            publicationRow(1L, "Maya Chen", 101L, "Clinical AI triage in hospitals", "Machine learning support for hospital care.", 2025, "Clinical AI"),
            publicationRow(2L, "Leo Rivera", 201L, "Panther corridors in fragmented landscapes", "Large feline conservation ecology.", 2025, "Panther Conservation")
        ));
        when(embeddingRepository.hasEmbeddings("ollama", "bge-m3", 3)).thenReturn(true);
        when(embeddingRepository.searchNearest(
            anyString(), eq("ollama"), eq("bge-m3"), eq(3), eq(125), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)
        )).thenReturn(List.of(
            new PublicationEmbeddingSearchRow(101L, 0.90),
            new PublicationEmbeddingSearchRow(201L, 0.18)
        ));

        ExpertFinderSearchResponse response = service.search(request("hospital AI"));

        assertEquals(1L, response.results().getFirst().researcher().id());
        assertTrue(response.results().getFirst().score() > response.results().get(1).score());
        assertTrue(response.results().getFirst().matchedTopics().contains("Clinical AI"));
    }

    @Test
    void pantherQueryRanksConservationResearchers() {
        when(evidenceRepository.findPublicationEvidence(null, null, null, true)).thenReturn(List.of(
            publicationRow(1L, "Maya Chen", 101L, "Clinical AI triage in hospitals", "Machine learning support for hospital care.", 2025, "Clinical AI"),
            publicationRow(2L, "Leo Rivera", 201L, "Panther corridors in fragmented landscapes", "Large feline conservation ecology.", 2025, "Panther Conservation")
        ));
        when(embeddingRepository.hasEmbeddings("ollama", "bge-m3", 3)).thenReturn(true);
        when(embeddingRepository.searchNearest(
            anyString(), eq("ollama"), eq("bge-m3"), eq(3), eq(125), eq(VisibilityScope.PUBLIC_VALIDATED), eq(null)
        )).thenReturn(List.of(
            new PublicationEmbeddingSearchRow(201L, 0.91),
            new PublicationEmbeddingSearchRow(101L, 0.16)
        ));

        ExpertFinderSearchResponse response = service.search(request("panther conservation"));

        assertEquals(2L, response.results().getFirst().researcher().id());
        assertTrue(response.results().getFirst().score() > response.results().get(1).score());
        assertTrue(response.results().getFirst().matchedTopics().contains("Panther Conservation"));
    }

    @Test
    void unrelatedResearchersRankLow() {
        when(evidenceRepository.findPublicationEvidence(null, null, null, true)).thenReturn(List.of(
            publicationRow(1L, "Maya Chen", 101L, "Clinical AI triage in hospitals", "Machine learning support for hospital care.", 2025, "Clinical AI"),
            publicationRow(2L, "Leo Rivera", 201L, "Panther corridors in fragmented landscapes", "Large feline conservation ecology.", 2025, "Panther Conservation")
        ));
        when(embeddingRepository.hasEmbeddings("ollama", "bge-m3", 3)).thenReturn(false);

        ExpertFinderSearchResponse response = service.search(request("quantum finance derivatives"));

        assertEquals(2, response.results().size());
        assertTrue(response.results().stream().allMatch(result -> result.score() < 0.25));
        assertTrue(response.results().stream().allMatch(result -> "LOW".equals(result.confidence())));
    }

    @Test
    void pendingAndRejectedDataIsExcludedByDefault() {
        when(evidenceRepository.findPublicationEvidence(null, null, null, true)).thenReturn(List.of(
            publicationRow(1L, "Validated Researcher", 101L, "Clinical AI triage", "Validated evidence.", 2025, "Clinical AI"),
            publicationRow(2L, "Pending Publication Researcher", 201L, "Clinical AI draft", "Pending evidence.", 2025, "Clinical AI", ValidationStatus.VALIDATED, ValidationStatus.PENDING_VALIDATION),
            publicationRow(3L, "Rejected Researcher", 301L, "Clinical AI rejected researcher", "Rejected profile.", 2025, "Clinical AI", ValidationStatus.REJECTED, ValidationStatus.VALIDATED)
        ));
        when(evidenceRepository.findEventEvidence(null, true)).thenReturn(List.of(
            eventRow(4L, "Pending Event Researcher", 401L, "Clinical AI talk", ValidationStatus.PENDING_VALIDATION)
        ));
        when(embeddingRepository.hasEmbeddings("ollama", "bge-m3", 3)).thenReturn(false);

        ExpertFinderSearchResponse response = service.search(request("clinical AI"));

        assertEquals(List.of(1L), response.results().stream().map(result -> result.researcher().id()).toList());
        assertTrue(response.validationFilterApplied());
    }

    private ExpertFinderSearchRequest request(String query) {
        return new ExpertFinderSearchRequest(query, 5, RetrievalMode.BALANCED, new ExpertFinderFiltersRequest(null, null, null));
    }

    private ExpertPublicationEvidenceRow publicationRow(
        Long researcherId,
        String researcherName,
        Long publicationId,
        String title,
        String abstractText,
        Integer year,
        String topic
    ) {
        return publicationRow(researcherId, researcherName, publicationId, title, abstractText, year, topic, ValidationStatus.VALIDATED, ValidationStatus.VALIDATED);
    }

    private ExpertPublicationEvidenceRow publicationRow(
        Long researcherId,
        String researcherName,
        Long publicationId,
        String title,
        String abstractText,
        Integer year,
        String topic,
        ValidationStatus researcherStatus,
        ValidationStatus publicationStatus
    ) {
        return new ExpertPublicationEvidenceRow(
            researcherId,
            researcherName,
            researcherName,
            null,
            true,
            researcherStatus,
            10L + researcherId,
            "Unit " + researcherId,
            ValidationStatus.VALIDATED,
            ValidationStatus.VALIDATED,
            publicationId,
            title,
            abstractText,
            year,
            "ARTICLE",
            null,
            "Demo",
            null,
            publicationStatus,
            publicationId + 1000,
            topic,
            topic.toLowerCase()
        );
    }

    private ExpertEventParticipationEvidenceRow eventRow(
        Long researcherId,
        String researcherName,
        Long participationId,
        String title,
        ValidationStatus participationStatus
    ) {
        return new ExpertEventParticipationEvidenceRow(
            researcherId,
            researcherName,
            researcherName,
            null,
            true,
            ValidationStatus.VALIDATED,
            10L + researcherId,
            "Unit " + researcherId,
            ValidationStatus.VALIDATED,
            ValidationStatus.VALIDATED,
            participationId,
            participationId + 100,
            "Clinical AI Workshop",
            ValidationStatus.VALIDATED,
            10L + researcherId,
            "Unit " + researcherId,
            ValidationStatus.VALIDATED,
            "TALK",
            title,
            "Event evidence",
            LocalDate.of(2025, 10, 15),
            null,
            participationStatus
        );
    }
}
