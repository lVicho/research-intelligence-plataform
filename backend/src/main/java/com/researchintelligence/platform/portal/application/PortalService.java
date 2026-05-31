package com.researchintelligence.platform.portal.application;

import com.researchintelligence.platform.events.persistence.EventParticipationEntity;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.events.persistence.ScientificEventEntity;
import com.researchintelligence.platform.events.persistence.ScientificEventRepository;
import com.researchintelligence.platform.graph.api.GraphMetadata;
import com.researchintelligence.platform.graph.api.GraphResponse;
import com.researchintelligence.platform.graph.application.ResearchGraphService;
import com.researchintelligence.platform.portal.api.PortalActivitySummaryResponse;
import com.researchintelligence.platform.portal.api.PortalAffiliationResponse;
import com.researchintelligence.platform.portal.api.PortalCoauthorResponse;
import com.researchintelligence.platform.portal.api.PortalCollaborationSummaryResponse;
import com.researchintelligence.platform.portal.api.PortalCountResponse;
import com.researchintelligence.platform.portal.api.PortalGraphSummaryResponse;
import com.researchintelligence.platform.portal.api.PortalPageResponse;
import com.researchintelligence.platform.portal.api.PortalPublicationAuthorResponse;
import com.researchintelligence.platform.portal.api.PortalPublicationDetailResponse;
import com.researchintelligence.platform.portal.api.PortalPublicationLinkedResearcherResponse;
import com.researchintelligence.platform.portal.api.PortalPublicationLinkedUnitResponse;
import com.researchintelligence.platform.portal.api.PortalPublicationSummaryResponse;
import com.researchintelligence.platform.portal.api.PortalPublicationTopicResponse;
import com.researchintelligence.platform.portal.api.PortalResearchUnitDetailResponse;
import com.researchintelligence.platform.portal.api.PortalResearchUnitSummaryResponse;
import com.researchintelligence.platform.portal.api.PortalResearcherDetailResponse;
import com.researchintelligence.platform.portal.api.PortalResearcherSummaryResponse;
import com.researchintelligence.platform.portal.api.PortalSearchResponse;
import com.researchintelligence.platform.portal.api.PortalSummaryResponse;
import com.researchintelligence.platform.publications.api.PublicationAuthorResponse;
import com.researchintelligence.platform.publications.api.PublicationResponse;
import com.researchintelligence.platform.publications.api.PublicationSummaryResponse;
import com.researchintelligence.platform.publications.api.RelatedPublicationsResponse;
import com.researchintelligence.platform.publications.api.TopicResponse;
import com.researchintelligence.platform.publications.application.RelatedPublicationService;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.application.PublicationService;
import com.researchintelligence.platform.publications.persistence.PublisherEntity;
import com.researchintelligence.platform.publications.persistence.PublisherRepository;
import com.researchintelligence.platform.publications.persistence.VenueEntity;
import com.researchintelligence.platform.publications.persistence.VenueRepository;
import com.researchintelligence.platform.researchers.api.ResearcherAffiliationResponse;
import com.researchintelligence.platform.researchers.api.ResearcherCoauthorResponse;
import com.researchintelligence.platform.researchers.api.ResearcherResponse;
import com.researchintelligence.platform.researchers.api.ResearcherSummaryResponse;
import com.researchintelligence.platform.researchers.application.ResearcherService;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.api.ResearchUnitListResponse;
import com.researchintelligence.platform.researchunits.api.ResearchUnitResponse;
import com.researchintelligence.platform.researchunits.application.ResearchUnitService;
import com.researchintelligence.platform.researchunits.domain.OrganizationScope;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PortalService {

    private static final VisibilityScope PUBLIC_SCOPE = VisibilityScope.PUBLIC_VALIDATED;
    private static final boolean VALIDATION_FILTER_APPLIED = true;

    private final PublicationService publicationService;
    private final RelatedPublicationService relatedPublicationService;
    private final ResearcherService researcherService;
    private final ResearchUnitService researchUnitService;
    private final PublicationRepository publicationRepository;
    private final VenueRepository venueRepository;
    private final PublisherRepository publisherRepository;
    private final ResearcherRepository researcherRepository;
    private final ResearcherAffiliationRepository affiliationRepository;
    private final ResearchUnitRepository researchUnitRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final ScientificEventRepository eventRepository;
    private final ResearchGraphService graphService;

    public PortalService(
        PublicationService publicationService,
        RelatedPublicationService relatedPublicationService,
        ResearcherService researcherService,
        ResearchUnitService researchUnitService,
        PublicationRepository publicationRepository,
        VenueRepository venueRepository,
        PublisherRepository publisherRepository,
        ResearcherRepository researcherRepository,
        ResearcherAffiliationRepository affiliationRepository,
        ResearchUnitRepository researchUnitRepository,
        EventParticipationRepository eventParticipationRepository,
        ScientificEventRepository eventRepository,
        ResearchGraphService graphService
    ) {
        this.publicationService = publicationService;
        this.relatedPublicationService = relatedPublicationService;
        this.researcherService = researcherService;
        this.researchUnitService = researchUnitService;
        this.publicationRepository = publicationRepository;
        this.venueRepository = venueRepository;
        this.publisherRepository = publisherRepository;
        this.researcherRepository = researcherRepository;
        this.affiliationRepository = affiliationRepository;
        this.researchUnitRepository = researchUnitRepository;
        this.eventParticipationRepository = eventParticipationRepository;
        this.eventRepository = eventRepository;
        this.graphService = graphService;
    }

    public PortalSummaryResponse summary() {
        long totalValidatedPublications = publicationRepository.countByValidationStatus(ValidationStatus.VALIDATED);
        long totalValidatedEventActivities = eventParticipationRepository.countPublicValidated(ValidationStatus.VALIDATED);
        PageResponse<PublicationSummaryResponse> recentPublications = publicationService.searchPublicValidated(
            0,
            5,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "createdAt",
            "desc"
        );
        PageResponse<ResearchUnitListResponse> featuredUnits = researchUnitService.searchPortalVisibleValidated(0, 5, null, null);
        return new PortalSummaryResponse(
            totalValidatedPublications,
            totalValidatedPublications + totalValidatedEventActivities,
            researcherRepository.countActivePortalVisibleValidated(ValidationStatus.VALIDATED, LocalDate.now()),
            researchUnitService.countPortalVisibleValidated(),
            countRows(publicationRepository.findTopValidatedTopicsByPublicationCount(ValidationStatus.VALIDATED, PageRequest.of(0, 10))),
            recentPublications.content().stream().map(this::toPortalPublication).toList(),
            featuredUnits.content().stream().map(this::toPortalResearchUnit).toList(),
            PUBLIC_SCOPE,
            VALIDATION_FILTER_APPLIED
        );
    }

    public PortalPageResponse<PortalResearchUnitSummaryResponse> researchUnits(
        int page,
        int size,
        String text,
        ResearchUnitType type
    ) {
        PageResponse<ResearchUnitListResponse> units = researchUnitService.searchPortalVisibleValidated(page, size, text, type);
        return toPortalPage(units, this::toPortalResearchUnit);
    }

    public PortalResearchUnitDetailResponse researchUnitDetail(Long id) {
        ResearchUnitResponse unit = researchUnitService.findPortalVisibleValidatedById(id);
        PageResponse<ResearcherSummaryResponse> researchers = researcherService.searchPortalVisibleValidated(0, 100, null, id, null);
        PageResponse<PublicationSummaryResponse> publications = publicationService.searchPublicValidated(
            0,
            25,
            null,
            null,
            null,
            null,
            null,
            id,
            null,
            null,
            "year",
            "desc"
        );
        List<PortalActivitySummaryResponse> activities = activitiesForResearchUnit(id, 25);
        return new PortalResearchUnitDetailResponse(
            toPortalResearchUnit(unit),
            researchers.content().stream().map(this::toPortalResearcher).toList(),
            publications.content().stream().map(this::toPortalPublication).toList(),
            activities,
            countRows(publicationRepository.findTopValidatedTopicsByResearchUnit(id, ValidationStatus.VALIDATED, PageRequest.of(0, 10))),
            researchUnitService.findPortalVisibleChildUnits(id).stream().map(this::toPortalResearchUnit).toList(),
            new PortalCollaborationSummaryResponse(researchers.totalElements(), publications.totalElements(), activities.size()),
            PUBLIC_SCOPE,
            VALIDATION_FILTER_APPLIED
        );
    }

    public PortalPageResponse<PortalResearcherSummaryResponse> researchers(
        int page,
        int size,
        String text,
        Long researchUnitId,
        String topic
    ) {
        PageResponse<ResearcherSummaryResponse> researchers = researcherService.searchPortalVisibleValidated(page, size, text, researchUnitId, topic);
        return toPortalPage(researchers, this::toPortalResearcher);
    }

    public PortalResearcherDetailResponse researcherDetail(Long id) {
        ResearcherResponse researcher = researcherService.findPortalVisibleValidatedById(id);
        List<PortalActivitySummaryResponse> activities = activitiesForResearcher(id, 25);
        return new PortalResearcherDetailResponse(
            researcher.id(),
            researcher.fullName(),
            researcher.displayName(),
            researcher.email(),
            researcher.orcid(),
            researcher.active(),
            researcher.affiliations().stream().map(this::toPortalAffiliation).toList(),
            researcher.authoredPublications().stream().map(this::toPortalPublication).toList(),
            activities,
            countRows(publicationRepository.findTopValidatedTopicsByResearcher(id, ValidationStatus.VALIDATED, PageRequest.of(0, 10))),
            researcher.coauthors().stream().map(this::toPortalCoauthor).toList(),
            graphSummary(id),
            PUBLIC_SCOPE,
            VALIDATION_FILTER_APPLIED
        );
    }

    public PortalPageResponse<PortalPublicationSummaryResponse> publications(
        int page,
        int size,
        String text,
        Integer yearFrom,
        Integer yearTo,
        PublicationType type,
        PublicationStatus status,
        Long researchUnitId,
        Long researcherId,
        String topic,
        String sortBy,
        String sortDirection
    ) {
        PageResponse<PublicationSummaryResponse> publications = publicationService.searchPublicValidated(
            page,
            size,
            text,
            yearFrom,
            yearTo,
            type,
            status,
            researchUnitId,
            researcherId,
            topic,
            sortBy,
            sortDirection
        );
        return toPortalPage(publications, this::toPortalPublication);
    }

    public PortalPublicationDetailResponse publicationDetail(Long id) {
        PublicationResponse publication = publicationService.findPublicValidatedById(id);
        PortalPublicationLinkData linkData = portalPublicationLinkData(publication.authors());
        RelatedPublicationsResponse relatedPublications = relatedPublicationService.findRelated(id, 5, null, false);
        return new PortalPublicationDetailResponse(
            publication.id(),
            publication.title(),
            publication.abstractText(),
            publication.publicSummary(),
            publication.year(),
            publication.publicationDate(),
            publication.type(),
            publication.status(),
            publication.doi(),
            publication.source(),
            publication.sourceDetail(),
            publication.url(),
            publication.venueId(),
            venueName(publication.venueId()),
            publication.publisherId(),
            publisherName(publication.publisherId()),
            publication.isbn(),
            publication.issn(),
            publication.languageCode(),
            publication.authors().stream().map(author -> toPortalAuthor(author, linkData.publicResearcherIds())).toList(),
            linkData.researchers(),
            linkData.units(),
            externalOrganizations(publication.authors()),
            publication.topics().stream().map(this::toPortalTopic).toList(),
            relatedPublications.relatedPublications().stream()
                .map(related -> toPortalPublication(related.publication()))
                .toList(),
            relatedPublications.warnings(),
            true,
            PUBLIC_SCOPE,
            VALIDATION_FILTER_APPLIED
        );
    }

    public PortalSearchResponse search(String query, int page, int size) {
        PageResponse<PublicationSummaryResponse> publications = publicationService.searchPublicValidated(
            page,
            size,
            query,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "year",
            "desc"
        );
        int sideSize = Math.min(Math.max(size, 1), 25);
        PageResponse<ResearcherSummaryResponse> researchers = researcherService.searchPortalVisibleValidated(0, sideSize, query, null, null);
        PageResponse<ResearchUnitListResponse> units = researchUnitService.searchPortalVisibleValidated(0, sideSize, query, null);
        return new PortalSearchResponse(
            query,
            toPortalPage(publications, this::toPortalPublication),
            researchers.content().stream().map(this::toPortalResearcher).toList(),
            units.content().stream().map(this::toPortalResearchUnit).toList(),
            PUBLIC_SCOPE,
            VALIDATION_FILTER_APPLIED
        );
    }

    private List<PortalActivitySummaryResponse> activitiesForResearcher(Long researcherId, int limit) {
        return toPortalActivities(eventParticipationRepository.findPublicValidatedByResearcherId(
            researcherId,
            ValidationStatus.VALIDATED,
            PageRequest.of(0, Math.max(limit, 1))
        ));
    }

    private List<PortalActivitySummaryResponse> activitiesForResearchUnit(Long researchUnitId, int limit) {
        return toPortalActivities(eventParticipationRepository.findPublicValidatedByResearchUnitId(
            researchUnitId,
            ValidationStatus.VALIDATED,
            PageRequest.of(0, Math.max(limit, 1))
        ));
    }

    private List<PortalActivitySummaryResponse> toPortalActivities(List<EventParticipationEntity> activities) {
        if (activities.isEmpty()) {
            return List.of();
        }
        Map<Long, ScientificEventEntity> eventsById = entitiesById(eventRepository.findAllById(activities.stream()
            .map(EventParticipationEntity::getEventId)
            .toList())
            .stream()
            .filter(event -> event.getValidationStatus() == ValidationStatus.VALIDATED)
            .toList(), ScientificEventEntity::getId);
        Map<Long, ResearcherEntity> researchersById = entitiesById(researcherRepository.findAllById(activities.stream()
            .map(EventParticipationEntity::getResearcherId)
            .toList())
            .stream()
            .filter(researcher -> researcher.getValidationStatus() == ValidationStatus.VALIDATED)
            .toList(), ResearcherEntity::getId);
        Map<Long, ResearchUnitEntity> unitsById = entitiesById(researchUnitRepository.findAllById(activities.stream()
            .map(EventParticipationEntity::getResearchUnitId)
            .filter(unitId -> unitId != null)
            .toList())
            .stream()
            .filter(unit -> unit.getValidationStatus() == ValidationStatus.VALIDATED)
            .toList(), ResearchUnitEntity::getId);
        Map<Long, PublicationEntity> publicationsById = entitiesById(publicationRepository.findAllById(activities.stream()
            .map(EventParticipationEntity::getRelatedPublicationId)
            .filter(publicationId -> publicationId != null)
            .toList())
            .stream()
            .filter(publication -> publication.getValidationStatus() == ValidationStatus.VALIDATED)
            .toList(), PublicationEntity::getId);
        return activities.stream()
            .map(activity -> toPortalActivity(activity, eventsById, researchersById, unitsById, publicationsById))
            .toList();
    }

    private PortalActivitySummaryResponse toPortalActivity(
        EventParticipationEntity activity,
        Map<Long, ScientificEventEntity> eventsById,
        Map<Long, ResearcherEntity> researchersById,
        Map<Long, ResearchUnitEntity> unitsById,
        Map<Long, PublicationEntity> publicationsById
    ) {
        ScientificEventEntity event = eventsById.get(activity.getEventId());
        ResearcherEntity researcher = researchersById.get(activity.getResearcherId());
        ResearchUnitEntity unit = activity.getResearchUnitId() == null ? null : unitsById.get(activity.getResearchUnitId());
        PublicationEntity relatedPublication = activity.getRelatedPublicationId() == null ? null : publicationsById.get(activity.getRelatedPublicationId());
        Long relatedPublicationId = relatedPublication == null ? null : relatedPublication.getId();
        return new PortalActivitySummaryResponse(
            activity.getId(),
            activity.getEventId(),
            event == null ? null : event.getName(),
            activity.getResearcherId(),
            researcher == null ? null : researcher.getFullName(),
            activity.getResearchUnitId(),
            unit == null ? null : unit.getName(),
            activity.getParticipationTypeCode(),
            activity.getTitle(),
            activity.getDescription(),
            activity.getParticipationDate(),
            relatedPublicationId,
            relatedPublication == null ? null : relatedPublication.getTitle()
        );
    }

    private PortalGraphSummaryResponse graphSummary(Long researcherId) {
        GraphResponse graph = graphService.researcherGraph(
            researcherId,
            null,
            true,
            true,
            true,
            true,
            false,
            10,
            10,
            10,
            false
        );
        GraphMetadata metadata = graph.metadata();
        return new PortalGraphSummaryResponse(
            metadata.totalNodes(),
            metadata.totalEdges(),
            metadata.displayedNodes(),
            metadata.displayedEdges(),
            metadata.truncated(),
            metadata.visibilityScope(),
            metadata.validationFilterApplied()
        );
    }

    private <T, R> PortalPageResponse<R> toPortalPage(PageResponse<T> page, Function<T, R> mapper) {
        return new PortalPageResponse<>(
            page.content().stream().map(mapper).toList(),
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages(),
            page.last(),
            PUBLIC_SCOPE,
            VALIDATION_FILTER_APPLIED
        );
    }

    private PortalPublicationSummaryResponse toPortalPublication(PublicationSummaryResponse publication) {
        return new PortalPublicationSummaryResponse(
            publication.id(),
            publication.title(),
            publication.year(),
            publication.type(),
            publication.status(),
            publication.doi(),
            publication.source(),
            publication.venueId(),
            publication.publisherId(),
            publication.isbn(),
            publication.issn(),
            publication.languageCode(),
            publication.createdAt(),
            publication.topics()
        );
    }

    private PortalPublicationAuthorResponse toPortalAuthor(
        PublicationAuthorResponse author,
        Set<Long> publicResearcherIds
    ) {
        boolean internal = author.researcherId() != null;
        String name = internal ? author.researcherName() : author.externalAuthorName();
        return new PortalPublicationAuthorResponse(
            author.id(),
            author.researcherId(),
            name,
            author.externalAffiliation(),
            author.authorOrder(),
            author.correspondingAuthor(),
            internal,
            internal && publicResearcherIds.contains(author.researcherId())
        );
    }

    private PortalPublicationTopicResponse toPortalTopic(TopicResponse topic) {
        return new PortalPublicationTopicResponse(topic.id(), topic.name(), topic.normalizedName());
    }

    private String venueName(Long venueId) {
        if (venueId == null) {
            return null;
        }
        return venueRepository.findById(venueId)
            .filter(venue -> venue.getValidationStatus() == ValidationStatus.VALIDATED)
            .map(VenueEntity::getName)
            .orElse(null);
    }

    private String publisherName(Long publisherId) {
        if (publisherId == null) {
            return null;
        }
        return publisherRepository.findById(publisherId)
            .filter(PublisherEntity::isActive)
            .map(PublisherEntity::getName)
            .orElse(null);
    }

    private List<String> externalOrganizations(List<PublicationAuthorResponse> authors) {
        return authors.stream()
            .map(PublicationAuthorResponse::externalAffiliation)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList();
    }

    private PortalPublicationLinkData portalPublicationLinkData(List<PublicationAuthorResponse> authors) {
        List<Long> researcherIds = authors.stream()
            .map(PublicationAuthorResponse::researcherId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        if (researcherIds.isEmpty()) {
            return PortalPublicationLinkData.empty();
        }

        Map<Long, ResearcherEntity> researchersById = researcherRepository.findAllById(researcherIds).stream()
            .filter(researcher -> researcher.isActive() && researcher.getValidationStatus() == ValidationStatus.VALIDATED)
            .collect(Collectors.toMap(
                ResearcherEntity::getId,
                Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new
            ));
        if (researchersById.isEmpty()) {
            return PortalPublicationLinkData.empty();
        }

        List<ResearcherAffiliationEntity> primaryAffiliations = affiliationRepository
            .findCurrentPrimaryByResearcherIds(researchersById.keySet(), LocalDate.now())
            .stream()
            .filter(affiliation -> affiliation.getValidationStatus() == ValidationStatus.VALIDATED)
            .toList();
        Map<Long, ResearchUnitEntity> unitsById = portalVisibleUnits(primaryAffiliations);
        Map<Long, String> primaryAffiliationNames = new LinkedHashMap<>();
        Set<Long> publicResearcherIds = new LinkedHashSet<>();
        Set<Long> linkedUnitIds = new LinkedHashSet<>();

        for (ResearcherAffiliationEntity affiliation : primaryAffiliations) {
            ResearchUnitEntity unit = unitsById.get(affiliation.getResearchUnitId());
            if (unit == null) {
                continue;
            }
            publicResearcherIds.add(affiliation.getResearcherId());
            primaryAffiliationNames.putIfAbsent(affiliation.getResearcherId(), unit.getName());
            linkedUnitIds.add(unit.getId());
        }

        List<PortalPublicationLinkedResearcherResponse> linkedResearchers = researcherIds.stream()
            .filter(publicResearcherIds::contains)
            .map(researchersById::get)
            .filter(researcher -> researcher != null)
            .map(researcher -> new PortalPublicationLinkedResearcherResponse(
                researcher.getId(),
                researcher.getFullName(),
                researcher.getDisplayName(),
                primaryAffiliationNames.get(researcher.getId())
            ))
            .toList();
        List<PortalPublicationLinkedUnitResponse> linkedUnits = linkedUnitIds.stream()
            .map(unitsById::get)
            .filter(unit -> unit != null)
            .map(unit -> new PortalPublicationLinkedUnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getShortName(),
                unit.getType()
            ))
            .toList();

        return new PortalPublicationLinkData(publicResearcherIds, linkedResearchers, linkedUnits);
    }

    private Map<Long, ResearchUnitEntity> portalVisibleUnits(List<ResearcherAffiliationEntity> affiliations) {
        List<Long> unitIds = affiliations.stream()
            .map(ResearcherAffiliationEntity::getResearchUnitId)
            .distinct()
            .toList();
        if (unitIds.isEmpty()) {
            return Map.of();
        }
        return researchUnitRepository.findAllById(unitIds).stream()
            .filter(unit -> unit.isActive())
            .filter(unit -> unit.isVisibleInPortal())
            .filter(unit -> unit.getOrganizationScope() == OrganizationScope.INTERNAL)
            .filter(unit -> unit.getValidationStatus() == ValidationStatus.VALIDATED)
            .collect(Collectors.toMap(
                ResearchUnitEntity::getId,
                Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new
            ));
    }

    private PortalResearcherSummaryResponse toPortalResearcher(ResearcherSummaryResponse researcher) {
        return new PortalResearcherSummaryResponse(
            researcher.id(),
            researcher.fullName(),
            researcher.displayName(),
            researcher.email(),
            researcher.orcid(),
            researcher.active(),
            researcher.primaryAffiliationName()
        );
    }

    private PortalResearchUnitSummaryResponse toPortalResearchUnit(ResearchUnitListResponse unit) {
        return new PortalResearchUnitSummaryResponse(
            unit.id(),
            unit.name(),
            unit.shortName(),
            unit.type(),
            unit.parentId(),
            unit.country(),
            unit.city(),
            unit.website(),
            unit.active()
        );
    }

    private PortalResearchUnitSummaryResponse toPortalResearchUnit(ResearchUnitResponse unit) {
        return new PortalResearchUnitSummaryResponse(
            unit.id(),
            unit.name(),
            unit.shortName(),
            unit.type(),
            unit.parentId(),
            unit.country(),
            unit.city(),
            unit.website(),
            unit.active()
        );
    }

    private PortalAffiliationResponse toPortalAffiliation(ResearcherAffiliationResponse affiliation) {
        return new PortalAffiliationResponse(
            affiliation.id(),
            affiliation.researcherId(),
            affiliation.researchUnitId(),
            affiliation.researchUnitName(),
            affiliation.role(),
            affiliation.affiliationType(),
            affiliation.startDate(),
            affiliation.endDate(),
            affiliation.primaryAffiliation(),
            affiliation.current()
        );
    }

    private PortalCoauthorResponse toPortalCoauthor(ResearcherCoauthorResponse coauthor) {
        return new PortalCoauthorResponse(
            coauthor.researcherId(),
            coauthor.name(),
            coauthor.internal(),
            coauthor.sharedPublicationCount()
        );
    }

    private List<PortalCountResponse> countRows(List<Object[]> rows) {
        return rows.stream()
            .map(row -> new PortalCountResponse((Long) row[0], String.valueOf(row[1]), (Long) row[2]))
            .toList();
    }

    private <T, ID> Map<ID, T> entitiesById(Collection<T> entities, Function<T, ID> idMapper) {
        return entities.stream().collect(Collectors.toMap(idMapper, Function.identity(), (first, second) -> first));
    }

    private record PortalPublicationLinkData(
        Set<Long> publicResearcherIds,
        List<PortalPublicationLinkedResearcherResponse> researchers,
        List<PortalPublicationLinkedUnitResponse> units
    ) {
        private static PortalPublicationLinkData empty() {
            return new PortalPublicationLinkData(Set.of(), List.of(), List.of());
        }
    }
}
