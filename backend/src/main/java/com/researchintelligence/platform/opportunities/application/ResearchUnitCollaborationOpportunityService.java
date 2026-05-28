package com.researchintelligence.platform.opportunities.application;

import com.researchintelligence.platform.ai.application.AiProperties;
import com.researchintelligence.platform.ai.application.EmbeddingService;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingRepository;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingSearchRow;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.opportunities.api.ComplementaryTopicResponse;
import com.researchintelligence.platform.opportunities.api.OpportunityPublicationResponse;
import com.researchintelligence.platform.opportunities.api.OpportunityResearchUnitResponse;
import com.researchintelligence.platform.opportunities.api.ResearchUnitCollaborationOpportunityResponse;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorEntity;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationSpecifications;
import com.researchintelligence.platform.publications.persistence.PublicationTopicEntity;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ResearchUnitCollaborationOpportunityService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_REPRESENTATIVE_PUBLICATIONS = 3;
    private static final int MAX_COMPLEMENTARY_TOPICS = 5;
    private static final int MAX_SHARED_TOPICS = 10;
    private static final int MAX_SEMANTIC_NEIGHBORS = 200;
    private static final Set<String> TOPIC_STOP_WORDS = Set.of(
        "a",
        "al",
        "and",
        "con",
        "de",
        "del",
        "el",
        "en",
        "for",
        "la",
        "las",
        "los",
        "of",
        "para",
        "por",
        "the",
        "to",
        "y"
    );

    private final AiProperties properties;
    private final EmbeddingService embeddingService;
    private final PublicationEmbeddingRepository embeddingRepository;
    private final PublicationRepository publicationRepository;
    private final PublicationAuthorRepository authorRepository;
    private final PublicationTopicRepository publicationTopicRepository;
    private final TopicRepository topicRepository;
    private final ResearcherRepository researcherRepository;
    private final ResearcherAffiliationRepository affiliationRepository;
    private final ResearchUnitRepository researchUnitRepository;
    private final VisibilityContext visibilityContext;

    public ResearchUnitCollaborationOpportunityService(
        AiProperties properties,
        EmbeddingService embeddingService,
        PublicationEmbeddingRepository embeddingRepository,
        PublicationRepository publicationRepository,
        PublicationAuthorRepository authorRepository,
        PublicationTopicRepository publicationTopicRepository,
        TopicRepository topicRepository,
        ResearcherRepository researcherRepository,
        ResearcherAffiliationRepository affiliationRepository,
        ResearchUnitRepository researchUnitRepository,
        VisibilityContext visibilityContext
    ) {
        this.properties = properties;
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.publicationRepository = publicationRepository;
        this.authorRepository = authorRepository;
        this.publicationTopicRepository = publicationTopicRepository;
        this.topicRepository = topicRepository;
        this.researcherRepository = researcherRepository;
        this.affiliationRepository = affiliationRepository;
        this.researchUnitRepository = researchUnitRepository;
        this.visibilityContext = visibilityContext;
    }

    public List<ResearchUnitCollaborationOpportunityResponse> findResearchUnitCollaborations(
        Integer yearFrom,
        Integer yearTo,
        OpportunityMode requestedMode,
        Integer requestedLimit,
        Boolean onlyValidated
    ) {
        if (yearFrom != null && yearTo != null && yearFrom > yearTo) {
            throw new BusinessRuleException("yearFrom must be less than or equal to yearTo");
        }

        OpportunityMode mode = requestedMode == null ? OpportunityMode.BALANCED : requestedMode;
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.min(requestedLimit, MAX_LIMIT);
        VisibilityResolution visibility = resolveVisibility(onlyValidated);
        List<PublicationEntity> publications = findPublications(yearFrom, yearTo, visibility.scope());
        if (publications.isEmpty()) {
            return List.of();
        }

        Map<Long, PublicationMetadata> metadataByPublicationId = metadataByPublicationId(publications, visibility.scope());
        Map<Long, ResearchUnitProfile> profiles = buildProfiles(publications, metadataByPublicationId, visibility.scope());
        if (profiles.size() < 2) {
            return List.of();
        }

        Map<PublicationPairKey, Double> semanticScores = semanticScores(publications, visibility.scope());
        int latestYear = publications.stream()
            .map(PublicationEntity::getPublicationYear)
            .filter(Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(LocalDate.now().getYear());

        List<ResearchUnitProfile> orderedProfiles = profiles.values()
            .stream()
            .sorted(Comparator.comparing(profile -> profile.unit().getName()))
            .toList();
        List<ScoredOpportunity> opportunities = new ArrayList<>();
        for (int i = 0; i < orderedProfiles.size(); i++) {
            for (int j = i + 1; j < orderedProfiles.size(); j++) {
                ScoredOpportunity opportunity = score(orderedProfiles.get(i), orderedProfiles.get(j), semanticScores, mode, latestYear, visibility.warning());
                if (opportunity.response().score() >= mode.minimumScore()) {
                    opportunities.add(opportunity);
                }
            }
        }

        return opportunities.stream()
            .sorted(Comparator
                .comparing((ScoredOpportunity opportunity) -> opportunity.response().score()).reversed()
                .thenComparing(opportunity -> opportunity.response().confidence(), Comparator.reverseOrder())
                .thenComparing(opportunity -> opportunity.response().unitA().name())
                .thenComparing(opportunity -> opportunity.response().unitB().name()))
            .limit(limit)
            .map(ScoredOpportunity::response)
            .toList();
    }

    private List<PublicationEntity> findPublications(Integer yearFrom, Integer yearTo, VisibilityScope visibilityScope) {
        ValidationStatus relationshipValidationStatus = visibilityScope == VisibilityScope.ADMIN_ALL ? null : ValidationStatus.VALIDATED;
        Specification<PublicationEntity> specification = PublicationSpecifications
            .visibleTo(visibilityScope, null)
            .and(PublicationSpecifications.matches(
                null,
                null,
                yearFrom,
                yearTo,
                null,
                null,
                null,
                null,
                null,
                null,
                relationshipValidationStatus
            ));
        return publicationRepository.findAll(specification)
            .stream()
            .filter(publication -> isVisible(publication.getValidationStatus(), visibilityScope))
            .filter(publication -> yearFrom == null || (publication.getPublicationYear() != null && publication.getPublicationYear() >= yearFrom))
            .filter(publication -> yearTo == null || (publication.getPublicationYear() != null && publication.getPublicationYear() <= yearTo))
            .sorted(Comparator
                .comparing(PublicationEntity::getPublicationYear, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PublicationEntity::getTitle)
                .thenComparing(PublicationEntity::getId))
            .toList();
    }

    private Map<Long, PublicationMetadata> metadataByPublicationId(
        List<PublicationEntity> publications,
        VisibilityScope visibilityScope
    ) {
        Map<Long, PublicationMetadataBuilder> builders = publications.stream()
            .collect(Collectors.toMap(
                PublicationEntity::getId,
                publication -> new PublicationMetadataBuilder(publication),
                (first, second) -> first,
                LinkedHashMap::new
            ));
        List<Long> publicationIds = publications.stream().map(PublicationEntity::getId).toList();
        addTopics(builders, publicationIds);
        addAuthorsAndUnits(builders, publicationIds, visibilityScope);
        return builders.entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().build(), (first, second) -> first, LinkedHashMap::new));
    }

    private void addTopics(Map<Long, PublicationMetadataBuilder> builders, Collection<Long> publicationIds) {
        List<PublicationTopicEntity> links = publicationTopicRepository.findByPublicationIdIn(publicationIds);
        Map<Long, TopicEntity> topicsById = topicRepository.findAllById(links.stream().map(PublicationTopicEntity::getTopicId).toList())
            .stream()
            .collect(Collectors.toMap(TopicEntity::getId, Function.identity(), (first, second) -> first));
        for (PublicationTopicEntity link : links) {
            TopicEntity topic = topicsById.get(link.getTopicId());
            PublicationMetadataBuilder builder = builders.get(link.getPublicationId());
            if (topic != null && builder != null) {
                builder.addTopic(topic);
            }
        }
    }

    private void addAuthorsAndUnits(
        Map<Long, PublicationMetadataBuilder> builders,
        Collection<Long> publicationIds,
        VisibilityScope visibilityScope
    ) {
        List<PublicationAuthorEntity> authors = authorRepository.findByPublicationIdIn(publicationIds);
        Set<Long> researcherIds = authors.stream()
            .map(PublicationAuthorEntity::getResearcherId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ResearcherEntity> researchersById = researcherRepository.findAllById(researcherIds)
            .stream()
            .filter(researcher -> isVisible(researcher.getValidationStatus(), visibilityScope))
            .collect(Collectors.toMap(ResearcherEntity::getId, Function.identity(), (first, second) -> first));
        Map<Long, List<ResearcherAffiliationEntity>> affiliationsByResearcherId = currentAffiliationsByResearcherId(
            researchersById.keySet(),
            visibilityScope
        );
        Set<Long> researchUnitIds = affiliationsByResearcherId.values()
            .stream()
            .flatMap(List::stream)
            .map(ResearcherAffiliationEntity::getResearchUnitId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ResearchUnitEntity> researchUnitsById = researchUnitRepository.findAllById(researchUnitIds)
            .stream()
            .filter(unit -> isVisible(unit.getValidationStatus(), visibilityScope))
            .collect(Collectors.toMap(ResearchUnitEntity::getId, Function.identity(), (first, second) -> first));

        for (PublicationAuthorEntity author : authors) {
            if (author.getResearcherId() == null || !researchersById.containsKey(author.getResearcherId())) {
                continue;
            }
            PublicationMetadataBuilder builder = builders.get(author.getPublicationId());
            if (builder == null) {
                continue;
            }
            builder.addResearcher(author.getResearcherId());
            for (ResearcherAffiliationEntity affiliation : affiliationsByResearcherId.getOrDefault(author.getResearcherId(), List.of())) {
                ResearchUnitEntity unit = researchUnitsById.get(affiliation.getResearchUnitId());
                if (unit != null) {
                    builder.addResearchUnit(unit);
                }
            }
        }
    }

    private Map<Long, List<ResearcherAffiliationEntity>> currentAffiliationsByResearcherId(
        Collection<Long> researcherIds,
        VisibilityScope visibilityScope
    ) {
        if (researcherIds.isEmpty()) {
            return Map.of();
        }
        LocalDate today = LocalDate.now();
        return affiliationRepository.findByResearcherIdIn(researcherIds)
            .stream()
            .filter(affiliation -> isVisible(affiliation.getValidationStatus(), visibilityScope))
            .filter(affiliation -> affiliation.getEndDate() == null || !affiliation.getEndDate().isBefore(today))
            .collect(Collectors.groupingBy(
                ResearcherAffiliationEntity::getResearcherId,
                LinkedHashMap::new,
                Collectors.toList()
            ));
    }

    private Map<Long, ResearchUnitProfile> buildProfiles(
        List<PublicationEntity> publications,
        Map<Long, PublicationMetadata> metadataByPublicationId,
        VisibilityScope visibilityScope
    ) {
        Map<Long, ResearchUnitProfileBuilder> builders = new LinkedHashMap<>();
        for (PublicationEntity publication : publications) {
            PublicationMetadata metadata = metadataByPublicationId.getOrDefault(publication.getId(), PublicationMetadata.empty(publication));
            for (ResearchUnitEntity unit : metadata.researchUnitsById().values()) {
                if (unit.isActive() && isVisible(unit.getValidationStatus(), visibilityScope)) {
                    builders.computeIfAbsent(unit.getId(), ignored -> new ResearchUnitProfileBuilder(unit)).addPublication(publication, metadata);
                }
            }
        }
        return builders.entrySet()
            .stream()
            .filter(entry -> !entry.getValue().publicationIds().isEmpty())
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().build(), (first, second) -> first, LinkedHashMap::new));
    }

    private Map<PublicationPairKey, Double> semanticScores(List<PublicationEntity> publications, VisibilityScope visibilityScope) {
        int semanticLimit = Math.min(Math.max(publications.size() - 1, 0), MAX_SEMANTIC_NEIGHBORS);
        if (semanticLimit == 0) {
            return Map.of();
        }
        Set<Long> selectedPublicationIds = publications.stream()
            .map(PublicationEntity::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<PublicationPairKey, Double> scores = new HashMap<>();
        for (PublicationEntity publication : publications) {
            Long publicationId = publication.getId();
            if (!embeddingRepository.hasEmbeddingForPublication(
                publicationId,
                embeddingService.provider(),
                embeddingService.model(),
                properties.getEmbeddingDimension()
            )) {
                continue;
            }
            for (PublicationEmbeddingSearchRow row : embeddingRepository.searchNearestToPublication(
                publicationId,
                embeddingService.provider(),
                embeddingService.model(),
                properties.getEmbeddingDimension(),
                semanticLimit,
                visibilityScope,
                null
            )) {
                if (!selectedPublicationIds.contains(row.publicationId())) {
                    continue;
                }
                PublicationPairKey pairKey = PublicationPairKey.of(publicationId, row.publicationId());
                scores.merge(pairKey, row.similarityScore(), Math::max);
            }
        }
        return scores;
    }

    private ScoredOpportunity score(
        ResearchUnitProfile left,
        ResearchUnitProfile right,
        Map<PublicationPairKey, Double> semanticScores,
        OpportunityMode mode,
        int latestYear,
        String visibilityWarning
    ) {
        double semanticScore = semanticProfileSimilarity(left, right, semanticScores);
        double sharedTopicScore = Math.max(jaccard(left.topicKeys(), right.topicKeys()), overlapCoefficient(left.topicKeys(), right.topicKeys()));
        List<String> sharedTopics = sharedTopics(left, right);
        List<ComplementaryTopicResponse> complementaryTopics = complementaryTopics(left, right, mode);
        double adjacentTopicScore = complementaryTopics.stream()
            .mapToDouble(ComplementaryTopicResponse::adjacencyScore)
            .max()
            .orElse(0.0);
        double topicScore = Math.max(sharedTopicScore, adjacentTopicScore);
        int existingCollaborationCount = intersectionSize(left.publicationIds(), right.publicationIds());
        double lowCollaborationScore = 1.0 / (1.0 + existingCollaborationCount);
        double complementaryScore = complementaryExpertiseScore(left, right, complementaryTopics);
        double recentActivityScore = (recentActivityScore(left, latestYear) + recentActivityScore(right, latestYear)) / 2.0;

        double score = round(0.35 * semanticScore
            + 0.25 * topicScore
            + 0.20 * lowCollaborationScore
            + 0.10 * complementaryScore
            + 0.10 * recentActivityScore);
        double confidence = confidence(left, right, semanticScore, topicScore, semanticScores);
        List<String> warnings = warnings(left, right, semanticScore, topicScore, semanticScores, visibilityWarning);

        return new ScoredOpportunity(new ResearchUnitCollaborationOpportunityResponse(
            unitResponse(left.unit()),
            unitResponse(right.unit()),
            score,
            confidence,
            sharedTopics,
            complementaryTopics,
            representativePublications(left, right, semanticScores),
            representativePublications(right, left, semanticScores),
            existingCollaborationCount,
            explanation(left, right, semanticScore, topicScore, lowCollaborationScore, complementaryScore, recentActivityScore, existingCollaborationCount),
            warnings
        ));
    }

    private double semanticProfileSimilarity(
        ResearchUnitProfile left,
        ResearchUnitProfile right,
        Map<PublicationPairKey, Double> semanticScores
    ) {
        if (semanticScores.isEmpty()) {
            return 0.0;
        }
        List<Double> leftBest = bestCrossPublicationScores(left.publications(), right.publicationIds(), semanticScores);
        List<Double> rightBest = bestCrossPublicationScores(right.publications(), left.publicationIds(), semanticScores);
        List<Double> combined = new ArrayList<>(leftBest);
        combined.addAll(rightBest);
        return combined.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private List<Double> bestCrossPublicationScores(
        List<PublicationEntity> sourcePublications,
        Set<Long> targetPublicationIds,
        Map<PublicationPairKey, Double> semanticScores
    ) {
        List<Double> scores = new ArrayList<>();
        for (PublicationEntity publication : sourcePublications) {
            double best = targetPublicationIds.stream()
                .map(targetId -> semanticScores.get(PublicationPairKey.of(publication.getId(), targetId)))
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
            if (best > 0.0) {
                scores.add(best);
            }
        }
        return scores;
    }

    private List<String> sharedTopics(ResearchUnitProfile left, ResearchUnitProfile right) {
        return left.topicKeys()
            .stream()
            .filter(right.topicKeys()::contains)
            .sorted(Comparator
                .comparingInt((String topicKey) -> left.topicCount(topicKey) + right.topicCount(topicKey)).reversed()
                .thenComparing(topicKey -> left.topicName(topicKey)))
            .limit(MAX_SHARED_TOPICS)
            .map(left::topicName)
            .toList();
    }

    private List<ComplementaryTopicResponse> complementaryTopics(
        ResearchUnitProfile left,
        ResearchUnitProfile right,
        OpportunityMode mode
    ) {
        List<ComplementaryTopicResponse> responses = new ArrayList<>();
        Set<String> sharedKeys = new HashSet<>(left.topicKeys());
        sharedKeys.retainAll(right.topicKeys());
        for (String leftTopic : left.topicKeys()) {
            if (sharedKeys.contains(leftTopic)) {
                continue;
            }
            for (String rightTopic : right.topicKeys()) {
                if (sharedKeys.contains(rightTopic)) {
                    continue;
                }
                double adjacencyScore = topicAdjacency(leftTopic, rightTopic);
                if (adjacencyScore >= mode.adjacentTopicThreshold()) {
                    responses.add(new ComplementaryTopicResponse(left.topicName(leftTopic), right.topicName(rightTopic), round(adjacencyScore)));
                }
            }
        }
        return responses.stream()
            .sorted(Comparator
                .comparing(ComplementaryTopicResponse::adjacencyScore).reversed()
                .thenComparing(ComplementaryTopicResponse::unitATopic)
                .thenComparing(ComplementaryTopicResponse::unitBTopic))
            .limit(MAX_COMPLEMENTARY_TOPICS)
            .toList();
    }

    private double complementaryExpertiseScore(
        ResearchUnitProfile left,
        ResearchUnitProfile right,
        List<ComplementaryTopicResponse> complementaryTopics
    ) {
        if (left.topicKeys().isEmpty() || right.topicKeys().isEmpty() || complementaryTopics.isEmpty()) {
            return 0.0;
        }
        Set<String> sharedKeys = new HashSet<>(left.topicKeys());
        sharedKeys.retainAll(right.topicKeys());
        double distinctTopicRatio = ((left.topicKeys().size() - sharedKeys.size()) + (right.topicKeys().size() - sharedKeys.size()))
            / (double) (left.topicKeys().size() + right.topicKeys().size());
        double adjacentBonus = complementaryTopics.stream()
            .mapToDouble(ComplementaryTopicResponse::adjacencyScore)
            .max()
            .orElse(0.0);
        return round(Math.min(1.0, 0.65 * distinctTopicRatio + 0.35 * adjacentBonus));
    }

    private double recentActivityScore(ResearchUnitProfile profile, int latestYear) {
        Integer unitLatestYear = profile.publications()
            .stream()
            .map(PublicationEntity::getPublicationYear)
            .filter(Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(null);
        if (unitLatestYear == null) {
            return 0.0;
        }
        double recency = Math.max(0.0, 1.0 - (Math.max(0, latestYear - unitLatestYear) / 5.0));
        double activityDepth = Math.min(1.0, profile.publications().size() / 3.0);
        return round(0.70 * recency + 0.30 * activityDepth);
    }

    private double confidence(
        ResearchUnitProfile left,
        ResearchUnitProfile right,
        double semanticScore,
        double topicScore,
        Map<PublicationPairKey, Double> semanticScores
    ) {
        double evidenceDepth = Math.min(1.0, (left.publications().size() + right.publications().size()) / 6.0);
        double semanticCoverage = semanticScores.isEmpty() ? 0.0 : semanticCoverage(left, right, semanticScores);
        double confidence = 0.25 + 0.25 * evidenceDepth + 0.25 * Math.max(semanticScore, semanticCoverage) + 0.25 * topicScore;
        return round(Math.min(0.95, confidence));
    }

    private double semanticCoverage(
        ResearchUnitProfile left,
        ResearchUnitProfile right,
        Map<PublicationPairKey, Double> semanticScores
    ) {
        int possible = left.publications().size() * right.publications().size();
        if (possible == 0) {
            return 0.0;
        }
        long present = left.publications()
            .stream()
            .flatMap(leftPublication -> right.publications()
                .stream()
                .map(rightPublication -> PublicationPairKey.of(leftPublication.getId(), rightPublication.getId())))
            .filter(semanticScores::containsKey)
            .count();
        return present / (double) possible;
    }

    private List<String> warnings(
        ResearchUnitProfile left,
        ResearchUnitProfile right,
        double semanticScore,
        double topicScore,
        Map<PublicationPairKey, Double> semanticScores,
        String visibilityWarning
    ) {
        List<String> warnings = new ArrayList<>();
        if (visibilityWarning != null) {
            warnings.add(visibilityWarning);
        }
        if (left.publications().size() < 2 || right.publications().size() < 2) {
            warnings.add("Evidencia limitada: una de las unidades tiene menos de dos publicaciones en el periodo.");
        }
        if (semanticScores.isEmpty()) {
            warnings.add("Sin embeddings comparables; la puntuacion depende de temas, colaboracion existente y actividad reciente.");
        } else if (semanticScore == 0.0) {
            warnings.add("Sin similitud semantica directa entre publicaciones de estas unidades.");
        }
        if (topicScore == 0.0) {
            warnings.add("No se detectaron temas compartidos ni adyacentes entre las unidades.");
        }
        return List.copyOf(warnings);
    }

    private List<OpportunityPublicationResponse> representativePublications(
        ResearchUnitProfile profile,
        ResearchUnitProfile otherProfile,
        Map<PublicationPairKey, Double> semanticScores
    ) {
        return profile.publications()
            .stream()
            .sorted(Comparator
                .comparingDouble((PublicationEntity publication) -> representativeScore(publication, otherProfile, profile, semanticScores)).reversed()
                .thenComparing(PublicationEntity::getPublicationYear, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PublicationEntity::getTitle)
                .thenComparing(PublicationEntity::getId))
            .limit(MAX_REPRESENTATIVE_PUBLICATIONS)
            .map(publication -> new OpportunityPublicationResponse(
                publication.getId(),
                publication.getTitle(),
                publication.getPublicationYear(),
                profile.topicsForPublication(publication.getId())
            ))
            .toList();
    }

    private double representativeScore(
        PublicationEntity publication,
        ResearchUnitProfile otherProfile,
        ResearchUnitProfile ownProfile,
        Map<PublicationPairKey, Double> semanticScores
    ) {
        double semantic = otherProfile.publicationIds()
            .stream()
            .map(targetId -> semanticScores.get(PublicationPairKey.of(publication.getId(), targetId)))
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0.0);
        double topicDepth = Math.min(1.0, ownProfile.topicsForPublication(publication.getId()).size() / 3.0);
        double yearScore = publication.getPublicationYear() == null ? 0.0 : 1.0;
        return 0.65 * semantic + 0.25 * topicDepth + 0.10 * yearScore;
    }

    private String explanation(
        ResearchUnitProfile left,
        ResearchUnitProfile right,
        double semanticScore,
        double topicScore,
        double lowCollaborationScore,
        double complementaryScore,
        double recentActivityScore,
        int existingCollaborationCount
    ) {
        List<String> signals = new ArrayList<>();
        if (semanticScore >= 0.65) {
            signals.add("alta similitud semantica entre perfiles de publicaciones");
        } else if (semanticScore >= 0.35) {
            signals.add("similitud semantica moderada");
        }
        if (topicScore > 0.0) {
            signals.add("temas compartidos o adyacentes");
        }
        if (existingCollaborationCount == 0) {
            signals.add("sin colaboraciones existentes detectadas");
        } else if (lowCollaborationScore >= 0.33) {
            signals.add("colaboracion existente baja");
        }
        if (complementaryScore >= 0.45) {
            signals.add("experiencia complementaria");
        }
        if (recentActivityScore >= 0.65) {
            signals.add("actividad reciente en ambas unidades");
        }
        String evidence = signals.isEmpty() ? "evidencia moderada en los metadatos disponibles" : joinSpanish(signals);
        return left.unit().getName() + " y " + right.unit().getName()
            + " aparecen como oportunidad por " + evidence
            + ". Colaboraciones existentes detectadas: " + existingCollaborationCount + ".";
    }

    private OpportunityResearchUnitResponse unitResponse(ResearchUnitEntity unit) {
        return new OpportunityResearchUnitResponse(
            unit.getId(),
            unit.getName(),
            unit.getShortName(),
            unit.getType().name()
        );
    }

    private VisibilityResolution resolveVisibility(Boolean onlyValidated) {
        boolean requestedOnlyValidated = onlyValidated == null || onlyValidated;
        if (!requestedOnlyValidated && visibilityContext.currentRoles().contains("ADMIN")) {
            return new VisibilityResolution(VisibilityScope.ADMIN_ALL, null);
        }
        if (!requestedOnlyValidated) {
            return new VisibilityResolution(
                VisibilityScope.PUBLIC_VALIDATED,
                "Solo usuarios ADMIN pueden incluir registros no validados; se aplico el filtro de validacion."
            );
        }
        return new VisibilityResolution(VisibilityScope.PUBLIC_VALIDATED, null);
    }

    private boolean isVisible(ValidationStatus validationStatus, VisibilityScope visibilityScope) {
        return visibilityScope == VisibilityScope.ADMIN_ALL || validationStatus == ValidationStatus.VALIDATED;
    }

    private int intersectionSize(Set<Long> left, Set<Long> right) {
        Set<Long> smaller = left.size() <= right.size() ? left : right;
        Set<Long> larger = left.size() <= right.size() ? right : left;
        int count = 0;
        for (Long value : smaller) {
            if (larger.contains(value)) {
                count++;
            }
        }
        return count;
    }

    private double jaccard(Set<?> left, Set<?> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<Object> union = new HashSet<>(left);
        union.addAll(right);
        Set<Object> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        return intersection.size() / (double) union.size();
    }

    private double overlapCoefficient(Set<?> left, Set<?> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<Object> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        return intersection.size() / (double) Math.min(left.size(), right.size());
    }

    private double topicAdjacency(String left, String right) {
        Set<String> leftTokens = topicTokens(left);
        Set<String> rightTokens = topicTokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0.0;
        }
        double tokenOverlap = jaccard(leftTokens, rightTokens);
        boolean substring = left.contains(right) || right.contains(left);
        return Math.min(1.0, tokenOverlap + (substring ? 0.25 : 0.0));
    }

    private Set<String> topicTokens(String value) {
        return Arrays.stream(value.split("\\s+"))
            .filter(token -> token.length() >= 3)
            .filter(token -> !TOPIC_STOP_WORDS.contains(token))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeTopic(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String joinSpanish(List<String> values) {
        List<String> cleanValues = values.stream()
            .filter(value -> value != null && !value.isBlank())
            .toList();
        if (cleanValues.isEmpty()) {
            return "";
        }
        if (cleanValues.size() == 1) {
            return cleanValues.getFirst();
        }
        return String.join(", ", cleanValues.subList(0, cleanValues.size() - 1))
            + " y "
            + cleanValues.getLast();
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private record VisibilityResolution(VisibilityScope scope, String warning) {
    }

    private record ScoredOpportunity(ResearchUnitCollaborationOpportunityResponse response) {
    }

    private record PublicationPairKey(Long first, Long second) {

        private static PublicationPairKey of(Long left, Long right) {
            if (left.compareTo(right) <= 0) {
                return new PublicationPairKey(left, right);
            }
            return new PublicationPairKey(right, left);
        }
    }

    private record TopicProfile(String key, String name, int count) {
    }

    private record PublicationMetadata(
        PublicationEntity publication,
        Map<String, TopicProfile> topicsByKey,
        Set<Long> researcherIds,
        Map<Long, ResearchUnitEntity> researchUnitsById
    ) {

        private static PublicationMetadata empty(PublicationEntity publication) {
            return new PublicationMetadata(publication, Map.of(), Set.of(), Map.of());
        }
    }

    private final class PublicationMetadataBuilder {

        private final PublicationEntity publication;
        private final Map<String, TopicProfile> topicsByKey = new LinkedHashMap<>();
        private final Set<Long> researcherIds = new LinkedHashSet<>();
        private final Map<Long, ResearchUnitEntity> researchUnitsById = new LinkedHashMap<>();

        private PublicationMetadataBuilder(PublicationEntity publication) {
            this.publication = publication;
        }

        private void addTopic(TopicEntity topic) {
            String key = normalizeTopic(topic.getNormalizedName() == null ? topic.getName() : topic.getNormalizedName());
            if (!key.isBlank()) {
                topicsByKey.putIfAbsent(key, new TopicProfile(key, topic.getName(), 1));
            }
        }

        private void addResearcher(Long researcherId) {
            researcherIds.add(researcherId);
        }

        private void addResearchUnit(ResearchUnitEntity unit) {
            researchUnitsById.putIfAbsent(unit.getId(), unit);
        }

        private PublicationMetadata build() {
            return new PublicationMetadata(
                publication,
                Map.copyOf(topicsByKey),
                Set.copyOf(researcherIds),
                Map.copyOf(researchUnitsById)
            );
        }
    }

    private record ResearchUnitProfile(
        ResearchUnitEntity unit,
        List<PublicationEntity> publications,
        Set<Long> publicationIds,
        Map<String, TopicProfile> topicsByKey,
        Map<Long, List<String>> publicationTopics
    ) {

        private Set<String> topicKeys() {
            return topicsByKey.keySet();
        }

        private String topicName(String topicKey) {
            TopicProfile topic = topicsByKey.get(topicKey);
            return topic == null ? topicKey : topic.name();
        }

        private int topicCount(String topicKey) {
            TopicProfile topic = topicsByKey.get(topicKey);
            return topic == null ? 0 : topic.count();
        }

        private List<String> topicsForPublication(Long publicationId) {
            return publicationTopics.getOrDefault(publicationId, List.of());
        }
    }

    private static final class ResearchUnitProfileBuilder {

        private final ResearchUnitEntity unit;
        private final List<PublicationEntity> publications = new ArrayList<>();
        private final Set<Long> publicationIds = new LinkedHashSet<>();
        private final Map<String, TopicProfile> topicsByKey = new LinkedHashMap<>();
        private final Map<Long, List<String>> publicationTopics = new LinkedHashMap<>();

        private ResearchUnitProfileBuilder(ResearchUnitEntity unit) {
            this.unit = unit;
        }

        private void addPublication(PublicationEntity publication, PublicationMetadata metadata) {
            if (!publicationIds.add(publication.getId())) {
                return;
            }
            publications.add(publication);
            publicationTopics.put(
                publication.getId(),
                metadata.topicsByKey().values().stream().map(TopicProfile::name).sorted().toList()
            );
            for (TopicProfile topic : metadata.topicsByKey().values()) {
                topicsByKey.compute(
                    topic.key(),
                    (ignored, existing) -> existing == null
                        ? topic
                        : new TopicProfile(existing.key(), existing.name(), existing.count() + 1)
                );
            }
        }

        private Set<Long> publicationIds() {
            return publicationIds;
        }

        private ResearchUnitProfile build() {
            return new ResearchUnitProfile(
                unit,
                List.copyOf(publications),
                Set.copyOf(publicationIds),
                Map.copyOf(topicsByKey),
                Map.copyOf(publicationTopics)
            );
        }
    }
}
