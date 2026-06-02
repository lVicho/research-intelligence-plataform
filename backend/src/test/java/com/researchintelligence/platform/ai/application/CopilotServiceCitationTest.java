package com.researchintelligence.platform.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.ai.api.CopilotAnswerRequest;
import com.researchintelligence.platform.ai.api.CopilotAnswerResponse;
import com.researchintelligence.platform.ai.api.CopilotAnswerSupportLevel;
import com.researchintelligence.platform.ai.api.CopilotRetrievedPublicationResponse;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CopilotServiceCitationTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private PublicationRetrievalService retrievalService;

    @Mock
    private VisibilityContext visibilityContext;

    private AiProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        lenient().when(retrievalService.visiblePublicationIds(any(), eq(VisibilityScope.PUBLIC_VALIDATED), isNull()))
            .thenAnswer(invocation -> {
                List<Long> ids = invocation.getArgument(0);
                return new LinkedHashSet<>(ids);
            });
    }

    @Test
    void answerWithPubMarkerReturnsCitedPublicationWithFirstCitationIndex() {
        CopilotAnswerResponse response = serviceWithAnswer("La evidencia principal está en [pub:203].")
            .answer(requestWith(publication(203L), publication(117L)));

        assertEquals(1, response.citedPublications().size());
        assertEquals(203L, response.citedPublications().getFirst().id());
        assertEquals(1, response.citedPublications().getFirst().citationIndex());
        assertEquals(0.82, response.citedPublications().getFirst().similarityScore());
    }

    @Test
    void answerWithLegacyPublicationMarkerIsParsed() {
        CopilotAnswerResponse response = serviceWithAnswer("La publicación antigua también se reconoce [publication:117].")
            .answer(requestWith(publication(203L), publication(117L)));

        assertEquals(1, response.citedPublications().size());
        assertEquals(117L, response.citedPublications().getFirst().id());
        assertEquals(1, response.citedPublications().getFirst().citationIndex());
    }

    @Test
    void duplicateMarkersAppearOnlyOnceInCitedPublications() {
        CopilotAnswerResponse response = serviceWithAnswer("Primero [pub:203], luego otra vez [pub:203], y después [pub:117].")
            .answer(requestWith(publication(203L), publication(117L)));

        assertEquals(List.of(203L, 117L), response.citedPublications().stream().map(citation -> citation.id()).toList());
        assertEquals(List.of(1, 2), response.citedPublications().stream().map(citation -> citation.citationIndex()).toList());
    }

    @Test
    void groupedMarkersReturnAllCitedPublicationsInFirstAppearanceOrder() {
        CopilotAnswerResponse response = serviceWithAnswer("La comparación se apoya en varias evidencias [pub:203, pub:117].")
            .answer(requestWith(publication(203L), publication(117L)));

        assertEquals(List.of(203L, 117L), response.citedPublications().stream().map(citation -> citation.id()).toList());
        assertEquals(List.of(1, 2), response.citedPublications().stream().map(citation -> citation.citationIndex()).toList());
    }

    @Test
    void groupedMarkersAreAcceptedByAnswerEvaluation() {
        properties.getCopilot().setAnswerEvaluationEnabled(true);

        CopilotAnswerResponse response = serviceWithAnswer("La comparación se apoya en varias evidencias [pub:203, pub:117].")
            .answer(requestWith(publication(203L), publication(117L)));

        assertNotNull(response.evaluation());
        assertEquals(CopilotAnswerSupportLevel.HIGH, response.evaluation().supportLevel());
    }

    @Test
    void markerOutsideRetrievedContextProducesWarningAndIsNotCited() {
        CopilotAnswerResponse response = serviceWithAnswer("Esta cita no procede del contexto [pub:999].")
            .answer(requestWith(publication(203L), publication(117L)));

        assertTrue(response.citedPublications().isEmpty());
        assertTrue(response.warnings().contains("La respuesta contiene una cita que no está en el contexto recuperado."));
    }

    @Test
    void retrievedPublicationsRemainAllRetrievedContext() {
        CopilotAnswerResponse response = serviceWithAnswer("Solo se usa una publicación [pub:203].")
            .answer(requestWith(publication(203L), publication(117L)));

        assertEquals(List.of(203L, 117L), response.retrievedPublications().stream().map(publication -> publication.id()).toList());
        assertEquals(List.of(203L), response.citedPublications().stream().map(citation -> citation.id()).toList());
    }

    @Test
    void publicAnswerDoesNotCitePublicationFilteredOutOfVisibleContext() {
        when(retrievalService.visiblePublicationIds(any(), eq(VisibilityScope.PUBLIC_VALIDATED), isNull()))
            .thenReturn(Set.of(203L));

        CopilotAnswerResponse response = serviceWithAnswer("Una cita visible [pub:203] y otra filtrada [pub:117].")
            .answer(requestWith(publication(203L), publication(117L)));

        assertEquals(List.of(203L), response.retrievedPublications().stream().map(publication -> publication.id()).toList());
        assertEquals(List.of(203L), response.citedPublications().stream().map(citation -> citation.id()).toList());
        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("contexto recuperado")));
    }

    @Test
    void answerWithoutMarkersProducesExplicitCitationWarning() {
        CopilotAnswerResponse response = serviceWithAnswer("Respuesta sin marcadores explícitos.")
            .answer(requestWith(publication(203L)));

        assertTrue(response.citedPublications().isEmpty());
        assertTrue(response.warnings().contains("La respuesta no contiene citas explícitas a publicaciones."));
    }

    @Test
    void answerIncludesEvaluationWhenEnabled() {
        properties.getCopilot().setAnswerEvaluationEnabled(true);

        CopilotAnswerResponse response = serviceWithAnswer("La evidencia principal esta en [pub:203].")
            .answer(requestWith(publication(203L)));

        assertNotNull(response.evaluation());
        assertEquals(CopilotAnswerSupportLevel.HIGH, response.evaluation().supportLevel());
    }

    @Test
    void promptUsesSpanishPublicationFieldsAndPreferredPubMarkers() {
        FixedLlmService llmService = new FixedLlmService("Respuesta [pub:203].");
        serviceWithLlm(llmService).answer(requestWith(publication(203L)));

        String context = llmService.lastPrompt().context();
        assertTrue(context.contains("[pub:203]"));
        assertFalse(context.contains("[publication:203]"));
        assertTrue(context.contains("Título: Título 203"));
        assertTrue(context.contains("Año: 2026"));
        assertTrue(context.contains("Autores: Autora 203"));
        assertTrue(context.contains("Temas: Tema 203"));
        assertTrue(context.contains("Fuente: Revista Demo"));
        assertTrue(context.contains("Resumen: Resumen 203"));
        assertFalse(context.contains("Supera umbral:"));
        assertFalse(context.contains("Baja similitud:"));
        assertFalse(context.contains("Motivo de recuperación:"));
    }

    private CopilotAnswerRequest requestWith(CopilotRetrievedPublicationResponse... publications) {
        return new CopilotAnswerRequest("¿Qué evidencia existe?", List.of(publications));
    }

    private CopilotService serviceWithAnswer(String answer) {
        return serviceWithLlm(new FixedLlmService(answer));
    }

    private CopilotService serviceWithLlm(LlmService llmService) {
        return new CopilotService(
            llmService,
            embeddingService,
            retrievalService,
            new CopilotAnswerEvaluationService(),
            properties,
            visibilityContext
        );
    }

    private CopilotRetrievedPublicationResponse publication(Long id) {
        return new CopilotRetrievedPublicationResponse(
            id,
            "Título " + id,
            "Resumen " + id,
            2026,
            "10.1000/demo." + id,
            "Revista Demo",
            "https://example.test/publications/" + id,
            List.of("Autora " + id),
            List.of(),
            List.of(),
            List.of("Tema " + id),
            0.82,
            true,
            false,
            "Similitud semántica por encima del umbral configurado."
        );
    }

    private static class FixedLlmService implements LlmService {

        private final String answer;
        private LlmPrompt lastPrompt;

        FixedLlmService(String answer) {
            this.answer = answer;
        }

        @Override
        public LlmResponse answer(LlmPrompt prompt) {
            this.lastPrompt = prompt;
            return new LlmResponse(answer, List.of());
        }

        @Override
        public String provider() {
            return "test";
        }

        @Override
        public String model() {
            return "test-llm";
        }

        LlmPrompt lastPrompt() {
            return lastPrompt;
        }
    }
}
