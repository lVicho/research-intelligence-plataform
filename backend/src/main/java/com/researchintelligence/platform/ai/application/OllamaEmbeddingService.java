package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.shared.application.BusinessRuleException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class OllamaEmbeddingService implements EmbeddingService {

    private final String baseUrl;
    private final String model;
    private final RestClient restClient;

    public OllamaEmbeddingService(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public EmbeddingResponse embed(String input) {
        try {
            Map<String, Object> response = restClient.post()
                .uri("/api/embed")
                .body(Map.of("model", model, "input", input == null ? "" : input))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
            return new EmbeddingResponse(extractEmbedding(response), List.of());
        } catch (RestClientException exception) {
            throw new BusinessRuleException("Ollama embedding request failed at " + baseUrl + "/api/embed: " + exception.getMessage());
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

    private List<Double> extractEmbedding(Map<String, Object> response) {
        Object embeddings = response == null ? null : response.get("embeddings");
        if (embeddings instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof List<?> firstVector) {
            return toDoubles(firstVector);
        }
        Object embedding = response == null ? null : response.get("embedding");
        if (embedding instanceof List<?> vector) {
            return toDoubles(vector);
        }
        throw new BusinessRuleException("Ollama embedding response did not include an embedding vector.");
    }

    private List<Double> toDoubles(List<?> values) {
        List<Double> vector = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Number number) {
                vector.add(number.doubleValue());
            }
        }
        return vector;
    }
}
