package com.researchintelligence.platform.masterdata.persistence;

public enum MasterDataCategory {
    PUBLICATION_TYPES("publication_types"),
    PUBLICATION_STATUSES("publication_statuses"),
    VENUE_TYPES("venue_types"),
    EVENT_TYPES("event_types"),
    EVENT_PARTICIPATION_TYPES("event_participation_types");

    private final String tableName;

    MasterDataCategory(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }
}
