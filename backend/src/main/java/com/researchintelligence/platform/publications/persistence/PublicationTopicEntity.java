package com.researchintelligence.platform.publications.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "publication_topics")
@IdClass(PublicationTopicId.class)
public class PublicationTopicEntity {

    @Id
    @Column(name = "publication_id")
    private Long publicationId;

    @Id
    @Column(name = "topic_id")
    private Long topicId;

    protected PublicationTopicEntity() {
    }

    public PublicationTopicEntity(Long publicationId, Long topicId) {
        this.publicationId = publicationId;
        this.topicId = topicId;
    }

    public Long getPublicationId() {
        return publicationId;
    }

    public Long getTopicId() {
        return topicId;
    }
}
