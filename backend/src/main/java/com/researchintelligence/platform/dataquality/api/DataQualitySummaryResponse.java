package com.researchintelligence.platform.dataquality.api;

public record DataQualitySummaryResponse(
    long publicationsWithoutDoi,
    long publicationsWithoutAbstract,
    long publicationsWithoutPublicSummary,
    long publicationsWithoutTopics,
    long publicationTitleCasingIssues,
    long researchersWithoutOrcid,
    long publicationsWithExternalAuthors,
    long unresolvedExternalAuthors,
    long activitiesPendingValidation,
    long venuesWithoutIdentifier,
    long eventsWithoutDates,
    long externalOrganizationDuplicateCandidates,
    long duplicateTopicCandidates,
    long duplicatePublicationCandidates
) {
}
