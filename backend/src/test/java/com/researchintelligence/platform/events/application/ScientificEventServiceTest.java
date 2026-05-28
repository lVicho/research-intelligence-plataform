package com.researchintelligence.platform.events.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.events.api.ScientificEventRequest;
import com.researchintelligence.platform.events.api.ScientificEventResponse;
import com.researchintelligence.platform.events.persistence.ScientificEventEntity;
import com.researchintelligence.platform.events.persistence.ScientificEventRepository;
import com.researchintelligence.platform.publications.persistence.VenueRepository;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScientificEventServiceTest {

    @Mock
    private ScientificEventRepository repository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private VisibilityContext visibilityContext;

    @Mock
    private ActivityAuditService auditService;

    private ScientificEventService service;

    @BeforeEach
    void setUp() {
        service = new ScientificEventService(repository, venueRepository, visibilityContext, auditService);
    }

    @Test
    void createsScientificEventWithMetadataAndAuditEvent() {
        when(venueRepository.existsById(2L)).thenReturn(true);
        when(repository.save(any(ScientificEventEntity.class))).thenAnswer(invocation -> {
            ScientificEventEntity event = invocation.getArgument(0);
            event.setId(12L);
            return event;
        });

        ScientificEventResponse response = service.create(request(ValidationStatus.VALIDATED, true));

        assertEquals(12L, response.id());
        assertEquals("Scientific event description", response.description());
        assertEquals("https://evidence.example.test/event", response.evidenceUrl());
        verify(auditService).recordCreated(ValidationEntityType.SCIENTIFIC_EVENT, 12L, ValidationStatus.VALIDATED);
    }

    @Test
    void updatesScientificEventAndAuditsStatusAndArchiveChanges() {
        ScientificEventEntity existing = new ScientificEventEntity(
            "Old event",
            "2025",
            "WORKSHOP",
            LocalDate.parse("2025-05-10"),
            LocalDate.parse("2025-05-11"),
            "Madrid",
            "Spain",
            "Organizer",
            "https://old.example.test",
            null,
            null,
            null,
            true,
            ValidationStatus.PENDING_VALIDATION
        );
        existing.setId(14L);
        when(repository.findById(14L)).thenReturn(Optional.of(existing));
        when(venueRepository.existsById(2L)).thenReturn(true);
        when(auditService.changes()).thenReturn(new LinkedHashMap<>());

        ScientificEventResponse response = service.update(14L, request(ValidationStatus.VALIDATED, false));

        assertEquals("Updated scientific event", response.name());
        assertFalse(response.active());
        assertEquals(ValidationStatus.VALIDATED, response.validationStatus());
        verify(auditService).recordUpdated(
            eq(ValidationEntityType.SCIENTIFIC_EVENT),
            eq(14L),
            eq(ValidationStatus.PENDING_VALIDATION),
            eq(ValidationStatus.VALIDATED),
            any()
        );
        verify(auditService).recordArchived(ValidationEntityType.SCIENTIFIC_EVENT, 14L, ValidationStatus.VALIDATED);
    }

    private ScientificEventRequest request(ValidationStatus validationStatus, boolean active) {
        return new ScientificEventRequest(
            "Updated scientific event",
            "2026",
            "CONFERENCE",
            LocalDate.parse("2026-06-12"),
            LocalDate.parse("2026-06-13"),
            "Sevilla",
            "Spain",
            "Research Center",
            "https://event.example.test",
            "Scientific event description",
            "https://evidence.example.test/event",
            2L,
            active,
            validationStatus
        );
    }
}
