package com.researchintelligence.platform.expertfinder.application;

import com.researchintelligence.platform.ai.application.AiProperties;
import com.researchintelligence.platform.ai.application.EmbeddingResponse;
import com.researchintelligence.platform.ai.application.EmbeddingService;
import com.researchintelligence.platform.ai.application.RetrievalMode;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingRepository;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingSearchRow;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderEventParticipationResponse;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderFiltersRequest;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderPublicationResponse;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderResearcherSummaryResponse;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderResultResponse;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderSearchRequest;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderSearchResponse;
import com.researchintelligence.platform.expertfinder.persistence.ExpertEventParticipationEvidenceRow;
import com.researchintelligence.platform.expertfinder.persistence.ExpertFinderEvidenceRepository;
import com.researchintelligence.platform.expertfinder.persistence.ExpertPublicationEvidenceRow;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExpertFinderService {

    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "and", "are", "by", "for", "from", "in", "into", "of", "on", "or", "the", "to", "with",
        "como", "con", "de", "del", "el", "en", "la", "las", "los", "para", "por", "que", "sobre", "un", "una",
        "busco", "buscar", "experto", "experta", "expertos", "investigador", "investigadora", "investigadores"
    );

    private static final String VALIDATED_ONLY_WARNING = "La busqueda usa solo evidencia validada.";

    private final ExpertFinderEvidenceRepository evidenceRepository;
    private final PublicationEmbeddingRepository embeddingRepository;
    private final EmbeddingService embeddingService;
    private final AiProperties aiProperties;
    private final VisibilityContext visibilityContext;

    public ExpertFinderService(
        ExpertFinderEvidenceRepository evidenceRepository,
        PublicationEmbeddingRepository embeddingRepository,
        EmbeddingService embeddingService,
        AiProperties aiProperties,
        VisibilityContext visibilityContext
    ) {
        this.evidenceRepository = evidenceRepository;
        this.embeddingRepository = embeddingRepository;
        this.embeddingService = embeddingService;
        this.aiProperties = aiProperties;
        this.visibilityContext = visibilityContext;
    }

    public ExpertFinderSearchResponse search(ExpertFinderSearchRequest request) {
        ExpertFinderFiltersRequest filters = request.filters();
        RetrievalMode mode = request.mode() == null ? RetrievalMode.BALANCED : request.mode();
        int limit = resolveLimit(request.limit(), mode);
        boolean requestedOnlyValidated = filters == null || filters.onlyValidated() == null || filters.onlyValidated();
        boolean adminRequestedAll = !requestedOnlyValidated && visibilityContext.currentRoles().contains("ADMIN");
        boolean onlyValidated = !adminRequestedAll;
        Long researchUnitId = filters == null ? null : filters.researchUnitId();
        String normalizedTopic = normalizeTopic(filters == null ? null : filters.topic());
        String topicPattern = normalizedTopic == null ? null : "%" + normalizedTopic + "%";
        Set<String> queryTerms = expandedTerms(request.query());
        List<String> warnings = new ArrayList<>();
        if (onlyValidated) {
            warnings.add(VALIDATED_ONLY_WARNING);
        }
        if (!requestedOnlyValidated && onlyValidated) {
            warnings.add("Solo los administradores pueden desactivar el filtro de validacion; se ha aplicado evidencia validada.");
        }

        List<ExpertPublicationEvidenceRow> publicationRows = evidenceRepository.findPublicationEvidence(
            researchUnitId,
            normalizedTopic,
            topicPattern,
            onlyValidated
        );
        Map<Long, Double> semanticScores = semanticScores(request.query(), publicationRows, limit, onlyValidated, warnings);
        Map<Long, Candidate> candidates = collectPublicationCandidates(publicationRows, semanticScores, queryTerms, normalizedTopic, onlyValidated);

        List<ExpertEventParticipationEvidenceRow> eventRows = evidenceRepository.findEventEvidence(researchUnitId, onlyValidated);
        collectEventEvidence(candidates, eventRows, queryTerms, onlyValidated, normalizedTopic == null);

        List<ExpertFinderResultResponse> results = candidates.values()
            .stream()
            .map(candidate -> candidate.toResponse(queryTerms, onlyValidated))
            .sorted(Comparator.comparing(ExpertFinderResultResponse::score).reversed()
                .thenComparing(result -> result.researcher().fullName()))
            .limit(limit)
            .toList();

        if (results.isEmpty()) {
            warnings.add("No se han encontrado investigadores con evidencia para la necesidad indicada.");
        }
        return new ExpertFinderSearchResponse(
            results,
            warnings.stream().distinct().toList(),
            "Deterministic evidence ranking: semantic publication similarity, topic matches, publication evidence, recent activity and event participation signals. LLMs are not used for ranking.",
            onlyValidated ? VisibilityScope.PUBLIC_VALIDATED.name() : VisibilityScope.ADMIN_ALL.name(),
            onlyValidated
        );
    }

    private int resolveLimit(Integer requestedLimit, RetrievalMode mode) {
        int defaultLimit = mode == RetrievalMode.BROAD ? aiProperties.getRetrieval().getMaxLimit() : aiProperties.getRetrieval().getDefaultLimit();
        int requested = requestedLimit == null ? defaultLimit : requestedLimit;
        return Math.min(Math.max(requested, 1), 50);
    }

    private Map<Long, Double> semanticScores(
        String query,
        List<ExpertPublicationEvidenceRow> publicationRows,
        int limit,
        boolean onlyValidated,
        List<String> warnings
    ) {
        if (publicationRows.isEmpty()) {
            return Map.of();
        }
        int dimension = aiProperties.getEmbeddingDimension();
        if (!embeddingRepository.hasEmbeddings(embeddingService.provider(), embeddingService.model(), dimension)) {
            warnings.add("No hay embeddings de publicaciones para el proveedor/modelo/dimension configurados; se usa coincidencia textual como respaldo.");
            return Map.of();
        }
        EmbeddingResponse response = embeddingService.embed(String.join(" ", expandedTerms(query)));
        if (response.vector().size() != dimension) {
            warnings.add("El embedding de la consulta no coincide con la dimension configurada; se usa coincidencia textual como respaldo.");
            return Map.of();
        }
        int semanticLimit = Math.max(limit * 25, Math.min(publicationRows.size(), 100));
        return embeddingRepository.searchNearest(
                toPgVector(response.vector()),
                embeddingService.provider(),
                embeddingService.model(),
                dimension,
                semanticLimit,
                onlyValidated ? VisibilityScope.PUBLIC_VALIDATED : VisibilityScope.ADMIN_ALL,
                null
            )
            .stream()
            .collect(Collectors.toMap(
                PublicationEmbeddingSearchRow::publicationId,
                PublicationEmbeddingSearchRow::similarityScore,
                (first, second) -> first,
                LinkedHashMap::new
            ));
    }

    private Map<Long, Candidate> collectPublicationCandidates(
        List<ExpertPublicationEvidenceRow> rows,
        Map<Long, Double> semanticScores,
        Set<String> queryTerms,
        String normalizedTopic,
        boolean onlyValidated
    ) {
        Map<Long, Candidate> candidates = new LinkedHashMap<>();
        for (ExpertPublicationEvidenceRow row : rows) {
            if (!isVisiblePublicationEvidence(row, onlyValidated)) {
                continue;
            }
            Candidate candidate = candidates.computeIfAbsent(row.researcherId(), id -> new Candidate(row, onlyValidated));
            PublicationEvidence publication = candidate.publications.computeIfAbsent(
                row.publicationId(),
                id -> new PublicationEvidence(row, semanticScores.get(row.publicationId()))
            );
            publication.addTopic(row.topicId(), row.topicName(), row.topicNormalizedName(), queryTerms, normalizedTopic);
        }
        return candidates;
    }

    private void collectEventEvidence(
        Map<Long, Candidate> candidates,
        List<ExpertEventParticipationEvidenceRow> rows,
        Set<String> queryTerms,
        boolean onlyValidated,
        boolean allowEventOnlyCandidates
    ) {
        for (ExpertEventParticipationEvidenceRow row : rows) {
            if (!isVisibleEventEvidence(row, onlyValidated)) {
                continue;
            }
            Candidate candidate = candidates.get(row.researcherId());
            if (candidate == null && allowEventOnlyCandidates) {
                candidate = new Candidate(row, onlyValidated);
                candidates.put(row.researcherId(), candidate);
            }
            if (candidate != null) {
                candidate.events.putIfAbsent(row.participationId(), new EventEvidence(row, textScore(queryTerms, eventText(row))));
            }
        }
    }

    private boolean isVisiblePublicationEvidence(ExpertPublicationEvidenceRow row, boolean onlyValidated) {
        return row.researcherActive()
            && (!onlyValidated || (row.researcherValidationStatus() == ValidationStatus.VALIDATED
            && row.publicationValidationStatus() == ValidationStatus.VALIDATED));
    }

    private boolean isVisibleEventEvidence(ExpertEventParticipationEvidenceRow row, boolean onlyValidated) {
        return row.researcherActive()
            && (!onlyValidated || (row.researcherValidationStatus() == ValidationStatus.VALIDATED
            && row.participationValidationStatus() == ValidationStatus.VALIDATED
            && row.eventValidationStatus() == ValidationStatus.VALIDATED
            && (row.researchUnitId() == null || row.researchUnitValidationStatus() == ValidationStatus.VALIDATED)));
    }

    private String normalizeTopic(String topic) {
        String normalized = normalize(topic);
        return normalized.isBlank() ? null : normalized;
    }

    private static Set<String> expandedTerms(String value) {
        Set<String> terms = new LinkedHashSet<>();
        for (String term : normalize(value).split("[^\\p{Alnum}]+")) {
            if (term.length() < 2 || STOP_WORDS.contains(term)) {
                continue;
            }
            addTermVariants(terms, term);
        }
        return terms;
    }

    private static void addTermVariants(Set<String> terms, String term) {
        terms.add(term);
        if (term.endsWith("es") && term.length() > 4) {
            terms.add(term.substring(0, term.length() - 2));
        }
        if (term.endsWith("s") && term.length() > 3) {
            terms.add(term.substring(0, term.length() - 1));
        }
        if ("ai".equals(term)) {
            terms.add("ia");
            terms.add("artificial");
            terms.add("intelligence");
            terms.add("inteligencia");
        }
        if ("ia".equals(term)) {
            terms.add("ai");
            terms.add("artificial");
            terms.add("intelligence");
            terms.add("inteligencia");
        }
        if ("hospital".equals(term) || "hospitals".equals(term) || "hospitales".equals(term)) {
            terms.add("clinical");
            terms.add("clinica");
            terms.add("clinico");
            terms.add("salud");
        }
        if ("clinical".equals(term) || "clinica".equals(term) || "clinico".equals(term)) {
            terms.add("hospital");
            terms.add("salud");
        }
        if ("panther".equals(term) || "panthers".equals(term) || "pantera".equals(term) || "panteras".equals(term)) {
            terms.add("pantera");
            terms.add("panteras");
            terms.add("felino");
            terms.add("felinos");
            terms.add("conservacion");
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.toLowerCase(), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private static double textScore(Set<String> queryTerms, String text) {
        if (queryTerms.isEmpty()) {
            return 0.0;
        }
        Set<String> textTerms = expandedTerms(text);
        if (textTerms.isEmpty()) {
            return 0.0;
        }
        long matches = queryTerms.stream().filter(textTerms::contains).count();
        return clamp(matches / (double) Math.max(queryTerms.size(), 1));
    }

    private static String eventText(ExpertEventParticipationEvidenceRow row) {
        return String.join(" ",
            safe(row.eventName()),
            safe(row.participationTypeCode()),
            safe(row.title()),
            safe(row.description())
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String toPgVector(List<Double> vector) {
        return vector.stream()
            .map(value -> String.format(Locale.ROOT, "%.10f", value))
            .collect(Collectors.joining(",", "[", "]"));
    }

    private static final class Candidate {

        private final Long researcherId;
        private final String fullName;
        private final String displayName;
        private final String orcid;
        private final Long primaryResearchUnitId;
        private final String primaryResearchUnitName;
        private final Map<Long, PublicationEvidence> publications = new LinkedHashMap<>();
        private final Map<Long, EventEvidence> events = new LinkedHashMap<>();

        private Candidate(ExpertPublicationEvidenceRow row, boolean onlyValidated) {
            this.researcherId = row.researcherId();
            this.fullName = row.researcherFullName();
            this.displayName = row.researcherDisplayName();
            this.orcid = row.researcherOrcid();
            this.primaryResearchUnitId = visiblePrimary(row.primaryResearchUnitId(), row.primaryAffiliationValidationStatus(), row.primaryResearchUnitValidationStatus(), onlyValidated);
            this.primaryResearchUnitName = this.primaryResearchUnitId == null ? null : row.primaryResearchUnitName();
        }

        private Candidate(ExpertEventParticipationEvidenceRow row, boolean onlyValidated) {
            this.researcherId = row.researcherId();
            this.fullName = row.researcherFullName();
            this.displayName = row.researcherDisplayName();
            this.orcid = row.researcherOrcid();
            this.primaryResearchUnitId = visiblePrimary(row.primaryResearchUnitId(), row.primaryAffiliationValidationStatus(), row.primaryResearchUnitValidationStatus(), onlyValidated);
            this.primaryResearchUnitName = this.primaryResearchUnitId == null ? null : row.primaryResearchUnitName();
        }

        private static Long visiblePrimary(
            Long researchUnitId,
            ValidationStatus affiliationStatus,
            ValidationStatus researchUnitStatus,
            boolean onlyValidated
        ) {
            if (researchUnitId == null) {
                return null;
            }
            if (!onlyValidated || (affiliationStatus == ValidationStatus.VALIDATED && researchUnitStatus == ValidationStatus.VALIDATED)) {
                return researchUnitId;
            }
            return null;
        }

        private ExpertFinderResultResponse toResponse(Set<String> queryTerms, boolean onlyValidated) {
            ScoredCandidate scored = score(queryTerms);
            List<String> matchedTopics = publications.values()
                .stream()
                .flatMap(publication -> publication.matchedTopics.stream())
                .distinct()
                .sorted()
                .limit(8)
                .toList();
            List<ExpertFinderPublicationResponse> representativePublications = publications.values()
                .stream()
                .sorted(Comparator.comparing((PublicationEvidence publication) -> publication.rankingSignal(queryTerms)).reversed()
                    .thenComparing(PublicationEvidence::yearForSort, Comparator.reverseOrder())
                    .thenComparing(PublicationEvidence::title))
                .limit(3)
                .map(PublicationEvidence::toResponse)
                .toList();
            Set<Long> representativePublicationIds = representativePublications.stream()
                .map(ExpertFinderPublicationResponse::id)
                .collect(Collectors.toSet());
            List<ExpertFinderEventParticipationResponse> relevantEvents = events.values()
                .stream()
                .filter(event -> event.textScore > 0.0 || representativePublicationIds.contains(event.relatedPublicationId))
                .sorted(Comparator.comparing(EventEvidence::rankingSignal).reversed()
                    .thenComparing(EventEvidence::dateForSort, Comparator.reverseOrder())
                    .thenComparing(EventEvidence::title))
                .limit(3)
                .map(EventEvidence::toResponse)
                .toList();
            List<String> reasons = reasons(scored, matchedTopics, representativePublications, relevantEvents, onlyValidated);
            List<String> warnings = new ArrayList<>();
            if (scored.score < 0.25) {
                warnings.add("Coincidencia debil: revisa la evidencia antes de contactar al investigador.");
            }
            return new ExpertFinderResultResponse(
                new ExpertFinderResearcherSummaryResponse(researcherId, fullName, displayName, orcid, primaryResearchUnitId, primaryResearchUnitName),
                scored.score,
                confidence(scored.score),
                matchedTopics,
                representativePublications,
                relevantEvents,
                reasons,
                explanation(scored.score, fullName, reasons),
                warnings
            );
        }

        private ScoredCandidate score(Set<String> queryTerms) {
            double semantic = publications.values().stream()
                .map(PublicationEvidence::semanticSimilarity)
                .filter(score -> score != null)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
            double topic = publications.values().stream().mapToDouble(PublicationEvidence::topicScore).max().orElse(0.0);
            double publicationText = publications.values().stream().mapToDouble(publication -> publication.textScore(queryTerms)).max().orElse(0.0);
            double event = events.values().stream().mapToDouble(EventEvidence::rankingSignal).max().orElse(0.0);
            double baseRelevance = Math.max(Math.max(semantic, topic), Math.max(publicationText, event));
            double recent = recentActivityScore();
            double score = (0.45 * semantic)
                + (0.20 * topic)
                + (0.15 * publicationText)
                + (0.10 * recent * baseRelevance)
                + (0.10 * event);
            return new ScoredCandidate(clamp(score), semantic, topic, publicationText, recent, event);
        }

        private double recentActivityScore() {
            int currentYear = LocalDate.now(ZoneOffset.UTC).getYear();
            int latestPublicationYear = publications.values().stream()
                .map(PublicationEvidence::year)
                .filter(year -> year != null)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
            int latestEventYear = events.values().stream()
                .map(EventEvidence::participationYear)
                .filter(year -> year != null)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
            int latestYear = Math.max(latestPublicationYear, latestEventYear);
            if (latestYear == 0) {
                return 0.0;
            }
            int age = Math.max(0, currentYear - latestYear);
            if (age <= 1) {
                return 1.0;
            }
            if (age <= 3) {
                return 0.7;
            }
            if (age <= 5) {
                return 0.4;
            }
            return 0.1;
        }

        private List<String> reasons(
            ScoredCandidate scored,
            List<String> matchedTopics,
            List<ExpertFinderPublicationResponse> representativePublications,
            List<ExpertFinderEventParticipationResponse> relevantEvents,
            boolean onlyValidated
        ) {
            List<String> reasons = new ArrayList<>();
            if (scored.semantic >= 0.01) {
                reasons.add("Similitud semantica maxima de publicaciones: " + String.format(java.util.Locale.ROOT, "%.2f", scored.semantic) + ".");
            }
            if (!matchedTopics.isEmpty()) {
                reasons.add("Temas coincidentes: " + String.join(", ", matchedTopics) + ".");
            }
            if (!representativePublications.isEmpty()) {
                reasons.add("Publicaciones representativas: " + representativePublications.stream().map(ExpertFinderPublicationResponse::title).limit(2).collect(Collectors.joining("; ")) + ".");
            }
            if (scored.recent > 0.0 && scored.score >= 0.01) {
                reasons.add("Actividad reciente usada como senal de apoyo.");
            }
            if (!relevantEvents.isEmpty()) {
                reasons.add("Participaciones en eventos relevantes disponibles.");
            }
            if (onlyValidated) {
                reasons.add("Solo se ha usado evidencia validada.");
            }
            if (reasons.isEmpty()) {
                reasons.add("Hay evidencia institucional, pero la coincidencia con la consulta es debil.");
            }
            return reasons;
        }

        private String confidence(double score) {
            if (score >= 0.65) {
                return "HIGH";
            }
            if (score >= 0.35) {
                return "MEDIUM";
            }
            return "LOW";
        }

        private String explanation(double score, String name, List<String> reasons) {
            if (score < 0.25) {
                return name + " aparece con baja confianza porque la evidencia disponible apenas coincide con la necesidad indicada.";
            }
            return name + " destaca por evidencia trazable: " + String.join(" ", reasons);
        }
    }

    private record ScoredCandidate(double score, double semantic, double topic, double publicationText, double recent, double event) {
    }

    private static final class PublicationEvidence {

        private final Long id;
        private final String title;
        private final String abstractText;
        private final Integer year;
        private final String type;
        private final String doi;
        private final String source;
        private final String url;
        private final Double semanticSimilarity;
        private final Set<String> topics = new LinkedHashSet<>();
        private final Set<String> matchedTopics = new LinkedHashSet<>();
        private double topicScore;

        private PublicationEvidence(ExpertPublicationEvidenceRow row, Double semanticSimilarity) {
            this.id = row.publicationId();
            this.title = row.publicationTitle();
            this.abstractText = row.publicationAbstract();
            this.year = row.publicationYear();
            this.type = row.publicationType();
            this.doi = row.publicationDoi();
            this.source = row.publicationSource();
            this.url = row.publicationUrl();
            this.semanticSimilarity = semanticSimilarity;
        }

        private void addTopic(Long topicId, String topicName, String topicNormalizedName, Set<String> queryTerms, String requestedTopic) {
            if (topicId == null || topicName == null || topicName.isBlank()) {
                return;
            }
            topics.add(topicName);
            double score = ExpertFinderService.textScore(queryTerms, topicName + " " + topicNormalizedName);
            boolean requestedTopicMatch = requestedTopic != null
                && (requestedTopic.equals(normalize(topicNormalizedName)) || normalize(topicName).contains(requestedTopic));
            if (score > 0.0 || requestedTopicMatch) {
                matchedTopics.add(topicName);
                topicScore = Math.max(topicScore, requestedTopicMatch ? 1.0 : score);
            }
        }

        private Double semanticSimilarity() {
            return semanticSimilarity;
        }

        private Integer year() {
            return year;
        }

        private double topicScore() {
            return topicScore;
        }

        private double textScore(Set<String> queryTerms) {
            return ExpertFinderService.textScore(queryTerms, String.join(" ", safe(title), safe(abstractText), safe(source), String.join(" ", topics)));
        }

        private double rankingSignal(Set<String> queryTerms) {
            return Math.max(semanticSimilarity == null ? 0.0 : semanticSimilarity, Math.max(topicScore, textScore(queryTerms)));
        }

        private Integer yearForSort() {
            return year == null ? 0 : year;
        }

        private String title() {
            return title;
        }

        private ExpertFinderPublicationResponse toResponse() {
            return new ExpertFinderPublicationResponse(
                id,
                title,
                year,
                type,
                doi,
                source,
                url,
                semanticSimilarity,
                new ArrayList<>(matchedTopics)
            );
        }
    }

    private static final class EventEvidence {

        private final Long id;
        private final Long eventId;
        private final String eventName;
        private final String participationTypeCode;
        private final String title;
        private final LocalDate participationDate;
        private final Long relatedPublicationId;
        private final double textScore;

        private EventEvidence(ExpertEventParticipationEvidenceRow row, double textScore) {
            this.id = row.participationId();
            this.eventId = row.eventId();
            this.eventName = row.eventName();
            this.participationTypeCode = row.participationTypeCode();
            this.title = row.title();
            this.participationDate = row.participationDate();
            this.relatedPublicationId = row.relatedPublicationId();
            this.textScore = textScore;
        }

        private double rankingSignal() {
            double recency = 0.0;
            if (participationDate != null) {
                int age = Math.max(0, LocalDate.now(ZoneOffset.UTC).getYear() - participationDate.getYear());
                recency = age <= 1 ? 0.25 : age <= 3 ? 0.15 : 0.05;
            }
            return clamp(textScore + recency);
        }

        private Integer participationYear() {
            return participationDate == null ? null : participationDate.getYear();
        }

        private LocalDate dateForSort() {
            return participationDate == null ? LocalDate.MIN : participationDate;
        }

        private String title() {
            return title;
        }

        private ExpertFinderEventParticipationResponse toResponse() {
            return new ExpertFinderEventParticipationResponse(
                id,
                eventId,
                eventName,
                participationTypeCode,
                title,
                participationDate,
                relatedPublicationId
            );
        }
    }
}
