package com.researchintelligence.platform.publications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.ai.application.EmbeddingService;
import com.researchintelligence.platform.ai.application.LlmService;
import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.publications.api.TopicMergeRequest;
import com.researchintelligence.platform.publications.api.TopicMergeResponse;
import com.researchintelligence.platform.publications.api.TopicNormalizationCandidateGroupResponse;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TopicNormalizationServiceTest {

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private PublicationTopicRepository publicationTopicRepository;

    @Mock
    private ActivityAuditService auditService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private LlmService llmService;

    private TopicNormalizationService service;

    @BeforeEach
    void setUp() {
        service = new TopicNormalizationService(topicRepository, publicationTopicRepository, auditService, embeddingService, llmService);
    }

    @Test
    void detectsSpanishAndEnglishClinicalAiVariants() {
        TopicEntity iaNoAccent = topic(1L, "IA clinica", "ia clinica");
        TopicEntity iaAccent = topic(2L, "IA cl\u00ednica", "ia cl\u00ednica");
        TopicEntity spanishLong = topic(3L, "Inteligencia artificial cl\u00ednica", "inteligencia artificial cl\u00ednica");
        TopicEntity english = topic(4L, "Clinical AI", "clinical ai");
        TopicEntity unrelated = topic(5L, "Oncolog\u00eda", "oncolog\u00eda");
        when(topicRepository.findAll()).thenReturn(List.of(iaNoAccent, iaAccent, spanishLong, english, unrelated));
        when(embeddingService.provider()).thenReturn("mock");
        when(publicationTopicRepository.countPublicationsByTopicIds(anyCollection())).thenReturn(List.of(
            new Object[] {1L, 2L},
            new Object[] {2L, 1L},
            new Object[] {3L, 4L},
            new Object[] {4L, 3L},
            new Object[] {5L, 8L}
        ));
        when(publicationTopicRepository.countDistinctPublicationsByTopicIds(anyCollection())).thenReturn(9L);

        List<TopicNormalizationCandidateGroupResponse> groups = service.findNormalizationCandidates();

        assertEquals(1, groups.size());
        TopicNormalizationCandidateGroupResponse group = groups.getFirst();
        assertEquals("Inteligencia artificial cl\u00ednica", group.canonicalSuggestion());
        assertEquals(4, group.topics().size());
        assertEquals(9L, group.affectedPublicationsCount());
        assertTrue(group.confidence() >= 0.99);
        assertTrue(group.similarityScores().stream().anyMatch(score -> "text".equals(score.method()) && score.score() == 1.0));
    }

    @Test
    void mergeMovesPublicationLinksDeletesSourceTopicsAndRecordsAudit() {
        TopicEntity canonical = topic(1L, "Inteligencia artificial cl\u00ednica", "inteligencia artificial cl\u00ednica");
        TopicEntity sourceOne = topic(2L, "IA clinica", "ia clinica");
        TopicEntity sourceTwo = topic(3L, "Clinical AI", "clinical ai");
        when(topicRepository.findById(1L)).thenReturn(Optional.of(canonical));
        when(topicRepository.findAllById(List.of(2L, 3L))).thenReturn(List.of(sourceOne, sourceTwo));
        when(publicationTopicRepository.countDistinctPublicationsByTopicIds(List.of(2L, 3L))).thenReturn(7L);
        when(auditService.changes()).thenReturn(new LinkedHashMap<>());

        TopicMergeResponse response = service.merge(new TopicMergeRequest(1L, null, List.of(2L, 3L)));

        assertEquals(1L, response.canonicalTopic().id());
        assertEquals(2, response.mergedTopics().size());
        assertEquals(7L, response.affectedPublicationsCount());
        assertTrue(response.auditEventCreated());
        verify(publicationTopicRepository).insertMissingCanonicalLinks(1L, List.of(2L, 3L));
        verify(publicationTopicRepository).deleteByTopicIdIn(List.of(2L, 3L));
        verify(topicRepository).deleteAll(List.of(sourceOne, sourceTwo));
        verify(auditService).recordMerged(eq(ValidationEntityType.TOPIC), eq(1L), any(), any(Map.class));
    }

    private TopicEntity topic(Long id, String name, String normalizedName) {
        TopicEntity topic = new TopicEntity(name, normalizedName);
        ReflectionTestUtils.setField(topic, "id", id);
        return topic;
    }
}
