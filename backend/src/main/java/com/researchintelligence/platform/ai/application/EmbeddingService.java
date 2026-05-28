package com.researchintelligence.platform.ai.application;

public interface EmbeddingService {

    EmbeddingResponse embed(String input);

    String provider();

    String model();
}
