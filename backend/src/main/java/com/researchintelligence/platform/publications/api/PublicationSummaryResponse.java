package com.researchintelligence.platform.publications.api;

import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.util.List;

public record PublicationSummaryResponse(
    Long id,
    String title,
    Integer year,
    PublicationType type,
    PublicationStatus status,
    String doi,
    String source,
    Long venueId,
    Long publisherId,
    String isbn,
    String issn,
    String languageCode,
    ValidationStatus validationStatus,
    String validationComment,
    Instant submittedAt,
    String submittedBy,
    Instant validatedAt,
    String validatedBy,
    boolean canEdit,
    boolean canSubmit,
    boolean canValidate,
    Instant createdAt,
    List<String> topics
) {
}
