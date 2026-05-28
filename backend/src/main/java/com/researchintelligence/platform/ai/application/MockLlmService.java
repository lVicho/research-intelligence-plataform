package com.researchintelligence.platform.ai.application;

import java.util.List;

public class MockLlmService implements LlmService {

    @Override
    public LlmResponse answer(LlmPrompt prompt) {
        String answer = """
            Respuesta simulada del copiloto: se ha encontrado contexto de publicaciones para la pregunta, pero el proveedor mock no genera análisis real con IA. Revisa el contexto recuperado o cambia ai.provider a ollama para usar un modelo local.
            """.trim();
        return new LlmResponse(answer, List.of("El proveedor LLM mock está activo; la respuesta es un texto determinista de prueba."));
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String model() {
        return "mock-llm";
    }
}
