package com.researchintelligence.platform.publications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.auth.persistence.UserRepository;
import com.researchintelligence.platform.publications.api.PublicationAuthorRequest;
import com.researchintelligence.platform.publications.api.PublicationRequest;
import com.researchintelligence.platform.publications.api.PublicationResponse;
import com.researchintelligence.platform.publications.api.PublicationSummaryResponse;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorEntity;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.PublisherRepository;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.publications.persistence.VenueRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PublicationServiceVisibilityTest {

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private PublicationAuthorRepository authorRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private PublicationTopicRepository publicationTopicRepository;

    @Mock
    private ResearcherRepository researcherRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private PublisherRepository publisherRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VisibilityContext visibilityContext;

    @Mock
    private ActivityAuditService auditService;

    private PublicationService service;

    @BeforeEach
    void setUp() {
        service = new PublicationService(
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
    }

    @Test
    void publicSearchUsesPublicValidatedVisibility() {
        PublicationEntity validated = publication(1L, "Validated publication", ValidationStatus.VALIDATED);
        when(visibilityContext.defaultScope()).thenReturn(VisibilityScope.PUBLIC_VALIDATED);
        when(visibilityContext.linkedResearcherId()).thenReturn(Optional.empty());
        when(publicationRepository.findAll(anySpecification(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(validated), PageRequest.of(0, 20), 1));
        when(publicationTopicRepository.findByPublicationIdIn(List.of(1L))).thenReturn(List.of());
        when(topicRepository.findAllById(List.of())).thenReturn(List.of());

        PageResponse<PublicationSummaryResponse> result = service.search(
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

        assertEquals(List.of(1L), result.content().stream().map(PublicationSummaryResponse::id).toList());
        verify(visibilityContext).defaultScope();
        verify(visibilityContext).linkedResearcherId();
        verify(publicationRepository).findAll(anySpecification(), any(Pageable.class));
    }

    @Test
    void researcherDetailUsesMyDataVisibilityForOwnRecords() {
        PublicationEntity ownDraft = publication(2L, "Own draft", ValidationStatus.DRAFT);
        when(visibilityContext.defaultScope()).thenReturn(VisibilityScope.MY_DATA);
        when(visibilityContext.linkedResearcherId()).thenReturn(Optional.of(42L));
        when(publicationRepository.findOne(anySpecification())).thenReturn(Optional.of(ownDraft));
        when(authorRepository.findByPublicationIdOrderByAuthorOrderAsc(2L))
            .thenReturn(List.of(new PublicationAuthorEntity(2L, 42L, null, null, 1, true)));
        when(researcherRepository.findAllById(List.of(42L))).thenReturn(List.of());
        when(publicationTopicRepository.findByPublicationId(2L)).thenReturn(List.of());

        PublicationResponse result = service.findById(2L);

        assertEquals(2L, result.id());
        assertEquals("Own draft", result.title());
        verify(publicationRepository).findOne(anySpecification());
    }

    @Test
    void adminDetailUsesAdminAllVisibility() {
        PublicationEntity pending = publication(3L, "Pending admin review", ValidationStatus.PENDING_VALIDATION);
        when(visibilityContext.defaultScope()).thenReturn(VisibilityScope.ADMIN_ALL);
        when(visibilityContext.linkedResearcherId()).thenReturn(Optional.empty());
        when(publicationRepository.findOne(anySpecification())).thenReturn(Optional.of(pending));
        when(authorRepository.findByPublicationIdOrderByAuthorOrderAsc(3L)).thenReturn(List.of());
        when(publicationTopicRepository.findByPublicationId(3L)).thenReturn(List.of());

        PublicationResponse result = service.findById(3L);

        assertEquals(3L, result.id());
        assertEquals("Pending admin review", result.title());
        verify(publicationRepository).findOne(anySpecification());
    }

    @ParameterizedTest
    @EnumSource(value = ValidationStatus.class, names = {"DRAFT", "CHANGES_REQUESTED"})
    void researcherCanEditOwnEditablePublication(ValidationStatus initialStatus) {
        PublicationEntity ownPublication = publication(20L, "Own publication", initialStatus);
        ResearcherEntity researcher = researcher(42L);
        PublicationRequest request = request("Updated publication", 42L);
        when(publicationRepository.findById(20L)).thenReturn(Optional.of(ownPublication));
        when(authorRepository.existsByPublicationIdAndResearcherId(20L, 42L)).thenReturn(true);
        when(researcherRepository.findAllById(List.of(42L))).thenReturn(List.of(researcher));
        when(auditService.changes()).thenReturn(new LinkedHashMap<>());
        when(authorRepository.findByPublicationIdOrderByAuthorOrderAsc(20L))
            .thenReturn(List.of(new PublicationAuthorEntity(20L, 42L, null, null, 1, true)));
        when(publicationTopicRepository.findByPublicationId(20L)).thenReturn(List.of());
        when(visibilityContext.defaultScope()).thenReturn(VisibilityScope.MY_DATA);
        when(visibilityContext.linkedResearcherId()).thenReturn(Optional.of(42L));
        when(visibilityContext.currentRoles()).thenReturn(Set.of("RESEARCHER"));

        PublicationResponse response = service.updateOwn(42L, 20L, request);

        assertEquals("Updated publication", response.title());
        assertEquals(ValidationStatus.DRAFT, response.validationStatus());
        assertTrue(response.canEdit());
        assertTrue(response.canSubmit());
    }

    @Test
    void researcherCannotEditAnotherResearchersPublication() {
        PublicationEntity publication = publication(21L, "Other publication", ValidationStatus.DRAFT);
        when(publicationRepository.findById(21L)).thenReturn(Optional.of(publication));
        when(researcherRepository.findAllById(List.of(42L))).thenReturn(List.of(researcher(42L)));
        when(authorRepository.existsByPublicationIdAndResearcherId(21L, 42L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.updateOwn(42L, 21L, request("Updated publication", 42L))
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void researcherCanSubmitOwnPublication() {
        PublicationEntity publication = publication(30L, "Own draft", ValidationStatus.DRAFT);
        publication.setValidatedByUserId(99L);
        publication.setValidatedAt(Instant.parse("2026-05-16T10:00:00Z"));
        PlatformUserPrincipal principal = principal(11L, 42L, "RESEARCHER");
        when(publicationRepository.findById(30L)).thenReturn(Optional.of(publication));
        when(authorRepository.existsByPublicationIdAndResearcherId(30L, 42L)).thenReturn(true);
        when(authorRepository.findByPublicationIdOrderByAuthorOrderAsc(30L))
            .thenReturn(List.of(new PublicationAuthorEntity(30L, 42L, null, null, 1, true)));
        when(researcherRepository.findAllById(List.of(42L))).thenReturn(List.of(researcher(42L)));
        when(publicationTopicRepository.findByPublicationId(30L)).thenReturn(List.of());
        when(visibilityContext.defaultScope()).thenReturn(VisibilityScope.MY_DATA);
        when(visibilityContext.linkedResearcherId()).thenReturn(Optional.of(42L));
        when(visibilityContext.currentRoles()).thenReturn(Set.of("RESEARCHER"));

        PublicationResponse response = service.submit(30L, principal);

        assertEquals(ValidationStatus.PENDING_VALIDATION, response.validationStatus());
        assertEquals(ValidationStatus.PENDING_VALIDATION, publication.getValidationStatus());
        assertNull(publication.getValidatedByUserId());
        assertNull(publication.getValidatedAt());
        assertFalse(response.canEdit());
        assertFalse(response.canSubmit());
        verify(auditService).recordStatusChange(
            ValidationEntityType.PUBLICATION,
            30L,
            ValidationStatus.DRAFT,
            ValidationStatus.PENDING_VALIDATION,
            null
        );
    }

    @Test
    void publicationDtoExposesValidationWorkflowFieldsForOwner() {
        PublicationEntity publication = publication(40L, "Returned publication", ValidationStatus.CHANGES_REQUESTED);
        publication.setValidationComment("Please add DOI.");
        publication.setValidatedByUserId(101L);
        publication.setValidatedAt(Instant.parse("2026-05-16T10:00:00Z"));
        ReflectionTestUtils.setField(publication, "createdAt", Instant.parse("2026-05-15T09:00:00Z"));
        ReflectionTestUtils.setField(publication, "createdByUserId", 100L);
        UserEntity submitter = user(100L, "Submitter");
        UserEntity validator = user(101L, "Validator");
        when(visibilityContext.defaultScope()).thenReturn(VisibilityScope.MY_DATA);
        when(visibilityContext.linkedResearcherId()).thenReturn(Optional.of(42L));
        when(visibilityContext.currentRoles()).thenReturn(Set.of("RESEARCHER"));
        when(publicationRepository.findOne(anySpecification())).thenReturn(Optional.of(publication));
        when(authorRepository.findByPublicationIdOrderByAuthorOrderAsc(40L))
            .thenReturn(List.of(new PublicationAuthorEntity(40L, 42L, null, null, 1, true)));
        when(authorRepository.existsByPublicationIdAndResearcherId(40L, 42L)).thenReturn(true);
        when(researcherRepository.findAllById(List.of(42L))).thenReturn(List.of(researcher(42L)));
        when(publicationTopicRepository.findByPublicationId(40L)).thenReturn(List.of());
        when(userRepository.findAllById(Set.of(100L, 101L))).thenReturn(List.of(submitter, validator));

        PublicationResponse response = service.findById(40L);

        assertEquals(ValidationStatus.CHANGES_REQUESTED, response.validationStatus());
        assertEquals("Please add DOI.", response.validationComment());
        assertEquals(Instant.parse("2026-05-15T09:00:00Z"), response.submittedAt());
        assertEquals("Submitter", response.submittedBy());
        assertEquals(Instant.parse("2026-05-16T10:00:00Z"), response.validatedAt());
        assertEquals("Validator", response.validatedBy());
        assertTrue(response.canEdit());
        assertTrue(response.canSubmit());
        assertFalse(response.canValidate());
    }

    @Test
    void publicDetailDoesNotExposeNonValidatedPublication() {
        when(visibilityContext.defaultScope()).thenReturn(VisibilityScope.PUBLIC_VALIDATED);
        when(visibilityContext.linkedResearcherId()).thenReturn(Optional.empty());
        when(publicationRepository.findOne(anySpecification())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(50L));
    }

    @SuppressWarnings("unchecked")
    private Specification<PublicationEntity> anySpecification() {
        return any(Specification.class);
    }

    private PublicationEntity publication(Long id, String title, ValidationStatus validationStatus) {
        PublicationEntity publication = new PublicationEntity(
            title,
            "Abstract",
            2025,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Journal",
            null
        );
        publication.setId(id);
        publication.setValidationStatus(validationStatus);
        return publication;
    }

    private PublicationRequest request(String title, Long researcherId) {
        return new PublicationRequest(
            title,
            "Abstract",
            null,
            2025,
            null,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Journal",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(new PublicationAuthorRequest(researcherId, null, null, 1, true)),
            List.of()
        );
    }

    private ResearcherEntity researcher(Long id) {
        ResearcherEntity researcher = new ResearcherEntity("Researcher " + id, "Researcher", "researcher@example.test", null, true);
        researcher.setId(id);
        researcher.setValidationStatus(ValidationStatus.VALIDATED);
        return researcher;
    }

    private PlatformUserPrincipal principal(Long userId, Long researcherId, String role) {
        UserEntity user = new UserEntity("researcher@example.test", "Researcher", "{noop}password", true, researcherId);
        user.setId(userId);
        user.setRoles(new LinkedHashSet<>(List.of(new RoleEntity(role, role, role))));
        return new PlatformUserPrincipal(user);
    }

    private UserEntity user(Long id, String displayName) {
        UserEntity user = new UserEntity(displayName.toLowerCase() + "@example.test", displayName, "{noop}password", true, null);
        user.setId(id);
        return user;
    }
}
