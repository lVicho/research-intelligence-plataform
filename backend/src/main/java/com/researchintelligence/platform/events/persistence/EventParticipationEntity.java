package com.researchintelligence.platform.events.persistence;

import com.researchintelligence.platform.shared.persistence.TimestampedEntity;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "event_participations")
public class EventParticipationEntity extends TimestampedEntity {

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "researcher_id", nullable = false)
    private Long researcherId;

    @Column(name = "research_unit_id")
    private Long researchUnitId;

    @Column(name = "participation_type_code", nullable = false)
    private String participationTypeCode;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "evidence_url")
    private String evidenceUrl;

    @Column(name = "participation_date")
    private LocalDate participationDate;

    @Column(name = "related_publication_id")
    private Long relatedPublicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false)
    private ValidationStatus validationStatus = ValidationStatus.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "validation_comment")
    private String validationComment;

    protected EventParticipationEntity() {
    }

    public EventParticipationEntity(
        Long eventId,
        Long researcherId,
        Long researchUnitId,
        String participationTypeCode,
        String title,
        String description,
        String evidenceUrl,
        LocalDate participationDate,
        Long relatedPublicationId,
        ValidationStatus validationStatus
    ) {
        this.eventId = eventId;
        this.researcherId = researcherId;
        this.researchUnitId = researchUnitId;
        this.participationTypeCode = participationTypeCode;
        this.title = title;
        this.description = description;
        this.evidenceUrl = evidenceUrl;
        this.participationDate = participationDate;
        this.relatedPublicationId = relatedPublicationId;
        this.validationStatus = validationStatus == null ? ValidationStatus.DRAFT : validationStatus;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getResearcherId() {
        return researcherId;
    }

    public void setResearcherId(Long researcherId) {
        this.researcherId = researcherId;
    }

    public Long getResearchUnitId() {
        return researchUnitId;
    }

    public void setResearchUnitId(Long researchUnitId) {
        this.researchUnitId = researchUnitId;
    }

    public String getParticipationTypeCode() {
        return participationTypeCode;
    }

    public void setParticipationTypeCode(String participationTypeCode) {
        this.participationTypeCode = participationTypeCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEvidenceUrl() {
        return evidenceUrl;
    }

    public void setEvidenceUrl(String evidenceUrl) {
        this.evidenceUrl = evidenceUrl;
    }

    public LocalDate getParticipationDate() {
        return participationDate;
    }

    public void setParticipationDate(LocalDate participationDate) {
        this.participationDate = participationDate;
    }

    public Long getRelatedPublicationId() {
        return relatedPublicationId;
    }

    public void setRelatedPublicationId(Long relatedPublicationId) {
        this.relatedPublicationId = relatedPublicationId;
    }

    public ValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(ValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
    }

    public String getValidationComment() {
        return validationComment;
    }

    public void setValidationComment(String validationComment) {
        this.validationComment = validationComment;
    }
}
