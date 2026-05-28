package com.researchintelligence.platform.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchintelligence.platform.ai.api.CopilotAnswerEvaluationRequest;
import com.researchintelligence.platform.ai.api.CopilotAnswerEvaluationResponse;
import com.researchintelligence.platform.ai.api.CopilotAnswerSupportLevel;
import com.researchintelligence.platform.ai.api.CopilotCitationResponse;
import com.researchintelligence.platform.ai.api.CopilotRetrievedPublicationResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class CopilotAnswerEvaluationServiceTest {

    private final CopilotAnswerEvaluationService service = new CopilotAnswerEvaluationService();

    @Test
    void unknownCitationMarkerIsDetected() {
        CopilotAnswerEvaluationResponse response = service.evaluate(new CopilotAnswerEvaluationRequest(
            "La evidencia principal procede de [pub:abc].",
            List.of(),
            List.of(publication(203L))
        ));

        assertEquals(CopilotAnswerSupportLevel.LOW, response.supportLevel());
        assertTrue(response.citationIssues().contains("Marcador de cita no reconocido: [pub:abc]."));
        assertTrue(response.warnings().contains("La respuesta contiene marcadores de cita no reconocidos."));
    }

    @Test
    void answerWithoutCitationsProducesWarningAndMissingCitation() {
        CopilotAnswerEvaluationResponse response = service.evaluate(new CopilotAnswerEvaluationRequest(
            "La publicacion demuestra resultados relevantes para el tema.",
            List.of(),
            List.of(publication(203L))
        ));

        assertEquals(CopilotAnswerSupportLevel.LOW, response.supportLevel());
        assertTrue(response.warnings().contains("La respuesta no contiene citas explicitas a publicaciones."));
        assertTrue(response.missingCitations().contains(
            "La respuesta parece contener afirmaciones sobre publicaciones, pero citedPublications esta vacio."
        ));
    }

    @Test
    void citedPublicationOutsideRetrievedContextProducesWarning() {
        CopilotAnswerEvaluationResponse response = service.evaluate(new CopilotAnswerEvaluationRequest(
            "La evidencia principal esta en [pub:203].",
            List.of(citation(203L)),
            List.of(publication(117L))
        ));

        assertEquals(CopilotAnswerSupportLevel.LOW, response.supportLevel());
        assertTrue(response.citationIssues().contains("La publicacion citada [pub:203] no esta en el contexto recuperado."));
        assertTrue(response.warnings().contains("La respuesta cita publicaciones que no estaban en el contexto recuperado."));
    }

    private CopilotCitationResponse citation(Long id) {
        return new CopilotCitationResponse(
            id,
            1,
            "Titulo " + id,
            2026,
            List.of("Autora " + id),
            List.of("Tema " + id),
            "10.1000/demo." + id,
            "Revista Demo",
            "https://example.test/publications/" + id,
            0.82
        );
    }

    private CopilotRetrievedPublicationResponse publication(Long id) {
        return new CopilotRetrievedPublicationResponse(
            id,
            "Titulo " + id,
            "Resumen " + id,
            2026,
            "10.1000/demo." + id,
            "Revista Demo",
            "https://example.test/publications/" + id,
            List.of("Autora " + id),
            List.of("Tema " + id),
            0.82,
            true,
            false,
            "Similitud semantica por encima del umbral configurado."
        );
    }
}
