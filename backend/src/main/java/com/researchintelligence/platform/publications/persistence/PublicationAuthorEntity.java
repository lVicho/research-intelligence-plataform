package com.researchintelligence.platform.publications.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "publication_authors")
public class PublicationAuthorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "publication_id", nullable = false)
    private Long publicationId;

    @Column(name = "researcher_id")
    private Long researcherId;

    @Column(name = "external_author_name")
    private String externalAuthorName;

    @Column(name = "external_affiliation")
    private String externalAffiliation;

    @Column(name = "author_order", nullable = false)
    private Integer authorOrder;

    @Column(name = "corresponding_author", nullable = false)
    private boolean correspondingAuthor;

    protected PublicationAuthorEntity() {
    }

    public PublicationAuthorEntity(
        Long publicationId,
        Long researcherId,
        String externalAuthorName,
        String externalAffiliation,
        Integer authorOrder,
        boolean correspondingAuthor
    ) {
        this.publicationId = publicationId;
        this.researcherId = researcherId;
        this.externalAuthorName = externalAuthorName;
        this.externalAffiliation = externalAffiliation;
        this.authorOrder = authorOrder;
        this.correspondingAuthor = correspondingAuthor;
    }

    public Long getId() {
        return id;
    }

    public Long getPublicationId() {
        return publicationId;
    }

    public void setPublicationId(Long publicationId) {
        this.publicationId = publicationId;
    }

    public Long getResearcherId() {
        return researcherId;
    }

    public void setResearcherId(Long researcherId) {
        this.researcherId = researcherId;
    }

    public String getExternalAuthorName() {
        return externalAuthorName;
    }

    public void setExternalAuthorName(String externalAuthorName) {
        this.externalAuthorName = externalAuthorName;
    }

    public String getExternalAffiliation() {
        return externalAffiliation;
    }

    public void setExternalAffiliation(String externalAffiliation) {
        this.externalAffiliation = externalAffiliation;
    }

    public Integer getAuthorOrder() {
        return authorOrder;
    }

    public void setAuthorOrder(Integer authorOrder) {
        this.authorOrder = authorOrder;
    }

    public boolean isCorrespondingAuthor() {
        return correspondingAuthor;
    }

    public void setCorrespondingAuthor(boolean correspondingAuthor) {
        this.correspondingAuthor = correspondingAuthor;
    }
}
