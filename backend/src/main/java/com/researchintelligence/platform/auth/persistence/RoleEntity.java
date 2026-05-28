package com.researchintelligence.platform.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "label_es", nullable = false)
    private String labelEs;

    @Column(name = "description_es", nullable = false)
    private String descriptionEs;

    protected RoleEntity() {
    }

    public RoleEntity(String code, String labelEs, String descriptionEs) {
        this.code = code;
        this.labelEs = labelEs;
        this.descriptionEs = descriptionEs;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLabelEs() {
        return labelEs;
    }

    public String getDescriptionEs() {
        return descriptionEs;
    }
}
