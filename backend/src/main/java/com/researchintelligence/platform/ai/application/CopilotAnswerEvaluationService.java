package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.ai.api.CopilotAnswerEvaluationRequest;
import com.researchintelligence.platform.ai.api.CopilotAnswerEvaluationResponse;
import com.researchintelligence.platform.ai.api.CopilotAnswerSupportLevel;
import com.researchintelligence.platform.ai.api.CopilotCitationResponse;
import com.researchintelligence.platform.ai.api.CopilotRetrievedPublicationResponse;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CopilotAnswerEvaluationService {

    private static final Pattern CITATION_MARKER_PATTERN = Pattern.compile("\\[(?:pub|publication):(\\d+)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PUB_LIKE_MARKER_PATTERN = Pattern.compile("\\[(?:pub|publication):[^\\]]*]", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTHER_CITATION_LIKE_MARKER_PATTERN = Pattern.compile(
        "\\[(?:citation|source|ref|cita|referencia):[^\\]]+]|\\[\\d+]",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("(?<=[.!?])\\s+");

    public CopilotAnswerEvaluationResponse evaluate(CopilotAnswerEvaluationRequest request) {
        String answer = request.answer() == null ? "" : request.answer();
        List<CopilotCitationResponse> citedPublications = request.citedPublications() == null
            ? List.of()
            : request.citedPublications();
        List<CopilotRetrievedPublicationResponse> retrievedPublications = request.retrievedPublications() == null
            ? List.of()
            : request.retrievedPublications();

        Set<Long> citedIds = idsFromCitedPublications(citedPublications);
        Set<Long> retrievedIds = idsFromRetrievedPublications(retrievedPublications);
        Map<Long, String> citationMarkers = citationMarkers(answer);
        List<String> unknownMarkers = unknownCitationMarkers(answer);

        List<String> unsupportedClaims = new ArrayList<>();
        List<String> missingCitations = new ArrayList<>();
        List<String> citationIssues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (citationMarkers.isEmpty()) {
            warnings.add("La respuesta no contiene citas explicitas a publicaciones.");
        }

        for (CopilotCitationResponse publication : citedPublications) {
            if (publication.id() == null) {
                citationIssues.add("Una publicacion citada no incluye id.");
            }
        }

        for (String marker : unknownMarkers) {
            citationIssues.add("Marcador de cita no reconocido: " + marker + ".");
            warnings.add("La respuesta contiene marcadores de cita no reconocidos.");
            sentenceContaining(answer, marker).ifPresent(unsupportedClaims::add);
        }

        for (Long citedId : citedIds) {
            if (!retrievedIds.contains(citedId)) {
                citationIssues.add("La publicacion citada [pub:" + citedId + "] no esta en el contexto recuperado.");
                warnings.add("La respuesta cita publicaciones que no estaban en el contexto recuperado.");
            }
        }

        for (Long markerId : citationMarkers.keySet()) {
            if (!retrievedIds.contains(markerId)) {
                citationIssues.add("El marcador " + citationMarkers.get(markerId) + " no pertenece al contexto recuperado.");
                warnings.add("La respuesta contiene marcadores fuera del contexto recuperado.");
                sentenceContaining(answer, citationMarkers.get(markerId)).ifPresent(unsupportedClaims::add);
            }
            if (!citedIds.contains(markerId)) {
                missingCitations.add("El marcador " + citationMarkers.get(markerId) + " no esta incluido en citedPublications.");
            }
        }

        for (Long citedId : citedIds) {
            if (!citationMarkers.containsKey(citedId)) {
                citationIssues.add("La publicacion citada [pub:" + citedId + "] no aparece como marcador en la respuesta.");
            }
        }

        if (citedPublications.isEmpty() && makesPublicationSpecificClaims(answer)) {
            missingCitations.add("La respuesta parece contener afirmaciones sobre publicaciones, pero citedPublications esta vacio.");
            unsupportedClaims.addAll(publicationSpecificSentences(answer));
        }

        unsupportedClaims = distinctNonBlank(unsupportedClaims);
        missingCitations = distinctNonBlank(missingCitations);
        citationIssues = distinctNonBlank(citationIssues);
        warnings = distinctNonBlank(warnings);

        CopilotAnswerSupportLevel supportLevel = supportLevel(unsupportedClaims, missingCitations, citationIssues, warnings, citedIds);
        return new CopilotAnswerEvaluationResponse(
            supportLevel,
            unsupportedClaims,
            missingCitations,
            citationIssues,
            summaryFor(supportLevel, unsupportedClaims, missingCitations, citationIssues, warnings),
            warnings
        );
    }

    private Set<Long> idsFromCitedPublications(List<CopilotCitationResponse> citedPublications) {
        Set<Long> ids = new LinkedHashSet<>();
        for (CopilotCitationResponse publication : citedPublications) {
            if (publication.id() != null) {
                ids.add(publication.id());
            }
        }
        return ids;
    }

    private Set<Long> idsFromRetrievedPublications(List<CopilotRetrievedPublicationResponse> retrievedPublications) {
        Set<Long> ids = new LinkedHashSet<>();
        for (CopilotRetrievedPublicationResponse publication : retrievedPublications) {
            if (publication.id() != null) {
                ids.add(publication.id());
            }
        }
        return ids;
    }

    private Map<Long, String> citationMarkers(String answer) {
        Map<Long, String> markers = new LinkedHashMap<>();
        Matcher matcher = CITATION_MARKER_PATTERN.matcher(answer);
        while (matcher.find()) {
            markers.putIfAbsent(Long.valueOf(matcher.group(1)), matcher.group());
        }
        return markers;
    }

    private List<String> unknownCitationMarkers(String answer) {
        List<String> markers = new ArrayList<>();
        Matcher pubLikeMatcher = PUB_LIKE_MARKER_PATTERN.matcher(answer);
        while (pubLikeMatcher.find()) {
            if (!CITATION_MARKER_PATTERN.matcher(pubLikeMatcher.group()).matches()) {
                markers.add(pubLikeMatcher.group());
            }
        }
        Matcher otherMatcher = OTHER_CITATION_LIKE_MARKER_PATTERN.matcher(answer);
        while (otherMatcher.find()) {
            markers.add(otherMatcher.group());
        }
        return distinctNonBlank(markers);
    }

    private java.util.Optional<String> sentenceContaining(String answer, String marker) {
        for (String sentence : sentences(answer)) {
            if (sentence.contains(marker)) {
                return java.util.Optional.of(sentence);
            }
        }
        return java.util.Optional.empty();
    }

    private boolean makesPublicationSpecificClaims(String answer) {
        return !publicationSpecificSentences(answer).isEmpty();
    }

    private List<String> publicationSpecificSentences(String answer) {
        List<String> claims = new ArrayList<>();
        for (String sentence : sentences(answer)) {
            String normalized = normalize(sentence);
            if (
                normalized.contains("publicacion")
                    || normalized.contains("publication")
                    || normalized.contains("estudio")
                    || normalized.contains("study")
                    || normalized.contains("articulo")
                    || normalized.contains("article")
                    || normalized.contains("paper")
                    || normalized.contains("doi")
                    || normalized.contains("autores")
                    || normalized.contains("authors")
                    || normalized.contains("revista")
                    || normalized.contains("journal")
                    || normalized.contains("evidencia")
                    || normalized.contains("evidence")
            ) {
                claims.add(sentence);
            }
        }
        return distinctNonBlank(claims);
    }

    private List<String> sentences(String answer) {
        String trimmed = answer == null ? "" : answer.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        List<String> sentences = new ArrayList<>();
        for (String sentence : SENTENCE_SPLIT_PATTERN.split(trimmed)) {
            if (!sentence.isBlank()) {
                sentences.add(sentence.trim());
            }
        }
        return sentences;
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private List<String> distinctNonBlank(List<String> values) {
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }

    private CopilotAnswerSupportLevel supportLevel(
        List<String> unsupportedClaims,
        List<String> missingCitations,
        List<String> citationIssues,
        List<String> warnings,
        Set<Long> citedIds
    ) {
        if (!unsupportedClaims.isEmpty() || citationIssues.stream().anyMatch(issue -> issue.contains("no esta en el contexto"))) {
            return CopilotAnswerSupportLevel.LOW;
        }
        if (!citationIssues.isEmpty() || !missingCitations.isEmpty() || !warnings.isEmpty() || citedIds.isEmpty()) {
            return CopilotAnswerSupportLevel.MEDIUM;
        }
        return CopilotAnswerSupportLevel.HIGH;
    }

    private String summaryFor(
        CopilotAnswerSupportLevel supportLevel,
        List<String> unsupportedClaims,
        List<String> missingCitations,
        List<String> citationIssues,
        List<String> warnings
    ) {
        if (supportLevel == CopilotAnswerSupportLevel.HIGH) {
            return "La respuesta usa citas reconocidas que pertenecen al contexto recuperado.";
        }
        if (supportLevel == CopilotAnswerSupportLevel.LOW) {
            return "La respuesta tiene problemas de grounding que requieren revision antes de confiar en ella.";
        }
        if (!citationIssues.isEmpty()) {
            return "La respuesta esta parcialmente citada, pero hay incidencias en las citas.";
        }
        if (!missingCitations.isEmpty() || !warnings.isEmpty()) {
            return "La respuesta necesita citas mas explicitas para considerarse bien respaldada.";
        }
        return "La respuesta tiene respaldo parcial en el contexto recuperado.";
    }
}
