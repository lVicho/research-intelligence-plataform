package com.researchintelligence.platform.publications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.publications.api.VenueRequest;
import com.researchintelligence.platform.publications.api.VenueResponse;
import com.researchintelligence.platform.publications.persistence.PublisherRepository;
import com.researchintelligence.platform.publications.persistence.VenueEntity;
import com.researchintelligence.platform.publications.persistence.VenueRepository;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

    @Mock
    private VenueRepository repository;

    @Mock
    private VisibilityContext visibilityContext;

    @Mock
    private PublisherRepository publisherRepository;

    @Mock
    private ActivityAuditService auditService;

    private VenueService service;

    @BeforeEach
    void setUp() {
        service = new VenueService(repository, visibilityContext, publisherRepository, auditService);
    }

    @Test
    void createsVenueWithPublisherDescriptionAndAuditEvent() {
        when(publisherRepository.existsById(5L)).thenReturn(true);
        when(repository.save(any(VenueEntity.class))).thenAnswer(invocation -> {
            VenueEntity venue = invocation.getArgument(0);
            venue.setId(9L);
            return venue;
        });

        VenueResponse response = service.create(request(ValidationStatus.VALIDATED, true));

        assertEquals(9L, response.id());
        assertEquals("Venue description", response.description());
        assertEquals(5L, response.publisherId());
        verify(auditService).recordCreated(ValidationEntityType.VENUE, 9L, ValidationStatus.VALIDATED);
    }

    @Test
    void updatesVenueAndAuditsStatusAndArchiveChanges() {
        VenueEntity existing = new VenueEntity(
            "Old venue",
            "OV",
            "JOURNAL",
            "1111-1111",
            null,
            null,
            "Spain",
            "https://old.example.test",
            null,
            null,
            true,
            ValidationStatus.PENDING_VALIDATION
        );
        existing.setId(10L);
        when(repository.findById(10L)).thenReturn(Optional.of(existing));
        when(publisherRepository.existsById(5L)).thenReturn(true);
        when(auditService.changes()).thenReturn(new LinkedHashMap<>());

        VenueResponse response = service.update(10L, request(ValidationStatus.VALIDATED, false));

        assertEquals("Updated venue", response.name());
        assertFalse(response.active());
        assertEquals(ValidationStatus.VALIDATED, response.validationStatus());
        verify(auditService).recordUpdated(
            eq(ValidationEntityType.VENUE),
            eq(10L),
            eq(ValidationStatus.PENDING_VALIDATION),
            eq(ValidationStatus.VALIDATED),
            any()
        );
        verify(auditService).recordArchived(ValidationEntityType.VENUE, 10L, ValidationStatus.VALIDATED);
    }

    private VenueRequest request(ValidationStatus validationStatus, boolean active) {
        return new VenueRequest(
            "Updated venue",
            "UV",
            "CONFERENCE",
            null,
            null,
            null,
            "Spain",
            "https://venue.example.test",
            "Venue description",
            5L,
            active,
            validationStatus
        );
    }
}
