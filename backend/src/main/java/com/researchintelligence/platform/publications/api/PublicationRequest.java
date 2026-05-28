package com.researchintelligence.platform.publications.api;

import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record PublicationRequest(
    @NotBlank @Size(max = 500) String title,
    String abstractText,
    String publicSummary,
    @Min(1500) @Max(2200) Integer year,
    LocalDate publicationDate,
    @NotNull PublicationType type,
    @NotNull PublicationStatus status,
    @Size(max = 255) String doi,
    @Size(max = 255) String source,
    String sourceDetail,
    @Size(max = 500) String url,
    Long venueId,
    Long publisherId,
    @Size(max = 32) String isbn,
    @Size(max = 32) String issn,
    @Size(max = 16) String languageCode,
    @NotEmpty List<@Valid PublicationAuthorRequest> authors,
    List<@NotBlank @Size(max = 255) String> topics
) {
}
