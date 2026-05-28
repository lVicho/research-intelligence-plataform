package com.researchintelligence.platform.researchers.api;

import com.researchintelligence.platform.publications.api.PublicationSummaryResponse;
import com.researchintelligence.platform.publications.api.TopicResponse;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.util.List;

public record ResearcherResponse(
    Long id,
    String fullName,
    String displayName,
    String email,
    String orcid,
    boolean active,
    ValidationStatus validationStatus,
    String validationComment,
    Instant submittedAt,
    String submittedBy,
    Instant validatedAt,
    String validatedBy,
    boolean canEdit,
    boolean canSubmit,
    boolean canValidate,
    List<ResearcherAffiliationResponse> affiliations,
    List<ResearcherAffiliationResponse> currentAffiliations,
    List<ResearcherAffiliationResponse> pastAffiliations,
    ResearcherAffiliationResponse primaryAffiliation,
    List<PublicationSummaryResponse> authoredPublications,
    List<TopicResponse> topics,
    List<ResearcherCoauthorResponse> coauthors,
    Instant createdAt,
    Instant updatedAt,
    Long createdByUserId,
    Long updatedByUserId
) {
}
