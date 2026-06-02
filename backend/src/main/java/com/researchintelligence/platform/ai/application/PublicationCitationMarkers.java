package com.researchintelligence.platform.ai.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PublicationCitationMarkers {

    private static final Pattern PUB_LIKE_MARKER_PATTERN = Pattern.compile("\\[(?:pub|publication):([^\\]]*)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile("\\d+");

    private PublicationCitationMarkers() {
    }

    static List<Marker> extract(String answer) {
        List<Marker> markers = new ArrayList<>();
        Matcher markerMatcher = PUB_LIKE_MARKER_PATTERN.matcher(answer == null ? "" : answer);
        while (markerMatcher.find()) {
            markers.add(new Marker(markerMatcher.group(), publicationIds(markerMatcher.group(1))));
        }
        return markers;
    }

    private static List<Long> publicationIds(String value) {
        Set<Long> ids = new LinkedHashSet<>();
        Matcher idMatcher = NUMERIC_ID_PATTERN.matcher(value == null ? "" : value);
        while (idMatcher.find()) {
            try {
                ids.add(Long.valueOf(idMatcher.group()));
            } catch (NumberFormatException ignored) {
                // Ignore numeric-looking fragments that do not fit in a Long.
            }
        }
        return new ArrayList<>(ids);
    }

    record Marker(String marker, List<Long> publicationIds) {
    }
}
