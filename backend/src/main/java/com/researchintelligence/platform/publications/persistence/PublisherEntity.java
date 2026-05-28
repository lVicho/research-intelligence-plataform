package com.researchintelligence.platform.publications.persistence;

import com.researchintelligence.platform.shared.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "publishers")
public class PublisherEntity extends TimestampedEntity {

    @Column(nullable = false)
    private String name;

    private String country;

    private String website;

    private String description;

    @Column(nullable = false)
    private boolean active = true;

    protected PublisherEntity() {
    }

    public PublisherEntity(String name, String country, String website, boolean active) {
        this.name = name;
        this.country = country;
        this.website = website;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
