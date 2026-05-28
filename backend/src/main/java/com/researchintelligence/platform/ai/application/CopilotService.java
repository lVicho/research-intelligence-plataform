package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.ai.api.CopilotAnswerRequest;
import com.researchintelligence.platform.ai.api.CopilotAnswerEvaluationRequest;
import com.researchintelligence.platform.ai.api.CopilotAnswerEvaluationResponse;
import com.researchintelligence.platform.ai.api.CopilotAnswerResponse;
import com.researchintelligence.platform.ai.api.CopilotAskRequest;
import com.researchintelligence.platform.ai.api.CopilotAskResponse;
import com.researchintelligence.platform.ai.api.CopilotCitationResponse;
import com.researchintelligence.platform.ai.api.CopilotRetrieveRequest;
import com.researchintelligence.platform.ai.api.CopilotRetrieveResponse;
import com.researchintelligence.platform.ai.api.CopilotRetrievedPublicationResponse;
import com.researchintelligence.platform.ai.api.CopilotSignalResponse;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CopilotService {

    private static final Pattern CITATION_MARKER_PATTERN = Pattern.compile("\\[(?:pub|publication):(\\d+)]", Pattern.CASE_INSENSITIVE);
    private static final String MISSING_CITATION_CONTEXT_WARNING = "La respuesta contiene una cita que no está en el contexto recuperado.";
    private static final String NO_EXPLICIT_CITATIONS_WARNING = "La respuesta no contiene citas explícitas a publicaciones.";

    private final LlmService llmService;
    private final EmbeddingService embeddingService;
    private final PublicationRetrievalService retrievalService;
    private final CopilotAnswerEvaluationService answerEvaluationService;
    private final AiProperties properties;
    private final VisibilityContext visibilityContext;

    public CopilotService(
        LlmService llmService,
        EmbeddingService embeddingService,
        PublicationRetrievalService retrievalService,
        CopilotAnswerEvaluationService answerEvaluationService,
        AiProperties properties,
        VisibilityContext visibilityContext
    ) {
        this.llmService = llmService;
        this.embeddingService = embeddingService;
        this.retrievalService = retrievalService;
        this.answerEvaluationService = answerEvaluationService;
        this.properties = properties;
        this.visibilityContext = visibilityContext;
    }

    public CopilotAskResponse ask(CopilotAskRequest request) {
        CopilotRetrieveResponse retrieval = retrieve(new CopilotRetrieveRequest(
            request.question(),
            request.limit(),
            null,
            null,
            request.includeNonValidated()
        ));
        CopilotAnswerResponse answer = answer(new CopilotAnswerRequest(
            request.question(),
            retrieval.retrievedPublications(),
            request.includeNonValidated()
        ));
        List<String> warnings = new ArrayList<>(retrieval.warnings());
        warnings.addAll(answer.warnings());
        return new CopilotAskResponse(
            answer.answerRaw(),
            answer.answer(),
            retrieval.retrievedPublications(),
            answer.citedPublications(),
            answer.provider(),
            answer.model(),
            retrieval.embeddingProvider(),
            retrieval.embeddingModel(),
            retrieval.retrievalMethod(),
            retrieval.retrievalMode(),
            retrieval.minSimilarity(),
            retrieval.detectedTopics(),
            retrieval.bridgingAuthors(),
            warnings.stream().distinct().toList(),
            answer.evaluation(),
            retrieval.visibilityScope(),
            retrieval.validationFilterApplied()
        );
    }

    public CopilotRetrieveResponse retrieve(CopilotRetrieveRequest request) {
        VisibilityScope visibilityScope = resolvePublicScope(request.includeNonValidated());
        PublicationRetrievalResult retrievalResult = retrievalService.retrieveBest(
            request.question(),
            new RetrievalOptions(request.limit(), request.minSimilarity(), request.retrievalMode()),
            visibilityScope,
            null
        );
        List<CopilotRetrievedPublicationResponse> publications = retrievalResult.publications()
            .stream()
            .map(this::toRetrievedPublication)
            .toList();
        List<String> warnings = new ArrayList<>(retrievalResult.warnings());
        if (publications.isEmpty()) {
            warnings.add("No se han encontrado publicaciones suficientemente relacionadas.");
        }
        return new CopilotRetrieveResponse(
            retrievalResult.retrievalMethod().name(),
            retrievalResult.retrievalMode().name(),
            retrievalResult.minSimilarity(),
            embeddingService.provider(),
            embeddingService.model(),
            llmService.provider(),
            llmService.model(),
            publications,
            topSignals(publications.stream().flatMap(publication -> publication.topics().stream()).toList(), 8, false),
            topSignals(publications.stream().flatMap(publication -> publication.authors().stream()).toList(), 6, true),
            warnings.stream().distinct().toList(),
            retrievalResult.visibilityScope().name(),
            retrievalResult.validationFilterApplied()
        );
    }

    public CopilotAnswerResponse answer(CopilotAnswerRequest request) {
        VisibilityScope visibilityScope = resolvePublicScope(request.includeNonValidated());
        List<CopilotRetrievedPublicationResponse> publications = filterVisibleRetrievedPublications(
            request.retrievedPublications() == null ? List.of() : request.retrievedPublications(),
            visibilityScope
        );
        List<String> warnings = new ArrayList<>();
        if ("mock".equals(llmService.provider())) {
            warnings.add("El proveedor de IA mock está activo. Configura ai.provider=ollama para respuestas del modelo local.");
        }
        if (publications.stream().anyMatch(CopilotRetrievedPublicationResponse::lowSimilarity)) {
            warnings.add("Algunos resultados tienen baja similitud; interpreta la respuesta con cautela.");
        }
        if (publications.isEmpty() && !properties.getRetrieval().isAllowNoContextAnswers()) {
            warnings.add("No se han encontrado publicaciones suficientemente relacionadas.");
            warnings.add(NO_EXPLICIT_CITATIONS_WARNING);
            String answerText = "No hay suficiente contexto recuperado para responder con evidencia.";
            CopilotAnswerEvaluationResponse evaluation = maybeEvaluate(answerText, List.of(), publications);
            return new CopilotAnswerResponse(
                answerText,
                answerText,
                publications,
                List.of(),
                llmService.provider(),
                llmService.model(),
                warnings,
                evaluation,
                visibilityScope.name(),
                validationFilterApplied(visibilityScope)
            );
        }

        LlmResponse llmResponse = llmService.answer(new LlmPrompt(request.question(), buildContextFromRetrieved(publications, warnings)));
        warnings.addAll(llmResponse.warnings());
        List<CopilotCitationResponse> citedPublications = extractCitedPublications(llmResponse.answer(), publications, warnings);
        CopilotAnswerEvaluationResponse evaluation = maybeEvaluate(llmResponse.answer(), citedPublications, publications);
        return new CopilotAnswerResponse(
            llmResponse.answer(),
            llmResponse.answer(),
            publications,
            citedPublications,
            llmService.provider(),
            llmService.model(),
            warnings.stream().distinct().toList(),
            evaluation,
            visibilityScope.name(),
            validationFilterApplied(visibilityScope)
        );
    }

    private CopilotAnswerEvaluationResponse maybeEvaluate(
        String answer,
        List<CopilotCitationResponse> citedPublications,
        List<CopilotRetrievedPublicationResponse> retrievedPublications
    ) {
        if (!properties.getCopilot().isAnswerEvaluationEnabled()) {
            return null;
        }
        return answerEvaluationService.evaluate(new CopilotAnswerEvaluationRequest(answer, citedPublications, retrievedPublications));
    }

    private List<CopilotRetrievedPublicationResponse> filterVisibleRetrievedPublications(
        List<CopilotRetrievedPublicationResponse> publications,
        VisibilityScope visibilityScope
    ) {
        if (publications.isEmpty()) {
            return List.of();
        }
        Set<Long> visibleIds = retrievalService.visiblePublicationIds(
            publications.stream().map(CopilotRetrievedPublicationResponse::id).toList(),
            visibilityScope,
            null
        );
        return publications.stream()
            .filter(publication -> visibleIds.contains(publication.id()))
            .toList();
    }

    private String buildContextFromRetrieved(List<CopilotRetrievedPublicationResponse> publications, List<String> warnings) {
        StringBuilder builder = new StringBuilder();
        if (!warnings.isEmpty()) {
            builder.append("Advertencias sobre la calidad del contexto:\n")
                .append(String.join("\n", warnings))
                .append("\n\n");
        }
        for (CopilotRetrievedPublicationResponse publication : publications) {
            builder.append("[pub:")
                .append(publication.id())
                .append("]\nTítulo: ")
                .append(publication.title())
                .append("\nAño: ")
                .append(publication.year() == null ? "desconocido" : publication.year())
                .append("\nAutores: ")
                .append(publication.authors() == null || publication.authors().isEmpty() ? "desconocidos" : String.join(", ", publication.authors()))
                .append("\nTemas: ")
                .append(publication.topics() == null || publication.topics().isEmpty() ? "sin temas" : String.join(", ", publication.topics()))
                .append("\nFuente: ")
                .append(publication.source() == null || publication.source().isBlank() ? "sin fuente" : publication.source())
                .append("\nResumen: ")
                .append(publication.abstractText() == null || publication.abstractText().isBlank() ? "Resumen no disponible." : publication.abstractText());
            builder.append("\n\n");
        }
        return builder.toString().trim();
    }

    private CopilotRetrievedPublicationResponse toRetrievedPublication(RetrievedPublicationContext context) {
        PublicationEntity publication = context.publication();
        return new CopilotRetrievedPublicationResponse(
            publication.getId(),
            publication.getTitle(),
            publication.getAbstractText(),
            publication.getPublicationYear(),
            publication.getDoi(),
            publication.getSource(),
            publication.getUrl(),
            context.authors(),
            context.topics(),
            context.similarityScore(),
            context.passedThreshold(),
            context.lowSimilarity(),
            context.retrievalReason()
        );
    }

    private List<CopilotCitationResponse> extractCitedPublications(
        String answer,
        List<CopilotRetrievedPublicationResponse> retrievedPublications,
        List<String> warnings
    ) {
        Map<Long, CopilotRetrievedPublicationResponse> retrievedById = new LinkedHashMap<>();
        for (CopilotRetrievedPublicationResponse publication : retrievedPublications) {
            retrievedById.putIfAbsent(publication.id(), publication);
        }

        List<CopilotCitationResponse> citedPublications = new ArrayList<>();
        Map<Long, Boolean> citedIds = new LinkedHashMap<>();
        Matcher matcher = CITATION_MARKER_PATTERN.matcher(answer == null ? "" : answer);
        boolean foundMarker = false;
        boolean foundMarkerOutsideContext = false;
        while (matcher.find()) {
            foundMarker = true;
            Long publicationId = Long.valueOf(matcher.group(1));
            CopilotRetrievedPublicationResponse publication = retrievedById.get(publicationId);
            if (publication == null) {
                foundMarkerOutsideContext = true;
                continue;
            }
            if (!citedIds.containsKey(publicationId)) {
                citedIds.put(publicationId, true);
                citedPublications.add(toCitation(publication, citedPublications.size() + 1));
            }
        }
        if (!foundMarker) {
            warnings.add(NO_EXPLICIT_CITATIONS_WARNING);
        }
        if (foundMarkerOutsideContext) {
            warnings.add(MISSING_CITATION_CONTEXT_WARNING);
        }
        return citedPublications;
    }

    private CopilotCitationResponse toCitation(CopilotRetrievedPublicationResponse publication, int citationIndex) {
        return new CopilotCitationResponse(
            publication.id(),
            citationIndex,
            publication.title(),
            publication.year(),
            publication.authors() == null ? List.of() : publication.authors(),
            publication.topics() == null ? List.of() : publication.topics(),
            publication.doi(),
            publication.source(),
            publication.url(),
            publication.similarityScore()
        );
    }

    private List<CopilotSignalResponse> topSignals(List<String> values, int limit, boolean repeatedOnly) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = value.trim();
            counts.put(normalized, counts.getOrDefault(normalized, 0L) + 1);
        }
        return counts.entrySet()
            .stream()
            .map(entry -> new CopilotSignalResponse(entry.getKey(), entry.getValue()))
            .filter(signal -> !repeatedOnly || signal.count() > 1)
            .sorted(Comparator.comparing(CopilotSignalResponse::count).reversed().thenComparing(CopilotSignalResponse::name))
            .limit(limit)
            .toList();
    }

    private VisibilityScope resolvePublicScope(Boolean includeNonValidated) {
        if (Boolean.TRUE.equals(includeNonValidated) && visibilityContext.currentRoles().contains("ADMIN")) {
            return VisibilityScope.ADMIN_ALL;
        }
        return VisibilityScope.PUBLIC_VALIDATED;
    }

    private boolean validationFilterApplied(VisibilityScope visibilityScope) {
        return visibilityScope != VisibilityScope.ADMIN_ALL;
    }
}
