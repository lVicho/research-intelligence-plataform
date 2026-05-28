package com.researchintelligence.platform.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.api.PublicSummaryAudience;
import com.researchintelligence.platform.ai.api.PublicSummaryGenerateRequest;
import com.researchintelligence.platform.ai.api.PublicSummaryGenerateResponse;
import com.researchintelligence.platform.ai.api.PublicSummaryStyle;
import com.researchintelligence.platform.ai.api.PublicSummaryTargetType;
import com.researchintelligence.platform.ai.api.ResearcherAssistantActivityResponse;
import com.researchintelligence.platform.ai.api.ResearcherAssistantAskRequest;
import com.researchintelligence.platform.ai.api.ResearcherAssistantMode;
import com.researchintelligence.platform.ai.api.ResearcherAssistantResponse;
import com.researchintelligence.platform.ai.api.ResearcherAssistantSuggestionResponse;
import com.researchintelligence.platform.ai.api.TopicRecommendationRequest;
import com.researchintelligence.platform.ai.api.TopicRecommendationResponse;
import com.researchintelligence.platform.ai.api.TopicRecommendationTargetType;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.persistence.ResearcherActivityRow;
import com.researchintelligence.platform.auth.persistence.ResearcherWorkspaceRepository;
import com.researchintelligence.platform.dataquality.persistence.DataQualityIssueRow;
import com.researchintelligence.platform.dataquality.persistence.DataQualityRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResearcherAssistantService {

    private static final int STATUS_ACTIVITY_LIMIT = 20;
    private static final int RELATED_ACTIVITY_LIMIT = 12;
    private static final int QUALITY_ISSUE_LIMIT = 10;
    private static final int CREATED_QUALITY_SUGGESTION_LIMIT = 3;
    private static final int PUBLIC_CONTEXT_LIMIT = 3;
    private static final Set<ValidationEntityType> SUGGESTION_SUPPORTED_TARGETS = Set.of(
        ValidationEntityType.RESEARCHER,
        ValidationEntityType.PUBLICATION,
        ValidationEntityType.RESEARCHER_AFFILIATION,
        ValidationEntityType.EVENT_PARTICIPATION
    );

    private final ResearcherWorkspaceRepository workspaceRepository;
    private final DataQualityRepository dataQualityRepository;
    private final PublicationRetrievalService retrievalService;
    private final PublicSummaryGenerationService publicSummaryGenerationService;
    private final TopicRecommendationService topicRecommendationService;
    private final AiSuggestionService aiSuggestionService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public ResearcherAssistantService(
        ResearcherWorkspaceRepository workspaceRepository,
        DataQualityRepository dataQualityRepository,
        PublicationRetrievalService retrievalService,
        PublicSummaryGenerationService publicSummaryGenerationService,
        TopicRecommendationService topicRecommendationService,
        AiSuggestionService aiSuggestionService,
        LlmService llmService,
        ObjectMapper objectMapper
    ) {
        this.workspaceRepository = workspaceRepository;
        this.dataQualityRepository = dataQualityRepository;
        this.retrievalService = retrievalService;
        this.publicSummaryGenerationService = publicSummaryGenerationService;
        this.topicRecommendationService = topicRecommendationService;
        this.aiSuggestionService = aiSuggestionService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResearcherAssistantResponse ask(PlatformUserPrincipal user, ResearcherAssistantAskRequest request) {
        validate(request);
        Long researcherId = requireLinkedResearcher(user);
        List<String> warnings = new ArrayList<>();
        if (user.roles().contains("ADMIN")) {
            warnings.add("Admin acting as linked researcher; this assistant did not use admin-wide private data.");
        }

        WorkspaceSnapshot snapshot = snapshot(researcherId, request, warnings);
        List<ResearcherAssistantSuggestionResponse> suggestions = createSuggestions(request, snapshot, warnings);
        List<ResearcherAssistantActivityResponse> relatedActivities = relatedActivities(request, snapshot);
        List<String> actionItems = actionItems(request.mode(), snapshot, suggestions);
        String fallbackAnswer = deterministicAnswer(request.mode(), snapshot, suggestions, actionItems);
        String answer = generatedAnswer(request, snapshot, suggestions, actionItems, fallbackAnswer, warnings);

        return new ResearcherAssistantResponse(
            answer,
            distinct(actionItems),
            relatedActivities,
            suggestions,
            distinct(warnings),
            llmService.provider(),
            llmService.model()
        );
    }

    private void validate(ResearcherAssistantAskRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assistant request is required.");
        }
        if (request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question is required.");
        }
        if (request.mode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode is required.");
        }
    }

    private Long requireLinkedResearcher(PlatformUserPrincipal user) {
        if (user == null || user.researcherId() == null) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Researcher assistant requires an authenticated user linked to a researcher profile. Admin role does not enable private assistant access without a linked researcher."
            );
        }
        return user.researcherId();
    }

    private WorkspaceSnapshot snapshot(Long researcherId, ResearcherAssistantAskRequest request, List<String> warnings) {
        ResearcherActivityRow profile = workspaceRepository.findOwnedActivity(researcherId, ValidationEntityType.RESEARCHER, researcherId)
            .filter(row -> ownedByResearcher(row, researcherId))
            .orElseThrow(() -> new ResourceNotFoundException("Researcher", researcherId));
        List<ResearcherActivityRow> pending = activitiesByStatus(researcherId, ValidationStatus.PENDING_VALIDATION);
        List<ResearcherActivityRow> changesRequested = activitiesByStatus(researcherId, ValidationStatus.CHANGES_REQUESTED);
        List<ResearcherActivityRow> drafts = activitiesByStatus(researcherId, ValidationStatus.DRAFT);
        List<ResearcherActivityRow> rejected = activitiesByStatus(researcherId, ValidationStatus.REJECTED);
        List<DataQualityIssueRow> qualityIssues = dataQualityRepository.search(
            null,
            null,
            null,
            null,
            null,
            0,
            QUALITY_ISSUE_LIMIT,
            researcherId
        ).getContent();
        List<RetrievedPublicationContext> publicContext = publicValidatedContext(request, warnings);

        return new WorkspaceSnapshot(
            researcherId,
            profile,
            pending,
            changesRequested,
            drafts,
            rejected,
            qualityIssues,
            publicContext
        );
    }

    private List<ResearcherActivityRow> activitiesByStatus(Long researcherId, ValidationStatus status) {
        Page<ResearcherActivityRow> page = workspaceRepository.activities(
            researcherId,
            status,
            null,
            null,
            0,
            STATUS_ACTIVITY_LIMIT
        );
        return page.getContent()
            .stream()
            .filter(row -> ownedByResearcher(row, researcherId))
            .toList();
    }

    private boolean ownedByResearcher(ResearcherActivityRow row, Long researcherId) {
        if (row == null || researcherId == null) {
            return false;
        }
        if (row.entityType() == ValidationEntityType.RESEARCHER) {
            return researcherId.equals(row.entityId());
        }
        return researcherId.equals(row.researcherId());
    }

    private List<RetrievedPublicationContext> publicValidatedContext(
        ResearcherAssistantAskRequest request,
        List<String> warnings
    ) {
        if (!needsPublicContext(request)) {
            return List.of();
        }
        try {
            PublicationRetrievalResult result = retrievalService.retrieveBest(
                request.question(),
                new RetrievalOptions(PUBLIC_CONTEXT_LIMIT, null, RetrievalMode.BALANCED),
                VisibilityScope.PUBLIC_VALIDATED,
                null
            );
            warnings.addAll(result.warnings());
            return result.publications();
        } catch (BusinessRuleException exception) {
            warnings.add(publicContextWarning(exception.getMessage()));
            return retrievalService.textSearch(request.question(), PUBLIC_CONTEXT_LIMIT, VisibilityScope.PUBLIC_VALIDATED, null);
        }
    }

    private boolean needsPublicContext(ResearcherAssistantAskRequest request) {
        if (request.mode() == ResearcherAssistantMode.GENERAL) {
            return true;
        }
        String text = normalized(request.question());
        return text.contains("public")
            || text.contains("valid")
            || text.contains("topic")
            || text.contains("tema")
            || text.contains("similar")
            || text.contains("context");
    }

    private List<ResearcherAssistantSuggestionResponse> createSuggestions(
        ResearcherAssistantAskRequest request,
        WorkspaceSnapshot snapshot,
        List<String> warnings
    ) {
        List<ResearcherAssistantSuggestionResponse> suggestions = new ArrayList<>();
        if (shouldCreateProfileSuggestion(request)) {
            createProfileSummarySuggestion(snapshot, warnings).stream().forEach(suggestions::add);
        }
        if (shouldCreateQualitySuggestions(request)) {
            suggestions.addAll(createQualitySuggestions(snapshot, warnings));
        }
        if (shouldCreateTopicSuggestion(request)) {
            createTopicSuggestion(snapshot, warnings).stream().forEach(suggestions::add);
        }
        return suggestions;
    }

    private boolean shouldCreateProfileSuggestion(ResearcherAssistantAskRequest request) {
        String text = normalized(request.question());
        return request.mode() == ResearcherAssistantMode.PROFILE
            || text.contains("perfil publico")
            || text.contains("public profile")
            || text.contains("resumen publico")
            || text.contains("profile summary");
    }

    private boolean shouldCreateQualitySuggestions(ResearcherAssistantAskRequest request) {
        String text = normalized(request.question());
        return request.mode() == ResearcherAssistantMode.QUALITY
            || text.contains("calidad")
            || text.contains("quality")
            || text.contains("incidencia")
            || text.contains("issue");
    }

    private boolean shouldCreateTopicSuggestion(ResearcherAssistantAskRequest request) {
        String text = normalized(request.question());
        return text.contains("tema")
            || text.contains("topic")
            || text.contains("keyword")
            || text.contains("palabra clave");
    }

    private List<ResearcherAssistantSuggestionResponse> createProfileSummarySuggestion(
        WorkspaceSnapshot snapshot,
        List<String> warnings
    ) {
        try {
            PublicSummaryGenerateResponse response = publicSummaryGenerationService.generate(new PublicSummaryGenerateRequest(
                PublicSummaryTargetType.RESEARCHER,
                snapshot.researcherId(),
                PublicSummaryStyle.STANDARD,
                PublicSummaryAudience.PUBLIC
            ));
            warnings.addAll(response.warnings());
            return List.of(new ResearcherAssistantSuggestionResponse(
                response.createdSuggestionId(),
                AiSuggestionType.PUBLIC_SUMMARY,
                "RESEARCHER",
                snapshot.researcherId(),
                "Resumen publico de perfil",
                response.summary()
            ));
        } catch (BusinessRuleException exception) {
            warnings.add("No se pudo crear la sugerencia de resumen publico: " + exception.getMessage());
            return List.of();
        }
    }

    private List<ResearcherAssistantSuggestionResponse> createQualitySuggestions(
        WorkspaceSnapshot snapshot,
        List<String> warnings
    ) {
        List<ResearcherAssistantSuggestionResponse> suggestions = new ArrayList<>();
        for (DataQualityIssueRow issue : snapshot.qualityIssues()) {
            if (suggestions.size() >= CREATED_QUALITY_SUGGESTION_LIMIT) {
                break;
            }
            ValidationEntityType targetType = suggestionTargetType(issue);
            if (targetType == null) {
                warnings.add("Some data quality issues require manual review and were not converted into AI suggestions.");
                continue;
            }
            suggestions.add(createQualitySuggestion(issue, targetType, warnings));
        }
        return suggestions.stream()
            .filter(suggestion -> suggestion != null)
            .toList();
    }

    private ValidationEntityType suggestionTargetType(DataQualityIssueRow issue) {
        try {
            ValidationEntityType targetType = ValidationEntityType.valueOf(issue.entityType().name());
            return SUGGESTION_SUPPORTED_TARGETS.contains(targetType) ? targetType : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private ResearcherAssistantSuggestionResponse createQualitySuggestion(
        DataQualityIssueRow issue,
        ValidationEntityType targetType,
        List<String> warnings
    ) {
        try {
            Long suggestionId = aiSuggestionService.create(new AiSuggestionCreateCommand(
                targetType.name(),
                issue.entityId(),
                AiSuggestionType.DATA_QUALITY_FIX,
                writeJson(orderedMap(
                    "action", "review_data_quality_issue",
                    "issueType", issue.issueType().name(),
                    "suggestedAction", issue.suggestedAction(),
                    "requiresHumanReview", true
                )),
                "Generated a researcher assistant data quality suggestion. No records were modified automatically.",
                writeJson(orderedMap(
                    "issueType", issue.issueType().name(),
                    "severity", issue.severity().name(),
                    "description", issue.description(),
                    "source", "researcher_assistant"
                )),
                llmService.provider(),
                llmService.model()
            )).id();
            return new ResearcherAssistantSuggestionResponse(
                suggestionId,
                AiSuggestionType.DATA_QUALITY_FIX,
                targetType.name(),
                issue.entityId(),
                qualityLabel(issue),
                issue.suggestedAction()
            );
        } catch (BusinessRuleException exception) {
            warnings.add("No se pudo crear una sugerencia de calidad de datos: " + exception.getMessage());
            return null;
        }
    }

    private List<ResearcherAssistantSuggestionResponse> createTopicSuggestion(
        WorkspaceSnapshot snapshot,
        List<String> warnings
    ) {
        ResearcherActivityRow target = candidatePublicationForTopics(snapshot);
        if (target == null) {
            warnings.add("No hay una publicacion propia no validada adecuada para crear recomendaciones de temas.");
            return List.of();
        }
        try {
            TopicRecommendationResponse response = topicRecommendationService.recommend(new TopicRecommendationRequest(
                TopicRecommendationTargetType.PUBLICATION,
                target.entityId(),
                target.title(),
                null,
                List.of(),
                8
            ));
            warnings.addAll(response.warnings());
            String labels = response.suggestedTopics().stream()
                .map(topic -> topic.label())
                .collect(Collectors.joining(", "));
            return List.of(new ResearcherAssistantSuggestionResponse(
                response.aiSuggestionId(),
                AiSuggestionType.TOPIC_RECOMMENDATION,
                "PUBLICATION",
                target.entityId(),
                "Temas recomendados para publicacion",
                labels.isBlank() ? "No se encontraron temas suficientemente fundamentados." : labels
            ));
        } catch (BusinessRuleException exception) {
            warnings.add(topicWarning(exception.getMessage()));
            return List.of();
        }
    }

    private ResearcherActivityRow candidatePublicationForTopics(WorkspaceSnapshot snapshot) {
        return allPrivateActivities(snapshot).stream()
            .filter(row -> row.entityType() == ValidationEntityType.PUBLICATION)
            .filter(row -> row.validationStatus() != ValidationStatus.VALIDATED)
            .min(Comparator.comparing(row -> row.topicCount() == null ? 0L : row.topicCount()))
            .orElse(null);
    }

    private List<ResearcherAssistantActivityResponse> relatedActivities(
        ResearcherAssistantAskRequest request,
        WorkspaceSnapshot snapshot
    ) {
        List<ResearcherActivityRow> selected = switch (request.mode()) {
            case TASKS -> prioritizedActivities(snapshot);
            case QUALITY -> activitiesForQualityIssues(snapshot);
            case PROFILE -> profileActivities(snapshot);
            case GENERAL -> generalActivities(request, snapshot);
        };
        return selected.stream()
            .filter(row -> ownedByResearcher(row, snapshot.researcherId()))
            .distinct()
            .limit(RELATED_ACTIVITY_LIMIT)
            .map(this::toActivityResponse)
            .toList();
    }

    private List<ResearcherActivityRow> prioritizedActivities(WorkspaceSnapshot snapshot) {
        List<ResearcherActivityRow> activities = new ArrayList<>();
        activities.addAll(snapshot.changesRequested());
        activities.addAll(snapshot.drafts());
        activities.addAll(snapshot.rejected());
        activities.addAll(snapshot.pending());
        return distinctActivities(activities);
    }

    private List<ResearcherActivityRow> activitiesForQualityIssues(WorkspaceSnapshot snapshot) {
        Map<String, ResearcherActivityRow> activitiesByKey = allPrivateActivities(snapshot).stream()
            .collect(Collectors.toMap(this::activityKey, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        return snapshot.qualityIssues().stream()
            .map(issue -> activitiesByKey.get(issue.entityType().name() + ":" + issue.entityId()))
            .filter(row -> row != null)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<ResearcherActivityRow> profileActivities(WorkspaceSnapshot snapshot) {
        List<ResearcherActivityRow> activities = new ArrayList<>();
        activities.add(snapshot.profile());
        allPrivateActivities(snapshot).stream()
            .filter(row -> row.entityType() == ValidationEntityType.RESEARCHER_AFFILIATION)
            .forEach(activities::add);
        return distinctActivities(activities);
    }

    private List<ResearcherActivityRow> generalActivities(ResearcherAssistantAskRequest request, WorkspaceSnapshot snapshot) {
        String text = normalized(request.question());
        if (text.contains("pendiente") || text.contains("pending")) {
            return snapshot.pending();
        }
        if (text.contains("cambio") || text.contains("changes")) {
            return snapshot.changesRequested();
        }
        if (text.contains("calidad") || text.contains("quality")) {
            return activitiesForQualityIssues(snapshot);
        }
        return prioritizedActivities(snapshot);
    }

    private ResearcherAssistantActivityResponse toActivityResponse(ResearcherActivityRow row) {
        return new ResearcherAssistantActivityResponse(
            row.entityType(),
            row.entityId(),
            row.title(),
            row.submittedAt(),
            row.validationStatus(),
            row.validationComment(),
            dataQualityReminders(row),
            canEdit(row.validationStatus()),
            canSubmit(row.validationStatus())
        );
    }

    private List<String> actionItems(
        ResearcherAssistantMode mode,
        WorkspaceSnapshot snapshot,
        List<ResearcherAssistantSuggestionResponse> suggestions
    ) {
        List<String> items = new ArrayList<>();
        if (!snapshot.changesRequested().isEmpty()) {
            items.add("Prioriza las actividades con cambios solicitados y responde a los comentarios de validacion.");
        }
        if (!snapshot.drafts().isEmpty()) {
            items.add("Completa los borradores y envialos a validacion cuando esten listos.");
        }
        if (!snapshot.rejected().isEmpty()) {
            items.add("Revisa las actividades rechazadas antes de reutilizar o reenviar la informacion.");
        }
        if (mode == ResearcherAssistantMode.QUALITY && !snapshot.qualityIssues().isEmpty()) {
            items.add("Revisa las incidencias de calidad listadas; ninguna se corrige automaticamente.");
        }
        if (mode == ResearcherAssistantMode.PROFILE) {
            items.add("Revisa la sugerencia de resumen publico antes de aceptarla o editarla.");
        }
        if (!suggestions.isEmpty()) {
            items.add("Revisa las sugerencias IA generadas; requieren aprobacion humana antes de aplicar cambios.");
        }
        if (items.isEmpty()) {
            items.add("No hay acciones urgentes detectadas en tus datos propios con el contexto actual.");
        }
        return items;
    }

    private String generatedAnswer(
        ResearcherAssistantAskRequest request,
        WorkspaceSnapshot snapshot,
        List<ResearcherAssistantSuggestionResponse> suggestions,
        List<String> actionItems,
        String fallbackAnswer,
        List<String> warnings
    ) {
        if (!"ollama".equalsIgnoreCase(llmService.provider())) {
            if ("mock".equalsIgnoreCase(llmService.provider())) {
                warnings.add("El proveedor LLM mock esta activo; se devolvio una respuesta determinista basada en contexto.");
            }
            return fallbackAnswer;
        }
        try {
            LlmResponse response = llmService.answer(new LlmPrompt(
                assistantQuestion(request),
                assistantContext(snapshot, suggestions, actionItems)
            ));
            warnings.addAll(response.warnings());
            String answer = normalizeWhitespace(response.answer());
            if (answer.isBlank()) {
                warnings.add("El proveedor de IA no devolvio contenido; se uso una respuesta determinista.");
                return fallbackAnswer;
            }
            return answer;
        } catch (BusinessRuleException exception) {
            warnings.add(ollamaWarning(exception.getMessage()));
            return fallbackAnswer;
        }
    }

    private String deterministicAnswer(
        ResearcherAssistantMode mode,
        WorkspaceSnapshot snapshot,
        List<ResearcherAssistantSuggestionResponse> suggestions,
        List<String> actionItems
    ) {
        return switch (mode) {
            case TASKS -> taskAnswer(snapshot, actionItems);
            case QUALITY -> qualityAnswer(snapshot, suggestions, actionItems);
            case PROFILE -> profileAnswer(snapshot, suggestions, actionItems);
            case GENERAL -> generalAnswer(snapshot, suggestions, actionItems);
        };
    }

    private String taskAnswer(WorkspaceSnapshot snapshot, List<String> actionItems) {
        String statusSummary = "Tienes %d actividad(es) pendiente(s), %d con cambios solicitados, %d borrador(es) y %d rechazada(s).".formatted(
            snapshot.pending().size(),
            snapshot.changesRequested().size(),
            snapshot.drafts().size(),
            snapshot.rejected().size()
        );
        String comments = validationComments(snapshot.changesRequested());
        return joinSentences(statusSummary, comments, "Siguiente paso: " + actionItems.getFirst());
    }

    private String qualityAnswer(
        WorkspaceSnapshot snapshot,
        List<ResearcherAssistantSuggestionResponse> suggestions,
        List<String> actionItems
    ) {
        if (snapshot.qualityIssues().isEmpty()) {
            return "No he encontrado incidencias de calidad en tus datos propios con las reglas actuales. " + actionItems.getFirst();
        }
        String issues = snapshot.qualityIssues().stream()
            .limit(5)
            .map(issue -> qualityLabel(issue) + ": " + normalizeWhitespace(issue.description()))
            .collect(Collectors.joining(" "));
        String suggestionText = suggestions.isEmpty()
            ? "No se crearon sugerencias IA nuevas para estas incidencias."
            : "He creado " + suggestions.size() + " sugerencia(s) IA para revision humana.";
        return issues + " " + suggestionText + " " + actionItems.getFirst();
    }

    private String profileAnswer(
        WorkspaceSnapshot snapshot,
        List<ResearcherAssistantSuggestionResponse> suggestions,
        List<String> actionItems
    ) {
        String profile = "Perfil propio: " + snapshot.profile().title() + ". Estado de validacion: " + snapshot.profile().validationStatus() + ".";
        String suggestionText = suggestions.stream()
            .filter(suggestion -> suggestion.suggestionType() == AiSuggestionType.PUBLIC_SUMMARY)
            .findFirst()
            .map(suggestion -> "He creado una sugerencia de resumen publico (#" + suggestion.suggestionId() + ") para revisar.")
            .orElse("No se creo una sugerencia de resumen publico en esta respuesta.");
        return profile + " " + suggestionText + " " + actionItems.getFirst();
    }

    private String generalAnswer(
        WorkspaceSnapshot snapshot,
        List<ResearcherAssistantSuggestionResponse> suggestions,
        List<String> actionItems
    ) {
        String base = "Puedo ayudarte con tus pendientes, comentarios de validacion, calidad de datos, perfil publico y temas para tus publicaciones propias.";
        String status = "Ahora veo %d pendiente(s), %d con cambios solicitados y %d incidencia(s) de calidad en tu contexto privado.".formatted(
            snapshot.pending().size(),
            snapshot.changesRequested().size(),
            snapshot.qualityIssues().size()
        );
        String suggestionText = suggestions.isEmpty()
            ? "No se crearon sugerencias IA nuevas."
            : "Se crearon " + suggestions.size() + " sugerencia(s) IA para revision humana.";
        return base + " " + status + " " + suggestionText + " " + actionItems.getFirst();
    }

    private String validationComments(List<ResearcherActivityRow> rows) {
        String comments = rows.stream()
            .filter(row -> row.validationComment() != null && !row.validationComment().isBlank())
            .limit(3)
            .map(row -> "\"" + row.title() + "\": " + normalizeWhitespace(row.validationComment()))
            .collect(Collectors.joining(" "));
        if (comments.isBlank()) {
            return "";
        }
        return "Comentarios de validacion a revisar: " + comments;
    }

    private String assistantQuestion(ResearcherAssistantAskRequest request) {
        return """
            Responde en espanol al investigador autenticado.
            Modo: %s.
            Pregunta: %s

            Reglas:
            - Usa solo el contexto proporcionado.
            - El contexto privado pertenece solo al investigador autenticado.
            - El contexto publico, si aparece, ya esta validado publicamente.
            - No inventes datos, metricas, autores, ni relaciones.
            - No afirmes que modificaste datos. Las sugerencias IA requieren revision humana.
            - Si el contexto no basta, dilo claramente.
            """.formatted(request.mode().name(), request.question()).trim();
    }

    private String assistantContext(
        WorkspaceSnapshot snapshot,
        List<ResearcherAssistantSuggestionResponse> suggestions,
        List<String> actionItems
    ) {
        List<String> parts = new ArrayList<>();
        parts.add("PRIVATE PROFILE\n" + activityContext(snapshot.profile()));
        parts.add("PRIVATE TASKS\n" + activityListContext(prioritizedActivities(snapshot)));
        parts.add("PRIVATE QUALITY ISSUES\n" + qualityContext(snapshot.qualityIssues()));
        parts.add("CREATED AI SUGGESTIONS\n" + suggestionsContext(suggestions));
        parts.add("ACTION ITEMS\n" + String.join("\n", actionItems));
        if (!snapshot.publicContext().isEmpty()) {
            parts.add("PUBLIC VALIDATED PUBLICATIONS\n" + publicPublicationContext(snapshot.publicContext()));
        }
        return String.join("\n\n", parts);
    }

    private String activityListContext(List<ResearcherActivityRow> activities) {
        if (activities.isEmpty()) {
            return "None.";
        }
        return activities.stream()
            .limit(RELATED_ACTIVITY_LIMIT)
            .map(this::activityContext)
            .collect(Collectors.joining("\n"));
    }

    private String activityContext(ResearcherActivityRow row) {
        List<String> parts = new ArrayList<>();
        parts.add(row.entityType() + ":" + row.entityId());
        parts.add("title=" + row.title());
        parts.add("status=" + row.validationStatus());
        addIfPresent(parts, "validationComment=" + normalizeWhitespace(row.validationComment()), row.validationComment());
        addIfPresent(parts, "year=" + row.yearValue(), row.yearValue() == null ? null : row.yearValue().toString());
        addIfPresent(parts, "doi=" + row.doi(), row.doi());
        addIfPresent(parts, "source=" + row.sourceValue(), row.sourceValue());
        if (row.topicCount() != null) {
            parts.add("topicCount=" + row.topicCount());
        }
        return "- " + String.join("; ", parts);
    }

    private String qualityContext(List<DataQualityIssueRow> issues) {
        if (issues.isEmpty()) {
            return "None.";
        }
        return issues.stream()
            .limit(QUALITY_ISSUE_LIMIT)
            .map(issue -> "- %s:%d; type=%s; severity=%s; description=%s; suggestedAction=%s".formatted(
                issue.entityType(),
                issue.entityId(),
                issue.issueType(),
                issue.severity(),
                normalizeWhitespace(issue.description()),
                normalizeWhitespace(issue.suggestedAction())
            ))
            .collect(Collectors.joining("\n"));
    }

    private String suggestionsContext(List<ResearcherAssistantSuggestionResponse> suggestions) {
        if (suggestions.isEmpty()) {
            return "None.";
        }
        return suggestions.stream()
            .map(suggestion -> "- suggestionId=%d; type=%s; target=%s:%d; title=%s; detail=%s".formatted(
                suggestion.suggestionId(),
                suggestion.suggestionType(),
                suggestion.targetType(),
                suggestion.targetId(),
                suggestion.title(),
                normalizeWhitespace(suggestion.detail())
            ))
            .collect(Collectors.joining("\n"));
    }

    private String publicPublicationContext(List<RetrievedPublicationContext> contexts) {
        return contexts.stream()
            .map(context -> "- publication:%d; title=%s; year=%s; topics=%s".formatted(
                context.publication().getId(),
                context.publication().getTitle(),
                context.publication().getPublicationYear(),
                context.topics().isEmpty() ? "None" : String.join(", ", context.topics())
            ))
            .collect(Collectors.joining("\n"));
    }

    private List<String> dataQualityReminders(ResearcherActivityRow row) {
        List<String> reminders = new ArrayList<>();
        switch (row.entityType()) {
            case RESEARCHER -> {
                addIfBlank(reminders, row.email(), "Completa tu email institucional.");
                addIfBlank(reminders, row.orcid(), "Anade tu ORCID si lo tienes.");
                addIfBlank(reminders, row.researchUnitName(), "Revisa tu afiliacion principal vigente.");
            }
            case RESEARCHER_AFFILIATION -> {
                addIfBlank(reminders, row.roleValue(), "Completa el rol de tu afiliacion.");
                if (!Boolean.TRUE.equals(row.primaryAffiliation())) {
                    reminders.add("Confirma si esta afiliacion debe ser principal.");
                }
            }
            case PUBLICATION -> {
                addIfBlank(reminders, row.doi(), "Anade DOI cuando exista.");
                addIfBlank(reminders, row.sourceValue(), "Completa la fuente de la publicacion.");
                if (!Boolean.TRUE.equals(row.abstractPresent())) {
                    reminders.add("Anade un resumen.");
                }
                if (row.topicCount() == null || row.topicCount() == 0) {
                    reminders.add("Anade al menos un tema.");
                }
            }
            case EVENT_PARTICIPATION -> {
                addIfBlank(reminders, row.sourceValue(), "Vincula la participacion con un evento.");
                addIfBlank(reminders, row.researchUnitName(), "Indica la unidad asociada a la participacion.");
                if (!Boolean.TRUE.equals(row.abstractPresent())) {
                    reminders.add("Anade una descripcion de la participacion.");
                }
            }
            case RESEARCH_UNIT, SCIENTIFIC_EVENT, VENUE, PUBLISHER, TOPIC, AI_SUGGESTION -> {
            }
        }
        return reminders;
    }

    private boolean canEdit(ValidationStatus status) {
        return status == ValidationStatus.DRAFT || status == ValidationStatus.CHANGES_REQUESTED;
    }

    private boolean canSubmit(ValidationStatus status) {
        return canEdit(status);
    }

    private List<ResearcherActivityRow> allPrivateActivities(WorkspaceSnapshot snapshot) {
        List<ResearcherActivityRow> activities = new ArrayList<>();
        activities.add(snapshot.profile());
        activities.addAll(snapshot.pending());
        activities.addAll(snapshot.changesRequested());
        activities.addAll(snapshot.drafts());
        activities.addAll(snapshot.rejected());
        return distinctActivities(activities);
    }

    private List<ResearcherActivityRow> distinctActivities(List<ResearcherActivityRow> activities) {
        Map<String, ResearcherActivityRow> byKey = new LinkedHashMap<>();
        for (ResearcherActivityRow activity : activities) {
            byKey.putIfAbsent(activityKey(activity), activity);
        }
        return new ArrayList<>(byKey.values());
    }

    private String activityKey(ResearcherActivityRow row) {
        return row.entityType().name() + ":" + row.entityId();
    }

    private String qualityLabel(DataQualityIssueRow issue) {
        return switch (issue.issueType()) {
            case PUBLICATIONS_WITHOUT_DOI -> "Publicacion sin DOI";
            case PUBLICATIONS_WITHOUT_ABSTRACT -> "Publicacion sin resumen";
            case PUBLICATIONS_WITHOUT_PUBLIC_SUMMARY -> "Publicacion sin resumen publico";
            case PUBLICATIONS_WITHOUT_TOPICS -> "Publicacion sin temas";
            case PUBLICATION_TITLE_CASING_ISSUES -> "Titulo con posible problema de capitalizacion";
            case RESEARCHERS_WITHOUT_ORCID -> "Perfil sin ORCID";
            case PUBLICATIONS_WITH_EXTERNAL_AUTHORS -> "Publicacion con autores externos";
            case UNRESOLVED_EXTERNAL_AUTHORS -> "Autor externo sin resolver";
            case ACTIVITIES_PENDING_VALIDATION -> "Actividad pendiente de validacion";
            case VENUES_WITHOUT_IDENTIFIER -> "Venue sin identificador";
            case EVENTS_WITHOUT_DATES -> "Evento sin fechas";
            case EXTERNAL_ORGANIZATION_DUPLICATE_CANDIDATES -> "Organizacion externa posiblemente duplicada";
            case DUPLICATE_TOPIC_CANDIDATES -> "Tema posiblemente duplicado";
            case DUPLICATE_PUBLICATION_CANDIDATES -> "Publicacion posiblemente duplicada";
        };
    }

    private void addIfBlank(List<String> values, String value, String message) {
        if (value == null || value.isBlank()) {
            values.add(message);
        }
    }

    private void addIfPresent(List<String> values, String formatted, String value) {
        if (value != null && !value.isBlank()) {
            values.add(formatted);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException("Could not serialize researcher assistant suggestion payload.");
        }
    }

    private Map<String, Object> orderedMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            map.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return map;
    }

    private String joinSentences(String... values) {
        return List.of(values).stream()
            .map(this::normalizeWhitespace)
            .filter(value -> !value.isBlank())
            .collect(Collectors.joining(" "));
    }

    private List<String> distinct(Collection<String> values) {
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new))
            .stream()
            .toList();
    }

    private String normalized(String value) {
        return normalizeWhitespace(value).toLowerCase(Locale.ROOT);
    }

    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String ollamaWarning(String message) {
        if (message != null && message.toLowerCase(Locale.ROOT).contains("ollama")) {
            return "Ollama no esta disponible para el asistente privado; se uso una respuesta determinista basada en tus datos.";
        }
        return "El proveedor de IA no esta disponible para el asistente privado; se uso una respuesta determinista basada en tus datos.";
    }

    private String publicContextWarning(String message) {
        if (message != null && message.toLowerCase(Locale.ROOT).contains("ollama")) {
            return "Ollama no esta disponible para recuperar contexto publico semantico; se intento usar busqueda de texto.";
        }
        return "No se pudo recuperar contexto publico semantico; se intento usar busqueda de texto.";
    }

    private String topicWarning(String message) {
        if (message != null && message.toLowerCase(Locale.ROOT).contains("ollama")) {
            return "Ollama no esta disponible para recomendar temas; se uso el flujo determinista disponible.";
        }
        return "No se pudo crear una recomendacion de temas: " + message;
    }

    private record WorkspaceSnapshot(
        Long researcherId,
        ResearcherActivityRow profile,
        List<ResearcherActivityRow> pending,
        List<ResearcherActivityRow> changesRequested,
        List<ResearcherActivityRow> drafts,
        List<ResearcherActivityRow> rejected,
        List<DataQualityIssueRow> qualityIssues,
        List<RetrievedPublicationContext> publicContext
    ) {
    }
}
