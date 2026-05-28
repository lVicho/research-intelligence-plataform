package com.researchintelligence.platform.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class VisibilityContextTest {

    private final VisibilityContext visibilityContext = new VisibilityContext();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedUsersUsePublicValidatedScope() {
        assertEquals(VisibilityScope.PUBLIC_VALIDATED, visibilityContext.defaultScope());
        assertTrue(visibilityContext.currentUser().isEmpty());
        assertTrue(visibilityContext.currentRoles().isEmpty());
        assertTrue(visibilityContext.linkedResearcherId().isEmpty());
    }

    @Test
    void linkedResearchersUseMyDataScope() {
        authenticate(principal(42L, "RESEARCHER"));

        assertEquals(VisibilityScope.MY_DATA, visibilityContext.defaultScope());
        assertEquals(List.of("RESEARCHER"), List.copyOf(visibilityContext.currentRoles()));
        assertEquals(42L, visibilityContext.linkedResearcherId().orElseThrow());
    }

    @Test
    void adminsUseAdminAllScope() {
        authenticate(principal(null, "ADMIN"));

        assertEquals(VisibilityScope.ADMIN_ALL, visibilityContext.defaultScope());
        assertEquals(List.of("ADMIN"), List.copyOf(visibilityContext.currentRoles()));
        assertTrue(visibilityContext.linkedResearcherId().isEmpty());
    }

    private void authenticate(PlatformUserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            principal,
            principal.getPassword(),
            principal.getAuthorities()
        ));
    }

    private PlatformUserPrincipal principal(Long researcherId, String role) {
        UserEntity user = new UserEntity("user@example.test", "Test User", "{noop}password", true, researcherId);
        user.setRoles(new LinkedHashSet<>(List.of(new RoleEntity(role, role, role))));
        return new PlatformUserPrincipal(user);
    }
}
