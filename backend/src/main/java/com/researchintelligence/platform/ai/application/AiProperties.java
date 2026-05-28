package com.researchintelligence.platform.ai.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String provider = "mock";
    private String embeddingProvider = "mock";
    private int embeddingDimension = 1024;
    private Retrieval retrieval = new Retrieval();
    private Copilot copilot = new Copilot();
    private Ollama ollama = new Ollama();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEmbeddingProvider() {
        return embeddingProvider;
    }

    public void setEmbeddingProvider(String embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(int embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(Retrieval retrieval) {
        this.retrieval = retrieval;
    }

    public Copilot getCopilot() {
        return copilot;
    }

    public void setCopilot(Copilot copilot) {
        this.copilot = copilot;
    }

    public Ollama getOllama() {
        return ollama;
    }

    public void setOllama(Ollama ollama) {
        this.ollama = ollama;
    }

    public static class Retrieval {

        private int defaultLimit = 10;
        private int maxLimit = 20;
        private double minSimilarity = 0.35;
        private double strictMinSimilarity = 0.45;
        private double broadMinSimilarity = 0.25;
        private boolean allowNoContextAnswers = false;

        public int getDefaultLimit() {
            return defaultLimit;
        }

        public void setDefaultLimit(int defaultLimit) {
            this.defaultLimit = defaultLimit;
        }

        public int getMaxLimit() {
            return maxLimit;
        }

        public void setMaxLimit(int maxLimit) {
            this.maxLimit = maxLimit;
        }

        public double getMinSimilarity() {
            return minSimilarity;
        }

        public void setMinSimilarity(double minSimilarity) {
            this.minSimilarity = minSimilarity;
        }

        public double getStrictMinSimilarity() {
            return strictMinSimilarity;
        }

        public void setStrictMinSimilarity(double strictMinSimilarity) {
            this.strictMinSimilarity = strictMinSimilarity;
        }

        public double getBroadMinSimilarity() {
            return broadMinSimilarity;
        }

        public void setBroadMinSimilarity(double broadMinSimilarity) {
            this.broadMinSimilarity = broadMinSimilarity;
        }

        public boolean isAllowNoContextAnswers() {
            return allowNoContextAnswers;
        }

        public void setAllowNoContextAnswers(boolean allowNoContextAnswers) {
            this.allowNoContextAnswers = allowNoContextAnswers;
        }
    }

    public static class Copilot {

        private boolean answerEvaluationEnabled = false;

        public boolean isAnswerEvaluationEnabled() {
            return answerEvaluationEnabled;
        }

        public void setAnswerEvaluationEnabled(boolean answerEvaluationEnabled) {
            this.answerEvaluationEnabled = answerEvaluationEnabled;
        }
    }

    public static class Ollama {

        private String baseUrl = "http://localhost:11434";
        private String chatModel = "qwen2.5:14b";
        private String embeddingModel = "bge-m3";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }
    }
}
