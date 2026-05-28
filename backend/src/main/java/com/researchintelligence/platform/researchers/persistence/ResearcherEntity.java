package com.researchintelligence.platform.researchers.persistence;

import com.researchintelligence.platform.shared.persistence.BaseEntity;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "researchers")
public class ResearcherEntity extends BaseEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "display_name")
    private String displayName;

    private String email;

    private String orcid;

    @Column(name = "public_profile_summary")
    private String publicProfileSummary;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false)
    private ValidationStatus validationStatus = ValidationStatus.PENDING_VALIDATION;

    @Column(name = "validation_comment")
    private String validationComment;

    @Column(name = "validated_by_user_id")
    private Long validatedByUserId;

    @Column(name = "validated_at")
    private Instant validatedAt;

    protected ResearcherEntity() {
    }

    public ResearcherEntity(String fullName, String displayName, String email, String orcid, boolean active) {
        this.fullName = fullName;
        this.displayName = displayName;
        this.email = email;
        this.orcid = orcid;
        this.active = active;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOrcid() {
        return orcid;
    }

    public void setOrcid(String orcid) {
        this.orcid = orcid;
    }

    public String getPublicProfileSummary() {
        return publicProfileSummary;
    }

    public void setPublicProfileSummary(String publicProfileSummary) {
        this.publicProfileSummary = publicProfileSummary;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
