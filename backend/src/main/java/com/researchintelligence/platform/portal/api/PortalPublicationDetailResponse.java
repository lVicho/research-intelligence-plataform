package com.researchintelligence.platform.portal.api;

import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import java.time.LocalDate;
import java.util.List;

public record PortalPublicationDetailResponse(
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
    String venueName,
    Long publisherId,
    String publisherName,
    String isbn,
    String issn,
    String languageCode,
    List<PortalPublicationAuthorResponse> authors,
    List<PortalPublicationLinkedResearcherResponse> internalResearchers,
    List<PortalPublicationLinkedUnitResponse> researchUnits,
    List<String> externalOrganizations,
    List<PortalPublicationTopicResponse> topics,
    List<PortalPublicationSummaryResponse> relatedPublications,
    List<String> warnings,
    boolean explanationAvailable,
    VisibilityScope visibilityScope,
    boolean validationFilterApplied
) {
}
