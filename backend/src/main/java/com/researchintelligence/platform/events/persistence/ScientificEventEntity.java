package com.researchintelligence.platform.events.persistence;

import com.researchintelligence.platform.shared.persistence.TimestampedEntity;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "scientific_events")
public class ScientificEventEntity extends TimestampedEntity {

    @Column(nullable = false)
    private String name;

    private String edition;

    @Column(name = "event_type_code", nullable = false)
    private String eventTypeCode;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    private String city;

    private String country;

    private String organizer;

    private String website;

    private String description;

    @Column(name = "evidence_url")
    private String evidenceUrl;

    @Column(name = "venue_id")
    private Long venueId;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false)
    private ValidationStatus validationStatus = ValidationStatus.PENDING_VALIDATION;

    protected ScientificEventEntity() {
    }

    public ScientificEventEntity(
        String name,
        String edition,
        String eventTypeCode,
        LocalDate startDate,
        LocalDate endDate,
        String city,
        String country,
        String organizer,
        String website,
        String description,
        String evidenceUrl,
        Long venueId,
        boolean active,
        ValidationStatus validationStatus
    ) {
        this.name = name;
        this.edition = edition;
        this.eventTypeCode = eventTypeCode;
        this.startDate = startDate;
        this.endDate = endDate;
        this.city = city;
        this.country = country;
        this.organizer = organizer;
        this.website = website;
        this.description = description;
        this.evidenceUrl = evidenceUrl;
        this.venueId = venueId;
        this.active = active;
        this.validationStatus = validationStatus == null ? ValidationStatus.PENDING_VALIDATION : validationStatus;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getEventTypeCode() {
        return eventTypeCode;
    }

    public void setEventTypeCode(String eventTypeCode) {
        this.eventTypeCode = eventTypeCode;
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

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getOrganizer() {
        return organizer;
    }

    public void setOrganizer(String organizer) {
        this.organizer = organizer;
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

    public String getEvidenceUrl() {
        return evidenceUrl;
    }

    public void setEvidenceUrl(String evidenceUrl) {
        this.evidenceUrl = evidenceUrl;
    }

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
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
