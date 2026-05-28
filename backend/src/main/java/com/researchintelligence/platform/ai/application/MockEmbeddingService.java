package com.researchintelligence.platform.ai.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class MockEmbeddingService implements EmbeddingService {

    private final int dimension;

    public MockEmbeddingService(int dimension) {
        this.dimension = Math.max(dimension, 1);
    }

    @Override
    public EmbeddingResponse embed(String input) {
        String text = input == null ? "" : input;
        List<Double> vector = new ArrayList<>();
        for (int i = 0; i < dimension; i++) {
            byte[] digest = sha256(text + "#" + i);
            vector.add((digest[0] & 0xff) / 255.0);
        }
        return new EmbeddingResponse(vector, List.of("Mock embedding provider is active; vectors are deterministic placeholders."));
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String model() {
        return "mock-embedding";
    }

    private byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
