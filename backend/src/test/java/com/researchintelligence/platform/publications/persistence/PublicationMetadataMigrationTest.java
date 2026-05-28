package com.researchintelligence.platform.publications.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PublicationMetadataMigrationTest {

    @Test
    void addsPublicationMetadataColumnsAsNullableFields() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V18__add_publication_public_metadata.sql"));
        String normalized = sql.toLowerCase(Locale.ROOT);

        assertTrue(normalized.contains("add column public_summary text"));
        assertTrue(normalized.contains("add column publication_date date"));
        assertTrue(normalized.contains("add column source_detail text"));
        assertFalse(normalized.contains("public_summary text not null"));
        assertFalse(normalized.contains("publication_date date not null"));
        assertFalse(normalized.contains("source_detail text not null"));
    }
}
