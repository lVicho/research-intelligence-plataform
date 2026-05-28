package com.researchintelligence.platform.publications.application;

import java.util.Locale;

final class TopicNormalizer {

    private TopicNormalizer() {
    }

    static String normalize(String topicName) {
        if (topicName == null) {
            return "";
        }
        return topicName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static String displayName(String topicName) {
        if (topicName == null) {
            return "";
        }
        return topicName.trim().replaceAll("\\s+", " ");
    }
}
