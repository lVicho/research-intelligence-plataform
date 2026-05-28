package com.researchintelligence.platform.publications.persistence;

import com.researchintelligence.platform.shared.persistence.BaseEntity;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "venues")
public class VenueEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "type_code", nullable = false)
    private String typeCode;

    private String issn;

    private String eissn;

    private String isbn;

    private String country;

    private String website;

    private String description;

    @Column(name = "publisher_id")
    private Long publisherId;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false)
    private ValidationStatus validationStatus = ValidationStatus.PENDING_VALIDATION;

    protected VenueEntity() {
    }

    public VenueEntity(
        String name,
        String shortName,
        String typeCode,
        String issn,
        String eissn,
        String isbn,
        String country,
        String website,
        String description,
        Long publisherId,
        boolean active,
        ValidationStatus validationStatus
    ) {
        this.name = name;
        this.shortName = shortName;
        this.typeCode = typeCode;
        this.issn = issn;
        this.eissn = eissn;
        this.isbn = isbn;
        this.country = country;
        this.website = website;
        this.description = description;
        this.publisherId = publisherId;
        this.active = active;
        this.validationStatus = validationStatus == null ? ValidationStatus.PENDING_VALIDATION : validationStatus;
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

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getIssn() {
        return issn;
    }

    public void setIssn(String issn) {
        this.issn = issn;
    }

    public String getEissn() {
        return eissn;
    }

    public void setEissn(String eissn) {
        this.eissn = eissn;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(Long publisherId) {
        this.publisherId = publisherId;
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
}
