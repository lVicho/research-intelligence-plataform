package com.researchintelligence.platform.graph.application;

import com.researchintelligence.platform.graph.api.GraphDensity;

public record GraphQueryOptions(
    GraphDensity density,
    boolean includePublications,
    boolean includeTopics,
    boolean includeCoauthors,
    boolean includeResearchUnits,
    boolean includeExternalAuthors,
    int maxPublications,
    int maxTopics,
    int maxCoauthors
) {

    public static GraphQueryOptions from(
        GraphDensity density,
        Boolean includePublications,
        Boolean includeTopics,
        Boolean includeCoauthors,
        Boolean includeResearchUnits,
        Boolean includeExternalAuthors,
        Integer maxPublications,
        Integer maxTopics,
        Integer maxCoauthors
    ) {
        GraphDensity resolvedDensity = density == null ? GraphDensity.NORMAL : density;
        GraphQueryOptions defaults = defaultsFor(resolvedDensity);
        return new GraphQueryOptions(
            resolvedDensity,
            includePublications == null ? defaults.includePublications() : includePublications,
            includeTopics == null ? defaults.includeTopics() : includeTopics,
            includeCoauthors == null ? defaults.includeCoauthors() : includeCoauthors,
            includeResearchUnits == null ? defaults.includeResearchUnits() : includeResearchUnits,
            includeExternalAuthors == null ? defaults.includeExternalAuthors() : includeExternalAuthors,
            positiveOrDefault(maxPublications, defaults.maxPublications()),
            positiveOrDefault(maxTopics, defaults.maxTopics()),
            positiveOrDefault(maxCoauthors, defaults.maxCoauthors())
        );
    }

    public static GraphQueryOptions complete() {
        return new GraphQueryOptions(GraphDensity.COMPLETE, true, true, true, true, true, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private static GraphQueryOptions defaultsFor(GraphDensity density) {
        return switch (density) {
            case SIMPLE -> new GraphQueryOptions(density, true, true, true, true, false, 5, 6, 4);
            case NORMAL -> new GraphQueryOptions(density, true, true, true, true, false, 10, 12, 8);
            case COMPLETE -> new GraphQueryOptions(density, true, true, true, true, false, 40, 40, 20);
        };
    }

    private static int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value < 1 ? defaultValue : value;
    }
}
