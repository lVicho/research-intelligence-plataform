package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.ai.api.AdminConversationalSearchRequest;
import com.researchintelligence.platform.ai.api.AdminConversationalSearchResponse;
import com.researchintelligence.platform.ai.api.ConversationalSearchEntityScope;
import com.researchintelligence.platform.dataquality.api.DataQualityIssueResponse;
import com.researchintelligence.platform.dataquality.application.DataQualityService;
import com.researchintelligence.platform.dataquality.domain.DataQualityEntityType;
import com.researchintelligence.platform.dataquality.domain.DataQualityIssueType;
import com.researchintelligence.platform.events.persistence.EventParticipationEntity;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.events.persistence.ScientificEventEntity;
import com.researchintelligence.platform.events.persistence.ScientificEventRepository;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationSpecifications;
import com.researchintelligence.platform.publications.persistence.PublicationTopicEntity;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.api.ValidationItemResponse;
import com.researchintelligence.platform.validation.application.ValidationInboxService;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AdminConversationalSearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final String MISSING_EVIDENCE = "MISSING_EVIDENCE";

    private final ConversationalSearchInterpreter interpreter;
    private final VisibilityContext visibilityContext;
    private final PublicationRepository publicationRepository;
    private final PublicationRetrievalService publicationRetrievalService;
    private final PublicationTopicRepository publicationTopicRepository;
    private final TopicRepository topicRepository;
    private final ResearcherRepository researcherRepository;
    private final ResearchUnitRepository researchUnitRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final ScientificEventRepository scientificEventRepository;
    private final DataQualityService dataQualityService;
    private final ValidationInboxService validationInboxService;

    public AdminConversationalSearchService(
        ConversationalSearchInterpreter interpreter,
        VisibilityContext visibilityContext,
        PublicationRepository publicationRepository,
        PublicationRetrievalService publicationRetrievalService,
        PublicationTopicRepository publicationTopicRepository,
        TopicRepository topicRepository,
        ResearcherRepository researcherRepository,
        ResearchUnitRepository researchUnitRepository,
        EventParticipationRepository eventParticipationRepository,
        ScientificEventRepository scientificEventRepository,
        DataQualityService dataQualityService,
        ValidationInboxService validationInboxService
    ) {
        this.interpreter = interpreter;
        this.visibilityContext = visibilityContext;
        this.publicationRepository = publicationRepository;
        this.publicationRetrievalService = publicationRetrievalService;
        this.publicationTopicRepository = publicationTopicRepository;
        this.topicRepository = topicRepository;
        this.researcherRepository = researcherRepository;
        this.researchUnitRepository = researchUnitRepository;
        this.eventParticipationRepository = eventParticipationRepository;
        this.scientificEventRepository = scientificEventRepository;
        this.dataQualityService = dataQualityService;
        this.validationInboxService = validationInboxService;
    }

    public AdminConversationalSearchResponse search(AdminConversationalSearchRequest request) {
        ensureAdminOrValidator();
        int limit = safeLimit(request.limit());
        ConversationalSearchInterpretation interpretation = interpreter.interpret(request.question(), request.entityScope());
        List<String> warnings = new ArrayList<>(interpretation.warnings());
        if (interpretation.clarificationNeeded() || interpretation.entityScope() == null) {
            return new AdminConversationalSearchResponse(
                interpretation.interpretedIntent(),
                interpretation.filters().toResponseMap(),
                "CLARIFICATION",
                List.of(),
                warnings.stream().distinct().toList(),
                "Necesito acotar la busqueda antes de consultar datos internos.",
                true,
                interpretation.clarificationOptions()
            );
        }

        SearchExecution execution = switch (interpretation.entityScope()) {
            case PUBLICATIONS -> searchPublications(interpretation.filters(), limit);
            case RESEARCHERS -> searchResearchers(interpretation.filters(), limit);
            case UNITS -> searchUnits(interpretation.filters(), limit);
            case ACTIVITIES -> searchActivities(interpretation.filters(), limit);
            case VALIDATION -> searchValidation(interpretation.filters(), limit);
            case DATA_QUALITY -> searchDataQuality(interpretation.filters(), limit);
        };
        warnings.addAll(execution.warnings());
        return new AdminConversationalSearchResponse(
            interpretation.interpretedIntent(),
            interpretation.filters().toResponseMap(),
            execution.resultType(),
            execution.results(),
            warnings.stream().distinct().toList(),
            explanation(interpretation.entityScope(), interpretation.filters(), execution.results().size()),
            false,
            List.of()
        );
    }

    private SearchExecution searchPublications(ConversationalSearchFilters filters, int limit) {
        List<String> warnings = new ArrayList<>();
        Long unitId = resolveResearchUnitId(filters.unit(), warnings).orElse(null);
        Long researcherId = resolveResearcherId(filters.researcher(), warnings).orElse(null);
        PublicationType type = enumValue(PublicationType.class, filters.type()).orElse(null);
        PublicationStatus academicStatus = enumValue(PublicationStatus.class, filters.academicStatus()).orElse(null);
        String topic = normalized(filters.topic());
        String text = filters.topic() == null ? blankToNull(filters.textQuery()) : null;
        Specification<PublicationEntity> specification = PublicationSpecifications.matches(
            text,
            pattern(text),
            filters.yearFrom(),
            filters.yearTo(),
            type,
            academicStatus,
            unitId,
            researcherId,
            topic,
            pattern(topic),
            null
        ).and(PublicationSpecifications.visibleTo(VisibilityScope.ADMIN_ALL, null));
        if (filters.validationStatus() != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("validationStatus"), filters.validationStatus()));
        }
        specification = specification.and(publicationDataQualitySpecification(filters.dataQualityIssue(), warnings));
        List<Long> semanticIds = semanticPublicationIds(filters, limit, warnings);
        if (!semanticIds.isEmpty()) {
            specification = specification.and(PublicationSpecifications.hasIdIn(semanticIds));
        }

        Page<PublicationEntity> page = publicationRepository.findAll(
            specification,
            PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "publicationYear").and(Sort.by("title")))
        );
        Map<Long, List<String>> topicsByPublicationId = topicNamesByPublicationId(page.getContent().stream()
            .map(PublicationEntity::getId)
            .toList());
        return new SearchExecution(
            "PUBLICATIONS",
            page.getContent().stream()
                .map(publication -> publicationResult(publication, topicsByPublicationId.getOrDefault(publication.getId(), List.of())))
                .toList(),
            warnings
        );
    }

    private SearchExecution searchResearchers(ConversationalSearchFilters filters, int limit) {
        List<String> warnings = new ArrayList<>();
        Long unitId = resolveResearchUnitId(filters.unit(), warnings).orElse(null);
        String topic = normalized(filters.topic());
        String text = topic == null ? blankToNull(filters.textQuery()) : null;
        Page<ResearcherEntity> page = researcherRepository.search(
            text,
            pattern(text),
            unitId,
            null,
            filters.validationStatus(),
            topic,
            pattern(topic),
            PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "fullName"))
        );
        List<ResearcherEntity> researchers = page.getContent().stream()
            .filter(researcher -> !isIssue(filters, DataQualityIssueType.RESEARCHERS_WITHOUT_ORCID)
                || researcher.getOrcid() == null
                || researcher.getOrcid().isBlank())
            .toList();
        return new SearchExecution(
            "RESEARCHERS",
            researchers.stream().map(this::researcherResult).toList(),
            warnings
        );
    }

    private SearchExecution searchUnits(ConversationalSearchFilters filters, int limit) {
        ResearchUnitType type = enumValue(ResearchUnitType.class, filters.type()).orElse(null);
        String text = firstNonBlank(filters.unit(), filters.textQuery(), filters.topic());
        List<Map<String, Object>> results = researchUnitRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
            .stream()
            .filter(unit -> filters.validationStatus() == null || unit.getValidationStatus() == filters.validationStatus())
            .filter(unit -> type == null || unit.getType() == type)
            .filter(unit -> text == null || unitMatches(unit, text))
            .limit(limit)
            .map(this::unitResult)
            .toList();
        return new SearchExecution("UNITS", results, List.of());
    }

    private SearchExecution searchActivities(ConversationalSearchFilters filters, int limit) {
        Specification<EventParticipationEntity> specification = activitySpecification(filters);
        Page<EventParticipationEntity> page = eventParticipationRepository.findAll(
            specification,
            PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "participationDate").and(Sort.by(Sort.Direction.DESC, "createdAt")))
        );
        return new SearchExecution("ACTIVITIES", activityResults(page.getContent()), List.of());
    }

    private SearchExecution searchValidation(ConversationalSearchFilters filters, int limit) {
        Long unitId = resolveResearchUnitId(filters.unit(), new ArrayList<>()).orElse(null);
        Long researcherId = resolveResearcherId(filters.researcher(), new ArrayList<>()).orElse(null);
        ValidationEntityType entityType = validationEntityType(filters.dataQualityIssue()).orElse(null);
        PageResponse<ValidationItemResponse> page = validationInboxService.search(
            filters.validationStatus(),
            entityType,
            researcherId,
            unitId,
            null,
            null,
            filters.textQuery(),
            0,
            limit,
            "submittedAt,desc"
        );
        return new SearchExecution(
            "VALIDATION",
            page.content().stream().map(this::validationResult).toList(),
            List.of()
        );
    }

    private SearchExecution searchDataQuality(ConversationalSearchFilters filters, int limit) {
        if (!currentRoles().contains("ADMIN")) {
            return new SearchExecution(
                "DATA_QUALITY",
                List.of(),
                List.of("La calidad de datos solo esta disponible para usuarios ADMIN.")
            );
        }
        List<String> warnings = new ArrayList<>();
        DataQualityIssueType issueType = dataQualityIssueType(filters.dataQualityIssue(), warnings).orElse(null);
        DataQualityEntityType entityType = issueType == null ? null : dataQualityEntityType(issueType);
        PageResponse<DataQualityIssueResponse> page = dataQualityService.issues(
            issueType,
            null,
            entityType,
            filters.validationStatus(),
            0,
            limit
        );
        return new SearchExecution(
            "DATA_QUALITY",
            page.content().stream().map(this::dataQualityResult).toList(),
            warnings
        );
    }

    private Specification<PublicationEntity> publicationDataQualitySpecification(String issue, List<String> warnings) {
        if (issue == null || issue.isBlank()) {
            return Specification.allOf();
        }
        if (isIssue(issue, DataQualityIssueType.PUBLICATIONS_WITHOUT_DOI)) {
            return (root, query, criteriaBuilder) -> blankPredicate(root, criteriaBuilder, "doi");
        }
        if (isIssue(issue, DataQualityIssueType.PUBLICATIONS_WITHOUT_PUBLIC_SUMMARY)) {
            return (root, query, criteriaBuilder) -> blankPredicate(root, criteriaBuilder, "publicSummary");
        }
        if (isIssue(issue, DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT)) {
            return (root, query, criteriaBuilder) -> blankPredicate(root, criteriaBuilder, "abstractText");
        }
        if (isIssue(issue, DataQualityIssueType.PUBLICATIONS_WITHOUT_TOPICS)) {
            return (root, query, criteriaBuilder) -> {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<PublicationTopicEntity> topic = subquery.from(PublicationTopicEntity.class);
                subquery.select(topic.get("publicationId"));
                subquery.where(criteriaBuilder.equal(topic.get("publicationId"), root.get("id")));
                return criteriaBuilder.not(criteriaBuilder.exists(subquery));
            };
        }
        warnings.add("El problema de calidad solicitado no tiene filtro directo para publicaciones.");
        return Specification.allOf();
    }

    private Specification<EventParticipationEntity> activitySpecification(ConversationalSearchFilters filters) {
        String text = blankToNull(filters.textQuery());
        String unit = blankToNull(filters.unit());
        String researcher = blankToNull(filters.researcher());
        String type = blankToNull(filters.type());
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (text != null) {
                String textPattern = pattern(text);
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root, criteriaBuilder, "description"), textPattern),
                    criteriaBuilder.like(lowerCoalesced(root, criteriaBuilder, "participationTypeCode"), textPattern),
                    eventTextMatches(root, query, criteriaBuilder, textPattern),
                    unitTextMatches(root, query, criteriaBuilder, textPattern),
                    researcherTextMatches(root, query, criteriaBuilder, textPattern)
                ));
            }
            if (filters.validationStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("validationStatus"), filters.validationStatus()));
            }
            if (type != null) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("participationTypeCode")), pattern(type)));
            }
            if (unit != null) {
                predicates.add(unitTextMatches(root, query, criteriaBuilder, pattern(unit)));
            }
            if (researcher != null) {
                predicates.add(researcherTextMatches(root, query, criteriaBuilder, pattern(researcher)));
            }
            if (filters.yearFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("participationDate"), LocalDate.of(filters.yearFrom(), 1, 1)));
            }
            if (filters.yearTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("participationDate"), LocalDate.of(filters.yearTo(), 12, 31)));
            }
            if (MISSING_EVIDENCE.equalsIgnoreCase(filters.dataQualityIssue())) {
                predicates.add(blankPredicate(root, criteriaBuilder, "evidenceUrl"));
            }
            return predicates.isEmpty() ? criteriaBuilder.conjunction() : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Predicate eventTextMatches(
        Root<EventParticipationEntity> root,
        jakarta.persistence.criteria.CriteriaQuery<?> query,
        jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
        String textPattern
    ) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<ScientificEventEntity> event = subquery.from(ScientificEventEntity.class);
        subquery.select(event.get("id"));
        subquery.where(
            criteriaBuilder.equal(event.get("id"), root.get("eventId")),
            criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(event.get("name")), textPattern),
                criteriaBuilder.like(lowerCoalesced(event, criteriaBuilder, "description"), textPattern),
                criteriaBuilder.like(lowerCoalesced(event, criteriaBuilder, "eventTypeCode"), textPattern)
            )
        );
        return criteriaBuilder.exists(subquery);
    }

    private Predicate unitTextMatches(
        Root<EventParticipationEntity> root,
        jakarta.persistence.criteria.CriteriaQuery<?> query,
        jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
        String textPattern
    ) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<ResearchUnitEntity> unit = subquery.from(ResearchUnitEntity.class);
        subquery.select(unit.get("id"));
        subquery.where(
            criteriaBuilder.equal(unit.get("id"), root.get("researchUnitId")),
            criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(unit.get("name")), textPattern),
                criteriaBuilder.like(lowerCoalesced(unit, criteriaBuilder, "shortName"), textPattern)
            )
        );
        return criteriaBuilder.exists(subquery);
    }

    private Predicate researcherTextMatches(
        Root<EventParticipationEntity> root,
        jakarta.persistence.criteria.CriteriaQuery<?> query,
        jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
        String textPattern
    ) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<ResearcherEntity> researcher = subquery.from(ResearcherEntity.class);
        subquery.select(researcher.get("id"));
        subquery.where(
            criteriaBuilder.equal(researcher.get("id"), root.get("researcherId")),
            criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(researcher.get("fullName")), textPattern),
                criteriaBuilder.like(lowerCoalesced(researcher, criteriaBuilder, "displayName"), textPattern)
            )
        );
        return criteriaBuilder.exists(subquery);
    }

    private List<Long> semanticPublicationIds(ConversationalSearchFilters filters, int limit, List<String> warnings) {
        if (!filters.semanticQuery() || filters.textQuery() == null || filters.textQuery().isBlank()) {
            return List.of();
        }
        try {
            PublicationRetrievalResult result = publicationRetrievalService.retrieveBest(
                filters.textQuery(),
                new RetrievalOptions(limit, null, RetrievalMode.BALANCED),
                VisibilityScope.ADMIN_ALL,
                null
            );
            warnings.addAll(result.warnings());
            return result.publications().stream()
                .map(context -> context.publication().getId())
                .toList();
        } catch (RuntimeException exception) {
            warnings.add("No se pudo usar busqueda semantica; se aplicaron filtros estructurados y texto.");
            return List.of();
        }
    }

    private Map<String, Object> publicationResult(PublicationEntity publication, List<String> topics) {
        Map<String, Object> result = baseResult(publication.getId(), publication.getTitle());
        put(result, "year", publication.getPublicationYear());
        put(result, "type", publication.getType());
        put(result, "academicStatus", publication.getStatus());
        put(result, "doi", publication.getDoi());
        put(result, "source", publication.getSource());
        put(result, "validationStatus", publication.getValidationStatus());
        put(result, "topics", topics);
        return result;
    }

    private Map<String, Object> researcherResult(ResearcherEntity researcher) {
        Map<String, Object> result = baseResult(researcher.getId(), researcher.getFullName());
        put(result, "displayName", researcher.getDisplayName());
        put(result, "active", researcher.isActive());
        put(result, "validationStatus", researcher.getValidationStatus());
        return result;
    }

    private Map<String, Object> unitResult(ResearchUnitEntity unit) {
        Map<String, Object> result = baseResult(unit.getId(), unit.getName());
        put(result, "shortName", unit.getShortName());
        put(result, "type", unit.getType());
        put(result, "active", unit.isActive());
        put(result, "validationStatus", unit.getValidationStatus());
        return result;
    }

    private List<Map<String, Object>> activityResults(List<EventParticipationEntity> activities) {
        Map<Long, String> eventNames = namesById(
            scientificEventRepository.findAllById(activities.stream().map(EventParticipationEntity::getEventId).toList()),
            ScientificEventEntity::getId,
            ScientificEventEntity::getName
        );
        Map<Long, String> researcherNames = namesById(
            researcherRepository.findAllById(activities.stream().map(EventParticipationEntity::getResearcherId).toList()),
            ResearcherEntity::getId,
            ResearcherEntity::getFullName
        );
        Map<Long, String> unitNames = namesById(
            researchUnitRepository.findAllById(activities.stream()
                .map(EventParticipationEntity::getResearchUnitId)
                .filter(id -> id != null)
                .toList()),
            ResearchUnitEntity::getId,
            ResearchUnitEntity::getName
        );
        return activities.stream()
            .map(activity -> activityResult(activity, eventNames, researcherNames, unitNames))
            .toList();
    }

    private Map<String, Object> activityResult(
        EventParticipationEntity activity,
        Map<Long, String> eventNames,
        Map<Long, String> researcherNames,
        Map<Long, String> unitNames
    ) {
        Map<String, Object> result = baseResult(activity.getId(), activity.getTitle());
        put(result, "eventId", activity.getEventId());
        put(result, "eventName", eventNames.get(activity.getEventId()));
        put(result, "researcherId", activity.getResearcherId());
        put(result, "researcherName", researcherNames.get(activity.getResearcherId()));
        put(result, "researchUnitId", activity.getResearchUnitId());
        put(result, "researchUnitName", unitNames.get(activity.getResearchUnitId()));
        put(result, "type", activity.getParticipationTypeCode());
        put(result, "participationDate", activity.getParticipationDate());
        put(result, "evidencePresent", activity.getEvidenceUrl() != null && !activity.getEvidenceUrl().isBlank());
        put(result, "validationStatus", activity.getValidationStatus());
        return result;
    }

    private Map<String, Object> validationResult(ValidationItemResponse item) {
        Map<String, Object> result = baseResult(item.entityId(), item.title());
        put(result, "entityType", item.entityType());
        put(result, "subtitle", item.subtitle());
        put(result, "researcherName", item.researcherName());
        put(result, "researchUnitName", item.researchUnitName());
        put(result, "submittedBy", item.submittedBy());
        put(result, "submittedAt", item.submittedAt());
        put(result, "validationStatus", item.validationStatus());
        put(result, "summaryFields", item.summaryFields());
        put(result, "warnings", item.warnings());
        put(result, "dataQualityFlags", item.dataQualityFlags());
        return result;
    }

    private Map<String, Object> dataQualityResult(DataQualityIssueResponse issue) {
        Map<String, Object> result = baseResult(issue.entityId(), issue.title());
        put(result, "issueType", issue.issueType());
        put(result, "severity", issue.severity());
        put(result, "entityType", issue.entityType());
        put(result, "description", issue.description());
        put(result, "suggestedAction", issue.suggestedAction());
        return result;
    }

    private Map<Long, List<String>> topicNamesByPublicationId(Collection<Long> publicationIds) {
        if (publicationIds == null || publicationIds.isEmpty()) {
            return Map.of();
        }
        List<PublicationTopicEntity> links = publicationTopicRepository.findByPublicationIdIn(publicationIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<Long, TopicEntity> topicsById = topicRepository.findAllById(links.stream()
                .map(PublicationTopicEntity::getTopicId)
                .toList())
            .stream()
            .collect(Collectors.toMap(TopicEntity::getId, Function.identity(), (first, second) -> first));
        return links.stream()
            .filter(link -> topicsById.containsKey(link.getTopicId()))
            .collect(Collectors.groupingBy(
                PublicationTopicEntity::getPublicationId,
                LinkedHashMap::new,
                Collectors.mapping(link -> topicsById.get(link.getTopicId()).getName(), Collectors.toList())
            ));
    }

    private Optional<Long> resolveResearchUnitId(String unit, List<String> warnings) {
        String normalizedUnit = normalized(unit);
        if (normalizedUnit == null) {
            return Optional.empty();
        }
        List<ResearchUnitEntity> matches = researchUnitRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
            .stream()
            .filter(candidate -> unitMatches(candidate, normalizedUnit))
            .toList();
        if (matches.size() > 1) {
            warnings.add("Hay varias unidades coincidentes; se uso la primera coincidencia por nombre.");
        }
        return matches.stream().findFirst().map(ResearchUnitEntity::getId);
    }

    private Optional<Long> resolveResearcherId(String researcher, List<String> warnings) {
        String normalizedResearcher = normalized(researcher);
        if (normalizedResearcher == null) {
            return Optional.empty();
        }
        List<ResearcherEntity> matches = researcherRepository.findAll(Sort.by(Sort.Direction.ASC, "fullName"))
            .stream()
            .filter(candidate -> normalized(candidate.getFullName()).contains(normalizedResearcher)
                || normalized(candidate.getDisplayName()).contains(normalizedResearcher))
            .toList();
        if (matches.size() > 1) {
            warnings.add("Hay varios investigadores coincidentes; se uso la primera coincidencia por nombre.");
        }
        return matches.stream().findFirst().map(ResearcherEntity::getId);
    }

    private Optional<DataQualityIssueType> dataQualityIssueType(String issue, List<String> warnings) {
        if (issue == null || issue.isBlank()) {
            return Optional.empty();
        }
        if (MISSING_EVIDENCE.equalsIgnoreCase(issue)) {
            warnings.add("El filtro sin evidencia se aplica a actividades; no existe como incidencia global de calidad de datos.");
            return Optional.empty();
        }
        try {
            return Optional.of(DataQualityIssueType.valueOf(issue.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            warnings.add("Se ignoro un problema de calidad no permitido.");
            return Optional.empty();
        }
    }

    private Optional<ValidationEntityType> validationEntityType(String issue) {
        if (isIssue(issue, DataQualityIssueType.PUBLICATIONS_WITHOUT_DOI)
            || isIssue(issue, DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT)
            || isIssue(issue, DataQualityIssueType.PUBLICATIONS_WITHOUT_PUBLIC_SUMMARY)
            || isIssue(issue, DataQualityIssueType.PUBLICATIONS_WITHOUT_TOPICS)
            || isIssue(issue, DataQualityIssueType.DUPLICATE_PUBLICATION_CANDIDATES)) {
            return Optional.of(ValidationEntityType.PUBLICATION);
        }
        if (isIssue(issue, DataQualityIssueType.RESEARCHERS_WITHOUT_ORCID)) {
            return Optional.of(ValidationEntityType.RESEARCHER);
        }
        if (MISSING_EVIDENCE.equalsIgnoreCase(issue) || isIssue(issue, DataQualityIssueType.ACTIVITIES_PENDING_VALIDATION)) {
            return Optional.of(ValidationEntityType.EVENT_PARTICIPATION);
        }
        if (isIssue(issue, DataQualityIssueType.EXTERNAL_ORGANIZATION_DUPLICATE_CANDIDATES)) {
            return Optional.of(ValidationEntityType.RESEARCH_UNIT);
        }
        return Optional.empty();
    }

    private DataQualityEntityType dataQualityEntityType(DataQualityIssueType issueType) {
        return switch (issueType) {
            case PUBLICATIONS_WITHOUT_DOI,
                PUBLICATIONS_WITHOUT_ABSTRACT,
                PUBLICATIONS_WITHOUT_PUBLIC_SUMMARY,
                PUBLICATIONS_WITHOUT_TOPICS,
                PUBLICATION_TITLE_CASING_ISSUES,
                PUBLICATIONS_WITH_EXTERNAL_AUTHORS,
                DUPLICATE_PUBLICATION_CANDIDATES -> DataQualityEntityType.PUBLICATION;
            case RESEARCHERS_WITHOUT_ORCID -> DataQualityEntityType.RESEARCHER;
            case UNRESOLVED_EXTERNAL_AUTHORS -> DataQualityEntityType.PUBLICATION_AUTHOR;
            case ACTIVITIES_PENDING_VALIDATION -> DataQualityEntityType.EVENT_PARTICIPATION;
            case VENUES_WITHOUT_IDENTIFIER -> DataQualityEntityType.VENUE;
            case EVENTS_WITHOUT_DATES -> DataQualityEntityType.SCIENTIFIC_EVENT;
            case EXTERNAL_ORGANIZATION_DUPLICATE_CANDIDATES -> DataQualityEntityType.RESEARCH_UNIT;
            case DUPLICATE_TOPIC_CANDIDATES -> DataQualityEntityType.TOPIC;
        };
    }

    private Predicate blankPredicate(
        Root<?> root,
        jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
        String attribute
    ) {
        Expression<String> value = root.get(attribute);
        return criteriaBuilder.or(
            criteriaBuilder.isNull(value),
            criteriaBuilder.equal(criteriaBuilder.trim(value), "")
        );
    }

    private Expression<String> lowerCoalesced(
        Root<?> root,
        jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
        String attribute
    ) {
        return criteriaBuilder.lower(criteriaBuilder.coalesce(root.<String>get(attribute), ""));
    }

    private boolean unitMatches(ResearchUnitEntity unit, String text) {
        String normalizedText = normalized(text);
        return normalized(unit.getName()).contains(normalizedText)
            || normalized(unit.getShortName()).contains(normalizedText)
            || normalized(unit.getCity()).contains(normalizedText)
            || normalized(unit.getCountry()).contains(normalizedText);
    }

    private boolean isIssue(ConversationalSearchFilters filters, DataQualityIssueType issueType) {
        return isIssue(filters.dataQualityIssue(), issueType);
    }

    private boolean isIssue(String issue, DataQualityIssueType issueType) {
        return issue != null && issueType.name().equalsIgnoreCase(issue.trim());
    }

    private <E extends Enum<E>> Optional<E> enumValue(Class<E> enumType, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private <T> Map<Long, String> namesById(List<T> values, Function<T, Long> idExtractor, Function<T, String> nameExtractor) {
        return values.stream()
            .collect(Collectors.toMap(idExtractor, nameExtractor, (first, second) -> first, LinkedHashMap::new));
    }

    private Map<String, Object> baseResult(Long id, String title) {
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "id", id);
        put(result, "title", title);
        return result;
    }

    private void put(Map<String, Object> result, String key, Object value) {
        if (value != null) {
            result.put(key, value);
        }
    }

    private String explanation(ConversationalSearchEntityScope scope, ConversationalSearchFilters filters, int resultCount) {
        StringBuilder builder = new StringBuilder();
        builder.append("Se tradujo la pregunta a filtros estructurados permitidos y se ejecutaron consultas seguras del backend.");
        builder.append(" Ambito: ").append(scope.name()).append(".");
        if (!filters.toResponseMap().isEmpty()) {
            builder.append(" Filtros aplicados: ").append(filters.toResponseMap().keySet()).append(".");
        }
        builder.append(" Resultados devueltos: ").append(resultCount).append(".");
        return builder.toString();
    }

    private void ensureAdminOrValidator() {
        Set<String> roles = currentRoles();
        if (!roles.contains("ADMIN") && !roles.contains("VALIDATOR")) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "El asistente de busqueda conversacional requiere rol ADMIN o VALIDATOR."
            );
        }
    }

    private Set<String> currentRoles() {
        Set<String> roles = visibilityContext.currentRoles();
        return roles == null ? Set.of() : roles;
    }

    private int safeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(requestedLimit, 1), MAX_LIMIT);
    }

    private String pattern(String value) {
        String normalizedValue = normalized(value);
        return normalizedValue == null ? "%" : "%" + normalizedValue + "%";
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return java.text.Normalizer.normalize(value.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String first, String second, String third) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return third == null || third.isBlank() ? null : third;
    }

    private record SearchExecution(
        String resultType,
        List<Map<String, Object>> results,
        List<String> warnings
    ) {
    }
}
