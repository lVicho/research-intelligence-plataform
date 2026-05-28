package com.researchintelligence.platform.publications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.publications.api.PublisherRequest;
import com.researchintelligence.platform.publications.api.PublisherResponse;
import com.researchintelligence.platform.publications.persistence.PublisherEntity;
import com.researchintelligence.platform.publications.persistence.PublisherRepository;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublisherServiceTest {

    @Mock
    private PublisherRepository repository;

    @Mock
    private ActivityAuditService auditService;

    private PublisherService service;

    @BeforeEach
    void setUp() {
        service = new PublisherService(repository, auditService);
    }

    @Test
    void createsPublisherWithDescriptionAndAuditEvent() {
        PublisherEntity saved = new PublisherEntity("Editorial Ciencia Local", "Espana", "https://publisher.example.test", true);
        saved.setId(12L);
        when(repository.save(any(PublisherEntity.class))).thenReturn(saved);

        PublisherResponse response = service.create(new PublisherRequest(
            "Editorial Ciencia Local",
            " Espana ",
            " https://publisher.example.test ",
            " Publisher profile ",
            true
        ));

        assertEquals(12L, response.id());
        assertEquals("Publisher profile", response.description());
        verify(auditService).recordCreated(ValidationEntityType.PUBLISHER, 12L, null);
    }

    @Test
    void updatesPublisherAndArchivesWhenActiveTurnsFalse() {
        PublisherEntity existing = new PublisherEntity("Old Publisher", "Spain", "https://old.example.test", true);
        existing.setId(14L);
        when(repository.findById(14L)).thenReturn(Optional.of(existing));
        when(auditService.changes()).thenReturn(new LinkedHashMap<>());

        PublisherResponse response = service.update(14L, new PublisherRequest(
            "Updated Publisher",
            "Spain",
            "https://updated.example.test",
            "Updated description",
            false
        ));

        assertEquals("Updated Publisher", response.name());
        assertEquals("Updated description", response.description());
        assertFalse(response.active());
        verify(auditService).recordUpdated(eq(ValidationEntityType.PUBLISHER), eq(14L), eq(null), eq(null), any());
        verify(auditService).recordArchived(ValidationEntityType.PUBLISHER, 14L, null);
    }
}
