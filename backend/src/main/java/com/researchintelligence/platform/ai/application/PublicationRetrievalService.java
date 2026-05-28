package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingRepository;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingSearchRow;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorEntity;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationSpecifications;
import com.researchintelligence.platform.publications.persistence.PublicationTopicEntity;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PublicationRetrievalService {

    private static final Set<String> STOP_WORDS = Set.of(
        "about", "after", "also", "and", "are", "from", "how", "into", "the", "their", "there", "this", "what", "when", "where", "which", "with",
        "aparecen", "agrupa", "agrupar", "cita", "citar", "como", "con", "concreto", "concretos", "cual", "cuales", "cuando", "del", "desde",
        "donde", "esta", "estas", "este", "estos", "explica", "hacia", "investigador", "investigadores", "linea", "lineas", "los", "para",
        "parecen", "por", "publicacion", "publicaciones", "que", "sin", "sobre", "tematica", "tematicas", "titulo", "titulos", "torno",
        "una", "unas", "uno", "unos", "usado", "usados", "varias"
    );

    private final AiProperties properties;
    private final EmbeddingService embeddingService;
    private final PublicationEmbeddingRepository embeddingRepository;
    private final PublicationRepository publicationRepository;
    private final PublicationAuthorRepository authorRepository;
    private final PublicationTopicRepository publicationTopicRepository;
    private final TopicRepository topicRepository;
    private final ResearcherRepository researcherRepository;
    private final RetrievalThresholdPolicy thresholdPolicy;

    public PublicationRetrievalService(
        AiProperties properties,
        EmbeddingService embeddingService,
        PublicationEmbeddingRepository embeddingRepository,
        PublicationRepository publicationRepository,
        PublicationAuthorRepository authorRepository,
        PublicationTopicRepository publicationTopicRepository,
        TopicRepository topicRepository,
        ResearcherRepository researcherRepository
    ) {
        this.properties = properties;
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.publicationRepository = publicationRepository;
        this.authorRepository = authorRepository;
        this.publicationTopicRepository = publicationTopicRepository;
        this.topicRepository = topicRepository;
        this.researcherRepository = researcherRepository;
        this.thresholdPolicy = new RetrievalThresholdPolicy(properties.getRetrieval());
    }

    public PublicationRetrievalResult retrieveBest(String query, int limit) {
        return retrieveBest(query, new RetrievalOptions(limit, null, RetrievalMode.BALANCED));
    }

    public PublicationRetrievalResult retrieveBest(String query, RetrievalOptions options) {
        return retrieveBest(query, options, VisibilityScope.PUBLIC_VALIDATED, null);
    }

    public PublicationRetrievalResult retrieveBest(
        String query,
        RetrievalOptions options,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        RetrievalPlan plan = thresholdPolicy.plan(options.limit(), options.minSimilarity(), options.mode());
        VisibilityScope effectiveScope = effectiveScope(visibilityScope);
        if (embeddingRepository.hasEmbeddings(embeddingService.provider(), embeddingService.model(), properties.getEmbeddingDimension())) {
            List<SemanticPublicationMatch> matches = semanticSearch(query, plan, effectiveScope, linkedResearcherId);
            List<RetrievedPublicationContext> contexts = matches.stream().map(SemanticPublicationMatch::context).toList();
            List<String> warnings = retrievalWarnings(contexts, plan);
            return new PublicationRetrievalResult(
                contexts,
                RetrievalMethod.SEMANTIC,
                plan.mode(),
                plan.minSimilarity(),
                warnings,
                effectiveScope,
                validationFilterApplied(effectiveScope)
            );
        }
        return new PublicationRetrievalResult(
            textSearch(query, plan.limit(), effectiveScope, linkedResearcherId),
            RetrievalMethod.TEXT,
            plan.mode(),
            plan.minSimilarity(),
            List.of("No se han encontrado embeddings para el proveedor/modelo/dimension configurados; se ha usado busqueda por texto."),
            effectiveScope,
            validationFilterApplied(effectiveScope)
        );
    }

    public List<SemanticPublicationMatch> semanticSearch(String query, int limit) {
        return semanticSearch(query, thresholdPolicy.plan(limit, null, RetrievalMode.BALANCED));
    }

    public List<SemanticPublicationMatch> semanticSearch(String query, Integer limit, Double minSimilarity) {
        return semanticSearch(query, thresholdPolicy.plan(limit, minSimilarity, RetrievalMode.BALANCED));
    }

    public List<SemanticPublicationMatch> semanticSearch(
        String query,
        Integer limit,
        Double minSimilarity,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        return semanticSearch(query, thresholdPolicy.plan(limit, minSimilarity, RetrievalMode.BALANCED), visibilityScope, linkedResearcherId);
    }

    List<SemanticPublicationMatch> semanticSearch(String query, RetrievalPlan plan) {
        return semanticSearch(query, plan, VisibilityScope.PUBLIC_VALIDATED, null);
    }

    List<SemanticPublicationMatch> semanticSearch(
        String query,
        RetrievalPlan plan,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        VisibilityScope effectiveScope = effectiveScope(visibilityScope);
        EmbeddingResponse response = embeddingService.embed(focusedSemanticQuery(query));
        int dimension = properties.getEmbeddingDimension();
        if (response.vector().size() != dimension) {
            throw new BusinessRuleException(
                "Embedding dimension mismatch for query: expected " + dimension + " but provider returned " + response.vector().size() + "."
            );
        }
        List<PublicationEmbeddingSearchRow> rows = embeddingRepository.searchNearest(
            EmbeddingVectorFormatter.toPgVector(response.vector()),
            embeddingService.provider(),
            embeddingService.model(),
            dimension,
            plan.limit(),
            effectiveScope,
            linkedResearcherId
        );
        Map<Long, Double> scores = rows.stream()
            .collect(Collectors.toMap(PublicationEmbeddingSearchRow::publicationId, PublicationEmbeddingSearchRow::similarityScore, (first, second) -> first, LinkedHashMap::new));
        Map<Long, RetrievedPublicationContext> contexts = contextsByPublicationId(scores.keySet(), scores, plan, effectiveScope, linkedResearcherId);
        return rows.stream()
            .map(row -> {
                RetrievedPublicationContext context = contexts.get(row.publicationId());
                return context == null ? null : new SemanticPublicationMatch(context, row.similarityScore(), context.passedThreshold(), context.retrievalReason());
            })
            .filter(match -> match != null && match.passedThreshold())
            .toList();
    }

    public List<RetrievedPublicationContext> textSearch(String query, int limit) {
        return textSearch(query, limit, VisibilityScope.PUBLIC_VALIDATED, null);
    }

    public List<RetrievedPublicationContext> textSearch(
        String query,
        int limit,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        VisibilityScope effectiveScope = effectiveScope(visibilityScope);
        List<PublicationEntity> publications = retrieveTextPublications(query, limit, effectiveScope, linkedResearcherId);
        return new ArrayList<>(contextsByPublicationId(
            publications.stream().map(PublicationEntity::getId).toList(),
            Map.of(),
            null,
            effectiveScope,
            linkedResearcherId
        ).values());
    }

    public Set<Long> visiblePublicationIds(Collection<Long> publicationIds, VisibilityScope visibilityScope, Long linkedResearcherId) {
        return visiblePublicationsById(publicationIds, effectiveScope(visibilityScope), linkedResearcherId).keySet();
    }

    private List<PublicationEntity> retrieveTextPublications(
        String question,
        int limit,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        Map<Long, PublicationEntity> publicationsById = new LinkedHashMap<>();
        addSearchResults(publicationsById, question.trim(), limit, visibilityScope, linkedResearcherId);
        if (publicationsById.size() < limit) {
            for (String term : searchTerms(question)) {
                addSearchResults(publicationsById, term, limit, visibilityScope, linkedResearcherId);
                if (publicationsById.size() >= limit) {
                    break;
                }
            }
        }
        return publicationsById.values().stream().limit(limit).toList();
    }

    private void addSearchResults(
        Map<Long, PublicationEntity> publicationsById,
        String text,
        int limit,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        if (text.isBlank()) {
            return;
        }
        String textPattern = "%" + text.toLowerCase() + "%";
        Specification<PublicationEntity> specification = PublicationSpecifications.matches(
            text,
            textPattern,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "%"
        ).and(PublicationSpecifications.visibleTo(visibilityScope, linkedResearcherId));
        publicationRepository.findAll(
            specification,
            PageRequest.of(0, Math.max(limit, 1), Sort.by(Sort.Direction.DESC, "publicationYear").and(Sort.by("title")))
        ).getContent()
            .stream()
            .filter(publication -> isVisible(publication, visibilityScope, linkedResearcherId))
            .forEach(publication -> publicationsById.putIfAbsent(publication.getId(), publication));
    }

    private List<String> searchTerms(String question) {
        return List.of(normalizeForSearch(question).split("[^\\p{Alnum}]+"))
            .stream()
            .filter(term -> term.length() >= 4 || "ia".equals(term))
            .filter(term -> !STOP_WORDS.contains(term))
            .distinct()
            .limit(12)
            .toList();
    }

    private String focusedSemanticQuery(String query) {
        List<String> terms = searchTerms(query);
        if (terms.isEmpty()) {
            return query == null ? "" : query;
        }
        return String.join(" ", terms);
    }

    private String normalizeForSearch(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.toLowerCase(), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private Map<Long, RetrievedPublicationContext> contextsByPublicationId(
        Collection<Long> publicationIds,
        Map<Long, Double> similarityScores,
        RetrievalPlan plan,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        if (publicationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PublicationEntity> publicationsById = visiblePublicationsById(publicationIds, visibilityScope, linkedResearcherId);
        Map<Long, List<String>> authors = authorsByPublicationId(publicationIds, visibilityScope);
        Map<Long, List<String>> topics = topicsByPublicationId(publicationIds);
        Map<Long, RetrievedPublicationContext> contexts = new LinkedHashMap<>();
        for (Long publicationId : publicationIds) {
            PublicationEntity publication = publicationsById.get(publicationId);
            if (publication != null) {
                contexts.put(publicationId, new RetrievedPublicationContext(
                    publication,
                    authors.getOrDefault(publicationId, List.of()),
                    topics.getOrDefault(publicationId, List.of()),
                    similarityScores.get(publicationId),
                    passedThreshold(similarityScores.get(publicationId), plan),
                    lowSimilarity(similarityScores.get(publicationId)),
                    retrievalReason(similarityScores.get(publicationId), plan)
                ));
            }
        }
        return contexts;
    }

    private Map<Long, PublicationEntity> visiblePublicationsById(
        Collection<Long> publicationIds,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        if (publicationIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = publicationIds.stream().distinct().toList();
        Specification<PublicationEntity> specification = PublicationSpecifications.hasIdIn(ids)
            .and(PublicationSpecifications.visibleTo(visibilityScope, linkedResearcherId));
        return publicationRepository.findAll(specification)
            .stream()
            .filter(publication -> isVisible(publication, visibilityScope, linkedResearcherId))
            .collect(Collectors.toMap(PublicationEntity::getId, Function.identity(), (first, second) -> first, LinkedHashMap::new));
    }

    private boolean isVisible(PublicationEntity publication, VisibilityScope visibilityScope, Long linkedResearcherId) {
        VisibilityScope effectiveScope = effectiveScope(visibilityScope);
        if (effectiveScope == VisibilityScope.ADMIN_ALL) {
            return true;
        }
        if (publication.getValidationStatus() == ValidationStatus.VALIDATED) {
            return true;
        }
        return effectiveScope == VisibilityScope.MY_DATA
            && linkedResearcherId != null
            && authorRepository.existsByPublicationIdAndResearcherId(publication.getId(), linkedResearcherId);
    }

    private VisibilityScope effectiveScope(VisibilityScope visibilityScope) {
        return visibilityScope == null ? VisibilityScope.PUBLIC_VALIDATED : visibilityScope;
    }

    private boolean validationFilterApplied(VisibilityScope visibilityScope) {
        return effectiveScope(visibilityScope) != VisibilityScope.ADMIN_ALL;
    }

    private boolean passedThreshold(Double similarityScore, RetrievalPlan plan) {
        return plan == null || similarityScore == null || similarityScore >= plan.minSimilarity();
    }

    private boolean lowSimilarity(Double similarityScore) {
        return similarityScore != null && similarityScore < properties.getRetrieval().getMinSimilarity();
    }

    private String retrievalReason(Double similarityScore, RetrievalPlan plan) {
        if (similarityScore == null || plan == null) {
            return "Coincidencia por texto con titulo, resumen, fuente, DOI, autores o temas.";
        }
        if (similarityScore >= plan.minSimilarity()) {
            return "Similitud semantica por encima del umbral configurado.";
        }
        return "Similitud semantica por debajo del umbral configurado.";
    }

    private List<String> retrievalWarnings(List<RetrievedPublicationContext> contexts, RetrievalPlan plan) {
        List<String> warnings = new ArrayList<>();
        if (contexts.isEmpty()) {
            warnings.add("No se han encontrado publicaciones suficientemente relacionadas.");
        }
        if (plan.mode() == RetrievalMode.BROAD) {
            warnings.add("La recuperacion se ha realizado en modo amplio.");
        }
        if (thresholdPolicy.hasLowSimilarity(contexts)) {
            warnings.add("Algunos resultados tienen baja similitud; interpreta la respuesta con cautela.");
        }
        return warnings;
    }

    private Map<Long, List<String>> authorsByPublicationId(Collection<Long> publicationIds, VisibilityScope visibilityScope) {
        List<PublicationAuthorEntity> authors = authorRepository.findByPublicationIdIn(publicationIds);
        Map<Long, String> researcherNames = researcherRepository.findAllById(authors.stream()
            .map(PublicationAuthorEntity::getResearcherId)
            .filter(id -> id != null)
            .toList())
            .stream()
            .filter(researcher -> isVisibleResearcher(researcher, visibilityScope))
            .collect(Collectors.toMap(ResearcherEntity::getId, ResearcherEntity::getFullName, (first, second) -> first));

        return authors.stream()
            .sorted(Comparator.comparing(PublicationAuthorEntity::getAuthorOrder))
            .filter(author -> author.getResearcherId() == null || researcherNames.containsKey(author.getResearcherId()))
            .collect(Collectors.groupingBy(
                PublicationAuthorEntity::getPublicationId,
                LinkedHashMap::new,
                Collectors.mapping(author -> authorName(author, researcherNames), Collectors.toList())
            ));
    }

    private String authorName(PublicationAuthorEntity author, Map<Long, String> researcherNames) {
        if (author.getResearcherId() != null) {
            return researcherNames.get(author.getResearcherId());
        }
        return author.getExternalAuthorName();
    }

    private boolean isVisibleResearcher(ResearcherEntity researcher, VisibilityScope visibilityScope) {
        return effectiveScope(visibilityScope) == VisibilityScope.ADMIN_ALL
            || researcher.getValidationStatus() == ValidationStatus.VALIDATED;
    }

    private Map<Long, List<String>> topicsByPublicationId(Collection<Long> publicationIds) {
        List<PublicationTopicEntity> links = publicationTopicRepository.findByPublicationIdIn(publicationIds);
        Map<Long, TopicEntity> topicsById = topicRepository.findAllById(links.stream()
            .map(PublicationTopicEntity::getTopicId)
            .toList())
            .stream()
            .collect(Collectors.toMap(TopicEntity::getId, Function.identity()));

        return links.stream()
            .collect(Collectors.groupingBy(
                PublicationTopicEntity::getPublicationId,
                LinkedHashMap::new,
                Collectors.mapping(link -> topicsById.get(link.getTopicId()).getName(), Collectors.toList())
            ));
    }
}
