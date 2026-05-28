package com.researchintelligence.platform.ai.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OllamaLlmServicePromptTest {

    @Test
    void systemPromptInstructsSpanishContextOnlyAnswersWithStablePubMarkers() {
        OllamaLlmService service = new OllamaLlmService("http://localhost:11434", "qwen2.5:14b");

        String prompt = service.systemPrompt();

        assertTrue(prompt.contains("Responde en español"));
        assertTrue(prompt.contains("Usa solo el contexto recuperado"));
        assertTrue(prompt.contains("No inventes publicaciones, autores, métricas ni relaciones"));
        assertTrue(prompt.contains("Cada vez que uses una publicación concreta para justificar una afirmación, incluye su marcador [pub:ID]"));
        assertTrue(prompt.contains("No cites todas las publicaciones por defecto"));
        assertTrue(prompt.contains("Cita solo las publicaciones realmente utilizadas"));
        assertTrue(prompt.contains("Si comparas líneas de investigación, cita al menos una publicación representativa por línea si está disponible"));
        assertTrue(prompt.contains("Si el contexto no es suficiente, dilo claramente"));
        assertTrue(prompt.contains("No añadas bibliografía externa"));
        assertTrue(prompt.contains("No cambies los marcadores [pub:ID]"));
        assertTrue(prompt.contains("Usa siempre marcadores exactamente como [pub:203]"));
        assertTrue(prompt.contains("nunca como publication:203"));
    }

    @Test
    void userPromptKeepsQuestionAndRetrievedContextSeparate() {
        OllamaLlmService service = new OllamaLlmService("http://localhost:11434", "qwen2.5:14b");

        String prompt = service.userPrompt(new LlmPrompt("¿Qué líneas aparecen?", "[pub:203]\nTítulo: Demo"));

        assertTrue(prompt.contains("Pregunta:\n¿Qué líneas aparecen?"));
        assertTrue(prompt.contains("Contexto de publicaciones recuperadas:\n[pub:203]\nTítulo: Demo"));
        assertFalse(prompt.contains("[publication:203]"));
    }
}
