package com.researchintelligence.platform.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.ai.persistence.AiSuggestionEntity;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicSummarySuggestionApplyHandlerTest {

    @Mock
    private ResearcherRepository researcherRepository;

    @Mock
    private ResearchUnitRepository researchUnitRepository;

    @Mock
    private PublicationRepository publicationRepository;

    private PublicSummarySuggestionApplyHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PublicSummarySuggestionApplyHandler(
            researcherRepository,
            researchUnitRepository,
            publicationRepository,
            new ObjectMapper()
        );
    }

    @Test
    void acceptedPublicationSummaryUpdatesPublicSummary() {
        PublicationEntity publication = new PublicationEntity(
            "Titulo",
            "Resumen",
            2025,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            null,
            null
        );
        publication.setId(20L);
        when(publicationRepository.findById(20L)).thenReturn(Optional.of(publication));
        AiSuggestionEntity suggestion = suggestion("PUBLICATION", 20L);

        AiSuggestionApplyResult result = handler.apply(suggestion, "{\"summary\":\"Resumen publico revisado\"}", null);

        assertTrue(result.applied());
        assertEquals("Resumen publico revisado", publication.getPublicSummary());
        verify(publicationRepository).save(publication);
    }

    @Test
    void acceptedResearcherSummaryUpdatesPublicProfileSummary() {
        ResearcherEntity researcher = new ResearcherEntity("Maya Chen", "Maya Chen", null, null, true);
        researcher.setId(7L);
        when(researcherRepository.findById(7L)).thenReturn(Optional.of(researcher));
        AiSuggestionEntity suggestion = suggestion("RESEARCHER", 7L);

        handler.apply(suggestion, "{\"summary\":\"Perfil publico revisado\"}", null);

        assertEquals("Perfil publico revisado", researcher.getPublicProfileSummary());
        verify(researcherRepository).save(researcher);
    }

    @Test
    void acceptedUnitSummaryUpdatesPublicDescription() {
        ResearchUnitEntity unit = new ResearchUnitEntity("Grupo", null, ResearchUnitType.RESEARCH_GROUP, null, null, null, null, true);
        unit.setId(9L);
        when(researchUnitRepository.findById(9L)).thenReturn(Optional.of(unit));
        AiSuggestionEntity suggestion = suggestion("RESEARCH_UNIT", 9L);

        handler.apply(suggestion, "{\"summary\":\"Descripcion publica revisada\"}", null);

        assertEquals("Descripcion publica revisada", unit.getPublicDescription());
        verify(researchUnitRepository).save(unit);
    }

    @Test
    void supportsOnlyPublicSummarySuggestions() {
        assertTrue(handler.supports(suggestion("PUBLICATION", 20L)));
        AiSuggestionEntity topicSuggestion = new AiSuggestionEntity(
            "PUBLICATION",
            20L,
            AiSuggestionType.TOPIC_RECOMMENDATION,
            "{}",
            "Generated.",
            null,
            "mock",
            "mock-llm"
        );
        assertFalse(handler.supports(topicSuggestion));
    }

    private AiSuggestionEntity suggestion(String targetType, Long targetId) {
        return new AiSuggestionEntity(
            targetType,
            targetId,
            AiSuggestionType.PUBLIC_SUMMARY,
            "{\"summary\":\"Resumen\"}",
            "Generated.",
            null,
            "mock",
            "mock-llm"
        );
    }
}
