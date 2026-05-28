package com.researchintelligence.platform.researchers.persistence;

import com.researchintelligence.platform.researchers.domain.AffiliationType;
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
@Table(name = "researcher_affiliations")
public class ResearcherAffiliationEntity extends BaseEntity {

    @Column(name = "researcher_id", nullable = false)
    private Long researcherId;

    @Column(name = "research_unit_id", nullable = false)
    private Long researchUnitId;

    private String role;

    @Enumerated(EnumType.STRING)
    @Column(name = "affiliation_type", nullable = false)
    private AffiliationType affiliationType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "primary_affiliation", nullable = false)
    private boolean primaryAffiliation;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false)
    private ValidationStatus validationStatus = ValidationStatus.PENDING_VALIDATION;

    @Column(name = "validation_comment")
    private String validationComment;

    @Column(name = "validated_by_user_id")
    private Long validatedByUserId;

    @Column(name = "validated_at")
    private Instant validatedAt;

    protected ResearcherAffiliationEntity() {
    }

    public ResearcherAffiliationEntity(
        Long researcherId,
        Long researchUnitId,
        String role,
        AffiliationType affiliationType,
        LocalDate startDate,
        LocalDate endDate,
        boolean primaryAffiliation
    ) {
        this.researcherId = researcherId;
        this.researchUnitId = researchUnitId;
        this.role = role;
        this.affiliationType = affiliationType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.primaryAffiliation = primaryAffiliation;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public AffiliationType getAffiliationType() {
        return affiliationType;
    }

    public void setAffiliationType(AffiliationType affiliationType) {
        this.affiliationType = affiliationType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public boolean isPrimaryAffiliation() {
        return primaryAffiliation;
    }

    public void setPrimaryAffiliation(boolean primaryAffiliation) {
        this.primaryAffiliation = primaryAffiliation;
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
