package com.researchintelligence.platform.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.api.AiSuggestionResponse;
import com.researchintelligence.platform.ai.api.DataQualityFixSuggestionRequest;
import com.researchintelligence.platform.ai.api.DataQualityFixSuggestionResponse;
import com.researchintelligence.platform.ai.api.DataQualitySuggestionScope;
import com.researchintelligence.platform.ai.api.TopicRecommendationRequest;
import com.researchintelligence.platform.ai.api.TopicRecommendationResponse;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.dataquality.api.DataQualityIssueResponse;
import com.researchintelligence.platform.dataquality.domain.DataQualityEntityType;
import com.researchintelligence.platform.dataquality.domain.DataQualityIssueType;
import com.researchintelligence.platform.dataquality.persistence.DataQualityIssueRow;
import com.researchintelligence.platform.dataquality.persistence.DataQualityRepository;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorEntity;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicEntity;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.domain.OrganizationScope;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AiDataQualitySuggestionService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final DataQualityRepository dataQualityRepository;
    private final AiSuggestionService aiSuggestionService;
    private final TopicRecommendationService topicRecommendationService;
    private final LlmService llmService;
    private final PublicationRepository publicationRepository;
    private final PublicationAuthorRepository publicationAuthorRepository;
    private final PublicationTopicRepository publicationTopicRepository;
    private final TopicRepository topicRepository;
    private final ResearcherRepository researcherRepository;
    private final ResearchUnitRepository researchUnitRepository;
    private final VisibilityContext visibilityContext;
    private final ObjectMapper objectMapper;

    public AiDataQualitySuggestionService(
        DataQualityRepository dataQualityRepository,
        AiSuggestionService aiSuggestionService,
        TopicRecommendationService topicRecommendationService,
        LlmService llmService,
        PublicationRepository publicationRepository,
        PublicationAuthorRepository publicationAuthorRepository,
        PublicationTopicRepository publicationTopicRepository,
        TopicRepository topicRepository,
        ResearcherRepository researcherRepository,
        ResearchUnitRepository researchUnitRepository,
        VisibilityContext visibilityContext,
        ObjectMapper objectMapper
    ) {
        this.dataQualityRepository = dataQualityRepository;
        this.aiSuggestionService = aiSuggestionService;
        this.topicRecommendationService = topicRecommendationService;
        this.llmService = llmService;
        this.publicationRepository = publicationRepository;
        this.publicationAuthorRepository = publicationAuthorRepository;
        this.publicationTopicRepository = publicationTopicRepository;
        this.topicRepository = topicRepository;
        this.researcherRepository = researcherRepository;
        this.researchUnitRepository = researchUnitRepository;
        this.visibilityContext = visibilityContext;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<DataQualityFixSuggestionResponse> suggestFixes(DataQualityFixSuggestionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data quality suggestion request is required.");
        }
        PlatformUserPrincipal user = requireAllowedRequester();
        Long researcherScope = researcherScope(request.scope(), user);
        int limit = safeLimit(request.limit());
        List<DataQualityIssueRow> issues = dataQualityRepository.search(
            request.issueType(),
            null,
            request.entityType(),
            request.entityId(),
            null,
            0,
            limit,
            researcherScope
        ).getContent();

        List<DataQualityFixSuggestionResponse> responses = new ArrayList<>();
        for (DataQualityIssueRow row : issues) {
            DataQualityIssueResponse issue = toIssueResponse(row);
            SuggestionDraft draft = suggestionFor(issue);
            AiSuggestionResponse createdSuggestion = createSuggestion(issue, draft);
            responses.add(new DataQualityFixSuggestionResponse(
                issue,
                draft.suggestedFix(),
                draft.confidence(),
                draft.evidence(),
                createdSuggestion.id()
            ));
        }
        return responses;
    }

    private PlatformUserPrincipal requireAllowedRequester() {
        PlatformUserPrincipal user = visibilityContext.currentUser()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is required to request AI data quality suggestions."));
        if (hasRole(user, "ADMIN") || hasRole(user, "VALIDATOR") || hasRole(user, "RESEARCHER")) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Public users cannot request AI data quality suggestions.");
    }

    private Long researcherScope(DataQualitySuggestionScope scope, PlatformUserPrincipal user) {
        if (scope == DataQualitySuggestionScope.ADMIN_ALL) {
            if (hasRole(user, "ADMIN") || hasRole(user, "VALIDATOR")) {
                return null;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins and validators can request all data quality issues.");
        }
        if (scope == DataQualitySuggestionScope.MY_DATA) {
            if (user.researcherId() == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MY_DATA scope requires a linked researcher profile.");
            }
            return user.researcherId();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported data quality suggestion scope.");
    }

    private SuggestionDraft suggestionFor(DataQualityIssueResponse issue) {
        return switch (issue.issueType()) {
            case PUBLICATIONS_WITHOUT_ABSTRACT -> missingAbstract(issue);
            case PUBLICATIONS_WITHOUT_TOPICS -> missingTopics(issue);
            case PUBLICATIONS_WITHOUT_PUBLIC_SUMMARY -> missingPublicSummary(issue);
            case DUPLICATE_PUBLICATION_CANDIDATES -> duplicatePublication(issue);
            case PUBLICATIONS_WITH_EXTERNAL_AUTHORS -> externalAuthorsForPublication(issue);
            case UNRESOLVED_EXTERNAL_AUTHORS -> externalAuthorMatch(issue);
            case EXTERNAL_ORGANIZATION_DUPLICATE_CANDIDATES -> externalOrganizationDuplicate(issue);
            case PUBLICATION_TITLE_CASING_ISSUES -> titleCasing(issue);
            case PUBLICATIONS_WITHOUT_DOI -> missingDoi(issue);
            case DUPLICATE_TOPIC_CANDIDATES -> duplicateTopic(issue);
            default -> manualReview(issue);
        };
    }

    private SuggestionDraft missingAbstract(DataQualityIssueResponse issue) {
        PublicationEntity publication = publication(issue.entityId());
        List<String> topics = topicNamesForPublication(publication.getId());
        List<String> warnings = new ArrayList<>();
        String fallback = "Draft abstract: This record for \"%s\" needs an author-reviewed abstract. Use the title, year%s%s to summarize the research objective, method, and main contribution without adding unsupported claims."
            .formatted(
                publication.getTitle(),
                publication.getPublicationYear() == null ? "" : " " + publication.getPublicationYear(),
                topics.isEmpty() ? "" : ", and topics " + String.join(", ", topics)
            );
        String draftAbstract = generatedText(
            "Draft a concise abstract for this publication using only the supplied metadata. Do not invent results, DOI values, funders, metrics, or claims.",
            publicationContext(publication, topics),
            fallback,
            warnings
        );
        Map<String, Object> fix = orderedMap(
            "action", "draft_abstract",
            "field", "abstractText",
            "draftAbstract", draftAbstract,
            "requiresHumanReview", true
        );
        return new SuggestionDraft(fix, 0.62, evidence(issue, topics, warnings));
    }

    private SuggestionDraft missingTopics(DataQualityIssueResponse issue) {
        PublicationEntity publication = publication(issue.entityId());
        TopicRecommendationResponse recommendation = topicRecommendationService.recommend(new TopicRecommendationRequest(
            null,
            null,
            publication.getTitle(),
            publication.getAbstractText(),
            List.of(),
            8
        ));
        double confidence = recommendation.suggestedTopics().stream()
            .mapToDouble(topic -> topic.confidence())
            .average()
            .orElse(0.45);
        Map<String, Object> fix = orderedMap(
            "action", "recommend_topics",
            "field", "topics",
            "suggestedTopics", recommendation.suggestedTopics(),
            "topicRecommendationSuggestionId", recommendation.aiSuggestionId(),
            "requiresHumanReview", true
        );
        Map<String, Object> evidence = orderedMap(
            "issueType", issue.issueType().name(),
            "source", "TopicRecommendationService",
            "warnings", recommendation.warnings()
        );
        return new SuggestionDraft(fix, round(confidence), evidence);
    }

    private SuggestionDraft missingPublicSummary(DataQualityIssueResponse issue) {
        PublicationEntity publication = publication(issue.entityId());
        List<String> topics = topicNamesForPublication(publication.getId());
        List<String> warnings = new ArrayList<>();
        String fallback = "Public summary draft: \"%s\" can be presented as a research output about %s. Add a reviewer-approved explanation of the problem, approach, and potential relevance for non-specialist readers."
            .formatted(publication.getTitle(), topics.isEmpty() ? "the documented publication metadata" : String.join(", ", topics));
        String publicSummary = generatedText(
            "Draft a plain-language public summary in Spanish from the supplied publication metadata. Do not invent outcomes, metrics, DOI values, or external facts.",
            publicationContext(publication, topics),
            fallback,
            warnings
        );
        Map<String, Object> fix = orderedMap(
            "action", "draft_public_summary",
            "field", "publicSummary",
            "publicSummary", publicSummary,
            "requiresHumanReview", true
        );
        return new SuggestionDraft(fix, 0.64, evidence(issue, topics, warnings));
    }

    private SuggestionDraft duplicatePublication(DataQualityIssueResponse issue) {
        PublicationEntity publication = publication(issue.entityId());
        List<Map<String, Object>> candidates = publicationRepository.findDuplicateCandidates(
            publication.getId(),
            publication.getTitle(),
            publication.getPublicationYear(),
            PageRequest.of(0, 10)
        ).stream()
            .map(candidate -> orderedMap(
                "publicationId", candidate.getId(),
                "title", candidate.getTitle(),
                "year", candidate.getPublicationYear(),
                "doi", candidate.getDoi()
            ))
            .toList();
        Map<String, Object> fix = orderedMap(
            "action", "review_duplicate_publication_candidates",
            "reason", "Shares normalized title and publication year with candidate records.",
            "candidates", candidates,
            "requiresHumanReview", true
        );
        return new SuggestionDraft(fix, candidates.isEmpty() ? 0.5 : 0.82, evidence(issue, List.of(), List.of()));
    }

    private SuggestionDraft externalAuthorMatch(DataQualityIssueResponse issue) {
        PublicationAuthorEntity author = publicationAuthor(issue.entityId());
        String authorName = normalizeWhitespace(author.getExternalAuthorName());
        List<Map<String, Object>> candidates = researcherCandidates(authorName).stream()
            .map(candidate -> orderedMap(
                "researcherId", candidate.getId(),
                "fullName", candidate.getFullName(),
                "displayName", candidate.getDisplayName(),
                "orcid", candidate.getOrcid()
            ))
            .toList();
        Map<String, Object> fix = orderedMap(
            "action", "review_external_author_internal_match",
            "externalAuthorName", authorName,
            "externalAffiliation", author.getExternalAffiliation(),
            "candidateResearchers", candidates,
            "requiresHumanReview", true
        );
        return new SuggestionDraft(fix, candidates.isEmpty() ? 0.42 : 0.72, evidence(issue, List.of(), List.of()));
    }

    private SuggestionDraft externalAuthorsForPublication(DataQualityIssueResponse issue) {
        List<Map<String, Object>> externalAuthors = publicationAuthorRepository.findByPublicationIdOrderByAuthorOrderAsc(issue.entityId())
            .stream()
            .filter(author -> author.getResearcherId() == null)
            .map(author -> orderedMap(
                "publicationAuthorId", author.getId(),
                "externalAuthorName", author.getExternalAuthorName(),
                "externalAffiliation", author.getExternalAffiliation(),
                "candidateResearchers", researcherCandidates(normalizeWhitespace(author.getExternalAuthorName())).stream()
                    .map(candidate -> orderedMap(
                        "researcherId", candidate.getId(),
                        "fullName", candidate.getFullName(),
                        "displayName", candidate.getDisplayName(),
                        "orcid", candidate.getOrcid()
                    ))
                    .toList()
            ))
            .toList();
        Map<String, Object> fix = orderedMap(
            "action", "review_external_authors",
            "externalAuthors", externalAuthors,
            "requiresHumanReview", true
        );
        boolean hasCandidate = externalAuthors.stream()
            .map(author -> author.get("candidateResearchers"))
            .filter(List.class::isInstance)
            .map(List.class::cast)
            .anyMatch(candidates -> !candidates.isEmpty());
        return new SuggestionDraft(fix, hasCandidate ? 0.7 : 0.45, evidence(issue, List.of(), List.of()));
    }

    private SuggestionDraft externalOrganizationDuplicate(DataQualityIssueResponse issue) {
        ResearchUnitEntity unit = researchUnit(issue.entityId());
        List<Map<String, Object>> candidates = researchUnitRepository.findDuplicateNameCandidates(
            unit.getId(),
            unit.getName(),
            OrganizationScope.EXTERNAL,
            PageRequest.of(0, 10)
        ).stream()
            .map(candidate -> orderedMap(
                "researchUnitId", candidate.getId(),
                "name", candidate.getName(),
                "shortName", candidate.getShortName(),
                "city", candidate.getCity(),
                "country", candidate.getCountry()
            ))
            .toList();
        Map<String, Object> fix = orderedMap(
            "action", "review_external_organization_normalization",
            "normalizationCandidate", normalizedOrganizationName(unit.getName()),
            "candidateOrganizations", candidates,
            "requiresHumanReview", true
        );
        return new SuggestionDraft(fix, candidates.isEmpty() ? 0.5 : 0.78, evidence(issue, List.of(), List.of()));
    }

    private SuggestionDraft titleCasing(DataQualityIssueResponse issue) {
        PublicationEntity publication = publication(issue.entityId());
        String normalizedTitle = titleCase(publication.getTitle());
        Map<String, Object> fix = orderedMap(
            "action", "suggest_normalized_title",
            "field", "title",
            "currentTitle", publication.getTitle(),
            "normalizedTitle", normalizedTitle,
            "requiresHumanReview", true
        );
        return new SuggestionDraft(fix, normalizedTitle.equals(publication.getTitle()) ? 0.45 : 0.86, evidence(issue, List.of(), List.of()));
    }

    private SuggestionDraft missingDoi(DataQualityIssueResponse issue) {
        PublicationEntity publication = publication(issue.entityId());
        Map<String, Object> fix = orderedMap(
            "action", "external_lookup_required",
            "field", "doi",
            "suggestedDoi", null,
            "lookupHint", "Search trusted external sources such as Crossref, PubMed, publisher pages, or institutional records using the exact title and year.",
            "lookupQuery", lookupQuery(publication),
            "requiresHumanReview", true
        );
        Map<String, Object> evidence = orderedMap(
            "issueType", issue.issueType().name(),
            "source", "metadata_only",
            "warning", "No DOI was generated because missing external identifiers must be verified externally."
        );
        return new SuggestionDraft(fix, 0.9, evidence);
    }

    private SuggestionDraft duplicateTopic(DataQualityIssueResponse issue) {
        TopicEntity topic = topic(issue.entityId());
        List<Map<String, Object>> candidates = topicRepository.findDuplicateNameCandidates(topic.getId(), topic.getName(), PageRequest.of(0, 10))
            .stream()
            .map(candidate -> orderedMap(
                "topicId", candidate.getId(),
                "name", candidate.getName(),
                "normalizedName", candidate.getNormalizedName()
            ))
            .toList();
        Map<String, Object> fix = orderedMap(
            "action", "review_duplicate_topic_candidates",
            "canonicalNameCandidate", topic.getName(),
            "candidates", candidates,
            "requiresHumanReview", true
        );
        return new SuggestionDraft(fix, candidates.isEmpty() ? 0.5 : 0.8, evidence(issue, List.of(), List.of()));
    }

    private SuggestionDraft manualReview(DataQualityIssueResponse issue) {
        Map<String, Object> fix = orderedMap(
            "action", "manual_review",
            "suggestedAction", issue.suggestedAction(),
            "requiresHumanReview", true
        );
        return new SuggestionDraft(fix, 0.4, evidence(issue, List.of(), List.of()));
    }

    private AiSuggestionResponse createSuggestion(DataQualityIssueResponse issue, SuggestionDraft draft) {
        return aiSuggestionService.create(new AiSuggestionCreateCommand(
            issue.entityType().name(),
            issue.entityId(),
            AiSuggestionType.DATA_QUALITY_FIX,
            writeJson(draft.suggestedFix()),
            "Generated a data quality fix suggestion for " + issue.issueType().name() + ". Records were not modified automatically.",
            writeJson(draft.evidence()),
            llmService.provider(),
            llmService.model()
        ));
    }

    private String generatedText(String question, String context, String fallback, List<String> warnings) {
        if (!"ollama".equalsIgnoreCase(llmService.provider())) {
            return fallback;
        }
        try {
            LlmResponse response = llmService.answer(new LlmPrompt(question, context));
            warnings.addAll(response.warnings());
            String answer = normalizeWhitespace(response.answer());
            return answer.isBlank() ? fallback : answer;
        } catch (BusinessRuleException exception) {
            warnings.add(ollamaWarning(exception.getMessage()));
            return fallback;
        }
    }

    private String publicationContext(PublicationEntity publication, List<String> topics) {
        return """
            Publication ID: %d
            Title: %s
            Year: %s
            Type: %s
            Status: %s
            Source: %s
            Existing abstract: %s
            Existing topics: %s
            """.formatted(
            publication.getId(),
            publication.getTitle(),
            publication.getPublicationYear(),
            publication.getType(),
            publication.getStatus(),
            publication.getSource(),
            publication.getAbstractText(),
            topics.isEmpty() ? "None" : String.join(", ", topics)
        );
    }

    private Map<String, Object> evidence(DataQualityIssueResponse issue, List<String> topics, List<String> warnings) {
        return orderedMap(
            "issueType", issue.issueType().name(),
            "issueDescription", issue.description(),
            "metadataTopics", topics,
            "warnings", distinct(warnings)
        );
    }

    private List<String> topicNamesForPublication(Long publicationId) {
        List<PublicationTopicEntity> links = publicationTopicRepository.findByPublicationId(publicationId);
        if (links.isEmpty()) {
            return List.of();
        }
        Map<Long, TopicEntity> topicsById = topicRepository.findAllById(links.stream().map(PublicationTopicEntity::getTopicId).toList())
            .stream()
            .collect(Collectors.toMap(TopicEntity::getId, Function.identity(), (first, second) -> first));
        return links.stream()
            .map(link -> topicsById.get(link.getTopicId()))
            .filter(topic -> topic != null)
            .map(TopicEntity::getName)
            .distinct()
            .toList();
    }

    private List<ResearcherEntity> researcherCandidates(String authorName) {
        if (authorName.isBlank()) {
            return List.of();
        }
        String normalizedName = normalizeText(authorName);
        return researcherRepository.search(authorName, "%" + authorName.toLowerCase(Locale.ROOT) + "%", null, true, null, null, null, PageRequest.of(0, 10))
            .stream()
            .filter(candidate -> normalizeText(candidate.getFullName()).equals(normalizedName)
                || normalizeText(candidate.getDisplayName()).equals(normalizedName)
                || shareLastToken(candidate.getFullName(), authorName))
            .limit(5)
            .toList();
    }

    private boolean shareLastToken(String left, String right) {
        String leftLast = lastToken(normalizeText(left));
        String rightLast = lastToken(normalizeText(right));
        return !leftLast.isBlank() && leftLast.equals(rightLast);
    }

    private String lastToken(String value) {
        String[] tokens = value.split("\\s+");
        return tokens.length == 0 ? "" : tokens[tokens.length - 1];
    }

    private PublicationEntity publication(Long id) {
        return publicationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Publication", id));
    }

    private PublicationAuthorEntity publicationAuthor(Long id) {
        return publicationAuthorRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PublicationAuthor", id));
    }

    private ResearchUnitEntity researchUnit(Long id) {
        return researchUnitRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ResearchUnit", id));
    }

    private TopicEntity topic(Long id) {
        return topicRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Topic", id));
    }

    private DataQualityIssueResponse toIssueResponse(DataQualityIssueRow row) {
        return new DataQualityIssueResponse(
            row.issueType(),
            row.severity(),
            row.entityType(),
            row.entityId(),
            row.title(),
            row.description(),
            row.suggestedAction()
        );
    }

    private String lookupQuery(PublicationEntity publication) {
        return normalizeWhitespace(publication.getTitle() + (publication.getPublicationYear() == null ? "" : " " + publication.getPublicationYear()));
    }

    private String normalizedOrganizationName(String name) {
        return normalizeWhitespace(name);
    }

    private String titleCase(String value) {
        String normalized = normalizeWhitespace(value);
        if (normalized.isBlank()) {
            return normalized;
        }
        Set<String> lowerCaseWords = Set.of("a", "an", "and", "as", "de", "del", "en", "for", "in", "la", "of", "on", "the", "to", "y");
        String[] words = normalized.toLowerCase(Locale.ROOT).split("\\s+");
        List<String> titled = new ArrayList<>();
        for (int index = 0; index < words.length; index++) {
            String word = words[index];
            if (index > 0 && lowerCaseWords.contains(word)) {
                titled.add(word);
            } else {
                titled.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return String.join(" ", titled);
    }

    private String normalizeText(String value) {
        String normalized = Normalizer.normalize(normalizeWhitespace(value).toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "").replaceAll("[^\\p{Alnum}]+", " ").trim();
    }

    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private List<String> distinct(Collection<String> values) {
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new))
            .stream()
            .toList();
    }

    private boolean hasRole(PlatformUserPrincipal user, String role) {
        return user.roles().contains(role);
    }

    private int safeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private double round(double value) {
        return Math.round(Math.min(Math.max(value, 0.0), 1.0) * 100.0) / 100.0;
    }

    private String ollamaWarning(String message) {
        if (message != null && message.toLowerCase(Locale.ROOT).contains("ollama")) {
            return "Ollama is unavailable for data quality drafting; deterministic fallback was used.";
        }
        return "AI drafting is unavailable; deterministic fallback was used.";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException("Could not serialize data quality suggestion payload.");
        }
    }

    private Map<String, Object> orderedMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            map.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return map;
    }

    private record SuggestionDraft(
        Map<String, Object> suggestedFix,
        double confidence,
        Map<String, Object> evidence
    ) {
    }
}
