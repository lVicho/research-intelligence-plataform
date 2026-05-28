package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.shared.application.BusinessRuleException;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class OllamaLlmService implements LlmService {

    private final String baseUrl;
    private final String model;
    private final RestClient restClient;

    public OllamaLlmService(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public LlmResponse answer(LlmPrompt prompt) {
        try {
            Map<String, Object> response = restClient.post()
                .uri("/api/generate")
                .body(Map.of(
                    "model", model,
                    "stream", false,
                    "system", systemPrompt(),
                    "prompt", userPrompt(prompt)
                ))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
            return new LlmResponse(extractContent(response), List.of());
        } catch (RestClientException exception) {
            throw new BusinessRuleException("Ollama LLM request failed at " + baseUrl + "/api/generate: " + exception.getMessage());
        }
    }

    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    public String model() {
        return model;
    }

    String systemPrompt() {
        return """
            Eres un copiloto local de investigación para una plataforma académica.
            Responde en español con un tono claro, natural y útil para una demo.
            Usa solo el contexto recuperado.
            No inventes publicaciones, autores, métricas ni relaciones.
            Cada vez que uses una publicación concreta para justificar una afirmación, incluye su marcador [pub:ID].
            No cites todas las publicaciones por defecto.
            Cita solo las publicaciones realmente utilizadas.
            Si comparas líneas de investigación, cita al menos una publicación representativa por línea si está disponible.
            Si el contexto no es suficiente, dilo claramente.
            No añadas bibliografía externa.
            No cambies los marcadores [pub:ID].
            Usa siempre marcadores exactamente como [pub:203], nunca como publication:203 ni [publication:203].
            """.trim();
    }

    String userPrompt(LlmPrompt prompt) {
        return "Pregunta:\n" + prompt.question() + "\n\nContexto de publicaciones recuperadas:\n" + prompt.context();
    }

    private String extractContent(Map<String, Object> response) {
        Object content = response == null ? null : response.get("response");
        if (content instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new BusinessRuleException("Ollama generate response did not include answer content.");
    }
}
