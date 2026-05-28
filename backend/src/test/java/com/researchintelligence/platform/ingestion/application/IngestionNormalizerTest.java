package com.researchintelligence.platform.ingestion.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IngestionNormalizerTest {

    @Test
    void normalizesTitlesAndNamesForMatching() {
        assertEquals("machine learning in cardiologia", IngestionNormalizer.normalizeText(" Machine-Learning in Cardiologia "));
        assertEquals("maria garcia lopez", IngestionNormalizer.normalizeText("Maria Garcia-Lopez"));
    }

    @Test
    void normalizesDoiAndOrcidIdentifiers() {
        assertEquals("10.1000/example", IngestionNormalizer.normalizeDoi("https://doi.org/10.1000/EXAMPLE"));
        assertEquals("0000-0002-1825-0097", IngestionNormalizer.normalizeOrcid("https://orcid.org/0000-0002-1825-0097"));
    }
}
