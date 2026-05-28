package com.researchintelligence.platform.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.api.ConversationalSearchEntityScope;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ConversationalSearchInterpreter {

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d{2}|20\\d{2}|21\\d{2})\\b");
    private static final Set<String> ALLOWED_FILTERS = Set.of(
        "validationStatus",
        "academicStatus",
        "type",
        "yearRange",
        "yearFrom",
        "yearTo",
        "topic",
        "researcher",
        "unit",
        "dataQualityIssue",
        "textQuery",
        "semanticQuery"
    );
    private static final List<String> DEFAULT_CLARIFICATION_OPTIONS = List.of(
        "Buscar publicaciones",
        "Buscar investigadores",
        "Buscar unidades",
        "Buscar actividades",
        "Buscar bandeja de validacion",
        "Buscar calidad de datos"
    );

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public ConversationalSearchInterpreter(LlmService llmService, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    public ConversationalSearchInterpretation interpret(String question, ConversationalSearchEntityScope requestedScope) {
        String safeQuestion = question == null ? "" : question.trim();
        List<String> warnings = new ArrayList<>();
        if (!"mock".equalsIgnoreCase(llmService.provider())) {
            Optional<ConversationalSearchInterpretation> llmInterpretation = tryInterpretWithLlm(safeQuestion, requestedScope, warnings);
            if (llmInterpretation.isPresent()) {
                return mergeWarnings(llmInterpretation.get(), warnings);
            }
        } else {
            warnings.add("El proveedor LLM mock esta activo; se uso el parser local de palabras clave.");
        }

        ConversationalSearchInterpretation fallback = fallbackInterpretation(safeQuestion, requestedScope);
        List<String> mergedWarnings = new ArrayList<>(warnings);
        mergedWarnings.addAll(fallback.warnings());
        return new ConversationalSearchInterpretation(
            fallback.entityScope(),
            fallback.interpretedIntent(),
            fallback.filters(),
            fallback.clarificationNeeded(),
            fallback.clarificationOptions(),
            mergedWarnings.stream().distinct().toList()
        );
    }

    private Optional<ConversationalSearchInterpretation> tryInterpretWithLlm(
        String question,
        ConversationalSearchEntityScope requestedScope,
        List<String> warnings
    ) {
        try {
            LlmResponse response = llmService.answer(new LlmPrompt(question, llmContext()));
            warnings.addAll(response.warnings());
            JsonNode root = objectMapper.readTree(extractJsonObject(response.answer()));
            List<String> localWarnings = new ArrayList<>();
            boolean ignoredUnsafeFields = hasUnknownTopLevelFields(root) || hasUnknownFilterFields(root.path("filters"));
            ConversationalSearchEntityScope modelScope = parseScope(text(root.get("entityScope")));
            ConversationalSearchEntityScope scope = requestedScope == null ? modelScope : requestedScope;
            if (requestedScope != null && modelScope != null && requestedScope != modelScope) {
                localWarnings.add("Se priorizo el entityScope enviado en la peticion sobre la sugerencia del modelo.");
            }
            ConversationalSearchFilters filters = filtersFromJson(root.path("filters"), localWarnings);
            boolean clarificationNeeded = root.path("clarificationNeeded").asBoolean(false);
            List<String> options = optionsFromJson(root.path("clarificationOptions"));
            if (ignoredUnsafeFields) {
                localWarnings.add("Se ignoraron campos no permitidos devueltos por el modelo.");
            }
            if (scope == null && !clarificationNeeded) {
                return Optional.empty();
            }
            warnings.addAll(localWarnings);
            return Optional.of(new ConversationalSearchInterpretation(
                scope,
                textOrDefault(root.get("interpretedIntent"), "Busqueda administrativa interpretada desde la pregunta."),
                filters,
                clarificationNeeded,
                options.isEmpty() ? DEFAULT_CLARIFICATION_OPTIONS : options,
                List.of()
            ));
        } catch (Exception exception) {
            warnings.add("No se pudo usar la interpretacion del LLM; se uso el parser local de palabras clave.");
            return Optional.empty();
        }
    }

    private ConversationalSearchInterpretation fallbackInterpretation(
        String question,
        ConversationalSearchEntityScope requestedScope
    ) {
        String normalized = normalize(question);
        ConversationalSearchEntityScope scope = requestedScope == null ? inferScope(normalized) : requestedScope;
        ConversationalSearchFilters.Builder filters = ConversationalSearchFilters.builder();
        filters.validationStatus(detectValidationStatus(normalized));
        filters.academicStatus(detectAcademicStatus(normalized));
        filters.type(detectType(normalized));
        applyYears(question, filters);
        String topic = extractAfterKeyword(question, "sobre");
        String unit = extractUnit(question);
        String researcher = extractResearcher(question);
        String dataQualityIssue = detectDataQualityIssue(normalized);
        filters.topic(topic);
        filters.unit(unit);
        filters.researcher(researcher);
        filters.dataQualityIssue(dataQualityIssue);
        String textQuery = textQuery(question, scope, topic, unit, researcher, dataQualityIssue);
        filters.textQuery(textQuery);
        filters.semanticQuery(topic != null || normalized.contains("semantic"));
        ConversationalSearchFilters builtFilters = filters.build();

        boolean ambiguous = scope == null || (scope == ConversationalSearchEntityScope.VALIDATION
            && !builtFilters.hasSearchTerm()
            && builtFilters.validationStatus() == null);
        if (scope == null && onlyStatusWords(normalized)) {
            ambiguous = true;
        }
        return new ConversationalSearchInterpretation(
            scope,
            interpretedIntent(scope, builtFilters),
            builtFilters,
            ambiguous,
            ambiguous ? DEFAULT_CLARIFICATION_OPTIONS : List.of(),
            List.of()
        );
    }

    private ConversationalSearchInterpretation mergeWarnings(
        ConversationalSearchInterpretation interpretation,
        List<String> warnings
    ) {
        List<String> mergedWarnings = new ArrayList<>(warnings);
        mergedWarnings.addAll(interpretation.warnings());
        return new ConversationalSearchInterpretation(
            interpretation.entityScope(),
            interpretation.interpretedIntent(),
            interpretation.filters(),
            interpretation.clarificationNeeded(),
            interpretation.clarificationOptions(),
            mergedWarnings.stream().distinct().toList()
        );
    }

    private String llmContext() {
        return """
            Traduce la pregunta a filtros estructurados seguros. Devuelve solo JSON.
            No generes SQL ni instrucciones ejecutables.
            entityScope permitido: PUBLICATIONS, RESEARCHERS, UNITS, ACTIVITIES, VALIDATION, DATA_QUALITY.
            Filtros permitidos: validationStatus, academicStatus, type, yearRange, topic, researcher, unit, dataQualityIssue, textQuery, semanticQuery.
            validationStatus permitido: DRAFT, PENDING_VALIDATION, VALIDATED, REJECTED, CHANGES_REQUESTED.
            Si la intencion es ambigua, usa clarificationNeeded=true y clarificationOptions en espanol.
            Formato:
            {"entityScope":"PUBLICATIONS","interpretedIntent":"...","filters":{"validationStatus":"PENDING_VALIDATION"},"clarificationNeeded":false,"clarificationOptions":[]}
            """.trim();
    }

    private ConversationalSearchFilters filtersFromJson(JsonNode filtersNode, List<String> warnings) {
        ConversationalSearchFilters.Builder builder = ConversationalSearchFilters.builder();
        if (filtersNode == null || !filtersNode.isObject()) {
            return builder.build();
        }
        builder.validationStatus(parseValidationStatus(text(filtersNode.get("validationStatus"))));
        builder.academicStatus(text(filtersNode.get("academicStatus")));
        builder.type(text(filtersNode.get("type")));
        JsonNode yearRange = filtersNode.path("yearRange");
        if (yearRange.isObject()) {
            builder.yearFrom(integer(yearRange.get("from")));
            builder.yearTo(integer(yearRange.get("to")));
        } else {
            builder.yearFrom(integer(filtersNode.get("yearFrom")));
            builder.yearTo(integer(filtersNode.get("yearTo")));
        }
        builder.topic(text(filtersNode.get("topic")));
        builder.researcher(text(filtersNode.get("researcher")));
        builder.unit(text(filtersNode.get("unit")));
        builder.dataQualityIssue(text(filtersNode.get("dataQualityIssue")));
        builder.textQuery(text(filtersNode.get("textQuery")));
        builder.semanticQuery(filtersNode.path("semanticQuery").asBoolean(false));
        if (filtersNode.has("sql") || filtersNode.has("rawSql")) {
            warnings.add("Se ignoro SQL sugerido por el modelo.");
        }
        return builder.build();
    }

    private boolean hasUnknownTopLevelFields(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }
        Set<String> allowed = Set.of("entityScope", "interpretedIntent", "filters", "clarificationNeeded", "clarificationOptions");
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            if (!allowed.contains(names.next())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUnknownFilterFields(JsonNode filtersNode) {
        if (filtersNode == null || !filtersNode.isObject()) {
            return false;
        }
        Iterator<String> names = filtersNode.fieldNames();
        while (names.hasNext()) {
            if (!ALLOWED_FILTERS.contains(names.next())) {
                return true;
            }
        }
        return false;
    }

    private String extractJsonObject(String answer) {
        String text = answer == null ? "" : answer.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("LLM answer is not JSON.");
        }
        return text.substring(start, end + 1);
    }

    private List<String> optionsFromJson(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        for (JsonNode option : node) {
            String text = text(option);
            if (text != null) {
                options.add(text);
            }
        }
        return options;
    }

    private ConversationalSearchEntityScope parseScope(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ConversationalSearchEntityScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private ValidationStatus parseValidationStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ValidationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return detectValidationStatus(normalize(value));
        }
    }

    private ConversationalSearchEntityScope inferScope(String normalized) {
        if (containsAny(normalized, "calidad de datos", "problema de datos", "incidencia", "duplicad")) {
            return ConversationalSearchEntityScope.DATA_QUALITY;
        }
        if (containsAny(normalized, "bandeja", "validacion", "validar")) {
            return ConversationalSearchEntityScope.VALIDATION;
        }
        if (containsAny(normalized, "actividad", "actividades", "participacion", "participaciones", "evento")) {
            return ConversationalSearchEntityScope.ACTIVITIES;
        }
        if (containsAny(normalized, "investigador", "investigadores", "investigadora", "investigadoras", "orcid")) {
            return ConversationalSearchEntityScope.RESEARCHERS;
        }
        if (containsAny(normalized, "unidad", "unidades", "departamento", "grupo", "laboratorio", "instituto", "centro")) {
            return ConversationalSearchEntityScope.UNITS;
        }
        if (containsAny(normalized, "publicacion", "publicaciones", "doi", "resumen publico", "abstract", "sin resumen", "sin temas")) {
            return ConversationalSearchEntityScope.PUBLICATIONS;
        }
        if (normalized.contains("sin evidencia")) {
            return ConversationalSearchEntityScope.ACTIVITIES;
        }
        return null;
    }

    private ValidationStatus detectValidationStatus(String normalized) {
        if (containsAny(normalized, "requiere cambios", "requieren cambios", "cambios solicitados", "solicitud de cambios")) {
            return ValidationStatus.CHANGES_REQUESTED;
        }
        if (containsAny(normalized, "pendiente", "pendientes", "por validar", "en validacion")) {
            return ValidationStatus.PENDING_VALIDATION;
        }
        if (containsAny(normalized, "validada", "validadas", "validado", "validados")) {
            return ValidationStatus.VALIDATED;
        }
        if (containsAny(normalized, "rechazada", "rechazadas", "rechazado", "rechazados")) {
            return ValidationStatus.REJECTED;
        }
        if (containsAny(normalized, "borrador", "borradores", "draft")) {
            return ValidationStatus.DRAFT;
        }
        return null;
    }

    private String detectAcademicStatus(String normalized) {
        if (containsAny(normalized, "publicada", "publicadas", "publicado", "publicados")) {
            return "PUBLISHED";
        }
        if (containsAny(normalized, "aceptada", "aceptadas", "aceptado", "aceptados")) {
            return "ACCEPTED";
        }
        if (containsAny(normalized, "en prensa", "in press")) {
            return "IN_PRESS";
        }
        return null;
    }

    private String detectType(String normalized) {
        Map<String, String> types = Map.ofEntries(
            Map.entry("articulo", "ARTICLE"),
            Map.entry("articulos", "ARTICLE"),
            Map.entry("libro", "BOOK"),
            Map.entry("capitulo", "BOOK_CHAPTER"),
            Map.entry("congreso", "CONFERENCE_PAPER"),
            Map.entry("tesis", "THESIS"),
            Map.entry("informe", "REPORT"),
            Map.entry("dataset", "DATASET"),
            Map.entry("software", "SOFTWARE"),
            Map.entry("departamento", "DEPARTMENT"),
            Map.entry("grupo", "RESEARCH_GROUP"),
            Map.entry("laboratorio", "LAB"),
            Map.entry("hospital", "HOSPITAL")
        );
        for (Map.Entry<String, String> entry : types.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String detectDataQualityIssue(String normalized) {
        if (normalized.contains("sin doi")) {
            return "PUBLICATIONS_WITHOUT_DOI";
        }
        if (containsAny(normalized, "sin resumen publico", "sin resumen publica")) {
            return "PUBLICATIONS_WITHOUT_PUBLIC_SUMMARY";
        }
        if (containsAny(normalized, "sin resumen", "sin abstract")) {
            return "PUBLICATIONS_WITHOUT_ABSTRACT";
        }
        if (normalized.contains("sin temas")) {
            return "PUBLICATIONS_WITHOUT_TOPICS";
        }
        if (normalized.contains("sin orcid")) {
            return "RESEARCHERS_WITHOUT_ORCID";
        }
        if (normalized.contains("sin evidencia")) {
            return "MISSING_EVIDENCE";
        }
        if (normalized.contains("duplicad") && normalized.contains("public")) {
            return "DUPLICATE_PUBLICATION_CANDIDATES";
        }
        if (normalized.contains("duplicad") && normalized.contains("tema")) {
            return "DUPLICATE_TOPIC_CANDIDATES";
        }
        if (normalized.contains("duplicad") && containsAny(normalized, "organizacion", "unidad")) {
            return "EXTERNAL_ORGANIZATION_DUPLICATE_CANDIDATES";
        }
        return null;
    }

    private void applyYears(String question, ConversationalSearchFilters.Builder filters) {
        Matcher matcher = YEAR_PATTERN.matcher(question == null ? "" : question);
        List<Integer> years = new ArrayList<>();
        while (matcher.find()) {
            years.add(Integer.valueOf(matcher.group(1)));
        }
        if (years.size() >= 2) {
            filters.yearFrom(Math.min(years.get(0), years.get(1)));
            filters.yearTo(Math.max(years.get(0), years.get(1)));
        } else if (years.size() == 1) {
            String normalized = normalize(question);
            if (containsAny(normalized, "desde", "a partir")) {
                filters.yearFrom(years.get(0));
            } else if (containsAny(normalized, "hasta", "antes de")) {
                filters.yearTo(years.get(0));
            } else {
                filters.yearFrom(years.get(0));
                filters.yearTo(years.get(0));
            }
        }
    }

    private String extractAfterKeyword(String question, String keyword) {
        Pattern pattern = Pattern.compile("(?iu)\\b" + Pattern.quote(keyword) + "\\s+(.+)$");
        Matcher matcher = pattern.matcher(question == null ? "" : question);
        if (!matcher.find()) {
            return null;
        }
        return cleanExtractedPhrase(matcher.group(1));
    }

    private String extractUnit(String question) {
        Pattern pattern = Pattern.compile("(?iu)\\b(?:unidad|unidades|grupo|departamento|laboratorio|centro|instituto)\\s+(?:de|del|de la)?\\s+(.+)$");
        Matcher matcher = pattern.matcher(question == null ? "" : question);
        if (!matcher.find()) {
            return null;
        }
        return cleanExtractedPhrase(matcher.group(1));
    }

    private String extractResearcher(String question) {
        Pattern pattern = Pattern.compile("(?iu)\\b(?:investigador|investigadora)\\s+(?:llamado|llamada|de nombre)?\\s+([\\p{L} .'-]{3,})$");
        Matcher matcher = pattern.matcher(question == null ? "" : question);
        if (!matcher.find()) {
            return null;
        }
        return cleanExtractedPhrase(matcher.group(1));
    }

    private String cleanExtractedPhrase(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value
            .replaceAll("(?iu)\\b(?:pendientes?|validad[ao]s?|rechazad[ao]s?|sin doi|sin resumen publico|sin resumen|sin evidencia)\\b", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String textQuery(
        String question,
        ConversationalSearchEntityScope scope,
        String topic,
        String unit,
        String researcher,
        String dataQualityIssue
    ) {
        if (topic != null) {
            return topic;
        }
        if (scope == ConversationalSearchEntityScope.ACTIVITIES && unit != null) {
            return unit;
        }
        if (researcher != null || dataQualityIssue != null) {
            return null;
        }
        String cleaned = question == null ? "" : question;
        cleaned = cleaned.replaceAll("(?iu)\\b(?:muestrame|mostrar|busca|buscar|dame|lista|listar)\\b", " ");
        cleaned = cleaned.replaceAll("(?iu)\\b(?:publicaciones?|investigadores?|investigadoras?|unidades?|actividades?|participaciones?|validacion|calidad de datos)\\b", " ");
        cleaned = cleaned.replaceAll("(?iu)\\b(?:pendientes?|validad[ao]s?|rechazad[ao]s?|borradores?|requieren cambios|requiere cambios|sin doi|sin resumen publico|sin resumen|sin evidencia|con|de|del|la|el|los|las|que)\\b", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned.length() < 3 ? null : cleaned;
    }

    private String interpretedIntent(ConversationalSearchEntityScope scope, ConversationalSearchFilters filters) {
        if (scope == null) {
            return "La pregunta necesita acotar el tipo de entidad.";
        }
        StringBuilder builder = new StringBuilder("Buscar ");
        builder.append(switch (scope) {
            case PUBLICATIONS -> "publicaciones";
            case RESEARCHERS -> "investigadores";
            case UNITS -> "unidades";
            case ACTIVITIES -> "actividades";
            case VALIDATION -> "elementos de validacion";
            case DATA_QUALITY -> "incidencias de calidad de datos";
        });
        if (filters.validationStatus() != null) {
            builder.append(" con estado ").append(filters.validationStatus().name());
        }
        if (filters.dataQualityIssue() != null) {
            builder.append(" y problema ").append(filters.dataQualityIssue());
        }
        if (filters.topic() != null) {
            builder.append(" sobre ").append(filters.topic());
        }
        return builder.toString();
    }

    private boolean onlyStatusWords(String normalized) {
        String cleaned = normalized
            .replace("pendientes", "")
            .replace("pendiente", "")
            .replace("validadas", "")
            .replace("validados", "")
            .replace("validada", "")
            .replace("validado", "")
            .replace("rechazadas", "")
            .replace("rechazados", "")
            .replace("rechazada", "")
            .replace("rechazado", "")
            .replace("muestrame", "")
            .replace("mostrar", "")
            .replace("busca", "")
            .replace("buscar", "")
            .replaceAll("\\s+", "");
        return cleaned.isBlank();
    }

    private Integer integer(JsonNode node) {
        return node == null || !node.canConvertToInt() ? null : node.asInt();
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String textOrDefault(JsonNode node, String defaultValue) {
        String value = text(node);
        return value == null ? defaultValue : value;
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }
}
