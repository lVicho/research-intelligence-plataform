package com.researchintelligence.platform.audit.application;

public record AuditFieldChange(
    String previousValue,
    String newValue
) {
}
