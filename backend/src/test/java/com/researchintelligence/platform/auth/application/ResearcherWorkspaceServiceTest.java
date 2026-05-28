package com.researchintelligence.platform.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.auth.api.MeActivityDetailResponse;
import com.researchintelligence.platform.auth.api.MeActivityResponse;
import com.researchintelligence.platform.auth.persistence.ResearcherActivityRow;
import com.researchintelligence.platform.auth.persistence.ResearcherWorkspaceRepository;
import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.events.persistence.EventParticipationEntity;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.researchers.domain.AffiliationType;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import com.researchintelligence.platform.validation.persistence.ValidationInboxRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ResearcherWorkspaceServiceTest {

    @Mock
    private ResearcherWorkspaceRepository workspaceRepository;

    @Mock
    private ValidationInboxRepository validationInboxRepository;

    @Mock
    private ResearcherRepository researcherRepository;

    @Mock
    private ResearcherAffiliationRepository affiliationRepository;

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private EventParticipationRepository eventParticipationRepository;

    @Mock
    private ActivityAuditService auditService;

    private ResearcherWorkspaceService service;

    @BeforeEach
    void setUp() {
        service = new ResearcherWorkspaceService(
            workspaceRepository,
            validationInboxRepository,
            researcherRepository,
            affiliationRepository,
            publicationRepository,
            eventParticipationRepository,
            auditService
        );
    }

    @Test
    void researcherSeesValidationCommentInActivityDetail() {
        PlatformUserPrincipal researcher = principal(11L, 7L, "RESEARCHER");
        when(workspaceRepository.findOwnedActivity(7L, ValidationEntityType.PUBLICATION, 20L))
            .thenReturn(Optional.of(activityRow(ValidationStatus.CHANGES_REQUESTED, "Please add DOI.")));

        MeActivityDetailResponse response = service.activityDetail(researcher, ValidationEntityType.PUBLICATION, 20L);

        assertEquals("Please add DOI.", response.validationComment());
    }

    @Test
    void researcherSeesOwnPendingPublicationInActivities() {
        PlatformUserPrincipal researcher = principal(11L, 7L, "RESEARCHER");
        ResearcherActivityRow pending = activityRow(ValidationStatus.PENDING_VALIDATION, "Submitted.");
        when(workspaceRepository.activities(7L, null, null, null, 0, 20))
            .thenReturn(new PageImpl<>(List.of(pending), PageRequest.of(0, 20), 1));

        var response = service.activities(researcher, null, null, null, 0, 20);

        assertEquals(List.of(ValidationStatus.PENDING_VALIDATION), response.content().stream().map(MeActivityResponse::validationStatus).toList());
    }

    @Test
    void resubmitFromChangesRequestedMovesStatusToPendingValidationAndCreatesAuditEvent() {
        PlatformUserPrincipal researcher = principal(11L, 7L, "RESEARCHER");
        when(workspaceRepository.findOwnedActivity(7L, ValidationEntityType.PUBLICATION, 20L))
            .thenReturn(
                Optional.of(activityRow(ValidationStatus.CHANGES_REQUESTED, "Please add DOI.")),
                Optional.of(activityRow(ValidationStatus.PENDING_VALIDATION, "Please add DOI."))
            );

        PublicationEntity publication = new PublicationEntity(
            "Publication",
            "Abstract",
            2026,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Journal",
            null
        );
        publication.setId(20L);
        publication.setValidationStatus(ValidationStatus.CHANGES_REQUESTED);
        publication.setValidatedByUserId(99L);
        publication.setValidatedAt(Instant.parse("2026-05-16T10:00:00Z"));
        when(publicationRepository.findById(20L)).thenReturn(Optional.of(publication));

        MeActivityDetailResponse response = service.submitActivity(researcher, ValidationEntityType.PUBLICATION, 20L);

        assertEquals(ValidationStatus.PENDING_VALIDATION, response.validationStatus());
        assertEquals(ValidationStatus.PENDING_VALIDATION, publication.getValidationStatus());
        assertNull(publication.getValidatedByUserId());
        assertNull(publication.getValidatedAt());
        verify(auditService).recordStatusChange(
            ValidationEntityType.PUBLICATION,
            20L,
            ValidationStatus.CHANGES_REQUESTED,
            ValidationStatus.PENDING_VALIDATION,
            null
        );
    }

    @Test
    void researcherCannotSubmitAnotherResearchersActivity() {
        PlatformUserPrincipal researcher = principal(11L, 7L, "RESEARCHER");
        when(workspaceRepository.findOwnedActivity(7L, ValidationEntityType.PUBLICATION, 21L))
            .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.submitActivity(researcher, ValidationEntityType.PUBLICATION, 21L)
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void resubmitEventParticipationMovesStatusToPendingValidationAndCreatesAuditEvent() {
        PlatformUserPrincipal researcher = principal(11L, 7L, "RESEARCHER");
        when(workspaceRepository.findOwnedActivity(7L, ValidationEntityType.EVENT_PARTICIPATION, 33L))
            .thenReturn(
                Optional.of(eventParticipationActivityRow(ValidationStatus.CHANGES_REQUESTED)),
                Optional.of(eventParticipationActivityRow(ValidationStatus.PENDING_VALIDATION))
            );
        EventParticipationEntity participation = new EventParticipationEntity(
            3L,
            7L,
            11L,
            "POSTER",
            "Poster",
            "Description",
            null,
            java.time.LocalDate.parse("2026-06-13"),
            null,
            ValidationStatus.CHANGES_REQUESTED
        );
        participation.setId(33L);
        participation.setValidatedAt(Instant.parse("2026-05-16T10:00:00Z"));
        when(eventParticipationRepository.findByIdAndResearcherId(33L, 7L)).thenReturn(Optional.of(participation));

        MeActivityDetailResponse response = service.submitActivity(researcher, ValidationEntityType.EVENT_PARTICIPATION, 33L);

        assertEquals(ValidationStatus.PENDING_VALIDATION, response.validationStatus());
        assertEquals(ValidationStatus.PENDING_VALIDATION, participation.getValidationStatus());
        assertNull(participation.getValidatedAt());
        verify(auditService).recordStatusChange(
            ValidationEntityType.EVENT_PARTICIPATION,
            33L,
            ValidationStatus.CHANGES_REQUESTED,
            ValidationStatus.PENDING_VALIDATION,
            null
        );
    }

    @Test
    void resubmitAffiliationMovesStatusToPendingValidationAndCreatesAuditEvent() {
        PlatformUserPrincipal researcher = principal(11L, 7L, "RESEARCHER");
        when(workspaceRepository.findOwnedActivity(7L, ValidationEntityType.RESEARCHER_AFFILIATION, 44L))
            .thenReturn(
                Optional.of(affiliationActivityRow(ValidationStatus.CHANGES_REQUESTED)),
                Optional.of(affiliationActivityRow(ValidationStatus.PENDING_VALIDATION))
            );
        ResearcherAffiliationEntity affiliation = new ResearcherAffiliationEntity(
            7L,
            12L,
            "Researcher",
            AffiliationType.MEMBER,
            java.time.LocalDate.parse("2025-01-01"),
            null,
            true
        );
        affiliation.setId(44L);
        affiliation.setValidationStatus(ValidationStatus.CHANGES_REQUESTED);
        affiliation.setValidatedByUserId(99L);
        affiliation.setValidatedAt(Instant.parse("2026-05-16T10:00:00Z"));
        when(affiliationRepository.findByIdAndResearcherId(44L, 7L)).thenReturn(Optional.of(affiliation));

        MeActivityDetailResponse response = service.submitActivity(researcher, ValidationEntityType.RESEARCHER_AFFILIATION, 44L);

        assertEquals(ValidationStatus.PENDING_VALIDATION, response.validationStatus());
        assertEquals(ValidationStatus.PENDING_VALIDATION, affiliation.getValidationStatus());
        assertNull(affiliation.getValidatedByUserId());
        assertNull(affiliation.getValidatedAt());
        verify(auditService).recordStatusChange(
            ValidationEntityType.RESEARCHER_AFFILIATION,
            44L,
            ValidationStatus.CHANGES_REQUESTED,
            ValidationStatus.PENDING_VALIDATION,
            null
        );
    }

    private PlatformUserPrincipal principal(Long userId, Long researcherId, String role) {
        UserEntity user = new UserEntity("researcher@example.test", "Researcher", "{noop}password", true, researcherId);
        user.setId(userId);
        user.setRoles(new LinkedHashSet<>(List.of(new RoleEntity(role, role, role))));
        return new PlatformUserPrincipal(user);
    }

    private ResearcherActivityRow activityRow(ValidationStatus status, String validationComment) {
        return new ResearcherActivityRow(
            ValidationEntityType.PUBLICATION,
            20L,
            "Publication",
            "2026 | Journal",
            7L,
            "Researcher",
            null,
            null,
            Instant.parse("2026-05-16T09:00:00Z"),
            status,
            validationComment,
            99L,
            "Validator",
            Instant.parse("2026-05-16T10:00:00Z"),
            "ARTICLE",
            "PUBLISHED",
            2026,
            null,
            "Journal",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true,
            1L,
            1L
        );
    }

    private ResearcherActivityRow eventParticipationActivityRow(ValidationStatus status) {
        return new ResearcherActivityRow(
            ValidationEntityType.EVENT_PARTICIPATION,
            33L,
            "Poster",
            "POSTER | Event",
            7L,
            "Researcher",
            11L,
            "Unit",
            Instant.parse("2026-05-16T09:00:00Z"),
            status,
            "Please add unit.",
            null,
            null,
            Instant.parse("2026-05-16T10:00:00Z"),
            "POSTER",
            "CONFERENCE",
            2026,
            null,
            "Event",
            null,
            null,
            null,
            "Organizer",
            null,
            java.time.LocalDate.parse("2026-06-13"),
            java.time.LocalDate.parse("2026-06-13"),
            true,
            null,
            null
        );
    }

    private ResearcherActivityRow affiliationActivityRow(ValidationStatus status) {
        return new ResearcherActivityRow(
            ValidationEntityType.RESEARCHER_AFFILIATION,
            44L,
            "Researcher / Unit",
            "Researcher | MEMBER",
            7L,
            "Researcher",
            12L,
            "Unit",
            Instant.parse("2026-05-16T09:00:00Z"),
            status,
            "Please confirm dates.",
            99L,
            "Validator",
            Instant.parse("2026-05-16T10:00:00Z"),
            "MEMBER",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "Researcher",
            true,
            java.time.LocalDate.parse("2025-01-01"),
            null,
            null,
            null,
            null
        );
    }
}
