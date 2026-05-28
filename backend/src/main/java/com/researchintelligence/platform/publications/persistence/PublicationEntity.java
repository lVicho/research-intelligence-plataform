package com.researchintelligence.platform.publications.persistence;

import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.shared.persistence.BaseEntity;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "publications")
public class PublicationEntity extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(name = "abstract_text")
    private String abstractText;

    @Column(name = "public_summary")
    private String publicSummary;

    @Column(name = "year")
    private Integer publicationYear;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublicationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublicationStatus status;

    private String doi;

    private String source;

    @Column(name = "source_detail")
    private String sourceDetail;

    private String url;

    @Column(name = "venue_id")
    private Long venueId;

    @Column(name = "publisher_id")
    private Long publisherId;

    private String isbn;

    private String issn;

    @Column(name = "language_code")
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false)
    private ValidationStatus validationStatus = ValidationStatus.PENDING_VALIDATION;

    @Column(name = "validation_comment")
    private String validationComment;

    @Column(name = "validated_by_user_id")
    private Long validatedByUserId;

    @Column(name = "validated_at")
    private Instant validatedAt;

    protected PublicationEntity() {
    }

    public PublicationEntity(
        String title,
        String abstractText,
        Integer publicationYear,
        PublicationType type,
        PublicationStatus status,
        String doi,
        String source,
        String url
    ) {
        this(title, abstractText, publicationYear, type, status, doi, source, url, null, null, null, null, null);
    }

    public PublicationEntity(
        String title,
        String abstractText,
        Integer publicationYear,
        PublicationType type,
        PublicationStatus status,
        String doi,
        String source,
        String url,
        Long venueId,
        Long publisherId,
        String isbn,
        String issn,
        String languageCode
    ) {
        this.title = title;
        this.abstractText = abstractText;
        this.publicationYear = publicationYear;
        this.type = type;
        this.status = status;
        this.doi = doi;
        this.source = source;
        this.url = url;
        this.venueId = venueId;
        this.publisherId = publisherId;
        this.isbn = isbn;
        this.issn = issn;
        this.languageCode = languageCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText;
    }

    public String getPublicSummary() {
        return publicSummary;
    }

    public void setPublicSummary(String publicSummary) {
        this.publicSummary = publicSummary;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }

    public void setPublicationYear(Integer publicationYear) {
        this.publicationYear = publicationYear;
    }

    public LocalDate getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(LocalDate publicationDate) {
        this.publicationDate = publicationDate;
    }

    public PublicationType getType() {
        return type;
    }

    public void setType(PublicationType type) {
        this.type = type;
    }

    public PublicationStatus getStatus() {
        return status;
    }

    public void setStatus(PublicationStatus status) {
        this.status = status;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceDetail() {
        return sourceDetail;
    }

    public void setSourceDetail(String sourceDetail) {
        this.sourceDetail = sourceDetail;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }

    public Long getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(Long publisherId) {
        this.publisherId = publisherId;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getIssn() {
        return issn;
    }

    public void setIssn(String issn) {
        this.issn = issn;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public ValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(ValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getValidationComment() {
        return validationComment;
    }

    public void setValidationComment(String validationComment) {
        this.validationComment = validationComment;
    }

    public Long getValidatedByUserId() {
        return validatedByUserId;
    }

    public void setValidatedByUserId(Long validatedByUserId) {
        this.validatedByUserId = validatedByUserId;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
    }
}
