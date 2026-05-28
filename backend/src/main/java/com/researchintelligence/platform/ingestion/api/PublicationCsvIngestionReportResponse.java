package com.researchintelligence.platform.ingestion.api;

import java.util.List;

public record PublicationCsvIngestionReportResponse(
    int totalRows,
    int insertedPublications,
    int updatedPublications,
    int matchedInternalAuthors,
    int externalAuthorsStored,
    int createdTopics,
    int skippedRows,
    List<IngestionRowErrorResponse> rowErrors
) {
}
