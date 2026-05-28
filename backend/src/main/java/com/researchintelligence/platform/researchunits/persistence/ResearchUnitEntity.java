package com.researchintelligence.platform.researchunits.persistence;

import com.researchintelligence.platform.researchunits.domain.OrganizationScope;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.shared.persistence.BaseEntity;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "research_units")
public class ResearchUnitEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "short_name")
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResearchUnitType type;

    @Column(name = "parent_id")
    private Long parentId;

    private String country;

    private String city;

    private String website;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "visible_in_portal", nullable = false)
    private boolean visibleInPortal = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "organization_scope", nullable = false)
    private OrganizationScope organizationScope = OrganizationScope.INTERNAL;

    @Column(name = "public_description")
    private String publicDescription;

    @Column(name = "internal_description")
    private String internalDescription;

    @Column(name = "responsible_researcher_id")
    private Long responsibleResearcherId;

    private Boolean featured = false;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false)
    private ValidationStatus validationStatus = ValidationStatus.PENDING_VALIDATION;

    @Column(name = "validation_comment")
    private String validationComment;

    @Column(name = "validated_by_user_id")
    private Long validatedByUserId;

    @Column(name = "validated_at")
    private Instant validatedAt;

    protected ResearchUnitEntity() {
    }

    public ResearchUnitEntity(String name, String shortName, ResearchUnitType type, Long parentId, String country, String city, String website, boolean active) {
        this.name = name;
        this.shortName = shortName;
        this.type = type;
        this.parentId = parentId;
        this.country = country;
        this.city = city;
        this.website = website;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public ResearchUnitType getType() {
        return type;
    }

    public void setType(ResearchUnitType type) {
        this.type = type;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isVisibleInPortal() {
        return visibleInPortal;
    }

    public void setVisibleInPortal(boolean visibleInPortal) {
        this.visibleInPortal = visibleInPortal;
    }

    public OrganizationScope getOrganizationScope() {
        return organizationScope;
    }

    public void setOrganizationScope(OrganizationScope organizationScope) {
        this.organizationScope = organizationScope == null ? OrganizationScope.INTERNAL : organizationScope;
    }

    public String getPublicDescription() {
        return publicDescription;
    }

    public void setPublicDescription(String publicDescription) {
        this.publicDescription = publicDescription;
    }

    public String getInternalDescription() {
        return internalDescription;
    }

    public void setInternalDescription(String internalDescription) {
        this.internalDescription = internalDescription;
    }

    public Long getResponsibleResearcherId() {
        return responsibleResearcherId;
    }

    public void setResponsibleResearcherId(Long responsibleResearcherId) {
        this.responsibleResearcherId = responsibleResearcherId;
    }

    public Boolean getFeatured() {
        return featured;
    }

    public void setFeatured(Boolean featured) {
        this.featured = featured;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
