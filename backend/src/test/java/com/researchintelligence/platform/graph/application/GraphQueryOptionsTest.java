package com.researchintelligence.platform.graph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchintelligence.platform.graph.api.GraphDensity;
import org.junit.jupiter.api.Test;

class GraphQueryOptionsTest {

    @Test
    void normalDefaultsKeepGraphReadableAndHideExternalAuthors() {
        GraphQueryOptions options = GraphQueryOptions.from(null, null, null, null, null, null, null, null, null);

        assertEquals(GraphDensity.NORMAL, options.density());
        assertTrue(options.includePublications());
        assertTrue(options.includeTopics());
        assertTrue(options.includeCoauthors());
        assertTrue(options.includeResearchUnits());
        assertFalse(options.includeExternalAuthors());
        assertEquals(10, options.maxPublications());
        assertEquals(12, options.maxTopics());
        assertEquals(8, options.maxCoauthors());
    }

    @Test
    void explicitParametersOverrideDensityDefaults() {
        GraphQueryOptions options = GraphQueryOptions.from(GraphDensity.SIMPLE, false, false, true, false, true, 3, 2, 1);

        assertEquals(GraphDensity.SIMPLE, options.density());
        assertFalse(options.includePublications());
        assertFalse(options.includeTopics());
        assertTrue(options.includeCoauthors());
        assertFalse(options.includeResearchUnits());
        assertTrue(options.includeExternalAuthors());
        assertEquals(3, options.maxPublications());
        assertEquals(2, options.maxTopics());
        assertEquals(1, options.maxCoauthors());
    }
}
