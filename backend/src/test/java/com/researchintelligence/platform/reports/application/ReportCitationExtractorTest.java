package com.researchintelligence.platform.reports.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportCitationExtractorTest {

    private final ReportCitationExtractor extractor = new ReportCitationExtractor();

    @Test
    void extractsUniqueCitationsInFirstAppearanceOrder() {
        ReportCitationExtractor.CitationExtraction extraction = extractor.extract(
            "Primera evidencia [pub:20], repetida [pub:20], y legado [publication:10].",
            List.of(publication(10L), publication(20L))
        );

        assertEquals(List.of(20L, 10L), extraction.citedPublications().stream().map(citation -> citation.id()).toList());
        assertEquals(List.of(1, 2), extraction.citedPublications().stream().map(citation -> citation.citationIndex()).toList());
        assertTrue(extraction.markdownContent().contains("[pub:10]"));
        assertFalse(extraction.markdownContent().contains("[publication:10]"));
    }

    @Test
    void removesInventedCitationMarkersFromReturnedMarkdown() {
        ReportCitationExtractor.CitationExtraction extraction = extractor.extract(
            "Evidencia real [pub:10] y cita inventada [pub:999].",
            List.of(publication(10L))
        );

        assertEquals(List.of(10L), extraction.citedPublications().stream().map(citation -> citation.id()).toList());
        assertTrue(extraction.markdownContent().contains("[pub:10]"));
        assertFalse(extraction.markdownContent().contains("[pub:999]"));
        assertTrue(extraction.warnings().stream().anyMatch(warning -> warning.contains("fuera del contexto")));
    }

    private ReportPublicationEvidence publication(Long id) {
        return new ReportPublicationEvidence(
            id,
            "Publication " + id,
            "Abstract " + id,
            2026,
            "10.1000/demo." + id,
            "Demo Source",
            "https://example.test/" + id,
            List.of("Author " + id),
            List.of("Topic " + id),
            List.of("Unit " + id),
            ValidationStatus.VALIDATED
        );
    }
}
