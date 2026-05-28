package com.researchintelligence.platform.publications.persistence;

import java.io.Serializable;
import java.util.Objects;

public class PublicationTopicId implements Serializable {

    private Long publicationId;
    private Long topicId;

    public PublicationTopicId() {
    }

    public PublicationTopicId(Long publicationId, Long topicId) {
        this.publicationId = publicationId;
        this.topicId = topicId;
    }

    public Long getPublicationId() {
        return publicationId;
    }

    public Long getTopicId() {
        return topicId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PublicationTopicId that)) {
            return false;
        }
        return Objects.equals(publicationId, that.publicationId) && Objects.equals(topicId, that.topicId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicationId, topicId);
    }
}
