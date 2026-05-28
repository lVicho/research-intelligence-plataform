package com.researchintelligence.platform.ingestion.application;

import com.researchintelligence.platform.ingestion.api.IngestionRowErrorResponse;
import com.researchintelligence.platform.ingestion.api.PublicationCsvIngestionReportResponse;
import java.util.ArrayList;
import java.util.List;

final class PublicationCsvIngestionReport {

    private int totalRows;
    private int insertedPublications;
    private int updatedPublications;
    private int matchedInternalAuthors;
    private int externalAuthorsStored;
    private int createdTopics;
    private int skippedRows;
    private final List<IngestionRowErrorResponse> rowErrors = new ArrayList<>();

    void totalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    void insertedPublication() {
        insertedPublications++;
    }

    void updatedPublication() {
        updatedPublications++;
    }

    void matchedInternalAuthor() {
        matchedInternalAuthors++;
    }

    void externalAuthorStored() {
        externalAuthorsStored++;
    }

    void createdTopic() {
        createdTopics++;
    }

    void skippedRow() {
        skippedRows++;
    }

    void rowError(int rowNumber, String message) {
        rowErrors.add(new IngestionRowErrorResponse(rowNumber, message));
    }

    PublicationCsvIngestionReportResponse toResponse() {
        return new PublicationCsvIngestionReportResponse(
            totalRows,
            insertedPublications,
            updatedPublications,
            matchedInternalAuthors,
            externalAuthorsStored,
            createdTopics,
            skippedRows,
            List.copyOf(rowErrors)
        );
    }
}
