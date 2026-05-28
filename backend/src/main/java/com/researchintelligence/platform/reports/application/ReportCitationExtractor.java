package com.researchintelligence.platform.reports.application;

import com.researchintelligence.platform.reports.api.ReportCitationResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReportCitationExtractor {

    private static final Pattern CITATION_MARKER_PATTERN = Pattern.compile("\\[(?:pub|publication):(\\d+)]", Pattern.CASE_INSENSITIVE);
    private static final String INVENTED_CITATION_WARNING =
        "El informe contenia citas a publicaciones fuera del contexto recuperado; se eliminaron del contenido devuelto.";
    private static final String NO_EXPLICIT_CITATIONS_WARNING = "El informe no contiene citas explicitas a publicaciones.";

    CitationExtraction extract(String markdown, List<ReportPublicationEvidence> evidence) {
        Map<Long, ReportPublicationEvidence> evidenceById = new LinkedHashMap<>();
        for (ReportPublicationEvidence publication : evidence) {
            evidenceById.putIfAbsent(publication.id(), publication);
        }

        List<String> warnings = new ArrayList<>();
        List<ReportCitationResponse> citedPublications = new ArrayList<>();
        Map<Long, Boolean> citedIds = new LinkedHashMap<>();
        String sourceMarkdown = markdown == null ? "" : markdown;
        Matcher matcher = CITATION_MARKER_PATTERN.matcher(sourceMarkdown);
        StringBuffer sanitized = new StringBuffer();
        boolean foundMarker = false;
        boolean foundMarkerOutsideContext = false;
        while (matcher.find()) {
            foundMarker = true;
            Long publicationId = Long.valueOf(matcher.group(1));
            ReportPublicationEvidence publication = evidenceById.get(publicationId);
            if (publication == null) {
                foundMarkerOutsideContext = true;
                matcher.appendReplacement(sanitized, "");
                continue;
            }
            if (!citedIds.containsKey(publicationId)) {
                citedIds.put(publicationId, true);
                citedPublications.add(toCitation(publication, citedPublications.size() + 1));
            }
            matcher.appendReplacement(sanitized, Matcher.quoteReplacement("[pub:" + publicationId + "]"));
        }
        matcher.appendTail(sanitized);

        if (!foundMarker) {
            warnings.add(NO_EXPLICIT_CITATIONS_WARNING);
        }
        if (foundMarkerOutsideContext) {
            warnings.add(INVENTED_CITATION_WARNING);
        }
        return new CitationExtraction(sanitized.toString(), citedPublications, warnings);
    }

    private ReportCitationResponse toCitation(ReportPublicationEvidence publication, int citationIndex) {
        return new ReportCitationResponse(
            publication.id(),
            citationIndex,
            publication.title(),
            publication.year(),
            publication.authors() == null ? List.of() : publication.authors(),
            publication.topics() == null ? List.of() : publication.topics(),
            publication.researchUnits() == null ? List.of() : publication.researchUnits(),
            publication.doi(),
            publication.source(),
            publication.url()
        );
    }

    record CitationExtraction(
        String markdownContent,
        List<ReportCitationResponse> citedPublications,
        List<String> warnings
    ) {
    }
}
