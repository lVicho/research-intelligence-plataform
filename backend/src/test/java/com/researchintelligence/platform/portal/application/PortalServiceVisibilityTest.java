package com.researchintelligence.platform.portal.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.UserRepository;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.events.persistence.EventParticipationEntity;
import com.researchintelligence.platform.events.persistence.ScientificEventRepository;
import com.researchintelligence.platform.events.persistence.ScientificEventEntity;
import com.researchintelligence.platform.graph.api.GraphMetadata;
import com.researchintelligence.platform.graph.api.GraphResponse;
import com.researchintelligence.platform.graph.application.ResearchGraphService;
import com.researchintelligence.platform.portal.api.PortalPublicationSummaryResponse;
import com.researchintelligence.platform.portal.api.PortalPublicationDetailResponse;
import com.researchintelligence.platform.portal.api.PortalPageResponse;
import com.researchintelligence.platform.portal.api.PortalResearchUnitDetailResponse;
import com.researchintelligence.platform.portal.api.PortalResearchUnitSummaryResponse;
import com.researchintelligence.platform.portal.api.PortalResearcherDetailResponse;
import com.researchintelligence.platform.portal.api.PortalSearchResponse;
import com.researchintelligence.platform.publications.application.PublicationService;
import com.researchintelligence.platform.publications.application.RelatedPublicationService;
import com.researchintelligence.platform.publications.api.RelatedPublicationsResponse;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.PublisherRepository;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.publications.persistence.VenueRepository;
import com.researchintelligence.platform.researchunits.domain.OrganizationScope;
import com.researchintelligence.platform.researchers.application.ResearcherService;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.application.ResearchUnitService;
import com.researchintelligence.platform.researchunits.application.ResearchUnitTreeBuilder;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class PortalServiceVisibilityTest {

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private RelatedPublicationService relatedPublicationService;

    @Mock
    private PublicationAuthorRepository authorRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private PublicationTopicRepository publicationTopicRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private PublisherRepository publisherRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ResearcherRepository researcherRepository;

    @Mock
    private ResearcherAffiliationRepository affiliationRepository;

    @Mock
    private ResearchUnitRepository researchUnitRepository;

    @Mock
    private EventParticipationRepository eventParticipationRepository;

    @Mock
    private ScientificEventRepository eventRepository;

    @Mock
    private VisibilityContext visibilityContext;

    @Mock
    private ActivityAuditService auditService;

    @Mock
    private ResearchGraphService graphService;

    private PortalService service;
    private ResearchUnitService researchUnitService;

    @BeforeEach
    void setUp() {
        PublicationService publicationService = new PublicationService(
            publicationRepository,
            authorRepository,
            topicRepository,
            publicationTopicRepository,
            researcherRepository,
            venueRepository,
            publisherRepository,
            userRepository,
            visibilityContext,
            auditService
        );
        ResearcherService researcherService = new ResearcherService(
            researcherRepository,
            affiliationRepository,
            researchUnitRepository,
            publicationRepository,
            authorRepository,
            publicationTopicRepository,
            topicRepository,
            userRepository,
            visibilityContext,
            auditService
        );
        researchUnitService = new ResearchUnitService(
            researchUnitRepository,
            new ResearchUnitTreeBuilder(),
            userRepository,
            visibilityContext,
            auditService
        );
        service = new PortalService(
            publicationService,
            relatedPublicationService,
            researcherService,
            researchUnitService,
            publicationRepository,
            venueRepository,
            publisherRepository,
            researcherRepository,
            affiliationRepository,
            researchUnitRepository,
            eventParticipationRepository,
            eventRepository,
            graphService
        );

        lenient().when(publicationTopicRepository.findByPublicationIdIn(any())).thenReturn(List.of());
        lenient().when(publicationTopicRepository.findByPublicationId(any())).thenReturn(List.of());
        lenient().when(topicRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(visibilityContext.linkedResearcherId()).thenReturn(Optional.empty());
        lenient().when(visibilityContext.currentRoles()).thenReturn(Set.of());
        lenient().when(authorRepository.findInternalCoauthorsByResearcherId(any(), any())).thenReturn(List.of());
        lenient().when(authorRepository.findExternalCoauthorsByResearcherId(any(), any())).thenReturn(List.of());
        lenient().when(affiliationRepository.findByResearcherIdOrderByPrimaryAffiliationDescStartDateDescIdAsc(any())).thenReturn(List.of());
        lenient().when(affiliationRepository.countCurrentPrimaryAffiliationsVisibleInPortal(any(), eq(ValidationStatus.VALIDATED), any()))
            .thenReturn(1L);
        lenient().when(eventParticipationRepository.findPublicValidatedByResearcherId(any(), eq(ValidationStatus.VALIDATED), any(Pageable.class))).thenReturn(List.of());
        lenient().when(eventParticipationRepository.findPublicValidatedByResearchUnitId(any(), eq(ValidationStatus.VALIDATED), any(Pageable.class))).thenReturn(List.of());
        lenient().when(publicationRepository.findTopValidatedTopicsByResearcher(any(), eq(ValidationStatus.VALIDATED), any(Pageable.class))).thenReturn(List.of());
        lenient().when(publicationRepository.findTopValidatedTopicsByResearchUnit(any(), eq(ValidationStatus.VALIDATED), any(Pageable.class))).thenReturn(List.of());
        lenient().when(researchUnitRepository.findByParentIdAndValidationStatusOrderByNameAsc(any(), eq(ValidationStatus.VALIDATED))).thenReturn(List.of());
        lenient().when(researchUnitRepository.countPublicValidatedById(any(), eq(ValidationStatus.VALIDATED))).thenReturn(1L);
        lenient().when(researcherRepository.search(
            nullable(String.class),
            anyString(),
            nullable(Long.class),
            nullable(Boolean.class),
            nullable(ValidationStatus.class),
            nullable(String.class),
            anyString(),
            any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        lenient().when(researcherRepository.searchPortalVisible(
            nullable(String.class),
            anyString(),
            nullable(Long.class),
            nullable(Boolean.class),
            nullable(ValidationStatus.class),
            nullable(String.class),
            anyString(),
            any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        lenient().when(researchUnitRepository.searchPortalVisibleValidated(
            nullable(String.class),
            anyString(),
            nullable(ResearchUnitType.class),
            eq(ValidationStatus.VALIDATED),
            eq(OrganizationScope.INTERNAL),
            any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        lenient().when(researchUnitRepository.countPortalVisibleValidated(eq(ValidationStatus.VALIDATED), eq(OrganizationScope.INTERNAL))).thenReturn(0L);
        lenient().when(researchUnitRepository.countPortalVisibleValidatedById(any(), eq(ValidationStatus.VALIDATED), eq(OrganizationScope.INTERNAL))).thenReturn(1L);
        lenient().when(researcherRepository.countActivePortalVisibleValidated(eq(ValidationStatus.VALIDATED), any())).thenReturn(0L);
        lenient().when(graphService.researcherGraph(
            eq(7L),
            isNull(),
            eq(true),
            eq(true),
            eq(true),
            eq(true),
            eq(false),
            eq(10),
            eq(10),
            eq(10),
            eq(false)
        )).thenReturn(new GraphResponse(List.of(), List.of(), new GraphMetadata(0, 0, 0, 0, false, "PUBLIC_VALIDATED", true), List.of()));
    }

    @Test
    void publicPublicationDetailReturnsValidatedPublication() {
        PublicationEntity publication = publication(50L, "Validated detail", ValidationStatus.VALIDATED);
        when(publicationRepository.findOne(anySpecification())).thenReturn(Optional.of(publication));
        when(authorRepository.findByPublicationIdOrderByAuthorOrderAsc(50L)).thenReturn(List.of());
        when(relatedPublicationService.findRelated(50L, 5, null, false)).thenReturn(relatedPublications(50L));

        PortalPublicationDetailResponse result = service.publicationDetail(50L);

        assertEquals(50L, result.id());
        assertEquals("Validated detail", result.title());
        assertEquals(VisibilityScope.PUBLIC_VALIDATED, result.visibilityScope());
        assertEquals(true, result.validationFilterApplied());
        assertEquals(true, result.explanationAvailable());
        verify(relatedPublicationService).findRelated(50L, 5, null, false);
    }

    @Test
    void publicPublicationDetailRejectsNonValidatedPublication() {
        when(publicationRepository.findOne(anySpecification())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.publicationDetail(51L));
    }

    @Test
    void publicPortalPublicationEndpointExcludesPendingAndRejected() {
        stubPublicationSearch(List.of(
            publication(1L, "Validated", ValidationStatus.VALIDATED),
            publication(2L, "Pending", ValidationStatus.PENDING_VALIDATION),
            publication(3L, "Rejected", ValidationStatus.REJECTED)
        ));

        PortalPageResponse<PortalPublicationSummaryResponse> result = service.publications(
            0,
            20,
            null,
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

        assertEquals(List.of("Validated"), result.content().stream().map(PortalPublicationSummaryResponse::title).toList());
    }

    @Test
    void publicResearcherDetailExcludesNonValidatedActivities() {
        ResearcherEntity researcher = researcher(7L, "Lucia Herrera", ValidationStatus.VALIDATED);
        when(researcherRepository.findById(7L)).thenReturn(Optional.of(researcher));
        when(publicationRepository.findAuthoredByResearcherId(7L)).thenReturn(List.of(
            publication(10L, "Validated activity", ValidationStatus.VALIDATED),
            publication(11L, "Pending activity", ValidationStatus.PENDING_VALIDATION),
            publication(12L, "Rejected activity", ValidationStatus.REJECTED)
        ));

        PortalResearcherDetailResponse result = service.researcherDetail(7L);

        assertEquals(List.of("Validated activity"), result.publications().stream().map(PortalPublicationSummaryResponse::title).toList());
    }

    @Test
    void publicResearcherActivitiesSuppressPendingRelatedPublication() {
        ResearcherEntity researcher = researcher(7L, "Lucia Herrera", ValidationStatus.VALIDATED);
        EventParticipationEntity activity = eventParticipation(70L, 9L, 7L, 99L);
        ScientificEventEntity event = scientificEvent(9L, "Validated conference", ValidationStatus.VALIDATED);
        PublicationEntity pendingPublication = publication(99L, "Pending related publication", ValidationStatus.PENDING_VALIDATION);
        when(researcherRepository.findById(7L)).thenReturn(Optional.of(researcher));
        when(publicationRepository.findAuthoredByResearcherId(7L)).thenReturn(List.of());
        when(eventParticipationRepository.findPublicValidatedByResearcherId(eq(7L), eq(ValidationStatus.VALIDATED), any(Pageable.class))).thenReturn(List.of(activity));
        when(eventRepository.findAllById(List.of(9L))).thenReturn(List.of(event));
        when(researcherRepository.findAllById(List.of(7L))).thenReturn(List.of(researcher));
        when(researchUnitRepository.findAllById(List.of())).thenReturn(List.of());
        when(publicationRepository.findAllById(List.of(99L))).thenReturn(List.of(pendingPublication));

        PortalResearcherDetailResponse result = service.researcherDetail(7L);

        assertEquals(1, result.activities().size());
        assertNull(result.activities().getFirst().relatedPublicationId());
        assertNull(result.activities().getFirst().relatedPublicationTitle());
    }

    @Test
    void publicUnitDetailExcludesNonValidatedActivities() {
        ResearchUnitEntity unit = new ResearchUnitEntity("Clinical AI Unit", "CAI", ResearchUnitType.CENTER, null, "Spain", "Madrid", null, true);
        unit.setId(4L);
        unit.setValidationStatus(ValidationStatus.VALIDATED);
        unit.setVisibleInPortal(true);
        unit.setOrganizationScope(OrganizationScope.INTERNAL);
        when(researchUnitRepository.findById(4L)).thenReturn(Optional.of(unit));
        stubPublicationSearch(List.of(
            publication(20L, "Validated unit activity", ValidationStatus.VALIDATED),
            publication(21L, "Draft unit activity", ValidationStatus.DRAFT),
            publication(22L, "Changes requested unit activity", ValidationStatus.CHANGES_REQUESTED)
        ));

        PortalResearchUnitDetailResponse result = service.researchUnitDetail(4L);

        assertEquals(List.of("Validated unit activity"), result.publications().stream().map(PortalPublicationSummaryResponse::title).toList());
    }

    @Test
    void portalUnitDirectoryIncludesInternalUnitAndExcludesExternalOrganization() {
        ResearchUnitEntity internal = unit(40L, "Internal Institute", ResearchUnitType.INSTITUTE, OrganizationScope.INTERNAL, true);
        ResearchUnitEntity external = unit(41L, "External Hospital", ResearchUnitType.HOSPITAL, OrganizationScope.EXTERNAL, true);
        when(researchUnitRepository.searchPortalVisibleValidated(
            isNull(),
            eq("%"),
            isNull(),
            eq(ValidationStatus.VALIDATED),
            eq(OrganizationScope.INTERNAL),
            any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(internal, external), PageRequest.of(0, 20), 2));

        PortalPageResponse<PortalResearchUnitSummaryResponse> result = service.researchUnits(0, 20, null, null);

        assertEquals(List.of("Internal Institute"), result.content().stream().map(PortalResearchUnitSummaryResponse::name).toList());
    }

    @Test
    void portalUnitDetailRejectsExternalOrganization() {
        ResearchUnitEntity external = unit(41L, "External Hospital", ResearchUnitType.HOSPITAL, OrganizationScope.EXTERNAL, true);
        when(researchUnitRepository.findById(41L)).thenReturn(Optional.of(external));

        assertThrows(ResourceNotFoundException.class, () -> service.researchUnitDetail(41L));
    }

    @Test
    void externalOrganizationCanStillBeFetchedForAdminManagement() {
        ResearchUnitEntity external = unit(41L, "External Hospital", ResearchUnitType.HOSPITAL, OrganizationScope.EXTERNAL, false);
        when(visibilityContext.defaultScope()).thenReturn(VisibilityScope.ADMIN_ALL);
        when(researchUnitRepository.findById(41L)).thenReturn(Optional.of(external));

        assertEquals(OrganizationScope.EXTERNAL, researchUnitService.findById(41L).organizationScope());
    }

    @Test
    void portalSearchExcludesPendingAndRejected() {
        stubPublicationSearch(List.of(
            publication(30L, "Validated search result", ValidationStatus.VALIDATED),
            publication(31L, "Pending search result", ValidationStatus.PENDING_VALIDATION),
            publication(32L, "Rejected search result", ValidationStatus.REJECTED)
        ));

        PortalSearchResponse result = service.search("clinical ai", 0, 10);

        assertEquals(List.of("Validated search result"), result.publications().content().stream().map(PortalPublicationSummaryResponse::title).toList());
    }

    @SuppressWarnings("unchecked")
    private void stubPublicationSearch(List<PublicationEntity> publications) {
        when(publicationRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(publications, PageRequest.of(0, 20), publications.size()));
    }

    private PublicationEntity publication(Long id, String title, ValidationStatus validationStatus) {
        PublicationEntity publication = new PublicationEntity(
            title,
            "Abstract",
            2025,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Demo source",
            null
        );
        publication.setId(id);
        publication.setValidationStatus(validationStatus);
        return publication;
    }

    private RelatedPublicationsResponse relatedPublications(Long publicationId) {
        return new RelatedPublicationsResponse(
            publicationId,
            5,
            0.35,
            true,
            List.of(),
            VisibilityScope.PUBLIC_VALIDATED.name(),
            true,
            List.of()
        );
    }

    private ResearcherEntity researcher(Long id, String fullName, ValidationStatus validationStatus) {
        ResearcherEntity researcher = new ResearcherEntity(fullName, fullName, null, null, true);
        researcher.setId(id);
        researcher.setValidationStatus(validationStatus);
        return researcher;
    }

    private EventParticipationEntity eventParticipation(Long id, Long eventId, Long researcherId, Long relatedPublicationId) {
        EventParticipationEntity activity = new EventParticipationEntity(
            eventId,
            researcherId,
            null,
            "PRESENTATION",
            "Validated activity",
            "Description",
            null,
            java.time.LocalDate.parse("2026-05-18"),
            relatedPublicationId,
            ValidationStatus.VALIDATED
        );
        activity.setId(id);
        return activity;
    }

    private ScientificEventEntity scientificEvent(Long id, String name, ValidationStatus validationStatus) {
        ScientificEventEntity event = new ScientificEventEntity(
            name,
            null,
            "CONFERENCE",
            java.time.LocalDate.parse("2026-05-18"),
            java.time.LocalDate.parse("2026-05-19"),
            "Madrid",
            "Spain",
            "Organizer",
            null,
            null,
            null,
            null,
            true,
            validationStatus
        );
        event.setId(id);
        return event;
    }

    private ResearchUnitEntity unit(
        Long id,
        String name,
        ResearchUnitType type,
        OrganizationScope organizationScope,
        boolean visibleInPortal
    ) {
        ResearchUnitEntity unit = new ResearchUnitEntity(name, null, type, null, "Spain", "Madrid", null, true);
        unit.setId(id);
        unit.setValidationStatus(ValidationStatus.VALIDATED);
        unit.setOrganizationScope(organizationScope);
        unit.setVisibleInPortal(visibleInPortal);
        return unit;
    }

    private Specification<PublicationEntity> anySpecification() {
        return any(Specification.class);
    }
}
