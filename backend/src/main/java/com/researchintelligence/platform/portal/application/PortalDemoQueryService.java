package com.researchintelligence.platform.portal.application;

import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.portal.api.PortalDemoQueryContext;
import com.researchintelligence.platform.portal.api.PortalDemoQueryFreshnessResponse;
import com.researchintelligence.platform.portal.api.PortalDemoQueryResponse;
import com.researchintelligence.platform.portal.persistence.PortalDemoQueryEvidenceRepository;
import com.researchintelligence.platform.portal.persistence.PortalDemoQueryEvidenceRow;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PortalDemoQueryService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;
    private static final int MAX_EVIDENCE_IDS = 6;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Set<String> STOP_WORDS = Set.of(
        "a", "al", "con", "de", "del", "el", "en", "la", "las", "los", "para", "por", "un", "una", "y"
    );

    private final PortalDemoQueryEvidenceRepository evidenceRepository;
    private final VisibilityContext visibilityContext;
    private final Map<CacheKey, CachedResult> cache = new ConcurrentHashMap<>();

    public PortalDemoQueryService(
        PortalDemoQueryEvidenceRepository evidenceRepository,
        VisibilityContext visibilityContext
    ) {
        this.evidenceRepository = evidenceRepository;
        this.visibilityContext = visibilityContext;
    }

    public List<PortalDemoQueryResponse> generate(
        PortalDemoQueryContext context,
        int limit,
        boolean onlyValidated
    ) {
        PortalDemoQueryContext effectiveContext = context == null ? PortalDemoQueryContext.GENERAL : context;
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        boolean effectiveOnlyValidated = resolveOnlyValidated(onlyValidated);

        CacheKey cacheKey = new CacheKey(effectiveContext, effectiveLimit, effectiveOnlyValidated);
        CachedResult cached = cache.get(cacheKey);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.queries();
        }

        List<PortalDemoQueryEvidenceRow> rows = evidenceRepository.findPublicationEvidence(effectiveOnlyValidated);
        if (rows.isEmpty()) {
            cache.put(cacheKey, new CachedResult(List.of(), now.plus(CACHE_TTL)));
            return List.of();
        }

        EvidenceCorpus corpus = EvidenceCorpus.from(rows);
        List<DemoQueryCandidate> candidates = candidatesForContext(effectiveContext, corpus);
        List<DemoQueryCandidate> selected = selectDiverse(candidates, effectiveLimit);
        Instant generatedAt = Instant.now();
        List<PortalDemoQueryResponse> responses = selected.stream()
            .map(candidate -> candidate.toResponse(generatedAt))
            .toList();
        cache.put(cacheKey, new CachedResult(responses, generatedAt.plus(CACHE_TTL)));
        return responses;
    }

    private boolean resolveOnlyValidated(boolean requestedOnlyValidated) {
        if (requestedOnlyValidated) {
            return true;
        }
        return !visibilityContext.currentRoles().contains("ADMIN");
    }

    private List<DemoQueryCandidate> candidatesForContext(PortalDemoQueryContext context, EvidenceCorpus corpus) {
        return switch (context) {
            case PUBLICATIONS -> publicationCandidates(context, corpus);
            case EXPERT_FINDER -> expertCandidates(context, corpus);
            case ASSISTANT -> assistantCandidates(context, corpus);
            case REPORTS -> reportCandidates(context, corpus);
            case STRATEGIC_MAP -> strategicMapCandidates(context, corpus);
            case GENERAL -> generalCandidates(corpus);
        };
    }

    private List<DemoQueryCandidate> generalCandidates(EvidenceCorpus corpus) {
        List<DemoQueryCandidate> candidates = new ArrayList<>();
        candidates.addAll(publicationCandidates(PortalDemoQueryContext.GENERAL, corpus));
        candidates.addAll(expertCandidates(PortalDemoQueryContext.GENERAL, corpus));
        candidates.addAll(assistantCandidates(PortalDemoQueryContext.GENERAL, corpus));
        candidates.addAll(reportCandidates(PortalDemoQueryContext.GENERAL, corpus));
        candidates.addAll(strategicMapCandidates(PortalDemoQueryContext.GENERAL, corpus));
        return candidates;
    }

    private List<DemoQueryCandidate> publicationCandidates(PortalDemoQueryContext context, EvidenceCorpus corpus) {
        List<DemoQueryCandidate> candidates = new ArrayList<>();
        addSpecialTopicCandidates(context, corpus, candidates);

        for (TopicSignal topic : corpus.topicsByEvidence(10)) {
            String query = topic.name();
            String reason = "Tema con evidencia en " + topic.publicationIds().size() + " publicaciones.";
            candidates.add(candidateForTopic(
                query,
                context,
                reason,
                List.of("PUBLICATION", "TOPIC"),
                topic,
                1.0 + topic.publicationIds().size()
            ));
        }

        for (TopicPairSignal pair : corpus.topTopicPairs(8)) {
            TopicSignal left = corpus.topicById(pair.leftTopicId());
            TopicSignal right = corpus.topicById(pair.rightTopicId());
            if (left == null || right == null) {
                continue;
            }
            String query = left.name() + " y " + right.name();
            String reason = "Combinacion observada en " + pair.publicationIds().size() + " publicaciones.";
            candidates.add(candidateForTopicPair(
                query,
                context,
                reason,
                List.of("PUBLICATION", "TOPIC"),
                left,
                right,
                pair.publicationIds(),
                1.5 + pair.publicationIds().size()
            ));
        }

        for (PublicationSignal publication : corpus.topPublications(6)) {
            String query = shortTitleQuery(publication.title());
            if (query == null) {
                continue;
            }
            candidates.add(new DemoQueryCandidate(
                query,
                context,
                "Consulta derivada de una publicacion validada reciente.",
                List.of("PUBLICATION"),
                List.of("pub:" + publication.id()),
                publication.updatedAt(),
                publication.updatedAt(),
                inferCluster(query),
                0.9
            ));
        }
        return candidates;
    }

    private List<DemoQueryCandidate> expertCandidates(PortalDemoQueryContext context, EvidenceCorpus corpus) {
        List<DemoQueryCandidate> candidates = new ArrayList<>();
        for (TopicSignal topic : corpus.topicsByEvidence(8)) {
            List<ResearcherSignal> experts = corpus.topResearchersForTopic(topic.id(), 2);
            if (experts.isEmpty()) {
                continue;
            }
            List<String> evidenceIds = new ArrayList<>();
            evidenceIds.add("topic:" + topic.id());
            experts.forEach(researcher -> evidenceIds.add("researcher:" + researcher.id()));
            topic.topPublicationIds(2).forEach(publicationId -> evidenceIds.add("pub:" + publicationId));
            candidates.add(new DemoQueryCandidate(
                "expertos en " + topic.name(),
                context,
                "Tema con especialistas activos: " + experts.stream().map(ResearcherSignal::name).collect(Collectors.joining(", ")) + ".",
                List.of("RESEARCHER", "TOPIC", "PUBLICATION"),
                truncateEvidence(evidenceIds),
                topic.newestEvidenceAt(),
                topic.oldestEvidenceAt(),
                inferCluster(topic.name()),
                1.1 + topic.publicationIds().size()
            ));
        }
        return candidates;
    }

    private List<DemoQueryCandidate> assistantCandidates(PortalDemoQueryContext context, EvidenceCorpus corpus) {
        List<DemoQueryCandidate> candidates = new ArrayList<>();
        for (TopicPairSignal pair : corpus.topTopicPairs(8)) {
            TopicSignal left = corpus.topicById(pair.leftTopicId());
            TopicSignal right = corpus.topicById(pair.rightTopicId());
            if (left == null || right == null) {
                continue;
            }
            String query = "Que evidencia hay sobre " + left.name() + " y " + right.name();
            String reason = "Asistente: relacion detectada en publicaciones reales.";
            candidates.add(candidateForTopicPair(
                query,
                context,
                reason,
                List.of("PUBLICATION", "TOPIC"),
                left,
                right,
                pair.publicationIds(),
                1.2 + pair.publicationIds().size()
            ));
        }
        return candidates;
    }

    private List<DemoQueryCandidate> reportCandidates(PortalDemoQueryContext context, EvidenceCorpus corpus) {
        List<DemoQueryCandidate> candidates = new ArrayList<>();
        for (TopicSignal topic : corpus.topicsByEvidence(6)) {
            UnitSignal unit = corpus.topUnitForTopic(topic.id());
            List<String> evidenceIds = new ArrayList<>();
            evidenceIds.add("topic:" + topic.id());
            topic.topPublicationIds(3).forEach(publicationId -> evidenceIds.add("pub:" + publicationId));
            if (unit != null) {
                evidenceIds.add("unit:" + unit.id());
            }
            String query = unit == null
                ? "informe sobre " + topic.name()
                : "informe de " + unit.name() + " sobre " + topic.name();
            String reason = unit == null
                ? "Tema con suficiente evidencia para un informe."
                : "Tema con evidencia vinculada a una unidad activa.";
            candidates.add(new DemoQueryCandidate(
                query,
                context,
                reason,
                List.of("PUBLICATION", "TOPIC", "RESEARCH_UNIT"),
                truncateEvidence(evidenceIds),
                topic.newestEvidenceAt(),
                topic.oldestEvidenceAt(),
                inferCluster(query),
                1.0 + topic.publicationIds().size()
            ));
        }
        return candidates;
    }

    private List<DemoQueryCandidate> strategicMapCandidates(PortalDemoQueryContext context, EvidenceCorpus corpus) {
        List<DemoQueryCandidate> candidates = new ArrayList<>();
        for (TopicPairSignal pair : corpus.topTopicPairs(8)) {
            TopicSignal left = corpus.topicById(pair.leftTopicId());
            TopicSignal right = corpus.topicById(pair.rightTopicId());
            if (left == null || right == null) {
                continue;
            }
            String query = "lineas de investigacion en " + left.name() + " y " + right.name();
            candidates.add(candidateForTopicPair(
                query,
                context,
                "Relacion util para mapa estrategico y agrupacion por linea tematica.",
                List.of("TOPIC", "PUBLICATION"),
                left,
                right,
                pair.publicationIds(),
                1.3 + pair.publicationIds().size()
            ));
        }
        for (UnitPairSignal pair : corpus.topUnitPairs(6)) {
            UnitSignal left = corpus.unitById(pair.leftUnitId());
            UnitSignal right = corpus.unitById(pair.rightUnitId());
            if (left == null || right == null) {
                continue;
            }
            List<String> evidenceIds = new ArrayList<>();
            evidenceIds.add("unit:" + left.id());
            evidenceIds.add("unit:" + right.id());
            pair.publicationIds().stream().limit(4).forEach(publicationId -> evidenceIds.add("pub:" + publicationId));
            candidates.add(new DemoQueryCandidate(
                "colaboraciones entre " + left.name() + " y " + right.name(),
                context,
                "Unidades conectadas por publicaciones compartidas.",
                List.of("RESEARCH_UNIT", "PUBLICATION", "COLLABORATION"),
                truncateEvidence(evidenceIds),
                pair.newestEvidenceAt(),
                pair.oldestEvidenceAt(),
                inferCluster(left.name() + " " + right.name()),
                1.2 + pair.publicationIds().size()
            ));
        }
        return candidates;
    }

    private DemoQueryCandidate candidateForTopic(
        String query,
        PortalDemoQueryContext context,
        String reason,
        List<String> expectedEntityTypes,
        TopicSignal topic,
        double score
    ) {
        List<String> evidenceIds = new ArrayList<>();
        evidenceIds.add("topic:" + topic.id());
        topic.topPublicationIds(5).forEach(publicationId -> evidenceIds.add("pub:" + publicationId));
        return new DemoQueryCandidate(
            query,
            context,
            reason,
            expectedEntityTypes,
            truncateEvidence(evidenceIds),
            topic.newestEvidenceAt(),
            topic.oldestEvidenceAt(),
            inferCluster(query),
            score
        );
    }

    private DemoQueryCandidate candidateForTopicPair(
        String query,
        PortalDemoQueryContext context,
        String reason,
        List<String> expectedEntityTypes,
        TopicSignal left,
        TopicSignal right,
        Set<Long> publicationIds,
        double score
    ) {
        List<String> evidenceIds = new ArrayList<>();
        evidenceIds.add("topic:" + left.id());
        evidenceIds.add("topic:" + right.id());
        publicationIds.stream().limit(4).forEach(publicationId -> evidenceIds.add("pub:" + publicationId));
        Instant newest = newestOf(left.newestEvidenceAt(), right.newestEvidenceAt(), publicationIds, null);
        Instant oldest = oldestOf(left.oldestEvidenceAt(), right.oldestEvidenceAt(), publicationIds, null);
        return new DemoQueryCandidate(
            query,
            context,
            reason,
            expectedEntityTypes,
            truncateEvidence(evidenceIds),
            newest,
            oldest,
            inferCluster(query),
            score
        );
    }

    private void addSpecialTopicCandidates(
        PortalDemoQueryContext context,
        EvidenceCorpus corpus,
        List<DemoQueryCandidate> candidates
    ) {
        addSpecialCandidate(context, corpus, candidates, "IA local en hospitales", List.of("ia", "local", "hospital"), "HOSPITAL_AI");
        addSpecialCandidate(context, corpus, candidates, "panteras y conservacion", List.of("panter", "conserv"), "CONSERVATION");
        addSpecialCandidate(context, corpus, candidates, "salud publica y clima urbano", List.of("salud", "clima", "urban"), "PUBLIC_HEALTH_CLIMATE");
        addSpecialCandidate(context, corpus, candidates, "grafos de conocimiento en genomica", List.of("grafo", "conocimiento", "genom"), "GENOMICS_GRAPH");
        addSpecialCandidate(context, corpus, candidates, "calidad de datos en investigacion", List.of("calidad", "dato"), "DATA_QUALITY");
        addSpecialCandidate(context, corpus, candidates, "colaboraciones entre salud digital y hospitales", List.of("salud digital", "hospital"), "HOSPITAL_AI");
    }

    private void addSpecialCandidate(
        PortalDemoQueryContext context,
        EvidenceCorpus corpus,
        List<DemoQueryCandidate> candidates,
        String query,
        List<String> requiredTokens,
        String cluster
    ) {
        Set<Long> publicationIds = corpus.publicationIdsContainingAll(requiredTokens);
        if (publicationIds.isEmpty()) {
            return;
        }
        List<String> evidenceIds = new ArrayList<>();
        publicationIds.stream().limit(5).forEach(publicationId -> evidenceIds.add("pub:" + publicationId));
        corpus.topicIdsMatching(requiredTokens).stream().limit(2).forEach(topicId -> evidenceIds.add("topic:" + topicId));
        candidates.add(new DemoQueryCandidate(
            query,
            context,
            "Frase anclada en evidencia actual del dataset.",
            List.of("PUBLICATION", "TOPIC", "RESEARCHER", "RESEARCH_UNIT"),
            truncateEvidence(evidenceIds),
            corpus.newestEvidenceAt(publicationIds),
            corpus.oldestEvidenceAt(publicationIds),
            cluster,
            2.0 + publicationIds.size()
        ));
    }

    private List<DemoQueryCandidate> selectDiverse(List<DemoQueryCandidate> candidates, int limit) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<String, DemoQueryCandidate> bestByQuery = new LinkedHashMap<>();
        candidates.stream()
            .filter(candidate -> candidate.query() != null && !candidate.query().isBlank())
            .forEach(candidate -> bestByQuery.merge(
                normalize(candidate.query()),
                candidate,
                (left, right) -> left.score() >= right.score() ? left : right
            ));

        List<DemoQueryCandidate> deduplicated = bestByQuery.values().stream()
            .sorted(Comparator.comparing(DemoQueryCandidate::score).reversed().thenComparing(DemoQueryCandidate::query))
            .toList();
        if (deduplicated.size() <= limit) {
            return deduplicated;
        }

        Map<String, Deque<DemoQueryCandidate>> byCluster = new LinkedHashMap<>();
        for (DemoQueryCandidate candidate : deduplicated) {
            byCluster.computeIfAbsent(candidate.cluster(), ignored -> new ArrayDeque<>()).add(candidate);
        }

        List<DemoQueryCandidate> selected = new ArrayList<>();
        while (selected.size() < limit && !byCluster.isEmpty()) {
            List<String> exhaustedClusters = new ArrayList<>();
            for (Map.Entry<String, Deque<DemoQueryCandidate>> entry : byCluster.entrySet()) {
                if (selected.size() >= limit) {
                    break;
                }
                DemoQueryCandidate next = entry.getValue().pollFirst();
                if (next != null) {
                    selected.add(next);
                }
                if (entry.getValue().isEmpty()) {
                    exhaustedClusters.add(entry.getKey());
                }
            }
            exhaustedClusters.forEach(byCluster::remove);
        }
        return selected;
    }

    private String shortTitleQuery(String title) {
        String normalized = normalize(title);
        if (normalized.isBlank()) {
            return null;
        }
        List<String> tokens = tokenize(normalized);
        if (tokens.isEmpty()) {
            return null;
        }
        return String.join(" ", tokens.stream().limit(5).toList());
    }

    private List<String> truncateEvidence(Collection<String> evidenceIds) {
        return evidenceIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .limit(MAX_EVIDENCE_IDS)
            .toList();
    }

    private Instant newestOf(Instant left, Instant right, Set<Long> publicationIds, EvidenceCorpus corpus) {
        Instant value = left != null && (right == null || left.isAfter(right)) ? left : right;
        if (corpus == null || publicationIds == null || publicationIds.isEmpty()) {
            return value;
        }
        Instant publicationNewest = corpus.newestEvidenceAt(publicationIds);
        if (publicationNewest == null) {
            return value;
        }
        return value == null || publicationNewest.isAfter(value) ? publicationNewest : value;
    }

    private Instant oldestOf(Instant left, Instant right, Set<Long> publicationIds, EvidenceCorpus corpus) {
        Instant value = left != null && (right == null || left.isBefore(right)) ? left : right;
        if (corpus == null || publicationIds == null || publicationIds.isEmpty()) {
            return value;
        }
        Instant publicationOldest = corpus.oldestEvidenceAt(publicationIds);
        if (publicationOldest == null) {
            return value;
        }
        return value == null || publicationOldest.isBefore(value) ? publicationOldest : value;
    }

    private String inferCluster(String query) {
        String text = normalize(query);
        if (text.contains("panter") || text.contains("conserv") || text.contains("biodivers")) {
            return "CONSERVATION";
        }
        if (text.contains("hospital") || text.contains("clinic") || text.contains("ia ") || text.startsWith("ia")) {
            return "HOSPITAL_AI";
        }
        if (text.contains("clima") || text.contains("salud publica") || text.contains("urban")) {
            return "PUBLIC_HEALTH_CLIMATE";
        }
        if (text.contains("genom") || text.contains("grafo") || text.contains("conocimiento")) {
            return "GENOMICS_GRAPH";
        }
        if (text.contains("calidad") || text.contains("dato")) {
            return "DATA_QUALITY";
        }
        return "GENERAL";
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private static List<String> tokenize(String text) {
        return List.of(text.split("[^\\p{Alnum}]+")).stream()
            .filter(token -> token.length() >= 3)
            .filter(token -> !STOP_WORDS.contains(token))
            .toList();
    }

    private record DemoQueryCandidate(
        String query,
        PortalDemoQueryContext context,
        String reason,
        List<String> expectedEntityTypes,
        List<String> evidenceIds,
        Instant newestEvidenceAt,
        Instant oldestEvidenceAt,
        String cluster,
        double score
    ) {

        private PortalDemoQueryResponse toResponse(Instant generatedAt) {
            return new PortalDemoQueryResponse(
                query,
                context,
                reason,
                expectedEntityTypes,
                evidenceIds,
                new PortalDemoQueryFreshnessResponse(newestEvidenceAt, oldestEvidenceAt, evidenceIds.size()),
                generatedAt
            );
        }
    }

    private record CacheKey(
        PortalDemoQueryContext context,
        int limit,
        boolean onlyValidated
    ) {
    }

    private record CachedResult(
        List<PortalDemoQueryResponse> queries,
        Instant expiresAt
    ) {
    }

    private static final class EvidenceCorpus {

        private final Map<Long, PublicationSignal> publicationsById = new LinkedHashMap<>();
        private final Map<Long, TopicSignalBuilder> topicBuildersById = new LinkedHashMap<>();
        private final Map<Long, ResearcherSignalBuilder> researcherBuildersById = new LinkedHashMap<>();
        private final Map<Long, UnitSignalBuilder> unitBuildersById = new LinkedHashMap<>();
        private final Map<Long, TopicSignal> topicsById;
        private final Map<Long, ResearcherSignal> researchersById;
        private final Map<Long, UnitSignal> unitsById;

        private EvidenceCorpus(List<PortalDemoQueryEvidenceRow> rows) {
            for (PortalDemoQueryEvidenceRow row : rows) {
                PublicationSignal publication = publicationsById.computeIfAbsent(
                    row.publicationId(),
                    id -> new PublicationSignal(row.publicationId(), row.publicationTitle(), row.publicationUpdatedAt())
                );
                publication.addText(row.publicationTitle());
                publication.addText(row.publicationAbstract());
                publication.addText(row.publicationPublicSummary());

                if (row.topicId() != null && row.topicName() != null && !row.topicName().isBlank()) {
                    publication.topicIds().add(row.topicId());
                    publication.addText(row.topicName());
                    topicBuildersById.computeIfAbsent(
                        row.topicId(),
                        id -> new TopicSignalBuilder(row.topicId(), row.topicName(), normalizeTopicName(row))
                    ).addPublication(row.publicationId(), row.publicationUpdatedAt());
                }

                if (row.researcherId() != null && row.researcherFullName() != null && !row.researcherFullName().isBlank()) {
                    publication.researcherIds().add(row.researcherId());
                    publication.addText(row.researcherFullName());
                    researcherBuildersById.computeIfAbsent(
                        row.researcherId(),
                        id -> new ResearcherSignalBuilder(row.researcherId(), row.researcherFullName())
                    ).addPublication(row.publicationId(), row.publicationUpdatedAt());
                }

                if (row.researchUnitId() != null && row.researchUnitName() != null && !row.researchUnitName().isBlank()) {
                    publication.unitIds().add(row.researchUnitId());
                    publication.addText(row.researchUnitName());
                    unitBuildersById.computeIfAbsent(
                        row.researchUnitId(),
                        id -> new UnitSignalBuilder(row.researchUnitId(), row.researchUnitName())
                    ).addPublication(row.publicationId(), row.publicationUpdatedAt());
                }
            }

            topicsById = topicBuildersById.values().stream()
                .map(TopicSignalBuilder::build)
                .collect(Collectors.toMap(TopicSignal::id, signal -> signal, (first, second) -> first, LinkedHashMap::new));
            researchersById = researcherBuildersById.values().stream()
                .map(ResearcherSignalBuilder::build)
                .collect(Collectors.toMap(ResearcherSignal::id, signal -> signal, (first, second) -> first, LinkedHashMap::new));
            unitsById = unitBuildersById.values().stream()
                .map(UnitSignalBuilder::build)
                .collect(Collectors.toMap(UnitSignal::id, signal -> signal, (first, second) -> first, LinkedHashMap::new));
        }

        private static EvidenceCorpus from(List<PortalDemoQueryEvidenceRow> rows) {
            return new EvidenceCorpus(rows);
        }

        private List<TopicSignal> topicsByEvidence(int limit) {
            return topicsById.values().stream()
                .sorted(Comparator
                    .comparingInt((TopicSignal signal) -> signal.publicationIds().size()).reversed()
                    .thenComparing(TopicSignal::name))
                .limit(limit)
                .toList();
        }

        private List<PublicationSignal> topPublications(int limit) {
            return publicationsById.values().stream()
                .sorted(Comparator
                    .comparing(PublicationSignal::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(PublicationSignal::title))
                .limit(limit)
                .toList();
        }

        private List<TopicPairSignal> topTopicPairs(int limit) {
            Map<PairKey, TopicPairSignalBuilder> pairs = new HashMap<>();
            for (PublicationSignal publication : publicationsById.values()) {
                List<Long> topicIds = publication.topicIds().stream().sorted().toList();
                for (int i = 0; i < topicIds.size(); i++) {
                    for (int j = i + 1; j < topicIds.size(); j++) {
                        PairKey key = PairKey.of(topicIds.get(i), topicIds.get(j));
                        pairs.computeIfAbsent(key, ignored -> new TopicPairSignalBuilder(key.left(), key.right()))
                            .addPublication(publication.id(), publication.updatedAt());
                    }
                }
            }
            return pairs.values().stream()
                .map(TopicPairSignalBuilder::build)
                .sorted(Comparator
                    .comparingInt((TopicPairSignal signal) -> signal.publicationIds().size()).reversed()
                    .thenComparing(TopicPairSignal::leftTopicId)
                    .thenComparing(TopicPairSignal::rightTopicId))
                .limit(limit)
                .toList();
        }

        private List<UnitPairSignal> topUnitPairs(int limit) {
            Map<PairKey, UnitPairSignalBuilder> pairs = new HashMap<>();
            for (PublicationSignal publication : publicationsById.values()) {
                List<Long> unitIds = publication.unitIds().stream().sorted().toList();
                for (int i = 0; i < unitIds.size(); i++) {
                    for (int j = i + 1; j < unitIds.size(); j++) {
                        PairKey key = PairKey.of(unitIds.get(i), unitIds.get(j));
                        pairs.computeIfAbsent(key, ignored -> new UnitPairSignalBuilder(key.left(), key.right()))
                            .addPublication(publication.id(), publication.updatedAt());
                    }
                }
            }
            return pairs.values().stream()
                .map(UnitPairSignalBuilder::build)
                .sorted(Comparator
                    .comparingInt((UnitPairSignal signal) -> signal.publicationIds().size()).reversed()
                    .thenComparing(UnitPairSignal::leftUnitId)
                    .thenComparing(UnitPairSignal::rightUnitId))
                .limit(limit)
                .toList();
        }

        private TopicSignal topicById(Long topicId) {
            return topicsById.get(topicId);
        }

        private UnitSignal unitById(Long unitId) {
            return unitsById.get(unitId);
        }

        private List<ResearcherSignal> topResearchersForTopic(Long topicId, int limit) {
            return researchersById.values().stream()
                .filter(signal -> signal.publicationIds().stream().anyMatch(publicationId -> publicationsById.get(publicationId).topicIds().contains(topicId)))
                .sorted(Comparator
                    .comparingInt((ResearcherSignal signal) -> signal.publicationIds().size()).reversed()
                    .thenComparing(ResearcherSignal::name))
                .limit(limit)
                .toList();
        }

        private UnitSignal topUnitForTopic(Long topicId) {
            return unitsById.values().stream()
                .filter(signal -> signal.publicationIds().stream().anyMatch(publicationId -> publicationsById.get(publicationId).topicIds().contains(topicId)))
                .max(Comparator
                    .comparingInt((UnitSignal signal) -> signal.publicationIds().size())
                    .thenComparing(UnitSignal::name))
                .orElse(null);
        }

        private Set<Long> publicationIdsContainingAll(List<String> tokens) {
            if (tokens == null || tokens.isEmpty()) {
                return Set.of();
            }
            List<String> normalizedTokens = tokens.stream().map(PortalDemoQueryService::normalize).filter(token -> !token.isBlank()).toList();
            return publicationsById.values().stream()
                .filter(publication -> normalizedTokens.stream().allMatch(token -> publication.searchableTextValue().contains(token)))
                .map(PublicationSignal::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        private Set<Long> topicIdsMatching(List<String> tokens) {
            if (tokens == null || tokens.isEmpty()) {
                return Set.of();
            }
            List<String> normalizedTokens = tokens.stream().map(PortalDemoQueryService::normalize).filter(token -> !token.isBlank()).toList();
            return topicsById.values().stream()
                .filter(topic -> normalizedTokens.stream().allMatch(token -> topic.normalizedName().contains(token)))
                .map(TopicSignal::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        private Instant newestEvidenceAt(Set<Long> publicationIds) {
            return publicationIds.stream()
                .map(publicationsById::get)
                .filter(Objects::nonNull)
                .map(PublicationSignal::updatedAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        }

        private Instant oldestEvidenceAt(Set<Long> publicationIds) {
            return publicationIds.stream()
                .map(publicationsById::get)
                .filter(Objects::nonNull)
                .map(PublicationSignal::updatedAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
        }

        private String normalizeTopicName(PortalDemoQueryEvidenceRow row) {
            String topicName = row.topicNormalizedName() == null ? row.topicName() : row.topicNormalizedName();
            return PortalDemoQueryService.normalize(topicName);
        }
    }

    private static final class TopicSignalBuilder {

        private final Long id;
        private final String name;
        private final String normalizedName;
        private final Set<Long> publicationIds = new LinkedHashSet<>();
        private Instant newestEvidenceAt;
        private Instant oldestEvidenceAt;

        private TopicSignalBuilder(Long id, String name, String normalizedName) {
            this.id = id;
            this.name = name;
            this.normalizedName = normalizedName;
        }

        private void addPublication(Long publicationId, Instant updatedAt) {
            publicationIds.add(publicationId);
            if (updatedAt == null) {
                return;
            }
            newestEvidenceAt = newestEvidenceAt == null || updatedAt.isAfter(newestEvidenceAt) ? updatedAt : newestEvidenceAt;
            oldestEvidenceAt = oldestEvidenceAt == null || updatedAt.isBefore(oldestEvidenceAt) ? updatedAt : oldestEvidenceAt;
        }

        private TopicSignal build() {
            return new TopicSignal(id, name, normalizedName, Set.copyOf(publicationIds), newestEvidenceAt, oldestEvidenceAt);
        }
    }

    private static final class ResearcherSignalBuilder {

        private final Long id;
        private final String name;
        private final Set<Long> publicationIds = new LinkedHashSet<>();
        private Instant newestEvidenceAt;
        private Instant oldestEvidenceAt;

        private ResearcherSignalBuilder(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        private void addPublication(Long publicationId, Instant updatedAt) {
            publicationIds.add(publicationId);
            if (updatedAt == null) {
                return;
            }
            newestEvidenceAt = newestEvidenceAt == null || updatedAt.isAfter(newestEvidenceAt) ? updatedAt : newestEvidenceAt;
            oldestEvidenceAt = oldestEvidenceAt == null || updatedAt.isBefore(oldestEvidenceAt) ? updatedAt : oldestEvidenceAt;
        }

        private ResearcherSignal build() {
            return new ResearcherSignal(id, name, Set.copyOf(publicationIds), newestEvidenceAt, oldestEvidenceAt);
        }
    }

    private static final class UnitSignalBuilder {

        private final Long id;
        private final String name;
        private final Set<Long> publicationIds = new LinkedHashSet<>();
        private Instant newestEvidenceAt;
        private Instant oldestEvidenceAt;

        private UnitSignalBuilder(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        private void addPublication(Long publicationId, Instant updatedAt) {
            publicationIds.add(publicationId);
            if (updatedAt == null) {
                return;
            }
            newestEvidenceAt = newestEvidenceAt == null || updatedAt.isAfter(newestEvidenceAt) ? updatedAt : newestEvidenceAt;
            oldestEvidenceAt = oldestEvidenceAt == null || updatedAt.isBefore(oldestEvidenceAt) ? updatedAt : oldestEvidenceAt;
        }

        private UnitSignal build() {
            return new UnitSignal(id, name, Set.copyOf(publicationIds), newestEvidenceAt, oldestEvidenceAt);
        }
    }

    private record PublicationSignal(
        Long id,
        String title,
        Instant updatedAt,
        Set<Long> topicIds,
        Set<Long> researcherIds,
        Set<Long> unitIds,
        StringBuilder searchableText
    ) {

        private PublicationSignal(Long id, String title, Instant updatedAt) {
            this(id, title, updatedAt, new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>(), new StringBuilder());
        }

        private void addText(String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            if (!searchableText.isEmpty()) {
                searchableText.append(' ');
            }
            searchableText.append(PortalDemoQueryService.normalize(value));
        }

        private String searchableTextValue() {
            return searchableText.toString();
        }
    }

    private record TopicSignal(
        Long id,
        String name,
        String normalizedName,
        Set<Long> publicationIds,
        Instant newestEvidenceAt,
        Instant oldestEvidenceAt
    ) {

        private List<Long> topPublicationIds(int limit) {
            return publicationIds.stream().sorted().limit(limit).toList();
        }
    }

    private record ResearcherSignal(
        Long id,
        String name,
        Set<Long> publicationIds,
        Instant newestEvidenceAt,
        Instant oldestEvidenceAt
    ) {
    }

    private record UnitSignal(
        Long id,
        String name,
        Set<Long> publicationIds,
        Instant newestEvidenceAt,
        Instant oldestEvidenceAt
    ) {
    }

    private record TopicPairSignal(
        Long leftTopicId,
        Long rightTopicId,
        Set<Long> publicationIds,
        Instant newestEvidenceAt,
        Instant oldestEvidenceAt
    ) {
    }

    private static final class TopicPairSignalBuilder {

        private final Long leftTopicId;
        private final Long rightTopicId;
        private final Set<Long> publicationIds = new LinkedHashSet<>();
        private Instant newestEvidenceAt;
        private Instant oldestEvidenceAt;

        private TopicPairSignalBuilder(Long leftTopicId, Long rightTopicId) {
            this.leftTopicId = leftTopicId;
            this.rightTopicId = rightTopicId;
        }

        private void addPublication(Long publicationId, Instant updatedAt) {
            publicationIds.add(publicationId);
            if (updatedAt == null) {
                return;
            }
            newestEvidenceAt = newestEvidenceAt == null || updatedAt.isAfter(newestEvidenceAt) ? updatedAt : newestEvidenceAt;
            oldestEvidenceAt = oldestEvidenceAt == null || updatedAt.isBefore(oldestEvidenceAt) ? updatedAt : oldestEvidenceAt;
        }

        private TopicPairSignal build() {
            return new TopicPairSignal(leftTopicId, rightTopicId, Set.copyOf(publicationIds), newestEvidenceAt, oldestEvidenceAt);
        }
    }

    private record UnitPairSignal(
        Long leftUnitId,
        Long rightUnitId,
        Set<Long> publicationIds,
        Instant newestEvidenceAt,
        Instant oldestEvidenceAt
    ) {
    }

    private static final class UnitPairSignalBuilder {

        private final Long leftUnitId;
        private final Long rightUnitId;
        private final Set<Long> publicationIds = new LinkedHashSet<>();
        private Instant newestEvidenceAt;
        private Instant oldestEvidenceAt;

        private UnitPairSignalBuilder(Long leftUnitId, Long rightUnitId) {
            this.leftUnitId = leftUnitId;
            this.rightUnitId = rightUnitId;
        }

        private void addPublication(Long publicationId, Instant updatedAt) {
            publicationIds.add(publicationId);
            if (updatedAt == null) {
                return;
            }
            newestEvidenceAt = newestEvidenceAt == null || updatedAt.isAfter(newestEvidenceAt) ? updatedAt : newestEvidenceAt;
            oldestEvidenceAt = oldestEvidenceAt == null || updatedAt.isBefore(oldestEvidenceAt) ? updatedAt : oldestEvidenceAt;
        }

        private UnitPairSignal build() {
            return new UnitPairSignal(leftUnitId, rightUnitId, Set.copyOf(publicationIds), newestEvidenceAt, oldestEvidenceAt);
        }
    }

    private record PairKey(
        Long left,
        Long right
    ) {

        private static PairKey of(Long left, Long right) {
            if (left.compareTo(right) <= 0) {
                return new PairKey(left, right);
            }
            return new PairKey(right, left);
        }
    }
}
