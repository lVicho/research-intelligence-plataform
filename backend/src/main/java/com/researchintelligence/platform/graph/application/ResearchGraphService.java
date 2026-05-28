package com.researchintelligence.platform.graph.application;

import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.graph.api.GraphEdge;
import com.researchintelligence.platform.graph.api.GraphDensity;
import com.researchintelligence.platform.graph.api.GraphMetadata;
import com.researchintelligence.platform.graph.api.GraphNode;
import com.researchintelligence.platform.graph.api.GraphResponse;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorEntity;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ResearchGraphService {

    private static final String RESEARCHER = "researcher";
    private static final String RESEARCH_UNIT = "research_unit";
    private static final String PUBLICATION = "publication";
    private static final String TOPIC = "topic";
    private static final String EXTERNAL_AUTHOR = "external_author";

    private final ResearcherRepository researcherRepository;
    private final ResearcherAffiliationRepository affiliationRepository;
    private final ResearchUnitRepository researchUnitRepository;
    private final PublicationRepository publicationRepository;
    private final PublicationAuthorRepository authorRepository;
    private final PublicationTopicRepository publicationTopicRepository;
    private final TopicRepository topicRepository;
    private final VisibilityContext visibilityContext;

    public ResearchGraphService(
        ResearcherRepository researcherRepository,
        ResearcherAffiliationRepository affiliationRepository,
        ResearchUnitRepository researchUnitRepository,
        PublicationRepository publicationRepository,
        PublicationAuthorRepository authorRepository,
        PublicationTopicRepository publicationTopicRepository,
        TopicRepository topicRepository,
        VisibilityContext visibilityContext
    ) {
        this.researcherRepository = researcherRepository;
        this.affiliationRepository = affiliationRepository;
        this.researchUnitRepository = researchUnitRepository;
        this.publicationRepository = publicationRepository;
        this.authorRepository = authorRepository;
        this.publicationTopicRepository = publicationTopicRepository;
        this.topicRepository = topicRepository;
        this.visibilityContext = visibilityContext;
    }

    public GraphResponse researcherGraph(Long researcherId) {
        return researcherGraph(researcherId, null, null, null, null, null, null, null, null, null, null);
    }

    public GraphResponse researcherGraph(
        Long researcherId,
        GraphDensity density,
        Boolean includePublications,
        Boolean includeTopics,
        Boolean includeCoauthors,
        Boolean includeResearchUnits,
        Boolean includeExternalAuthors,
        Integer maxPublications,
        Integer maxTopics,
        Integer maxCoauthors,
        Boolean includeNonValidated
    ) {
        VisibilityScope visibilityScope = resolvePublicScope(includeNonValidated);
        GraphQueryOptions options = GraphQueryOptions.from(
            density,
            includePublications,
            includeTopics,
            includeCoauthors,
            includeResearchUnits,
            includeExternalAuthors,
            maxPublications,
            maxTopics,
            maxCoauthors
        );
        GraphBuildResult completeGraph = buildGraph(researcherId, GraphQueryOptions.complete(), visibilityScope);
        GraphBuildResult displayedGraph = buildGraph(researcherId, options, visibilityScope);
        boolean truncated = displayedGraph.truncated()
            || displayedGraph.nodes().size() < completeGraph.nodes().size()
            || displayedGraph.edges().size() < completeGraph.edges().size();
        List<String> warnings = new ArrayList<>(displayedGraph.warnings());
        if (truncated) {
            warnings.add("El grafo se ha simplificado para mejorar la legibilidad. Cambia la densidad para ver más nodos.");
        }
        return new GraphResponse(
            List.copyOf(displayedGraph.nodes().values()),
            List.copyOf(displayedGraph.edges().values()),
            new GraphMetadata(
                completeGraph.nodes().size(),
                completeGraph.edges().size(),
                displayedGraph.nodes().size(),
                displayedGraph.edges().size(),
                truncated,
                visibilityScope.name(),
                validationFilterApplied(visibilityScope)
            ),
            warnings.stream().distinct().toList()
        );
    }

    private GraphBuildResult buildGraph(Long researcherId, GraphQueryOptions options, VisibilityScope visibilityScope) {
        ResearcherEntity researcher = findVisibleResearcher(researcherId, visibilityScope);
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        Map<String, GraphEdge> edges = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        addNode(nodes, researcherNode(researcher));
        if (options.includeResearchUnits()) {
            addAffiliationGraph(researcherId, nodes, edges, visibilityScope);
        }
        if (options.includePublications()) {
            addPublicationGraph(researcherId, nodes, edges, options, warnings, visibilityScope);
        }
        if (options.includeCoauthors() || options.includeExternalAuthors()) {
            addCoauthorEdges(researcherId, nodes, edges, options, warnings, visibilityScope);
        }

        return new GraphBuildResult(nodes, edges, !warnings.isEmpty(), warnings);
    }

    private void addAffiliationGraph(
        Long researcherId,
        Map<String, GraphNode> nodes,
        Map<String, GraphEdge> edges,
        VisibilityScope visibilityScope
    ) {
        LocalDate today = LocalDate.now();
        List<ResearcherAffiliationEntity> affiliations = affiliationRepository.findByResearcherIdOrderByPrimaryAffiliationDescStartDateDescIdAsc(researcherId)
            .stream()
            .filter(affiliation -> isVisible(affiliation.getValidationStatus(), visibilityScope))
            .toList();
        Map<Long, ResearchUnitEntity> unitsById = researchUnitRepository.findAllById(affiliations.stream()
            .map(ResearcherAffiliationEntity::getResearchUnitId)
            .toList())
            .stream()
            .filter(unit -> isVisible(unit.getValidationStatus(), visibilityScope))
            .collect(Collectors.toMap(ResearchUnitEntity::getId, Function.identity()));

        for (ResearcherAffiliationEntity affiliation : affiliations) {
            ResearchUnitEntity unit = unitsById.get(affiliation.getResearchUnitId());
            if (unit == null) {
                continue;
            }
            addResearchUnitWithParents(unit, nodes, edges, visibilityScope);
            addEdge(edges, new GraphEdge(
                edgeId("affiliated_with", researcherNodeId(researcherId), researchUnitNodeId(unit.getId())),
                researcherNodeId(researcherId),
                researchUnitNodeId(unit.getId()),
                "affiliated_with",
                affiliation.isPrimaryAffiliation() ? 2.0 : 1.0,
                metadata(
                    "role", affiliation.getRole(),
                    "affiliationType", affiliation.getAffiliationType(),
                    "current", affiliation.getEndDate() == null || !affiliation.getEndDate().isBefore(today),
                    "primary", affiliation.isPrimaryAffiliation(),
                    "startDate", affiliation.getStartDate(),
                    "endDate", affiliation.getEndDate()
                )
            ));
        }
    }

    private void addPublicationGraph(
        Long researcherId,
        Map<String, GraphNode> nodes,
        Map<String, GraphEdge> edges,
        GraphQueryOptions options,
        List<String> warnings,
        VisibilityScope visibilityScope
    ) {
        List<PublicationEntity> allPublications = visibleAuthoredPublications(researcherId, visibilityScope)
            .stream()
            .sorted(Comparator.comparing(PublicationEntity::getPublicationYear, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PublicationEntity::getTitle))
            .toList();
        List<PublicationEntity> publications = limitList(allPublications, options.maxPublications());
        if (allPublications.size() > publications.size()) {
            warnings.add("Se han limitado las publicaciones mostradas.");
        }
        if (publications.isEmpty()) {
            return;
        }
        List<Long> publicationIds = publications.stream().map(PublicationEntity::getId).toList();
        Map<Long, PublicationEntity> publicationsById = publications.stream()
            .collect(Collectors.toMap(PublicationEntity::getId, Function.identity()));
        List<Object[]> internalCoauthorRows = options.includeCoauthors()
            ? limitList(internalCoauthorRows(researcherId, publicationIds, visibilityScope), options.maxCoauthors())
            : List.of();
        List<Object[]> externalCoauthorRows = options.includeExternalAuthors()
            ? limitList(externalCoauthorRows(researcherId, publicationIds), options.maxCoauthors())
            : List.of();
        Collection<Long> allowedCoauthorIds = internalCoauthorRows.stream().map(row -> (Long) row[0]).toList();
        Collection<String> allowedExternalAuthorNames = externalCoauthorRows.stream().map(row -> (String) row[0]).toList();
        Map<Long, ResearcherEntity> researchersById = researcherRepository.findAllById(authorResearcherIds(publicationIds))
            .stream()
            .filter(researcher -> isVisible(researcher.getValidationStatus(), visibilityScope))
            .collect(Collectors.toMap(ResearcherEntity::getId, Function.identity()));
        List<PublicationTopicEntity> topicLinks = options.includeTopics() ? publicationTopicRepository.findByPublicationIdIn(publicationIds) : List.of();
        Collection<Long> selectedTopicIds = limitList(topicLinks.stream().map(PublicationTopicEntity::getTopicId).distinct().toList(), options.maxTopics());
        if (topicLinks.stream().map(PublicationTopicEntity::getTopicId).distinct().count() > selectedTopicIds.size()) {
            warnings.add("Se han limitado los temas mostrados.");
        }
        Map<Long, TopicEntity> topicsById = topicRepository.findAllById(selectedTopicIds)
            .stream()
            .collect(Collectors.toMap(TopicEntity::getId, Function.identity()));

        publications.forEach(publication -> addNode(nodes, publicationNode(publication)));

        for (PublicationAuthorEntity author : authorRepository.findByPublicationIdIn(publicationIds)) {
            PublicationEntity publication = publicationsById.get(author.getPublicationId());
            if (publication == null) {
                continue;
            }
            String authorNodeId = addAuthorNode(author, researcherId, allowedCoauthorIds, allowedExternalAuthorNames, researchersById, nodes);
            if (authorNodeId != null) {
                addEdge(edges, new GraphEdge(
                    edgeId("authored", authorNodeId, publicationNodeId(publication.getId())),
                    authorNodeId,
                    publicationNodeId(publication.getId()),
                    "authored",
                    author.isCorrespondingAuthor() ? 2.0 : 1.0,
                    metadata("authorOrder", author.getAuthorOrder(), "correspondingAuthor", author.isCorrespondingAuthor())
                ));
            }
        }

        for (PublicationTopicEntity link : topicLinks) {
            TopicEntity topic = topicsById.get(link.getTopicId());
            if (topic == null) {
                continue;
            }
            addNode(nodes, topicNode(topic));
            addEdge(edges, new GraphEdge(
                edgeId("has_topic", publicationNodeId(link.getPublicationId()), topicNodeId(topic.getId())),
                publicationNodeId(link.getPublicationId()),
                topicNodeId(topic.getId()),
                "has_topic",
                1.0,
                metadata()
            ));
        }
    }

    private void addCoauthorEdges(
        Long researcherId,
        Map<String, GraphNode> nodes,
        Map<String, GraphEdge> edges,
        GraphQueryOptions options,
        List<String> warnings,
        VisibilityScope visibilityScope
    ) {
        String researcherNodeId = researcherNodeId(researcherId);
        List<Long> visiblePublicationIds = visibleAuthoredPublications(researcherId, visibilityScope)
            .stream()
            .map(PublicationEntity::getId)
            .toList();
        List<Object[]> allInternalRows = internalCoauthorRows(researcherId, visiblePublicationIds, visibilityScope);
        List<Object[]> internalRows = options.includeCoauthors() ? limitList(allInternalRows, options.maxCoauthors()) : List.of();
        if (options.includeCoauthors() && allInternalRows.size() > internalRows.size()) {
            warnings.add("Se han limitado los coautores mostrados.");
        }
        Map<Long, ResearcherEntity> coauthorsById = researcherRepository.findAllById(internalRows.stream().map(row -> (Long) row[0]).toList())
            .stream()
            .filter(researcher -> isVisible(researcher.getValidationStatus(), visibilityScope))
            .collect(Collectors.toMap(ResearcherEntity::getId, Function.identity()));
        for (Object[] row : internalRows) {
            Long coauthorId = (Long) row[0];
            long sharedPublicationCount = (Long) row[2];
            ResearcherEntity coauthor = coauthorsById.get(coauthorId);
            if (coauthor == null) {
                continue;
            }
            addNode(nodes, researcherNode(coauthor));
            addEdge(edges, new GraphEdge(
                edgeId("coauthored_with", researcherNodeId, researcherNodeId(coauthorId)),
                researcherNodeId,
                researcherNodeId(coauthorId),
                "coauthored_with",
                sharedPublicationCount,
                metadata("sharedPublicationCount", sharedPublicationCount, "internal", true)
            ));
        }
        List<Object[]> allExternalRows = externalCoauthorRows(researcherId, visiblePublicationIds);
        List<Object[]> externalRows = options.includeExternalAuthors() ? limitList(allExternalRows, options.maxCoauthors()) : List.of();
        if (options.includeExternalAuthors() && allExternalRows.size() > externalRows.size()) {
            warnings.add("Se han limitado los autores externos mostrados.");
        }
        for (Object[] row : externalRows) {
            String name = (String) row[0];
            long sharedPublicationCount = (Long) row[1];
            String externalNodeId = externalAuthorNodeId(name);
            addNode(nodes, new GraphNode(externalNodeId, EXTERNAL_AUTHOR, name, metadata()));
            addEdge(edges, new GraphEdge(
                edgeId("coauthored_with", researcherNodeId, externalNodeId),
                researcherNodeId,
                externalNodeId,
                "coauthored_with",
                sharedPublicationCount,
                metadata("sharedPublicationCount", sharedPublicationCount, "internal", false)
            ));
        }
    }

    private ResearcherEntity findVisibleResearcher(Long researcherId, VisibilityScope visibilityScope) {
        return researcherRepository.findById(researcherId)
            .filter(researcher -> isVisible(researcher.getValidationStatus(), visibilityScope))
            .orElseThrow(() -> new ResourceNotFoundException("Researcher", researcherId));
    }

    private List<PublicationEntity> visibleAuthoredPublications(Long researcherId, VisibilityScope visibilityScope) {
        return publicationRepository.findAuthoredByResearcherId(researcherId)
            .stream()
            .filter(publication -> isVisible(publication.getValidationStatus(), visibilityScope))
            .toList();
    }

    private List<Object[]> internalCoauthorRows(
        Long researcherId,
        Collection<Long> publicationIds,
        VisibilityScope visibilityScope
    ) {
        if (publicationIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Set<Long>> publicationIdsByCoauthor = new LinkedHashMap<>();
        for (PublicationAuthorEntity author : authorRepository.findByPublicationIdIn(publicationIds)) {
            Long coauthorId = author.getResearcherId();
            if (coauthorId == null || coauthorId.equals(researcherId)) {
                continue;
            }
            publicationIdsByCoauthor.computeIfAbsent(coauthorId, ignored -> new LinkedHashSet<>()).add(author.getPublicationId());
        }
        Map<Long, ResearcherEntity> visibleResearchersById = researcherRepository.findAllById(publicationIdsByCoauthor.keySet())
            .stream()
            .filter(researcher -> isVisible(researcher.getValidationStatus(), visibilityScope))
            .collect(Collectors.toMap(ResearcherEntity::getId, Function.identity()));
        return publicationIdsByCoauthor.entrySet()
            .stream()
            .filter(entry -> visibleResearchersById.containsKey(entry.getKey()))
            .map(entry -> new Object[] {
                entry.getKey(),
                visibleResearchersById.get(entry.getKey()).getFullName(),
                (long) entry.getValue().size()
            })
            .sorted(Comparator
                .<Object[]>comparingLong(row -> (Long) row[2]).reversed()
                .thenComparing(row -> (String) row[1]))
            .toList();
    }

    private List<Object[]> externalCoauthorRows(Long researcherId, Collection<Long> publicationIds) {
        if (publicationIds.isEmpty()) {
            return List.of();
        }
        Map<String, Set<Long>> publicationIdsByAuthor = new LinkedHashMap<>();
        for (PublicationAuthorEntity author : authorRepository.findByPublicationIdIn(publicationIds)) {
            if (author.getResearcherId() != null || author.getExternalAuthorName() == null || author.getExternalAuthorName().isBlank()) {
                continue;
            }
            publicationIdsByAuthor.computeIfAbsent(author.getExternalAuthorName(), ignored -> new LinkedHashSet<>()).add(author.getPublicationId());
        }
        return publicationIdsByAuthor.entrySet()
            .stream()
            .map(entry -> new Object[] { entry.getKey(), (long) entry.getValue().size() })
            .sorted(Comparator
                .<Object[]>comparingLong(row -> (Long) row[1]).reversed()
                .thenComparing(row -> (String) row[0]))
            .toList();
    }

    private String addAuthorNode(
        PublicationAuthorEntity author,
        Long mainResearcherId,
        Collection<Long> allowedCoauthorIds,
        Collection<String> allowedExternalAuthorNames,
        Map<Long, ResearcherEntity> researchersById,
        Map<String, GraphNode> nodes
    ) {
        if (author.getResearcherId() != null) {
            if (!author.getResearcherId().equals(mainResearcherId) && !allowedCoauthorIds.contains(author.getResearcherId())) {
                return null;
            }
            ResearcherEntity coauthor = researchersById.get(author.getResearcherId());
            if (coauthor == null) {
                return null;
            }
            addNode(nodes, researcherNode(coauthor));
            return researcherNodeId(coauthor.getId());
        }
        if (author.getExternalAuthorName() == null || author.getExternalAuthorName().isBlank()) {
            return null;
        }
        if (!allowedExternalAuthorNames.contains(author.getExternalAuthorName())) {
            return null;
        }
        GraphNode node = new GraphNode(
            externalAuthorNodeId(author.getExternalAuthorName()),
            EXTERNAL_AUTHOR,
            author.getExternalAuthorName(),
            metadata("externalAffiliation", author.getExternalAffiliation())
        );
        addNode(nodes, node);
        return node.id();
    }

    private void addResearchUnitWithParents(
        ResearchUnitEntity unit,
        Map<String, GraphNode> nodes,
        Map<String, GraphEdge> edges,
        VisibilityScope visibilityScope
    ) {
        addNode(nodes, researchUnitNode(unit));
        Long parentId = unit.getParentId();
        while (parentId != null) {
            ResearchUnitEntity parent = researchUnitRepository.findById(parentId).orElse(null);
            if (parent == null || !isVisible(parent.getValidationStatus(), visibilityScope)) {
                return;
            }
            addNode(nodes, researchUnitNode(parent));
            addEdge(edges, new GraphEdge(
                edgeId("belongs_to", researchUnitNodeId(unit.getId()), researchUnitNodeId(parent.getId())),
                researchUnitNodeId(unit.getId()),
                researchUnitNodeId(parent.getId()),
                "belongs_to",
                1.0,
                metadata()
            ));
            unit = parent;
            parentId = unit.getParentId();
        }
    }

    private Collection<Long> authorResearcherIds(Collection<Long> publicationIds) {
        return authorRepository.findByPublicationIdIn(publicationIds).stream()
            .map(PublicationAuthorEntity::getResearcherId)
            .filter(id -> id != null)
            .distinct()
            .toList();
    }

    private Collection<Long> topicIds(Collection<Long> publicationIds) {
        return publicationTopicRepository.findByPublicationIdIn(publicationIds).stream()
            .map(PublicationTopicEntity::getTopicId)
            .distinct()
            .toList();
    }

    private GraphNode researcherNode(ResearcherEntity researcher) {
        return new GraphNode(
            researcherNodeId(researcher.getId()),
            RESEARCHER,
            researcher.getDisplayName() == null || researcher.getDisplayName().isBlank() ? researcher.getFullName() : researcher.getDisplayName(),
            metadata("id", researcher.getId(), "email", researcher.getEmail(), "orcid", researcher.getOrcid(), "active", researcher.isActive())
        );
    }

    private GraphNode researchUnitNode(ResearchUnitEntity unit) {
        return new GraphNode(
            researchUnitNodeId(unit.getId()),
            RESEARCH_UNIT,
            unit.getName(),
            metadata("id", unit.getId(), "shortName", unit.getShortName(), "unitType", unit.getType(), "city", unit.getCity(), "country", unit.getCountry())
        );
    }

    private GraphNode publicationNode(PublicationEntity publication) {
        return new GraphNode(
            publicationNodeId(publication.getId()),
            PUBLICATION,
            publication.getTitle(),
            metadata("id", publication.getId(), "year", publication.getPublicationYear(), "type", publication.getType(), "status", publication.getStatus(), "doi", publication.getDoi())
        );
    }

    private GraphNode topicNode(TopicEntity topic) {
        return new GraphNode(topicNodeId(topic.getId()), TOPIC, topic.getName(), metadata("id", topic.getId(), "normalizedName", topic.getNormalizedName()));
    }

    private void addNode(Map<String, GraphNode> nodes, GraphNode node) {
        nodes.putIfAbsent(node.id(), node);
    }

    private void addEdge(Map<String, GraphEdge> edges, GraphEdge edge) {
        edges.putIfAbsent(edge.id(), edge);
    }

    private String researcherNodeId(Long id) {
        return RESEARCHER + ":" + id;
    }

    private String researchUnitNodeId(Long id) {
        return RESEARCH_UNIT + ":" + id;
    }

    private String publicationNodeId(Long id) {
        return PUBLICATION + ":" + id;
    }

    private String topicNodeId(Long id) {
        return TOPIC + ":" + id;
    }

    private String externalAuthorNodeId(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-");
        return EXTERNAL_AUTHOR + ":" + normalized + "-" + Math.abs(name.toLowerCase(Locale.ROOT).hashCode());
    }

    private String edgeId(String type, String source, String target) {
        return type + ":" + source + "->" + target;
    }

    private Map<String, Object> metadata(Object... values) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (values[i + 1] != null) {
                metadata.put(String.valueOf(values[i]), values[i + 1]);
            }
        }
        return metadata;
    }

    private <T> List<T> limitList(List<T> values, int limit) {
        if (values.size() <= limit) {
            return values;
        }
        return values.subList(0, limit);
    }

    private VisibilityScope resolvePublicScope(Boolean includeNonValidated) {
        if (Boolean.TRUE.equals(includeNonValidated) && visibilityContext.currentRoles().contains("ADMIN")) {
            return VisibilityScope.ADMIN_ALL;
        }
        return VisibilityScope.PUBLIC_VALIDATED;
    }

    private boolean isVisible(ValidationStatus validationStatus, VisibilityScope visibilityScope) {
        return visibilityScope == VisibilityScope.ADMIN_ALL || validationStatus == ValidationStatus.VALIDATED;
    }

    private boolean validationFilterApplied(VisibilityScope visibilityScope) {
        return visibilityScope != VisibilityScope.ADMIN_ALL;
    }

    private record GraphBuildResult(
        Map<String, GraphNode> nodes,
        Map<String, GraphEdge> edges,
        boolean truncated,
        List<String> warnings
    ) {
    }
}
