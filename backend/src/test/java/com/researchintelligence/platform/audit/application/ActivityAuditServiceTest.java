package com.researchintelligence.platform.audit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.audit.domain.ActivityAuditAction;
import com.researchintelligence.platform.audit.persistence.ActivityAuditEventQueryRepository;
import com.researchintelligence.platform.audit.persistence.ActivityAuditEventRepository;
import com.researchintelligence.platform.audit.persistence.ActivityAuditEventRow;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
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
class ActivityAuditServiceTest {

    @Mock
    private ActivityAuditEventRepository repository;

    @Mock
    private ActivityAuditEventQueryRepository queryRepository;

    @Mock
    private VisibilityContext visibilityContext;

    private ActivityAuditService service;

    @BeforeEach
    void setUp() {
        service = new ActivityAuditService(repository, queryRepository, visibilityContext, new ObjectMapper());
    }

    @Test
    void researcherCanReadOwnAudit() {
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(10L, 7L, "RESEARCHER")));
        ActivityAuditEventRow row = row(ValidationEntityType.RESEARCHER, 7L);
        when(queryRepository.findVisibleToResearcher(7L, null, null, null, 0, 20))
            .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        PageResponse<?> result = service.findEvents(null, null, null, 0, 20);

        assertEquals(1, result.totalElements());
        verify(queryRepository).findVisibleToResearcher(7L, null, null, null, 0, 20);
    }

    @Test
    void researcherCannotReadOthersAudit() {
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(10L, 7L, "RESEARCHER")));
        when(queryRepository.isEntityOwnedByResearcher(ValidationEntityType.RESEARCHER, 8L, 7L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.findEntityEvents(ValidationEntityType.RESEARCHER, 8L, 0, 20)
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void adminCanReadAllAudit() {
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(1L, null, "ADMIN")));
        ActivityAuditEventRow row = row(ValidationEntityType.PUBLICATION, 99L);
        when(queryRepository.findAllVisible(null, null, null, 0, 20))
            .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        PageResponse<?> result = service.findEvents(null, null, null, 0, 20);

        assertEquals(1, result.totalElements());
        verify(queryRepository).findAllVisible(null, null, null, 0, 20);
    }

    @Test
    void validatorCanReadAllAudit() {
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(2L, null, "VALIDATOR")));
        ActivityAuditEventRow row = row(ValidationEntityType.PUBLICATION, 99L);
        when(queryRepository.findAllVisible(null, null, null, 0, 20))
            .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        PageResponse<?> result = service.findEvents(null, null, null, 0, 20);

        assertEquals(1, result.totalElements());
        verify(queryRepository).findAllVisible(null, null, null, 0, 20);
    }

    private ActivityAuditEventRow row(ValidationEntityType entityType, Long entityId) {
        return new ActivityAuditEventRow(
            1L,
            entityType,
            entityId,
            ActivityAuditAction.VALIDATED,
            1L,
            "Admin",
            "ADMIN",
            Instant.parse("2026-05-16T12:00:00Z"),
            ValidationStatus.PENDING_VALIDATION,
            ValidationStatus.VALIDATED,
            null,
            null
        );
    }

    private PlatformUserPrincipal principal(Long userId, Long researcherId, String role) {
        UserEntity user = new UserEntity("user@example.test", "Test User", "{noop}password", true, researcherId);
        user.setId(userId);
        user.setRoles(new LinkedHashSet<>(List.of(new RoleEntity(role, role, role))));
        return new PlatformUserPrincipal(user);
    }
}
