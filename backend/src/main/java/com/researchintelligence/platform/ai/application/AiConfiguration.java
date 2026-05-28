package com.researchintelligence.platform.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public LlmService llmService(AiProperties properties) {
        String provider = normalizeProvider(properties.getProvider());
        return switch (provider) {
            case "mock" -> new MockLlmService();
            case "ollama" -> new OllamaLlmService(properties.getOllama().getBaseUrl(), properties.getOllama().getChatModel());
            case "openai" -> new UnsupportedLlmService("openai", "not-configured");
            default -> throw new IllegalArgumentException("Unsupported ai.provider: " + properties.getProvider());
        };
    }

    @Bean
    public EmbeddingService embeddingService(AiProperties properties) {
        String provider = normalizeProvider(properties.getEmbeddingProvider());
        return switch (provider) {
            case "mock" -> new MockEmbeddingService(properties.getEmbeddingDimension());
            case "ollama" -> new OllamaEmbeddingService(properties.getOllama().getBaseUrl(), properties.getOllama().getEmbeddingModel());
            case "openai" -> new UnsupportedEmbeddingService("openai", "not-configured");
            default -> throw new IllegalArgumentException("Unsupported ai.embedding-provider: " + properties.getEmbeddingProvider());
        };
    }

    private String normalizeProvider(String provider) {
        return provider == null || provider.isBlank() ? "mock" : provider.trim().toLowerCase(Locale.ROOT);
    }
}
