package com.researchintelligence.platform.reports.persistence;

import com.researchintelligence.platform.reports.domain.ReportOutputFormat;
import com.researchintelligence.platform.reports.domain.ReportType;
import com.researchintelligence.platform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "report_templates")
public class ReportTemplateEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ReportType targetType;

    @Column(name = "sections_json", nullable = false, columnDefinition = "text")
    private String sectionsJson;

    @Column(name = "default_year_from")
    private Integer defaultYearFrom;

    @Column(name = "default_year_to")
    private Integer defaultYearTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false)
    private ReportOutputFormat outputFormat = ReportOutputFormat.MARKDOWN;

    @Column(nullable = false)
    private boolean active = true;

    protected ReportTemplateEntity() {
    }

    public ReportTemplateEntity(
        String name,
        String description,
        ReportType targetType,
        String sectionsJson,
        Integer defaultYearFrom,
        Integer defaultYearTo,
        ReportOutputFormat outputFormat,
        boolean active
    ) {
        this.name = name;
        this.description = description;
        this.targetType = targetType;
        this.sectionsJson = sectionsJson;
        this.defaultYearFrom = defaultYearFrom;
        this.defaultYearTo = defaultYearTo;
        this.outputFormat = outputFormat;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ReportType getTargetType() {
        return targetType;
    }

    public void setTargetType(ReportType targetType) {
        this.targetType = targetType;
    }

    public String getSectionsJson() {
        return sectionsJson;
    }

    public void setSectionsJson(String sectionsJson) {
        this.sectionsJson = sectionsJson;
    }

    public Integer getDefaultYearFrom() {
        return defaultYearFrom;
    }

    public void setDefaultYearFrom(Integer defaultYearFrom) {
        this.defaultYearFrom = defaultYearFrom;
    }

    public Integer getDefaultYearTo() {
        return defaultYearTo;
    }

    public void setDefaultYearTo(Integer defaultYearTo) {
        this.defaultYearTo = defaultYearTo;
    }

    public ReportOutputFormat getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(ReportOutputFormat outputFormat) {
        this.outputFormat = outputFormat;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
