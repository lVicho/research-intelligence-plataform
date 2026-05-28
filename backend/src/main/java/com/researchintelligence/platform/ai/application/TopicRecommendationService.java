package com.researchintelligence.platform.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.api.TopicRecommendationRequest;
import com.researchintelligence.platform.ai.api.TopicRecommendationResponse;
import com.researchintelligence.platform.ai.api.TopicRecommendationTargetType;
import com.researchintelligence.platform.ai.api.TopicRecommendationTopicResponse;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.events.persistence.EventParticipationEntity;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicEntity;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.text.Normalizer;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class TopicRecommendationService {

    private static final int DEFAULT_MAX_TOPICS = 8;
    private static final int MAX_TOPICS = 20;
    private static final int MIN_MEANINGFUL_TOKENS = 3;
    private static final int MIN_MEANINGFUL_CHARACTERS = 30;
    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "and", "are", "for", "from", "in", "into", "of", "on", "or", "the", "to", "with",
        "con", "de", "del", "el", "en", "la", "las", "los", "para", "por", "sobre", "un", "una", "y"
    );

    private final PublicationRetrievalService retrievalService;
    private final EmbeddingService embeddingService;
    private final LlmService llmService;
    private final PublicationRepository publicationRepository;
    private final PublicationAuthorRepository publicationAuthorRepository;
    private final PublicationTopicRepository publicationTopicRepository;
    private final TopicRepository topicRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final ResearcherRepository researcherRepository;
    private final AiSuggestionService aiSuggestionService;
    private final VisibilityContext visibilityContext;
    private final ObjectMapper objectMapper;

    public TopicRecommendationService(
        PublicationRetrievalService retrievalService,
        EmbeddingService embeddingService,
        LlmService llmService,
        PublicationRepository publicationRepository,
        PublicationAuthorRepository publicationAuthorRepository,
        PublicationTopicRepository publicationTopicRepository,
        TopicRepository topicRepository,
        EventParticipationRepository eventParticipationRepository,
        ResearcherRepository researcherRepository,
        AiSuggestionService aiSuggestionService,
        VisibilityContext visibilityContext,
        ObjectMapper objectMapper
    ) {
        this.retrievalService = retrievalService;
        this.embeddingService = embeddingService;
        this.llmService = llmService;
        this.publicationRepository = publicationRepository;
        this.publicationAuthorRepository = publicationAuthorRepository;
        this.publicationTopicRepository = publicationTopicRepository;
        this.topicRepository = topicRepository;
        this.eventParticipationRepository = eventParticipationRepository;
        this.researcherRepository = researcherRepository;
        this.aiSuggestionService = aiSuggestionService;
        this.visibilityContext = visibilityContext;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TopicRecommendationResponse recommend(TopicRecommendationRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Topic recommendation request is required.");
        }
        PlatformUserPrincipal user = requireAllowedRequester();
        TargetText targetText = resolveAndAuthorizeTarget(request, user);
        int maxTopics = safeMaxTopics(request.maxTopics());
        String combinedText = combinedText(request, targetText);
        List<String> warnings = new ArrayList<>();
        List<RetrievedPublicationContext> similarPublications = List.of();

        if (isWeakInput(combinedText)) {
            warnings.add("Input is weak; provide a more specific title, abstract, or keywords before trusting topic recommendations.");
        } else {
            RetrievalOutcome outcome = retrieveSimilarPublications(combinedText, maxTopics);
            similarPublications = outcome.publications();
            warnings.addAll(outcome.warnings());
        }

        if (similarPublications.isEmpty()) {
            warnings.add("No similar validated publications were found.");
        }

        List<TopicRecommendationTopicResponse> suggestedTopics = isWeakInput(combinedText)
            ? List.of()
            : suggestTopics(request, similarPublications, maxTopics, warnings);

        if (suggestedTopics.isEmpty() && !isWeakInput(combinedText)) {
            warnings.add("No topic recommendations could be grounded in similar publication topics or canonical keyword matches.");
        }

        Long aiSuggestionId = createSuggestion(request, suggestedTopics, similarPublications, warnings);
        return new TopicRecommendationResponse(aiSuggestionId, suggestedTopics, distinct(warnings));
    }

    private RetrievalOutcome retrieveSimilarPublications(String combinedText, int maxTopics) {
        try {
            PublicationRetrievalResult result = retrievalService.retrieveBest(
                combinedText,
                new RetrievalOptions(Math.max(maxTopics * 2, DEFAULT_MAX_TOPICS), null, RetrievalMode.BALANCED),
                VisibilityScope.PUBLIC_VALIDATED,
                null
            );
            List<String> warnings = new ArrayList<>(result.warnings());
            if (result.retrievalMethod() == RetrievalMethod.TEXT) {
                warnings.add("Embeddings are unavailable for the configured provider/model/dimension; text fallback was used.");
            }
            return new RetrievalOutcome(result.publications(), warnings);
        } catch (BusinessRuleException exception) {
            List<String> warnings = new ArrayList<>();
            warnings.add(ollamaWarning(exception.getMessage(), "embeddings"));
            return new RetrievalOutcome(
                retrievalService.textSearch(combinedText, Math.max(maxTopics * 2, DEFAULT_MAX_TOPICS), VisibilityScope.PUBLIC_VALIDATED, null),
                warnings
            );
        }
    }

    private List<TopicRecommendationTopicResponse> suggestTopics(
        TopicRecommendationRequest request,
        List<RetrievedPublicationContext> similarPublications,
        int maxTopics,
        List<String> warnings
    ) {
        Map<String, TopicCandidate> candidates = candidatesFromSimilarPublications(similarPublications);
        addCanonicalKeywordCandidates(candidates, request.keywords());
        List<TopicCandidate> ranked = candidates.values().stream()
            .sorted(candidateComparator())
            .toList();
        ranked = rerankWithLlm(ranked, similarPublications, warnings);
        return ranked.stream()
            .limit(maxTopics)
            .map(TopicCandidate::toResponse)
            .toList();
    }

    private Map<String, TopicCandidate> candidatesFromSimilarPublications(List<RetrievedPublicationContext> similarPublications) {
        if (similarPublications.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<Long, RetrievedPublicationContext> contextsById = similarPublications.stream()
            .collect(Collectors.toMap(context -> context.publication().getId(), Function.identity(), (first, second) -> first, LinkedHashMap::new));
        List<PublicationTopicEntity> links = publicationTopicRepository.findByPublicationIdIn(contextsById.keySet());
        Map<Long, TopicEntity> topicsById = topicRepository.findAllById(links.stream()
            .map(PublicationTopicEntity::getTopicId)
            .toList())
            .stream()
            .collect(Collectors.toMap(TopicEntity::getId, Function.identity(), (first, second) -> first));
        Map<String, TopicCandidate> candidates = new LinkedHashMap<>();
        for (PublicationTopicEntity link : links) {
            TopicEntity topic = topicsById.get(link.getTopicId());
            RetrievedPublicationContext context = contextsById.get(link.getPublicationId());
            if (topic == null || context == null) {
                continue;
            }
            String key = normalizeTopic(topic.getName());
            TopicCandidate candidate = candidates.computeIfAbsent(key, ignored -> new TopicCandidate(topic.getName(), topic.getId()));
            candidate.addEvidence(context.publication().getId(), context.similarityScore());
        }
        return candidates;
    }

    private void addCanonicalKeywordCandidates(Map<String, TopicCandidate> candidates, List<String> keywords) {
        List<String> normalizedKeywords = normalizedKeywords(keywords);
        if (normalizedKeywords.isEmpty()) {
            return;
        }
        for (TopicEntity topic : topicRepository.findByNormalizedNameIn(normalizedKeywords)) {
            String key = normalizeTopic(topic.getName());
            TopicCandidate candidate = candidates.computeIfAbsent(key, ignored -> new TopicCandidate(topic.getName(), topic.getId()));
            candidate.markKeywordMatch();
        }
    }

    private List<TopicCandidate> rerankWithLlm(
        List<TopicCandidate> ranked,
        List<RetrievedPublicationContext> similarPublications,
        List<String> warnings
    ) {
        if (ranked.size() < 2 || !"ollama".equalsIgnoreCase(llmService.provider())) {
            return ranked;
        }
        try {
            LlmResponse response = llmService.answer(new LlmPrompt(
                """
                    Rank these existing topic labels for the submitted publication. Return only labels already present in the candidate list, one per line.
                    Candidate labels:
                    %s
                    """.formatted(ranked.stream().map(TopicCandidate::label).collect(Collectors.joining("\n"))),
                similarPublications.stream()
                    .map(context -> "[pub:%d] %s Topics: %s".formatted(
                        context.publication().getId(),
                        context.publication().getTitle(),
                        String.join(", ", context.topics())
                    ))
                    .collect(Collectors.joining("\n"))
            ));
            Map<String, TopicCandidate> byNormalizedLabel = ranked.stream()
                .collect(Collectors.toMap(candidate -> normalizeTopic(candidate.label()), Function.identity(), (first, second) -> first));
            List<TopicCandidate> llmRanked = parseLlmRankedLabels(response.answer()).stream()
                .map(byNormalizedLabel::get)
                .filter(candidate -> candidate != null)
                .distinct()
                .toList();
            if (llmRanked.isEmpty()) {
                return ranked;
            }
            Set<String> selected = llmRanked.stream().map(candidate -> normalizeTopic(candidate.label())).collect(Collectors.toSet());
            List<TopicCandidate> merged = new ArrayList<>(llmRanked);
            ranked.stream()
                .filter(candidate -> !selected.contains(normalizeTopic(candidate.label())))
                .forEach(merged::add);
            return merged;
        } catch (BusinessRuleException exception) {
            warnings.add(ollamaWarning(exception.getMessage(), "topic ranking"));
            return ranked;
        }
    }

    private Long createSuggestion(
        TopicRecommendationRequest request,
        List<TopicRecommendationTopicResponse> suggestedTopics,
        List<RetrievedPublicationContext> similarPublications,
        List<String> warnings
    ) {
        AiSuggestionCreateCommand command = new AiSuggestionCreateCommand(
            request.targetType() == null ? null : request.targetType().name(),
            request.targetId(),
            AiSuggestionType.TOPIC_RECOMMENDATION,
            writeJson(new ProposedTopicsPayload(suggestedTopics)),
            "Generated topic recommendations from submitted text and similar validated publications.",
            writeJson(new TopicEvidencePayload(
                similarPublications.stream().map(this::toEvidencePublication).toList(),
                suggestedTopics,
                distinct(warnings)
            )),
            "embedding:" + embeddingService.provider() + ",llm:" + llmService.provider(),
            "embedding:" + embeddingService.model() + ",llm:" + llmService.model()
        );
        return aiSuggestionService.create(command).id();
    }

    private EvidencePublicationPayload toEvidencePublication(RetrievedPublicationContext context) {
        return new EvidencePublicationPayload(
            context.publication().getId(),
            context.publication().getTitle(),
            context.similarityScore(),
            context.topics()
        );
    }

    private TargetText resolveAndAuthorizeTarget(TopicRecommendationRequest request, PlatformUserPrincipal user) {
        if (request.targetId() != null && request.targetType() == null) {
            throw new BusinessRuleException("targetType is required when targetId is provided.");
        }
        if (user.roles().contains("ADMIN")) {
            return targetText(request);
        }
        if (user.roles().contains("VALIDATOR")) {
            requireValidationWorkflowTarget(request);
            return targetText(request);
        }
        if (user.roles().contains("RESEARCHER")) {
            requireOwnedTarget(request, user);
            return targetText(request);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Public users cannot create AI topic recommendations.");
    }

    private TargetText targetText(TopicRecommendationRequest request) {
        if (request.targetType() == null || request.targetId() == null) {
            return TargetText.empty();
        }
        return switch (request.targetType()) {
            case PUBLICATION -> {
                PublicationEntity publication = publicationRepository.findById(request.targetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Publication", request.targetId()));
                yield new TargetText(publication.getTitle(), publication.getAbstractText(), List.of());
            }
            case EVENT_PARTICIPATION -> {
                EventParticipationEntity participation = eventParticipationRepository.findById(request.targetId())
                    .orElseThrow(() -> new ResourceNotFoundException("EventParticipation", request.targetId()));
                yield new TargetText(participation.getTitle(), participation.getDescription(), List.of());
            }
            case RESEARCHER_PROFILE -> {
                ResearcherEntity researcher = researcherRepository.findById(request.targetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Researcher", request.targetId()));
                yield new TargetText(researcher.getDisplayName() == null ? researcher.getFullName() : researcher.getDisplayName(), null, List.of());
            }
        };
    }

    private void requireValidationWorkflowTarget(TopicRecommendationRequest request) {
        if (request.targetType() == null || request.targetId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Validators can request topic recommendations only for validation workflow entities.");
        }
        ValidationStatus status = validationStatus(request.targetType(), request.targetId());
        if (status == ValidationStatus.VALIDATED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Validators can request topic recommendations only for non-validated workflow entities.");
        }
    }

    private void requireOwnedTarget(TopicRecommendationRequest request, PlatformUserPrincipal user) {
        if (user.researcherId() == null || request.targetType() == null || request.targetId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Researchers can request topic recommendations only for their own linked draft or activity.");
        }
        boolean owned = switch (request.targetType()) {
            case PUBLICATION -> publicationAuthorRepository.existsByPublicationIdAndResearcherId(request.targetId(), user.researcherId())
                && validationStatus(request.targetType(), request.targetId()) != ValidationStatus.VALIDATED;
            case EVENT_PARTICIPATION -> eventParticipationRepository.findByIdAndResearcherId(request.targetId(), user.researcherId())
                .filter(participation -> participation.getValidationStatus() != ValidationStatus.VALIDATED)
                .isPresent();
            case RESEARCHER_PROFILE -> request.targetId().equals(user.researcherId())
                && validationStatus(request.targetType(), request.targetId()) != ValidationStatus.VALIDATED;
        };
        if (!owned) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Topic recommendation target does not belong to the current researcher.");
        }
    }

    private ValidationStatus validationStatus(TopicRecommendationTargetType targetType, Long targetId) {
        return switch (targetType) {
            case PUBLICATION -> publicationRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Publication", targetId))
                .getValidationStatus();
            case EVENT_PARTICIPATION -> eventParticipationRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("EventParticipation", targetId))
                .getValidationStatus();
            case RESEARCHER_PROFILE -> researcherRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Researcher", targetId))
                .getValidationStatus();
        };
    }

    private PlatformUserPrincipal requireAllowedRequester() {
        PlatformUserPrincipal user = visibilityContext.currentUser()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is required to create AI topic recommendations."));
        if (user.roles().contains("ADMIN") || user.roles().contains("VALIDATOR") || user.roles().contains("RESEARCHER")) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Public users cannot create AI topic recommendations.");
    }

    private String combinedText(TopicRecommendationRequest request, TargetText targetText) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, request.title());
        addIfPresent(parts, targetText.title());
        addIfPresent(parts, request.abstractText());
        addIfPresent(parts, targetText.abstractText());
        parts.addAll(normalizedDisplayKeywords(request.keywords()));
        parts.addAll(targetText.keywords());
        return String.join("\n", parts).trim();
    }

    private boolean isWeakInput(String value) {
        List<String> tokens = meaningfulTokens(value);
        return tokens.size() < MIN_MEANINGFUL_TOKENS || normalizeWhitespace(value).length() < MIN_MEANINGFUL_CHARACTERS;
    }

    private List<String> meaningfulTokens(String value) {
        return List.of(normalizeText(value).split("[^\\p{Alnum}]+"))
            .stream()
            .filter(token -> token.length() >= 3)
            .filter(token -> !STOP_WORDS.contains(token))
            .distinct()
            .toList();
    }

    private List<String> normalizedKeywords(List<String> keywords) {
        return normalizedDisplayKeywords(keywords).stream()
            .map(this::normalizeTopic)
            .filter(keyword -> !keyword.isBlank())
            .distinct()
            .toList();
    }

    private List<String> normalizedDisplayKeywords(List<String> keywords) {
        if (keywords == null) {
            return List.of();
        }
        return keywords.stream()
            .map(this::normalizeWhitespace)
            .filter(keyword -> !keyword.isBlank())
            .distinct()
            .toList();
    }

    private Comparator<TopicCandidate> candidateComparator() {
        return Comparator.comparingDouble(TopicCandidate::rankingScore)
            .reversed()
            .thenComparing(TopicCandidate::label);
    }

    private List<String> parseLlmRankedLabels(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        return answer.lines()
            .map(line -> line.replaceFirst("^\\s*[-*\\d.)]+\\s*", ""))
            .map(line -> line.replace("\"", "").trim())
            .filter(line -> !line.isBlank())
            .toList();
    }

    private String ollamaWarning(String message, String capability) {
        if (message != null && message.toLowerCase(Locale.ROOT).contains("ollama")) {
            return "Ollama is unavailable for " + capability + "; deterministic/text fallback was used.";
        }
        return "AI " + capability + " are unavailable; deterministic/text fallback was used.";
    }

    private int safeMaxTopics(Integer maxTopics) {
        if (maxTopics == null) {
            return DEFAULT_MAX_TOPICS;
        }
        return Math.min(Math.max(maxTopics, 1), MAX_TOPICS);
    }

    private void addIfPresent(List<String> parts, String value) {
        String normalized = normalizeWhitespace(value);
        if (!normalized.isBlank()) {
            parts.add(normalized);
        }
    }

    private List<String> distinct(Collection<String> values) {
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new))
            .stream()
            .toList();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException("Could not serialize topic recommendation suggestion payload.");
        }
    }

    private String normalizeTopic(String value) {
        return normalizeText(value).replaceAll("[^\\p{Alnum}]+", " ").trim();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private record TargetText(String title, String abstractText, List<String> keywords) {
        static TargetText empty() {
            return new TargetText(null, null, List.of());
        }
    }

    private record RetrievalOutcome(List<RetrievedPublicationContext> publications, List<String> warnings) {
    }

    private record ProposedTopicsPayload(List<TopicRecommendationTopicResponse> suggestedTopics) {
    }

    private record TopicEvidencePayload(
        List<EvidencePublicationPayload> similarPublications,
        List<TopicRecommendationTopicResponse> suggestedTopics,
        List<String> warnings
    ) {
    }

    private record EvidencePublicationPayload(
        Long publicationId,
        String title,
        Double similarityScore,
        List<String> topics
    ) {
    }

    private static final class TopicCandidate {
        private final String label;
        private final Long existingTopicId;
        private final Set<Long> evidencePublicationIds = new LinkedHashSet<>();
        private double similarityTotal;
        private int similarityCount;
        private boolean keywordMatch;

        private TopicCandidate(String label, Long existingTopicId) {
            this.label = label;
            this.existingTopicId = existingTopicId;
        }

        private String label() {
            return label;
        }

        private void addEvidence(Long publicationId, Double similarityScore) {
            evidencePublicationIds.add(publicationId);
            if (similarityScore != null) {
                similarityTotal += similarityScore;
                similarityCount++;
            }
        }

        private void markKeywordMatch() {
            keywordMatch = true;
        }

        private double rankingScore() {
            return confidence();
        }

        private TopicRecommendationTopicResponse toResponse() {
            return new TopicRecommendationTopicResponse(
                label,
                existingTopicId,
                confidence(),
                reason(),
                evidencePublicationIds.stream().toList()
            );
        }

        private double confidence() {
            double averageSimilarity = similarityCount == 0 ? 0.0 : similarityTotal / similarityCount;
            double value = 0.42
                + Math.min(0.30, evidencePublicationIds.size() * 0.10)
                + Math.min(0.20, averageSimilarity * 0.20)
                + (keywordMatch ? 0.10 : 0.0);
            return Math.round(Math.min(value, 0.95) * 100.0) / 100.0;
        }

        private String reason() {
            if (evidencePublicationIds.isEmpty() && keywordMatch) {
                return "Matches a provided keyword and an existing canonical topic.";
            }
            String base = evidencePublicationIds.size() == 1
                ? "Found in 1 similar validated publication."
                : "Found in " + evidencePublicationIds.size() + " similar validated publications.";
            if (keywordMatch) {
                return base + " Also matches a provided keyword.";
            }
            return base;
        }
    }
}
