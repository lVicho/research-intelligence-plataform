package com.researchintelligence.platform.strategicmap.application;

import com.researchintelligence.platform.ai.application.AiProperties;
import com.researchintelligence.platform.ai.application.EmbeddingService;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingRepository;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingSearchRow;
import com.researchintelligence.platform.auth.application.VisibilityContext;
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
import com.researchintelligence.platform.strategicmap.api.ResearchLineNamedCountResponse;
import com.researchintelligence.platform.strategicmap.api.ResearchLinePublicationResponse;
import com.researchintelligence.platform.strategicmap.api.ResearchLineResponse;
import com.researchintelligence.platform.strategicmap.api.StrategicResearchMapResponse;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
public class StrategicResearchMapService {

    private static final String GROUPING_APPROACH = "hybrid_embedding_topic_connected_components";
    private static final double SEMANTIC_CLUSTER_THRESHOLD = 0.72;
    private static final double METADATA_CLUSTER_THRESHOLD = 0.60;
    private static final int MAX_REPRESENTATIVE_PUBLICATIONS = 3;
    private static final int MAX_NAMED_ITEMS = 10;
    private static final int MAX_SEMANTIC_NEIGHBORS = 120;
    private static final Set<String> TITLE_STOP_WORDS = Set.of(
        "a",
        "al",
        "and",
        "ante",
        "con",
        "de",
        "del",
        "el",
        "en",
        "entre",
        "for",
        "from",
        "la",
        "las",
        "los",
        "of",
        "para",
        "por",
        "the",
        "to",
        "un",
        "una",
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

    public StrategicResearchMapService(
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

    public StrategicResearchMapResponse researchLines(
        Integer yearFrom,
        Integer yearTo,
        Long researchUnitId,
        Boolean onlyValidated
    ) {
        if (yearFrom != null && yearTo != null && yearFrom > yearTo) {
            throw new BusinessRuleException("yearFrom must be less than or equal to yearTo");
        }

        boolean requestedOnlyValidated = onlyValidated == null || onlyValidated;
        VisibilityScope visibilityScope = resolvePublicScope(requestedOnlyValidated);
        List<String> warnings = new ArrayList<>();
        if (!requestedOnlyValidated && visibilityScope != VisibilityScope.ADMIN_ALL) {
            warnings.add("Solo usuarios ADMIN pueden incluir publicaciones no validadas; se aplico el filtro de validacion.");
        }

        List<PublicationEntity> publications = findPublications(yearFrom, yearTo, researchUnitId, visibilityScope);
        Map<Long, PublicationMetadata> metadataByPublicationId = metadataByPublicationId(publications, visibilityScope);
        if (researchUnitId != null) {
            publications = publications.stream()
                .filter(publication -> metadataByPublicationId
                    .getOrDefault(publication.getId(), PublicationMetadata.empty(publication.getId()))
                    .researchUnitIds()
                    .contains(researchUnitId))
                .toList();
        }
        Set<Long> selectedPublicationIds = publications.stream()
            .map(PublicationEntity::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (publications.isEmpty()) {
            warnings.add("No hay publicaciones disponibles para construir lineas de investigacion con los filtros indicados.");
            return new StrategicResearchMapResponse(
                yearFrom,
                yearTo,
                researchUnitId,
                visibilityScope != VisibilityScope.ADMIN_ALL,
                visibilityScope.name(),
                visibilityScope != VisibilityScope.ADMIN_ALL,
                GROUPING_APPROACH,
                List.copyOf(warnings),
                List.of()
            );
        }

        UnionFind unionFind = new UnionFind(selectedPublicationIds);
        Map<PairKey, PairEvidence> pairEvidence = new LinkedHashMap<>();
        Set<Long> publicationsWithEmbeddings = addSemanticEdges(
            publications,
            selectedPublicationIds,
            visibilityScope,
            unionFind,
            pairEvidence
        );
        addMetadataEdges(publications, metadataByPublicationId, unionFind, pairEvidence);

        if (publicationsWithEmbeddings.isEmpty()) {
            warnings.add("No hay embeddings comparables para el proveedor/modelo/dimension configurados; se uso agrupacion por temas y metadatos.");
        } else if (publicationsWithEmbeddings.size() < publications.size()) {
            warnings.add("La cobertura de embeddings es parcial; algunas publicaciones se agruparon solo por temas y metadatos.");
        }

        List<ResearchLineResponse> researchLines = buildResearchLines(
            publications,
            metadataByPublicationId,
            publicationsWithEmbeddings,
            pairEvidence,
            unionFind,
            visibilityScope
        );

        return new StrategicResearchMapResponse(
            yearFrom,
            yearTo,
            researchUnitId,
            visibilityScope != VisibilityScope.ADMIN_ALL,
            visibilityScope.name(),
            visibilityScope != VisibilityScope.ADMIN_ALL,
            GROUPING_APPROACH,
            List.copyOf(warnings),
            researchLines
        );
    }

    private List<PublicationEntity> findPublications(
        Integer yearFrom,
        Integer yearTo,
        Long researchUnitId,
        VisibilityScope visibilityScope
    ) {
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
                researchUnitId,
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

    private Set<Long> addSemanticEdges(
        List<PublicationEntity> publications,
        Set<Long> selectedPublicationIds,
        VisibilityScope visibilityScope,
        UnionFind unionFind,
        Map<PairKey, PairEvidence> pairEvidence
    ) {
        Set<Long> publicationsWithEmbeddings = new LinkedHashSet<>();
        int semanticLimit = Math.min(Math.max(publications.size() - 1, 0), MAX_SEMANTIC_NEIGHBORS);
        if (semanticLimit == 0) {
            return publicationsWithEmbeddings;
        }

        for (PublicationEntity publication : publications) {
            Long publicationId = publication.getId();
            boolean hasEmbedding = embeddingRepository.hasEmbeddingForPublication(
                publicationId,
                embeddingService.provider(),
                embeddingService.model(),
                properties.getEmbeddingDimension()
            );
            if (!hasEmbedding) {
                continue;
            }
            publicationsWithEmbeddings.add(publicationId);
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
                PairKey pairKey = PairKey.of(publicationId, row.publicationId());
                PairEvidence evidence = pairEvidence.computeIfAbsent(pairKey, ignored -> new PairEvidence());
                evidence.addSemanticScore(row.similarityScore());
                if (row.similarityScore() >= SEMANTIC_CLUSTER_THRESHOLD) {
                    evidence.markSemanticEdge();
                    unionFind.union(pairKey.first(), pairKey.second());
                }
            }
        }
        return publicationsWithEmbeddings;
    }

    private void addMetadataEdges(
        List<PublicationEntity> publications,
        Map<Long, PublicationMetadata> metadataByPublicationId,
        UnionFind unionFind,
        Map<PairKey, PairEvidence> pairEvidence
    ) {
        for (int i = 0; i < publications.size(); i++) {
            PublicationEntity left = publications.get(i);
            PublicationMetadata leftMetadata = metadataByPublicationId.getOrDefault(left.getId(), PublicationMetadata.empty(left.getId()));
            for (int j = i + 1; j < publications.size(); j++) {
                PublicationEntity right = publications.get(j);
                PublicationMetadata rightMetadata = metadataByPublicationId.getOrDefault(right.getId(), PublicationMetadata.empty(right.getId()));
                MetadataSimilarity metadataSimilarity = metadataSimilarity(left, leftMetadata, right, rightMetadata);
                PairKey pairKey = PairKey.of(left.getId(), right.getId());
                PairEvidence evidence = pairEvidence.computeIfAbsent(pairKey, ignored -> new PairEvidence());
                evidence.setMetadataScore(metadataSimilarity.score());
                if (metadataSimilarity.clustersTogether()) {
                    unionFind.union(pairKey.first(), pairKey.second());
                }
            }
        }
    }

    private MetadataSimilarity metadataSimilarity(
        PublicationEntity left,
        PublicationMetadata leftMetadata,
        PublicationEntity right,
        PublicationMetadata rightMetadata
    ) {
        int sharedTopicCount = intersectionSize(leftMetadata.topicIds(), rightMetadata.topicIds());
        double topicScore = jaccard(leftMetadata.topicIds(), rightMetadata.topicIds());
        double unitScore = jaccard(leftMetadata.researchUnitIds(), rightMetadata.researchUnitIds());
        double researcherScore = jaccard(leftMetadata.researcherIds(), rightMetadata.researcherIds());
        double titleScore = jaccard(leftMetadata.titleTokens(), rightMetadata.titleTokens());
        double yearScore = yearProximityScore(left, right);
        double score = 0.50 * topicScore
            + 0.20 * unitScore
            + 0.15 * researcherScore
            + 0.10 * titleScore
            + 0.05 * yearScore;
        boolean hasTopicalOrTextualEvidence = sharedTopicCount > 0 || titleScore >= 0.25;
        boolean strongMetadata = score >= METADATA_CLUSTER_THRESHOLD && hasTopicalOrTextualEvidence;
        boolean strongTopicHybrid = sharedTopicCount >= 2 && score >= 0.45;
        return new MetadataSimilarity(round(score), strongMetadata || strongTopicHybrid);
    }

    private List<ResearchLineResponse> buildResearchLines(
        List<PublicationEntity> publications,
        Map<Long, PublicationMetadata> metadataByPublicationId,
        Set<Long> publicationsWithEmbeddings,
        Map<PairKey, PairEvidence> pairEvidence,
        UnionFind unionFind,
        VisibilityScope visibilityScope
    ) {
        Map<Long, List<PublicationEntity>> publicationsByCluster = publications.stream()
            .collect(Collectors.groupingBy(
                publication -> unionFind.find(publication.getId()),
                LinkedHashMap::new,
                Collectors.toList()
            ));
        return publicationsByCluster.values()
            .stream()
            .map(cluster -> buildResearchLine(cluster, metadataByPublicationId, publicationsWithEmbeddings, pairEvidence, visibilityScope))
            .sorted(Comparator
                .comparingInt(ResearchLineResponse::publicationCount).reversed()
                .thenComparing(ResearchLineResponse::confidence, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ResearchLineResponse::title)
                .thenComparing(ResearchLineResponse::lineId))
            .toList();
    }

    private ResearchLineResponse buildResearchLine(
        List<PublicationEntity> publications,
        Map<Long, PublicationMetadata> metadataByPublicationId,
        Set<Long> publicationsWithEmbeddings,
        Map<PairKey, PairEvidence> pairEvidence,
        VisibilityScope visibilityScope
    ) {
        List<Long> publicationIds = publications.stream()
            .map(PublicationEntity::getId)
            .sorted()
            .toList();
        List<ResearchLineNamedCountResponse> topics = topNamedCounts(publications, metadataByPublicationId, PublicationMetadata::topicNamesById);
        List<ResearchLineNamedCountResponse> researchers = topNamedCounts(publications, metadataByPublicationId, PublicationMetadata::researcherNamesById);
        List<ResearchLineNamedCountResponse> researchUnits = topNamedCounts(publications, metadataByPublicationId, PublicationMetadata::researchUnitNamesById);
        List<ResearchLinePublicationResponse> representatives = representativePublications(publications, metadataByPublicationId, pairEvidence);
        Double confidence = lineConfidence(publications, metadataByPublicationId, publicationsWithEmbeddings, pairEvidence);
        List<String> warnings = lineWarnings(publications, publicationsWithEmbeddings, pairEvidence, confidence);

        return new ResearchLineResponse(
            lineId(publicationIds),
            lineTitle(topics),
            lineDescription(publications, topics, researchUnits, representatives, visibilityScope),
            publications.size(),
            researchers,
            researchUnits,
            topics,
            representatives,
            trendSummary(publications),
            confidence,
            warnings
        );
    }

    private List<ResearchLineNamedCountResponse> topNamedCounts(
        List<PublicationEntity> publications,
        Map<Long, PublicationMetadata> metadataByPublicationId,
        Function<PublicationMetadata, Map<Long, String>> namesExtractor
    ) {
        Map<Long, NamedCountBuilder> counts = new HashMap<>();
        for (PublicationEntity publication : publications) {
            PublicationMetadata metadata = metadataByPublicationId.getOrDefault(publication.getId(), PublicationMetadata.empty(publication.getId()));
            for (Map.Entry<Long, String> entry : namesExtractor.apply(metadata).entrySet()) {
                counts.computeIfAbsent(entry.getKey(), id -> new NamedCountBuilder(id, entry.getValue())).increment();
            }
        }
        return counts.values()
            .stream()
            .map(NamedCountBuilder::build)
            .sorted(Comparator
                .comparingInt(ResearchLineNamedCountResponse::publicationCount).reversed()
                .thenComparing(ResearchLineNamedCountResponse::name)
                .thenComparing(ResearchLineNamedCountResponse::id))
            .limit(MAX_NAMED_ITEMS)
            .toList();
    }

    private List<ResearchLinePublicationResponse> representativePublications(
        List<PublicationEntity> publications,
        Map<Long, PublicationMetadata> metadataByPublicationId,
        Map<PairKey, PairEvidence> pairEvidence
    ) {
        List<Integer> years = publications.stream()
            .map(PublicationEntity::getPublicationYear)
            .filter(Objects::nonNull)
            .toList();
        Integer maxYear = years.stream().max(Integer::compareTo).orElse(null);
        return publications.stream()
            .map(publication -> representativeCandidate(publication, publications, metadataByPublicationId, pairEvidence, maxYear))
            .sorted(Comparator
                .comparingDouble(RepresentativeCandidate::relevanceScore).reversed()
                .thenComparing(candidate -> candidate.publication().getPublicationYear(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(candidate -> candidate.publication().getTitle())
                .thenComparing(candidate -> candidate.publication().getId()))
            .limit(MAX_REPRESENTATIVE_PUBLICATIONS)
            .map(candidate -> {
                PublicationEntity publication = candidate.publication();
                PublicationMetadata metadata = metadataByPublicationId.getOrDefault(publication.getId(), PublicationMetadata.empty(publication.getId()));
                return new ResearchLinePublicationResponse(
                    publication.getId(),
                    citationKey(publication.getId()),
                    publication.getTitle(),
                    publication.getPublicationYear(),
                    publication.getDoi(),
                    publication.getSource(),
                    List.copyOf(metadata.topicNamesById().values()),
                    round(candidate.relevanceScore()),
                    candidate.semanticCentrality() == null ? null : round(candidate.semanticCentrality())
                );
            })
            .toList();
    }

    private RepresentativeCandidate representativeCandidate(
        PublicationEntity publication,
        List<PublicationEntity> linePublications,
        Map<Long, PublicationMetadata> metadataByPublicationId,
        Map<PairKey, PairEvidence> pairEvidence,
        Integer maxYear
    ) {
        if (linePublications.size() == 1) {
            return new RepresentativeCandidate(publication, 0.50, semanticCentrality(publication, linePublications, pairEvidence));
        }
        List<Double> connectionScores = linePublications.stream()
            .filter(other -> !other.getId().equals(publication.getId()))
            .map(other -> pairEvidence.get(PairKey.of(publication.getId(), other.getId())))
            .filter(Objects::nonNull)
            .map(PairEvidence::strongestScore)
            .filter(score -> score > 0.0)
            .toList();
        double averageConnection = connectionScores.isEmpty()
            ? 0.0
            : connectionScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double topicCoverage = metadataByPublicationId
            .getOrDefault(publication.getId(), PublicationMetadata.empty(publication.getId()))
            .topicIds()
            .isEmpty() ? 0.0 : 1.0;
        double recency = maxYear == null || publication.getPublicationYear() == null
            ? 0.5
            : Math.max(0.0, 1.0 - ((maxYear - publication.getPublicationYear()) / 10.0));
        double relevance = 0.70 * averageConnection + 0.20 * recency + 0.10 * topicCoverage;
        return new RepresentativeCandidate(publication, relevance, semanticCentrality(publication, linePublications, pairEvidence));
    }

    private Double semanticCentrality(
        PublicationEntity publication,
        List<PublicationEntity> linePublications,
        Map<PairKey, PairEvidence> pairEvidence
    ) {
        List<Double> scores = linePublications.stream()
            .filter(other -> !other.getId().equals(publication.getId()))
            .map(other -> pairEvidence.get(PairKey.of(publication.getId(), other.getId())))
            .filter(Objects::nonNull)
            .map(PairEvidence::semanticScore)
            .filter(Objects::nonNull)
            .toList();
        if (scores.isEmpty()) {
            return null;
        }
        return scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private Double lineConfidence(
        List<PublicationEntity> publications,
        Map<Long, PublicationMetadata> metadataByPublicationId,
        Set<Long> publicationsWithEmbeddings,
        Map<PairKey, PairEvidence> pairEvidence
    ) {
        double semanticCoverage = publications.stream()
            .filter(publication -> publicationsWithEmbeddings.contains(publication.getId()))
            .count() / (double) publications.size();
        double topicCoverage = publications.stream()
            .filter(publication -> !metadataByPublicationId
                .getOrDefault(publication.getId(), PublicationMetadata.empty(publication.getId()))
                .topicIds()
                .isEmpty())
            .count() / (double) publications.size();
        if (publications.size() == 1) {
            return round(Math.min(0.70, 0.40 + 0.20 * semanticCoverage + 0.10 * topicCoverage));
        }

        double averageBestConnection = publications.stream()
            .mapToDouble(publication -> bestConnection(publication, publications, pairEvidence))
            .average()
            .orElse(0.0);
        double confidence = 0.35 + 0.35 * averageBestConnection + 0.20 * semanticCoverage + 0.10 * topicCoverage;
        return round(Math.min(0.95, confidence));
    }

    private double bestConnection(
        PublicationEntity publication,
        List<PublicationEntity> linePublications,
        Map<PairKey, PairEvidence> pairEvidence
    ) {
        return linePublications.stream()
            .filter(other -> !other.getId().equals(publication.getId()))
            .map(other -> pairEvidence.get(PairKey.of(publication.getId(), other.getId())))
            .filter(Objects::nonNull)
            .mapToDouble(PairEvidence::strongestScore)
            .max()
            .orElse(0.0);
    }

    private List<String> lineWarnings(
        List<PublicationEntity> publications,
        Set<Long> publicationsWithEmbeddings,
        Map<PairKey, PairEvidence> pairEvidence,
        Double confidence
    ) {
        List<String> warnings = new ArrayList<>();
        if (publications.size() == 1) {
            warnings.add("Linea de baja evidencia: solo contiene una publicacion.");
        }
        long embeddedCount = publications.stream()
            .filter(publication -> publicationsWithEmbeddings.contains(publication.getId()))
            .count();
        if (embeddedCount == 0) {
            warnings.add("Sin embeddings comparables en esta linea; la agrupacion depende de temas y metadatos.");
        } else if (embeddedCount < publications.size()) {
            warnings.add("Cobertura semantica parcial en esta linea.");
        }
        boolean hasSemanticEdge = publications.stream()
            .flatMap(left -> publications.stream().map(right -> PairKey.of(left.getId(), right.getId())))
            .map(pairEvidence::get)
            .filter(Objects::nonNull)
            .anyMatch(PairEvidence::semanticEdge);
        if (!hasSemanticEdge && publications.size() > 1) {
            warnings.add("Linea agrupada sin enlace semantico fuerte; revisar temas y publicaciones representativas.");
        }
        if (confidence != null && confidence < 0.55) {
            warnings.add("Confianza baja; conviene validar manualmente la coherencia de la linea.");
        }
        return List.copyOf(warnings);
    }

    private String lineTitle(List<ResearchLineNamedCountResponse> topics) {
        if (topics.isEmpty()) {
            return "Linea de investigacion sin tema dominante";
        }
        return "Linea sobre " + joinSpanish(topics.stream().limit(3).map(ResearchLineNamedCountResponse::name).toList());
    }

    private String lineDescription(
        List<PublicationEntity> publications,
        List<ResearchLineNamedCountResponse> topics,
        List<ResearchLineNamedCountResponse> researchUnits,
        List<ResearchLinePublicationResponse> representatives,
        VisibilityScope visibilityScope
    ) {
        String validationLabel = visibilityScope == VisibilityScope.ADMIN_ALL ? "disponibles" : "validadas";
        List<String> phrases = new ArrayList<>();
        if (!topics.isEmpty()) {
            phrases.add("temas como " + joinSpanish(topics.stream().limit(3).map(ResearchLineNamedCountResponse::name).toList()));
        }
        if (!researchUnits.isEmpty()) {
            phrases.add("participacion de " + joinSpanish(researchUnits.stream().limit(2).map(ResearchLineNamedCountResponse::name).toList()));
        }
        String evidence = phrases.isEmpty() ? "metadatos bibliograficos disponibles" : joinSpanish(phrases);
        String citations = representatives.stream()
            .map(ResearchLinePublicationResponse::citationKey)
            .collect(Collectors.joining(", "));
        return "Linea identificada a partir de " + publications.size() + " publicaciones " + validationLabel
            + " relacionadas por " + evidence + ". Publicaciones representativas citadas: " + citations + ".";
    }

    private String trendSummary(List<PublicationEntity> publications) {
        Map<Integer, Long> countsByYear = publications.stream()
            .map(PublicationEntity::getPublicationYear)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        if (countsByYear.isEmpty()) {
            return "Sin anos de publicacion disponibles para estimar tendencia.";
        }
        List<Integer> years = countsByYear.keySet().stream().sorted().toList();
        if (years.size() == 1) {
            Integer year = years.getFirst();
            return "Tendencia no estimable: " + countsByYear.get(year) + " publicaciones en " + year + ".";
        }
        Integer firstYear = years.getFirst();
        Integer lastYear = years.getLast();
        long firstCount = countsByYear.get(firstYear);
        long lastCount = countsByYear.get(lastYear);
        String direction;
        if (lastCount > firstCount) {
            direction = "creciente";
        } else if (lastCount < firstCount) {
            direction = "descendente";
        } else {
            direction = "estable";
        }
        return "Actividad " + direction + " entre " + firstYear + " y " + lastYear
            + " (" + firstCount + " -> " + lastCount + " publicaciones).";
    }

    private Map<Long, PublicationMetadata> metadataByPublicationId(
        List<PublicationEntity> publications,
        VisibilityScope visibilityScope
    ) {
        if (publications.isEmpty()) {
            return Map.of();
        }
        Map<Long, PublicationMetadataBuilder> builders = publications.stream()
            .collect(Collectors.toMap(
                PublicationEntity::getId,
                publication -> new PublicationMetadataBuilder(publication.getId(), titleTokens(publication.getTitle())),
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
            .filter(Objects::nonNull)
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
            String researcherName = researcherNames.get(author.getResearcherId());
            if (builder == null || researcherName == null) {
                continue;
            }
            builder.addResearcher(author.getResearcherId(), researcherName);
            for (ResearcherAffiliationEntity affiliation : currentAffiliations.getOrDefault(author.getResearcherId(), List.of())) {
                String researchUnitName = researchUnitNames.get(affiliation.getResearchUnitId());
                if (researchUnitName != null) {
                    builder.addResearchUnit(affiliation.getResearchUnitId(), researchUnitName);
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

    private VisibilityScope resolvePublicScope(boolean requestedOnlyValidated) {
        if (!requestedOnlyValidated && visibilityContext.currentRoles().contains("ADMIN")) {
            return VisibilityScope.ADMIN_ALL;
        }
        return VisibilityScope.PUBLIC_VALIDATED;
    }

    private boolean isVisible(ValidationStatus validationStatus, VisibilityScope visibilityScope) {
        return visibilityScope == VisibilityScope.ADMIN_ALL || validationStatus == ValidationStatus.VALIDATED;
    }

    private String lineId(List<Long> publicationIds) {
        String fingerprint = Integer.toUnsignedString(publicationIds.toString().hashCode(), 16);
        return "line-" + publicationIds.getFirst() + "-" + fingerprint;
    }

    private String citationKey(Long publicationId) {
        return "pub:" + publicationId;
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

    private double yearProximityScore(PublicationEntity left, PublicationEntity right) {
        if (left.getPublicationYear() == null || right.getPublicationYear() == null) {
            return 0.0;
        }
        return Math.max(0.0, 1.0 - (Math.abs(left.getPublicationYear() - right.getPublicationYear()) / 10.0));
    }

    private Set<String> titleTokens(String title) {
        if (title == null || title.isBlank()) {
            return Set.of();
        }
        String normalized = Normalizer.normalize(title.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z0-9 ]", " ");
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 3 && !TITLE_STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
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

    private record MetadataSimilarity(double score, boolean clustersTogether) {
    }

    private record PairKey(Long first, Long second) {

        private static PairKey of(Long left, Long right) {
            if (left.compareTo(right) <= 0) {
                return new PairKey(left, right);
            }
            return new PairKey(right, left);
        }
    }

    private static final class PairEvidence {

        private Double semanticScore;
        private double metadataScore;
        private boolean semanticEdge;

        private void addSemanticScore(double score) {
            if (semanticScore == null || score > semanticScore) {
                semanticScore = score;
            }
        }

        private void setMetadataScore(double metadataScore) {
            this.metadataScore = metadataScore;
        }

        private void markSemanticEdge() {
            this.semanticEdge = true;
        }

        private Double semanticScore() {
            return semanticScore;
        }

        private boolean semanticEdge() {
            return semanticEdge;
        }

        private double strongestScore() {
            return Math.max(semanticScore == null ? 0.0 : semanticScore, metadataScore);
        }
    }

    private record RepresentativeCandidate(
        PublicationEntity publication,
        double relevanceScore,
        Double semanticCentrality
    ) {
    }

    private static final class NamedCountBuilder {

        private final Long id;
        private final String name;
        private int publicationCount;

        private NamedCountBuilder(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        private void increment() {
            publicationCount++;
        }

        private ResearchLineNamedCountResponse build() {
            return new ResearchLineNamedCountResponse(id, name, publicationCount);
        }
    }

    private static final class PublicationMetadataBuilder {

        private final Long publicationId;
        private final Set<String> titleTokens;
        private final Map<Long, String> topicNamesById = new LinkedHashMap<>();
        private final Map<Long, String> researcherNamesById = new LinkedHashMap<>();
        private final Map<Long, String> researchUnitNamesById = new LinkedHashMap<>();

        private PublicationMetadataBuilder(Long publicationId, Set<String> titleTokens) {
            this.publicationId = publicationId;
            this.titleTokens = titleTokens;
        }

        private void addTopic(Long topicId, String name) {
            topicNamesById.putIfAbsent(topicId, name);
        }

        private void addResearcher(Long researcherId, String name) {
            researcherNamesById.putIfAbsent(researcherId, name);
        }

        private void addResearchUnit(Long researchUnitId, String name) {
            researchUnitNamesById.putIfAbsent(researchUnitId, name);
        }

        private PublicationMetadata build() {
            return new PublicationMetadata(
                publicationId,
                Collections.unmodifiableSet(new LinkedHashSet<>(topicNamesById.keySet())),
                Collections.unmodifiableMap(new LinkedHashMap<>(topicNamesById)),
                Collections.unmodifiableSet(new LinkedHashSet<>(researcherNamesById.keySet())),
                Collections.unmodifiableMap(new LinkedHashMap<>(researcherNamesById)),
                Collections.unmodifiableSet(new LinkedHashSet<>(researchUnitNamesById.keySet())),
                Collections.unmodifiableMap(new LinkedHashMap<>(researchUnitNamesById)),
                Collections.unmodifiableSet(new LinkedHashSet<>(titleTokens))
            );
        }
    }

    private record PublicationMetadata(
        Long publicationId,
        Set<Long> topicIds,
        Map<Long, String> topicNamesById,
        Set<Long> researcherIds,
        Map<Long, String> researcherNamesById,
        Set<Long> researchUnitIds,
        Map<Long, String> researchUnitNamesById,
        Set<String> titleTokens
    ) {

        private static PublicationMetadata empty(Long publicationId) {
            return new PublicationMetadata(publicationId, Set.of(), Map.of(), Set.of(), Map.of(), Set.of(), Map.of(), Set.of());
        }
    }

    private static final class UnionFind {

        private final Map<Long, Long> parents = new HashMap<>();

        private UnionFind(Collection<Long> ids) {
            for (Long id : ids) {
                parents.put(id, id);
            }
        }

        private Long find(Long id) {
            Long parent = parents.get(id);
            if (parent == null || parent.equals(id)) {
                return id;
            }
            Long root = find(parent);
            parents.put(id, root);
            return root;
        }

        private void union(Long left, Long right) {
            Long leftRoot = find(left);
            Long rightRoot = find(right);
            if (!leftRoot.equals(rightRoot)) {
                Long parent = leftRoot.compareTo(rightRoot) <= 0 ? leftRoot : rightRoot;
                Long child = leftRoot.compareTo(rightRoot) <= 0 ? rightRoot : leftRoot;
                parents.put(child, parent);
            }
        }
    }
}
