package com.researchintelligence.platform.publications.application;

import com.researchintelligence.platform.ai.application.AiProperties;
import com.researchintelligence.platform.ai.application.EmbeddingService;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingRepository;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingSearchRow;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.publications.api.RelatedPublicationResponse;
import com.researchintelligence.platform.publications.api.RelatedPublicationsResponse;
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
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RelatedPublicationService {

    private static final double SEMANTIC_WEIGHT = 0.60;
    private static final double TOPIC_WEIGHT = 0.15;
    private static final double AUTHOR_WEIGHT = 0.10;
    private static final double RESEARCH_UNIT_WEIGHT = 0.10;
    private static final double YEAR_WEIGHT = 0.05;
    private static final double METADATA_WEIGHT = TOPIC_WEIGHT + AUTHOR_WEIGHT + RESEARCH_UNIT_WEIGHT + YEAR_WEIGHT;
    private static final double DEFAULT_MIN_SCORE = 0.35;
    private static final int DEFAULT_LIMIT = 15;
    private static final int MAX_LIMIT = 50;

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

    public RelatedPublicationService(
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

    public RelatedPublicationsResponse findRelated(Long publicationId, Integer requestedLimit, Double requestedMinScore) {
        return findRelated(publicationId, requestedLimit, requestedMinScore, null);
    }

    public RelatedPublicationsResponse findRelated(
        Long publicationId,
        Integer requestedLimit,
        Double requestedMinScore,
        Boolean includeNonValidated
    ) {
        VisibilityScope visibilityScope = resolvePublicScope(includeNonValidated);
        PublicationEntity sourcePublication = findVisiblePublication(publicationId, visibilityScope);
        int limit = clampLimit(requestedLimit);
        double minScore = clampScore(requestedMinScore == null ? DEFAULT_MIN_SCORE : requestedMinScore);
        List<PublicationEntity> candidates = findVisiblePublications(visibilityScope)
            .stream()
            .filter(publication -> !publication.getId().equals(publicationId))
            .toList();

        List<Long> publicationIds = new ArrayList<>();
        publicationIds.add(publicationId);
        publicationIds.addAll(candidates.stream().map(PublicationEntity::getId).toList());

        Map<Long, PublicationMetadata> metadata = metadataByPublicationId(publicationIds, visibilityScope);
        PublicationMetadata sourceMetadata = metadata.getOrDefault(publicationId, PublicationMetadata.empty(publicationId));
        boolean semanticAvailable = hasSourceEmbedding(publicationId);
        Map<Long, Double> semanticScores = semanticAvailable ? semanticScores(publicationId, candidates.size(), limit, visibilityScope) : Map.of();
        boolean metadataOnly = !semanticAvailable;

        List<String> warnings = new ArrayList<>();
        if (metadataOnly) {
            warnings.add("No hay embeddings disponibles para esta publicacion con el proveedor/modelo/dimension configurados; se ha usado ranking solo por metadatos.");
        }

        List<RelatedPublicationResponse> related = candidates.stream()
            .map(candidate -> scoreCandidate(sourcePublication, sourceMetadata, candidate, metadata.getOrDefault(candidate.getId(), PublicationMetadata.empty(candidate.getId())), semanticScores.get(candidate.getId()), metadataOnly))
            .filter(candidate -> candidate.finalScore() >= minScore)
            .sorted(Comparator
                .comparingDouble(RelatedPublicationResponse::finalScore).reversed()
                .thenComparing(response -> response.publication().year(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(response -> response.publication().title())
                .thenComparing(response -> response.publication().id()))
            .limit(limit)
            .toList();

        if (related.isEmpty()) {
            warnings.add("No se han encontrado publicaciones suficientemente relacionadas.");
        } else if (related.getFirst().finalScore() < DEFAULT_MIN_SCORE) {
            warnings.add("Los resultados son de baja confianza; se muestran por un umbral amplio.");
        }

        return new RelatedPublicationsResponse(
            publicationId,
            limit,
            minScore,
            metadataOnly,
            warnings,
            visibilityScope.name(),
            validationFilterApplied(visibilityScope),
            related
        );
    }

    private RelatedPublicationResponse scoreCandidate(
        PublicationEntity sourcePublication,
        PublicationMetadata sourceMetadata,
        PublicationEntity candidate,
        PublicationMetadata candidateMetadata,
        Double semanticScore,
        boolean metadataOnly
    ) {
        List<String> sharedTopicNames = sharedNames(sourceMetadata.topicIds(), candidateMetadata.topicIds(), sourceMetadata.topicNamesById());
        List<String> sharedAuthorNames = sharedNames(sourceMetadata.internalAuthorIds(), candidateMetadata.internalAuthorIds(), sourceMetadata.authorNamesById());
        List<String> relatedResearchUnitNames = sharedNames(sourceMetadata.researchUnitIds(), candidateMetadata.researchUnitIds(), sourceMetadata.researchUnitNamesById());
        int yearDistance = yearDistance(sourcePublication, candidate);

        double topicScore = ratio(sharedTopicNames.size(), sourceMetadata.topicIds().size());
        double authorScore = ratio(sharedAuthorNames.size(), sourceMetadata.internalAuthorIds().size());
        double researchUnitScore = ratio(relatedResearchUnitNames.size(), sourceMetadata.researchUnitIds().size());
        double yearScore = yearProximityScore(sourcePublication, candidate);
        double metadataContribution = TOPIC_WEIGHT * topicScore
            + AUTHOR_WEIGHT * authorScore
            + RESEARCH_UNIT_WEIGHT * researchUnitScore
            + YEAR_WEIGHT * yearScore;
        double metadataScore = metadataContribution / METADATA_WEIGHT;
        double finalScore = metadataOnly
            ? metadataScore
            : SEMANTIC_WEIGHT * (semanticScore == null ? 0.0 : semanticScore) + metadataContribution;

        List<String> reasons = explanationReasons(sharedTopicNames, sharedAuthorNames, relatedResearchUnitNames, yearDistance, semanticScore, finalScore, metadataOnly);
        String warning = candidateWarning(finalScore, semanticScore, metadataOnly);
        return new RelatedPublicationResponse(
            PublicationMapper.toSummary(candidate, topicNames(candidateMetadata)),
            round(finalScore),
            semanticScore == null ? null : round(semanticScore),
            round(metadataScore),
            sharedTopicNames,
            sharedAuthorNames,
            relatedResearchUnitNames,
            sourcePublication.getPublicationYear() == null || candidate.getPublicationYear() == null ? null : yearDistance,
            reasons,
            warning
        );
    }

    private List<String> topicNames(PublicationMetadata metadata) {
        return metadata.topicIds().stream()
            .map(metadata.topicNamesById()::get)
            .filter(name -> name != null && !name.isBlank())
            .toList();
    }

    private boolean hasSourceEmbedding(Long publicationId) {
        return embeddingRepository.hasEmbeddingForPublication(
            publicationId,
            embeddingService.provider(),
            embeddingService.model(),
            properties.getEmbeddingDimension()
        );
    }

    private Map<Long, Double> semanticScores(Long publicationId, int candidateCount, int limit, VisibilityScope visibilityScope) {
        int semanticLimit = Math.min(Math.max(candidateCount, 0), Math.max(50, limit * 8));
        if (semanticLimit == 0) {
            return Map.of();
        }
        return embeddingRepository.searchNearestToPublication(
            publicationId,
            embeddingService.provider(),
            embeddingService.model(),
            properties.getEmbeddingDimension(),
            semanticLimit,
            visibilityScope,
            null
        ).stream().collect(Collectors.toMap(
            PublicationEmbeddingSearchRow::publicationId,
            PublicationEmbeddingSearchRow::similarityScore,
            (first, second) -> first,
            LinkedHashMap::new
        ));
    }

    private PublicationEntity findVisiblePublication(Long publicationId, VisibilityScope visibilityScope) {
        return publicationRepository.findById(publicationId)
            .filter(publication -> isVisible(publication, visibilityScope))
            .orElseThrow(() -> new ResourceNotFoundException("Publication", publicationId));
    }

    private List<PublicationEntity> findVisiblePublications(VisibilityScope visibilityScope) {
        Specification<PublicationEntity> specification = PublicationSpecifications.visibleTo(visibilityScope, null);
        return publicationRepository.findAll(specification)
            .stream()
            .filter(publication -> isVisible(publication, visibilityScope))
            .toList();
    }

    private boolean isVisible(PublicationEntity publication, VisibilityScope visibilityScope) {
        return isVisible(publication.getValidationStatus(), visibilityScope);
    }

    private boolean isVisible(ValidationStatus validationStatus, VisibilityScope visibilityScope) {
        return visibilityScope == VisibilityScope.ADMIN_ALL || validationStatus == ValidationStatus.VALIDATED;
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

    private Map<Long, PublicationMetadata> metadataByPublicationId(Collection<Long> publicationIds, VisibilityScope visibilityScope) {
        Map<Long, PublicationMetadataBuilder> builders = publicationIds.stream()
            .collect(Collectors.toMap(Function.identity(), PublicationMetadataBuilder::new, (first, second) -> first, LinkedHashMap::new));
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
            .collect(Collectors.toMap(TopicEntity::getId, Function.identity()));
        for (PublicationTopicEntity link : links) {
            TopicEntity topic = topicsById.get(link.getTopicId());
            PublicationMetadataBuilder builder = builders.get(link.getPublicationId());
            if (topic != null && builder != null) {
                builder.addTopic(topic.getId(), topic.getName());
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
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> researcherNames = researcherRepository.findAllById(researcherIds)
            .stream()
            .filter(researcher -> isVisible(researcher.getValidationStatus(), visibilityScope))
            .collect(Collectors.toMap(ResearcherEntity::getId, ResearcherEntity::getFullName, (first, second) -> first));
        Map<Long, List<ResearcherAffiliationEntity>> currentAffiliations = currentAffiliationsByResearcherId(researcherIds, visibilityScope);
        Set<Long> researchUnitIds = currentAffiliations.values()
            .stream()
            .flatMap(List::stream)
            .map(ResearcherAffiliationEntity::getResearchUnitId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> researchUnitNames = researchUnitRepository.findAllById(researchUnitIds)
            .stream()
            .filter(unit -> isVisible(unit.getValidationStatus(), visibilityScope))
            .collect(Collectors.toMap(ResearchUnitEntity::getId, ResearchUnitEntity::getName, (first, second) -> first));

        for (PublicationAuthorEntity author : authors) {
            if (author.getResearcherId() == null) {
                continue;
            }
            PublicationMetadataBuilder builder = builders.get(author.getPublicationId());
            if (builder == null) {
                continue;
            }
            Long researcherId = author.getResearcherId();
            String researcherName = researcherNames.get(researcherId);
            if (researcherName == null) {
                continue;
            }
            builder.addAuthor(researcherId, researcherName);
            for (ResearcherAffiliationEntity affiliation : currentAffiliations.getOrDefault(researcherId, List.of())) {
                Long unitId = affiliation.getResearchUnitId();
                String unitName = researchUnitNames.get(unitId);
                if (unitName != null) {
                    builder.addResearchUnit(unitId, unitName);
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

    private List<String> sharedNames(Set<Long> sourceIds, Set<Long> candidateIds, Map<Long, String> sourceNamesById) {
        Set<Long> candidateIdSet = new HashSet<>(candidateIds);
        return sourceIds.stream()
            .filter(candidateIdSet::contains)
            .map(sourceNamesById::get)
            .filter(name -> name != null && !name.isBlank())
            .toList();
    }

    private List<String> explanationReasons(
        List<String> sharedTopicNames,
        List<String> sharedAuthorNames,
        List<String> relatedResearchUnitNames,
        int yearDistance,
        Double semanticScore,
        double finalScore,
        boolean metadataOnly
    ) {
        List<String> reasons = new ArrayList<>();
        if (!metadataOnly && semanticScore != null) {
            reasons.add("Similitud semantica " + formatPercent(semanticScore) + ".");
        }
        if (!sharedTopicNames.isEmpty()) {
            reasons.add("Comparte temas: " + String.join(", ", sharedTopicNames) + ".");
        }
        if (!sharedAuthorNames.isEmpty()) {
            reasons.add("Comparte autores internos: " + String.join(", ", sharedAuthorNames) + ".");
        }
        if (!relatedResearchUnitNames.isEmpty()) {
            reasons.add("Relacion por unidades de investigacion: " + String.join(", ", relatedResearchUnitNames) + ".");
        }
        if (yearDistance <= 2) {
            reasons.add("Publicaciones cercanas en el tiempo.");
        }
        if (reasons.isEmpty() && finalScore > 0.0) {
            reasons.add("Relacion debil por proximidad temporal o metadatos parciales.");
        }
        return reasons;
    }

    private String candidateWarning(double finalScore, Double semanticScore, boolean metadataOnly) {
        if (metadataOnly) {
            return "Resultado calculado sin embeddings.";
        }
        if (semanticScore == null) {
            return "Sin embedding comparable para esta publicacion; la relacion depende de metadatos.";
        }
        if (finalScore < DEFAULT_MIN_SCORE) {
            return "Relacion debil; revisa los motivos antes de usarla.";
        }
        return null;
    }

    private int yearDistance(PublicationEntity source, PublicationEntity candidate) {
        if (source.getPublicationYear() == null || candidate.getPublicationYear() == null) {
            return Integer.MAX_VALUE;
        }
        return Math.abs(source.getPublicationYear() - candidate.getPublicationYear());
    }

    private double yearProximityScore(PublicationEntity source, PublicationEntity candidate) {
        int distance = yearDistance(source, candidate);
        if (distance == Integer.MAX_VALUE) {
            return 0.0;
        }
        return Math.max(0.0, 1.0 - (distance / 10.0));
    }

    private double ratio(int sharedCount, int sourceCount) {
        if (sourceCount == 0 || sharedCount == 0) {
            return 0.0;
        }
        return Math.min(1.0, sharedCount / (double) sourceCount);
    }

    private int clampLimit(Integer requestedLimit) {
        return Math.min(Math.max(requestedLimit == null ? DEFAULT_LIMIT : requestedLimit, 1), MAX_LIMIT);
    }

    private double clampScore(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private String formatPercent(double value) {
        return Math.round(value * 100.0) + "%";
    }

    private record PublicationMetadata(
        Long publicationId,
        Set<Long> topicIds,
        Map<Long, String> topicNamesById,
        Set<Long> internalAuthorIds,
        Map<Long, String> authorNamesById,
        Set<Long> researchUnitIds,
        Map<Long, String> researchUnitNamesById
    ) {

        private static PublicationMetadata empty(Long publicationId) {
            return new PublicationMetadata(publicationId, Set.of(), Map.of(), Set.of(), Map.of(), Set.of(), Map.of());
        }
    }

    private static final class PublicationMetadataBuilder {

        private final Long publicationId;
        private final Set<Long> topicIds = new LinkedHashSet<>();
        private final Map<Long, String> topicNamesById = new LinkedHashMap<>();
        private final Set<Long> internalAuthorIds = new LinkedHashSet<>();
        private final Map<Long, String> authorNamesById = new LinkedHashMap<>();
        private final Set<Long> researchUnitIds = new LinkedHashSet<>();
        private final Map<Long, String> researchUnitNamesById = new LinkedHashMap<>();

        private PublicationMetadataBuilder(Long publicationId) {
            this.publicationId = publicationId;
        }

        private void addTopic(Long topicId, String name) {
            topicIds.add(topicId);
            topicNamesById.putIfAbsent(topicId, name);
        }

        private void addAuthor(Long researcherId, String name) {
            internalAuthorIds.add(researcherId);
            authorNamesById.putIfAbsent(researcherId, name);
        }

        private void addResearchUnit(Long researchUnitId, String name) {
            researchUnitIds.add(researchUnitId);
            researchUnitNamesById.putIfAbsent(researchUnitId, name);
        }

        private PublicationMetadata build() {
            return new PublicationMetadata(
                publicationId,
                Collections.unmodifiableSet(new LinkedHashSet<>(topicIds)),
                Collections.unmodifiableMap(new LinkedHashMap<>(topicNamesById)),
                Collections.unmodifiableSet(new LinkedHashSet<>(internalAuthorIds)),
                Collections.unmodifiableMap(new LinkedHashMap<>(authorNamesById)),
                Collections.unmodifiableSet(new LinkedHashSet<>(researchUnitIds)),
                Collections.unmodifiableMap(new LinkedHashMap<>(researchUnitNamesById))
            );
        }
    }
}
