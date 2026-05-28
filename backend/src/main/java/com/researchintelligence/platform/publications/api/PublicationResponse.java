package com.researchintelligence.platform.publications.api;

import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PublicationResponse(
    Long id,
    String title,
    String abstractText,
    String publicSummary,
    Integer year,
    LocalDate publicationDate,
    PublicationType type,
    PublicationStatus status,
    String doi,
    String source,
    String sourceDetail,
    String url,
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
    List<PublicationAuthorResponse> authors,
    List<TopicResponse> topics,
    Instant createdAt,
    Instant updatedAt,
    Long createdByUserId,
    Long updatedByUserId
) {
}
