package com.researchintelligence.platform.validation.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.events.persistence.EventParticipationEntity;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import com.researchintelligence.platform.validation.persistence.ValidationInboxRepository;
import com.researchintelligence.platform.validation.persistence.ValidationItemRow;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidationInboxServiceAuditTest {

    @Mock
    private ValidationInboxRepository inboxRepository;

    @Mock
    private ResearchUnitRepository researchUnitRepository;

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

    private ValidationInboxService service;

    @BeforeEach
    void setUp() {
        service = new ValidationInboxService(
            inboxRepository,
            researchUnitRepository,
            researcherRepository,
            affiliationRepository,
            publicationRepository,
            eventParticipationRepository,
            auditService
        );
    }

    @Test
    void validationActionCreatesAuditEvent() {
        PublicationEntity publication = new PublicationEntity(
            "Validated publication",
            "Abstract",
            2026,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Journal",
            null
        );
        publication.setId(15L);
        publication.setValidationStatus(ValidationStatus.PENDING_VALIDATION);
        when(publicationRepository.findById(15L)).thenReturn(Optional.of(publication));
        when(inboxRepository.findByEntity(ValidationEntityType.PUBLICATION, 15L))
            .thenReturn(Optional.of(row(ValidationStatus.VALIDATED)));

        service.validate(ValidationEntityType.PUBLICATION, 15L, "Approved", 1L);

        verify(auditService).recordStatusChange(
            ValidationEntityType.PUBLICATION,
            15L,
            ValidationStatus.PENDING_VALIDATION,
            ValidationStatus.VALIDATED,
            "Approved"
        );
    }

    @Test
    void validationActionRejectsNonPendingItems() {
        PublicationEntity publication = new PublicationEntity(
            "Already validated publication",
            "Abstract",
            2026,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Journal",
            null
        );
        publication.setId(15L);
        publication.setValidationStatus(ValidationStatus.VALIDATED);
        when(publicationRepository.findById(15L)).thenReturn(Optional.of(publication));

        assertThrows(
            BusinessRuleException.class,
            () -> service.reject(ValidationEntityType.PUBLICATION, 15L, "No longer valid", 1L)
        );
    }

    @Test
    void rejectRequiresComment() {
        PublicationEntity publication = new PublicationEntity(
            "Pending publication",
            "Abstract",
            2026,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Journal",
            null
        );
        publication.setId(15L);
        publication.setValidationStatus(ValidationStatus.PENDING_VALIDATION);
        when(publicationRepository.findById(15L)).thenReturn(Optional.of(publication));

        assertThrows(
            BusinessRuleException.class,
            () -> service.reject(ValidationEntityType.PUBLICATION, 15L, "   ", 1L)
        );

        verify(auditService, never()).recordStatusChange(
            ValidationEntityType.PUBLICATION,
            15L,
            ValidationStatus.PENDING_VALIDATION,
            ValidationStatus.REJECTED,
            null
        );
    }

    @Test
    void requestChangesRequiresComment() {
        PublicationEntity publication = new PublicationEntity(
            "Pending publication",
            "Abstract",
            2026,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Journal",
            null
        );
        publication.setId(15L);
        publication.setValidationStatus(ValidationStatus.PENDING_VALIDATION);
        when(publicationRepository.findById(15L)).thenReturn(Optional.of(publication));

        assertThrows(
            BusinessRuleException.class,
            () -> service.requestChanges(ValidationEntityType.PUBLICATION, 15L, null, 1L)
        );

        verify(auditService, never()).recordStatusChange(
            ValidationEntityType.PUBLICATION,
            15L,
            ValidationStatus.PENDING_VALIDATION,
            ValidationStatus.CHANGES_REQUESTED,
            null
        );
    }

    @Test
    void validatesEventParticipationAndCreatesAuditEvent() {
        EventParticipationEntity participation = new EventParticipationEntity(
            3L,
            7L,
            11L,
            "ORAL_PRESENTATION",
            "Event talk",
            "Description",
            null,
            java.time.LocalDate.parse("2026-06-12"),
            null,
            ValidationStatus.PENDING_VALIDATION
        );
        participation.setId(33L);
        when(eventParticipationRepository.findById(33L)).thenReturn(Optional.of(participation));
        when(inboxRepository.findByEntity(ValidationEntityType.EVENT_PARTICIPATION, 33L))
            .thenReturn(Optional.of(eventParticipationRow(ValidationStatus.VALIDATED)));

        service.validate(ValidationEntityType.EVENT_PARTICIPATION, 33L, "Approved", 1L);

        verify(auditService).recordStatusChange(
            ValidationEntityType.EVENT_PARTICIPATION,
            33L,
            ValidationStatus.PENDING_VALIDATION,
            ValidationStatus.VALIDATED,
            "Approved"
        );
    }

    @Test
    void catalogManagedScientificEventsAndVenuesAreNotValidationInboxItems() {
        assertThrows(
            BusinessRuleException.class,
            () -> service.validate(ValidationEntityType.SCIENTIFIC_EVENT, 40L, "Approved", 1L)
        );
        assertThrows(
            BusinessRuleException.class,
            () -> service.validate(ValidationEntityType.VENUE, 41L, "Approved", 1L)
        );
    }

    private ValidationItemRow row(ValidationStatus status) {
        return new ValidationItemRow(
            ValidationEntityType.PUBLICATION,
            15L,
            "Validated publication",
            null,
            null,
            null,
            null,
            null,
            "Admin",
            Instant.parse("2026-05-16T12:00:00Z"),
            status,
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
            true,
            1L,
            1L,
            null,
            null,
            null,
            null,
            null,
            1L,
            "Admin",
            Instant.parse("2026-05-16T12:05:00Z")
        );
    }

    private ValidationItemRow eventParticipationRow(ValidationStatus status) {
        return new ValidationItemRow(
            ValidationEntityType.EVENT_PARTICIPATION,
            33L,
            "Event talk",
            "ORAL_PRESENTATION | Event",
            7L,
            "Researcher",
            11L,
            "Unit",
            "Researcher",
            Instant.parse("2026-05-16T12:00:00Z"),
            status,
            "ORAL_PRESENTATION",
            "CONFERENCE",
            2026,
            null,
            "Event",
            null,
            null,
            null,
            "https://event.example.test",
            "Spain",
            "Madrid",
            true,
            null,
            null,
            "Organizer",
            null,
            java.time.LocalDate.parse("2026-06-12"),
            java.time.LocalDate.parse("2026-06-13"),
            null,
            null,
            null,
            Instant.parse("2026-05-16T12:05:00Z")
        );
    }
}
