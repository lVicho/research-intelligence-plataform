package com.researchintelligence.platform.events.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.events.api.EventParticipationRequest;
import com.researchintelligence.platform.events.api.EventParticipationResponse;
import com.researchintelligence.platform.events.persistence.EventParticipationEntity;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.events.persistence.ScientificEventRepository;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EventParticipationServiceTest {

    @Mock
    private EventParticipationRepository repository;

    @Mock
    private ScientificEventRepository eventRepository;

    @Mock
    private ResearcherRepository researcherRepository;

    @Mock
    private ResearchUnitRepository researchUnitRepository;

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private PublicationAuthorRepository publicationAuthorRepository;

    @Mock
    private VisibilityContext visibilityContext;

    @Mock
    private ActivityAuditService auditService;

    private EventParticipationService service;

    @BeforeEach
    void setUp() {
        service = new EventParticipationService(
            repository,
            eventRepository,
            researcherRepository,
            researchUnitRepository,
            publicationRepository,
            publicationAuthorRepository,
            visibilityContext,
            auditService
        );
    }

    @Test
    void storesEvidenceUrlWhenCreatingEventParticipation() {
        PlatformUserPrincipal admin = principal(1L, null, "ADMIN");
        when(eventRepository.existsById(3L)).thenReturn(true);
        when(researcherRepository.existsById(7L)).thenReturn(true);
        when(repository.save(any(EventParticipationEntity.class))).thenAnswer(invocation -> {
            EventParticipationEntity participation = invocation.getArgument(0);
            participation.setId(33L);
            return participation;
        });
        when(visibilityContext.currentRoles()).thenReturn(Set.of("ADMIN"));

        EventParticipationResponse response = service.create(request(7L), admin);

        assertEquals("https://evidence.example.test/poster", response.evidenceUrl());
        verify(auditService).recordCreated(ValidationEntityType.EVENT_PARTICIPATION, 33L, ValidationStatus.DRAFT);
    }

    @Test
    void researcherCannotCreateParticipationForAnotherResearcher() {
        PlatformUserPrincipal researcher = principal(2L, 7L, "RESEARCHER");
        when(eventRepository.existsById(3L)).thenReturn(true);
        when(researcherRepository.existsById(8L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.create(request(8L), researcher)
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    private EventParticipationRequest request(Long researcherId) {
        return new EventParticipationRequest(
            3L,
            researcherId,
            null,
            "POSTER",
            "Poster title",
            "Poster description",
            "https://evidence.example.test/poster",
            LocalDate.parse("2026-06-13"),
            null,
            null
        );
    }

    private PlatformUserPrincipal principal(Long userId, Long researcherId, String role) {
        UserEntity user = new UserEntity(role.toLowerCase() + "@example.test", role, "{noop}password", true, researcherId);
        user.setId(userId);
        user.setRoles(new LinkedHashSet<>(List.of(new RoleEntity(role, role, role))));
        return new PlatformUserPrincipal(user);
    }
}
