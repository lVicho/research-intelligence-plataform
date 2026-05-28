package com.researchintelligence.platform.publications.application;

import com.researchintelligence.platform.publications.api.PublicationAuthorResponse;
import com.researchintelligence.platform.publications.api.PublicationRequest;
import com.researchintelligence.platform.publications.api.PublicationResponse;
import com.researchintelligence.platform.publications.api.PublicationSummaryResponse;
import com.researchintelligence.platform.publications.api.TopicResponse;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorEntity;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class PublicationMapper {

    private PublicationMapper() {
    }

    static PublicationSummaryResponse toSummary(PublicationEntity entity, List<String> topicNames) {
        return toSummary(entity, topicNames, null, null, false, false, false);
    }

    static PublicationSummaryResponse toSummary(
        PublicationEntity entity,
        List<String> topicNames,
        String submittedBy,
        String validatedBy,
        boolean canEdit,
        boolean canSubmit,
        boolean canValidate
    ) {
        return new PublicationSummaryResponse(
            entity.getId(),
            entity.getTitle(),
            entity.getPublicationYear(),
            entity.getType(),
            entity.getStatus(),
            entity.getDoi(),
            entity.getSource(),
            entity.getVenueId(),
            entity.getPublisherId(),
            entity.getIsbn(),
            entity.getIssn(),
            entity.getLanguageCode(),
            entity.getValidationStatus(),
            entity.getValidationComment(),
            submittedAt(entity.getCreatedAt()),
            submittedBy,
            entity.getValidatedAt(),
            validatedBy,
            canEdit,
            canSubmit,
            canValidate,
            entity.getCreatedAt(),
            topicNames
        );
    }

    static PublicationResponse toResponse(
        PublicationEntity entity,
        List<PublicationAuthorResponse> authors,
        List<TopicResponse> topics,
        String submittedBy,
        String validatedBy,
        boolean canEdit,
        boolean canSubmit,
        boolean canValidate
    ) {
        return new PublicationResponse(
            entity.getId(),
            entity.getTitle(),
            entity.getAbstractText(),
            entity.getPublicSummary(),
            entity.getPublicationYear(),
            entity.getPublicationDate(),
            entity.getType(),
            entity.getStatus(),
            entity.getDoi(),
            entity.getSource(),
            entity.getSourceDetail(),
            entity.getUrl(),
            entity.getVenueId(),
            entity.getPublisherId(),
            entity.getIsbn(),
            entity.getIssn(),
            entity.getLanguageCode(),
            entity.getValidationStatus(),
            entity.getValidationComment(),
            submittedAt(entity.getCreatedAt()),
            submittedBy,
            entity.getValidatedAt(),
            validatedBy,
            canEdit,
            canSubmit,
            canValidate,
            authors,
            topics,
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getCreatedByUserId(),
            entity.getUpdatedByUserId()
        );
    }

    static PublicationEntity toEntity(PublicationRequest request) {
        PublicationEntity entity = new PublicationEntity(
            request.title(),
            request.abstractText(),
            request.year(),
            request.type(),
            request.status(),
            blankToNull(request.doi()),
            request.source(),
            request.url(),
            request.venueId(),
            request.publisherId(),
            blankToNull(request.isbn()),
            blankToNull(request.issn()),
            blankToNull(request.languageCode())
        );
        entity.setPublicSummary(blankToNull(request.publicSummary()));
        entity.setPublicationDate(request.publicationDate());
        entity.setSourceDetail(blankToNull(request.sourceDetail()));
        return entity;
    }

    static void updateEntity(PublicationEntity entity, PublicationRequest request) {
        entity.setTitle(request.title());
        entity.setAbstractText(request.abstractText());
        entity.setPublicSummary(blankToNull(request.publicSummary()));
        entity.setPublicationYear(request.year());
        entity.setPublicationDate(request.publicationDate());
        entity.setType(request.type());
        entity.setStatus(request.status());
        entity.setDoi(blankToNull(request.doi()));
        entity.setSource(request.source());
        entity.setSourceDetail(blankToNull(request.sourceDetail()));
        entity.setUrl(request.url());
        entity.setVenueId(request.venueId());
        entity.setPublisherId(request.publisherId());
        entity.setIsbn(blankToNull(request.isbn()));
        entity.setIssn(blankToNull(request.issn()));
        entity.setLanguageCode(blankToNull(request.languageCode()));
    }

    static PublicationAuthorResponse toAuthorResponse(PublicationAuthorEntity entity, Map<Long, String> researcherNames) {
        return new PublicationAuthorResponse(
            entity.getId(),
            entity.getResearcherId(),
            entity.getResearcherId() == null ? null : researcherNames.get(entity.getResearcherId()),
            entity.getExternalAuthorName(),
            entity.getExternalAffiliation(),
            entity.getAuthorOrder(),
            entity.isCorrespondingAuthor()
        );
    }

    static TopicResponse toTopicResponse(TopicEntity entity) {
        return new TopicResponse(entity.getId(), entity.getName(), entity.getNormalizedName());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Instant submittedAt(Instant createdAt) {
        return createdAt;
    }
}
