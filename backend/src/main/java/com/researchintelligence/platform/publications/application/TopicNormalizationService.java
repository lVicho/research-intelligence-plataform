package com.researchintelligence.platform.publications.application;

import com.researchintelligence.platform.ai.application.EmbeddingResponse;
import com.researchintelligence.platform.ai.application.EmbeddingService;
import com.researchintelligence.platform.ai.application.LlmPrompt;
import com.researchintelligence.platform.ai.application.LlmResponse;
import com.researchintelligence.platform.ai.application.LlmService;
import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.audit.application.AuditFieldChange;
import com.researchintelligence.platform.publications.api.TopicCanonicalNameRequest;
import com.researchintelligence.platform.publications.api.TopicCanonicalNameResponse;
import com.researchintelligence.platform.publications.api.TopicMergeRequest;
import com.researchintelligence.platform.publications.api.TopicMergeResponse;
import com.researchintelligence.platform.publications.api.TopicNormalizationCandidateGroupResponse;
import com.researchintelligence.platform.publications.api.TopicNormalizationTopicResponse;
import com.researchintelligence.platform.publications.api.TopicResponse;
import com.researchintelligence.platform.publications.api.TopicSimilarityScoreResponse;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TopicNormalizationService {

    private static final double TEXT_GROUP_THRESHOLD = 0.82;
    private static final double EMBEDDING_GROUP_THRESHOLD = 0.88;
    private static final double DISPLAY_SCORE_THRESHOLD = 0.70;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private final TopicRepository topicRepository;
    private final PublicationTopicRepository publicationTopicRepository;
    private final ActivityAuditService auditService;
    private final EmbeddingService embeddingService;
    private final LlmService llmService;

    public TopicNormalizationService(
        TopicRepository topicRepository,
        PublicationTopicRepository publicationTopicRepository,
        ActivityAuditService auditService,
        EmbeddingService embeddingService,
        LlmService llmService
    ) {
        this.topicRepository = topicRepository;
        this.publicationTopicRepository = publicationTopicRepository;
        this.auditService = auditService;
        this.embeddingService = embeddingService;
        this.llmService = llmService;
    }

    public List<TopicNormalizationCandidateGroupResponse> findNormalizationCandidates() {
        List<TopicEntity> topics = topicRepository.findAll();
        if (topics.size() < 2) {
            return List.of();
        }

        Map<Long, Long> countsByTopicId = publicationCounts(topics.stream().map(TopicEntity::getId).toList());
        Map<Long, TopicSignal> signals = topics.stream()
            .collect(Collectors.toMap(TopicEntity::getId, TopicSignal::from));
        UnionFind unionFind = new UnionFind(topics.stream().map(TopicEntity::getId).toList());
        Map<PairKey, PairScores> pairScores = new LinkedHashMap<>();

        for (int i = 0; i < topics.size(); i++) {
            TopicEntity left = topics.get(i);
            for (int j = i + 1; j < topics.size(); j++) {
                TopicEntity right = topics.get(j);
                double textScore = textSimilarity(signals.get(left.getId()), signals.get(right.getId()));
                PairScores scores = pairScores.computeIfAbsent(PairKey.of(left.getId(), right.getId()), key -> new PairScores());
                scores.textScore = textScore;
                if (textScore >= TEXT_GROUP_THRESHOLD) {
                    unionFind.union(left.getId(), right.getId());
                }
            }
        }

        Map<Long, List<Double>> embeddings = embeddingsByTopicId(topics);
        if (!embeddings.isEmpty()) {
            for (int i = 0; i < topics.size(); i++) {
                TopicEntity left = topics.get(i);
                List<Double> leftVector = embeddings.get(left.getId());
                if (leftVector == null) {
                    continue;
                }
                for (int j = i + 1; j < topics.size(); j++) {
                    TopicEntity right = topics.get(j);
                    List<Double> rightVector = embeddings.get(right.getId());
                    if (rightVector == null) {
                        continue;
                    }
                    double embeddingScore = cosineSimilarity(leftVector, rightVector);
                    PairScores scores = pairScores.computeIfAbsent(PairKey.of(left.getId(), right.getId()), key -> new PairScores());
                    scores.embeddingScore = embeddingScore;
                    if (embeddingScore >= EMBEDDING_GROUP_THRESHOLD) {
                        unionFind.union(left.getId(), right.getId());
                    }
                }
            }
        }

        Map<Long, List<TopicEntity>> groups = topics.stream()
            .collect(Collectors.groupingBy(topic -> unionFind.find(topic.getId()), LinkedHashMap::new, Collectors.toList()));

        return groups.values().stream()
            .filter(group -> group.size() > 1)
            .map(group -> toCandidateGroup(group, countsByTopicId, pairScores))
            .sorted(Comparator
                .comparingDouble(TopicNormalizationCandidateGroupResponse::confidence)
                .reversed()
                .thenComparing(TopicNormalizationCandidateGroupResponse::canonicalSuggestion))
            .toList();
    }

    public TopicCanonicalNameResponse suggestCanonicalName(TopicCanonicalNameRequest request) {
        List<TopicEntity> topics = topicsFromCanonicalRequest(request);
        List<String> names = new ArrayList<>();
        if (topics != null) {
            topics.stream().map(TopicEntity::getName).forEach(names::add);
        }
        if (request != null && request.topicNames() != null) {
            request.topicNames().stream()
                .filter(name -> name != null && !name.isBlank())
                .map(TopicNormalizer::displayName)
                .forEach(names::add);
        }
        if (names.isEmpty()) {
            throw new BusinessRuleException("At least one topic id or topic name is required.");
        }
        String fallback = deterministicCanonicalName(names, Map.of());
        String llmSuggestion = suggestWithLocalLlm(names).orElse(null);
        if (llmSuggestion != null) {
            return new TopicCanonicalNameResponse(llmSuggestion, llmService.provider(), llmService.model(), "Suggested by the configured local LLM provider.");
        }
        return new TopicCanonicalNameResponse(fallback, "deterministic", "text-normalization", "Local LLM is not configured; selected the clearest deterministic label.");
    }

    @Transactional
    public TopicMergeResponse merge(TopicMergeRequest request) {
        if (request == null || request.topicIdsToMerge() == null || request.topicIdsToMerge().isEmpty()) {
            throw new BusinessRuleException("At least one topic id to merge is required.");
        }
        TopicEntity canonical = resolveCanonicalTopic(request);
        List<Long> sourceIds = request.topicIdsToMerge().stream()
            .filter(Objects::nonNull)
            .filter(id -> !id.equals(canonical.getId()))
            .distinct()
            .toList();
        if (sourceIds.isEmpty()) {
            throw new BusinessRuleException("At least one non-canonical topic id must be provided.");
        }

        List<TopicEntity> sourceTopics = topicRepository.findAllById(sourceIds);
        if (sourceTopics.size() != sourceIds.size()) {
            Set<Long> foundIds = sourceTopics.stream().map(TopicEntity::getId).collect(Collectors.toSet());
            Long missingId = sourceIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElse(null);
            throw new ResourceNotFoundException("Topic", missingId);
        }

        long affectedPublicationsCount = publicationTopicRepository.countDistinctPublicationsByTopicIds(sourceIds);
        publicationTopicRepository.insertMissingCanonicalLinks(canonical.getId(), sourceIds);
        publicationTopicRepository.deleteByTopicIdIn(sourceIds);
        topicRepository.deleteAll(sourceTopics);

        Map<String, AuditFieldChange> changes = auditService.changes();
        auditService.addChange(changes, "canonicalTopicId", null, canonical.getId());
        auditService.addChange(changes, "canonicalName", null, canonical.getName());
        auditService.addChange(changes, "mergedTopicIds", null, sourceIds);
        auditService.addChange(changes, "mergedLabels", null, sourceTopics.stream().map(TopicEntity::getName).toList());
        auditService.addChange(changes, "affectedPublicationsCount", null, affectedPublicationsCount);
        auditService.recordMerged(
            ValidationEntityType.TOPIC,
            canonical.getId(),
            "Merged topic variants into canonical topic '" + canonical.getName() + "'.",
            changes
        );

        return new TopicMergeResponse(
            toTopicResponse(canonical),
            sourceTopics.stream().map(this::toTopicResponse).toList(),
            affectedPublicationsCount,
            true
        );
    }

    private TopicNormalizationCandidateGroupResponse toCandidateGroup(
        List<TopicEntity> group,
        Map<Long, Long> countsByTopicId,
        Map<PairKey, PairScores> pairScores
    ) {
        List<Long> ids = group.stream().map(TopicEntity::getId).toList();
        List<TopicSimilarityScoreResponse> scores = new ArrayList<>();
        double maxScore = 0.0;
        boolean hasExactSignature = false;
        boolean hasEmbedding = false;

        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                PairScores pair = pairScores.get(PairKey.of(ids.get(i), ids.get(j)));
                if (pair == null) {
                    continue;
                }
                if (pair.textScore >= DISPLAY_SCORE_THRESHOLD) {
                    scores.add(new TopicSimilarityScoreResponse(ids.get(i), ids.get(j), round(pair.textScore), "text"));
                    maxScore = Math.max(maxScore, pair.textScore);
                    hasExactSignature = hasExactSignature || pair.textScore >= 0.999;
                }
                if (pair.embeddingScore != null && pair.embeddingScore >= DISPLAY_SCORE_THRESHOLD) {
                    scores.add(new TopicSimilarityScoreResponse(ids.get(i), ids.get(j), round(pair.embeddingScore), "embedding"));
                    maxScore = Math.max(maxScore, pair.embeddingScore);
                    hasEmbedding = true;
                }
            }
        }

        List<String> names = group.stream().map(TopicEntity::getName).toList();
        String canonical = suggestWithLocalLlm(names)
            .orElseGet(() -> deterministicCanonicalName(names, countsByTopicId));
        String reason = reason(hasExactSignature, hasEmbedding, scores);
        return new TopicNormalizationCandidateGroupResponse(
            canonical,
            group.stream()
                .sorted(topicComparator(countsByTopicId))
                .map(topic -> new TopicNormalizationTopicResponse(
                    topic.getId(),
                    topic.getName(),
                    topic.getNormalizedName(),
                    countsByTopicId.getOrDefault(topic.getId(), 0L)
                ))
                .toList(),
            scores,
            reason,
            round(Math.min(0.99, Math.max(0.50, maxScore))),
            publicationTopicRepository.countDistinctPublicationsByTopicIds(ids)
        );
    }

    private String reason(boolean hasExactSignature, boolean hasEmbedding, List<TopicSimilarityScoreResponse> scores) {
        if (hasExactSignature && hasEmbedding) {
            return "Matched by accent-insensitive text normalization and embedding similarity.";
        }
        if (hasExactSignature) {
            return "Matched by accent-insensitive text normalization, token normalization, and Spanish/English AI synonyms.";
        }
        if (hasEmbedding) {
            return "Matched by embedding similarity from the configured local embedding provider.";
        }
        if (!scores.isEmpty()) {
            return "Matched by high fuzzy text similarity after normalization.";
        }
        return "Matched by normalization signals.";
    }

    private TopicEntity resolveCanonicalTopic(TopicMergeRequest request) {
        if (request.canonicalTopicId() != null) {
            return topicRepository.findById(request.canonicalTopicId())
                .orElseThrow(() -> new ResourceNotFoundException("Topic", request.canonicalTopicId()));
        }
        if (request.canonicalName() == null || request.canonicalName().isBlank()) {
            throw new BusinessRuleException("canonicalTopicId or canonicalName is required.");
        }
        String displayName = TopicNormalizer.displayName(request.canonicalName());
        String normalizedName = TopicNormalizer.normalize(displayName);
        return topicRepository.findByNormalizedName(normalizedName)
            .orElseGet(() -> topicRepository.save(new TopicEntity(displayName, normalizedName)));
    }

    private List<TopicEntity> topicsFromCanonicalRequest(TopicCanonicalNameRequest request) {
        if (request == null || request.topicIds() == null || request.topicIds().isEmpty()) {
            return List.of();
        }
        List<Long> ids = request.topicIds().stream().filter(Objects::nonNull).distinct().toList();
        List<TopicEntity> topics = topicRepository.findAllById(ids);
        if (topics.size() != ids.size()) {
            Set<Long> foundIds = topics.stream().map(TopicEntity::getId).collect(Collectors.toSet());
            Long missingId = ids.stream().filter(id -> !foundIds.contains(id)).findFirst().orElse(null);
            throw new ResourceNotFoundException("Topic", missingId);
        }
        return topics;
    }

    private Map<Long, Long> publicationCounts(Collection<Long> topicIds) {
        if (topicIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        publicationTopicRepository.countPublicationsByTopicIds(topicIds)
            .forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        return counts;
    }

    private Map<Long, List<Double>> embeddingsByTopicId(List<TopicEntity> topics) {
        if ("mock".equalsIgnoreCase(embeddingService.provider())) {
            return Map.of();
        }
        Map<Long, List<Double>> embeddings = new HashMap<>();
        try {
            for (TopicEntity topic : topics) {
                EmbeddingResponse response = embeddingService.embed(topic.getName());
                if (response.vector() != null && !response.vector().isEmpty()) {
                    embeddings.put(topic.getId(), response.vector());
                }
            }
        } catch (RuntimeException exception) {
            return Map.of();
        }
        return embeddings;
    }

    private java.util.Optional<String> suggestWithLocalLlm(List<String> names) {
        if (!"ollama".equalsIgnoreCase(llmService.provider())) {
            return java.util.Optional.empty();
        }
        String context = "Topic labels:\n" + String.join("\n", names);
        LlmPrompt prompt = new LlmPrompt(
            "Return one canonical research topic label. Prefer concise Spanish when labels are mixed Spanish and English. Return only the label.",
            context
        );
        try {
            LlmResponse response = llmService.answer(prompt);
            String answer = response.answer() == null ? "" : response.answer().trim();
            if (answer.isBlank()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(answer.lines().findFirst().orElse(answer).trim());
        } catch (RuntimeException exception) {
            return java.util.Optional.empty();
        }
    }

    private String deterministicCanonicalName(List<String> names, Map<Long, Long> countsByTopicId) {
        return names.stream()
            .filter(name -> name != null && !name.isBlank())
            .map(TopicNormalizer::displayName)
            .max(Comparator
                .comparingInt(this::canonicalQuality)
                .thenComparingInt(String::length)
                .thenComparing(Function.identity()))
            .orElse("");
    }

    private int canonicalQuality(String name) {
        String normalized = normalizeComparable(name);
        int score = normalized.length();
        if (normalized.contains("inteligencia artificial")) {
            score += 30;
        }
        if (normalized.contains("clinical") || normalized.contains("clinica") || normalized.contains("clinico")) {
            score += 8;
        }
        if (normalized.matches(".*\\bia\\b.*") || normalized.matches(".*\\bai\\b.*")) {
            score -= 4;
        }
        return score;
    }

    private Comparator<TopicEntity> topicComparator(Map<Long, Long> countsByTopicId) {
        return Comparator
            .comparingLong((TopicEntity topic) -> countsByTopicId.getOrDefault(topic.getId(), 0L))
            .reversed()
            .thenComparing(TopicEntity::getName);
    }

    private TopicResponse toTopicResponse(TopicEntity topic) {
        return new TopicResponse(topic.getId(), topic.getName(), topic.getNormalizedName());
    }

    private static double textSimilarity(TopicSignal left, TopicSignal right) {
        if (left.signature().equals(right.signature())) {
            return 1.0;
        }
        double tokenScore = jaccard(left.tokens(), right.tokens());
        double editScore = normalizedLevenshtein(left.comparableText(), right.comparableText());
        return Math.max(tokenScore, editScore);
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private static double normalizedLevenshtein(String left, String right) {
        int maxLength = Math.max(left.length(), right.length());
        if (maxLength == 0) {
            return 1.0;
        }
        return 1.0 - ((double) levenshtein(left, right) / maxLength);
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private static double cosineSimilarity(List<Double> left, List<Double> right) {
        int dimensions = Math.min(left.size(), right.size());
        if (dimensions == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double leftMagnitude = 0.0;
        double rightMagnitude = 0.0;
        for (int i = 0; i < dimensions; i++) {
            double leftValue = left.get(i);
            double rightValue = right.get(i);
            dot += leftValue * rightValue;
            leftMagnitude += leftValue * leftValue;
            rightMagnitude += rightValue * rightValue;
        }
        if (leftMagnitude == 0.0 || rightMagnitude == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    private static String normalizeComparable(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(decomposed).replaceAll("");
        return NON_ALNUM.matcher(withoutDiacritics).replaceAll(" ").trim().replaceAll("\\s+", " ");
    }

    private static String signature(String value) {
        String normalized = normalizeComparable(value)
            .replace("inteligencia artificial", " ai ")
            .replace("artificial intelligence", " ai ");
        Set<String> tokens = tokens(normalized).stream()
            .map(TopicNormalizationService::normalizeToken)
            .filter(token -> !token.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return tokens.stream().sorted().collect(Collectors.joining(" "));
    }

    private static Set<String> tokens(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : value.split("\\s+")) {
            String normalized = normalizeToken(token);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static String normalizeToken(String token) {
        return switch (token) {
            case "ia", "ai" -> "ai";
            case "clinica", "clinico", "clinicas", "clinicos", "clinical" -> "clinical";
            default -> token;
        };
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record TopicSignal(String comparableText, String signature, Set<String> tokens) {
        static TopicSignal from(TopicEntity topic) {
            String comparableText = normalizeComparable(topic.getName());
            String signature = TopicNormalizationService.signature(topic.getName());
            return new TopicSignal(comparableText, signature, TopicNormalizationService.tokens(signature));
        }
    }

    private record PairKey(Long leftId, Long rightId) {
        static PairKey of(Long leftId, Long rightId) {
            if (leftId <= rightId) {
                return new PairKey(leftId, rightId);
            }
            return new PairKey(rightId, leftId);
        }
    }

    private static class PairScores {
        private double textScore;
        private Double embeddingScore;
    }

    private static class UnionFind {
        private final Map<Long, Long> parents = new HashMap<>();

        UnionFind(List<Long> ids) {
            ids.forEach(id -> parents.put(id, id));
        }

        Long find(Long id) {
            Long parent = parents.get(id);
            if (parent == null || parent.equals(id)) {
                return id;
            }
            Long root = find(parent);
            parents.put(id, root);
            return root;
        }

        void union(Long left, Long right) {
            Long leftRoot = find(left);
            Long rightRoot = find(right);
            if (!leftRoot.equals(rightRoot)) {
                parents.put(rightRoot, leftRoot);
            }
        }
    }
}
