package com.researchintelligence.platform.auth.application;

import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class VisibilityContext {

    public Optional<PlatformUserPrincipal> currentUser() {
        return currentAuthentication()
            .map(Authentication::getPrincipal)
            .filter(PlatformUserPrincipal.class::isInstance)
            .map(PlatformUserPrincipal.class::cast);
    }

    public Set<String> currentRoles() {
        Optional<PlatformUserPrincipal> currentUser = currentUser();
        if (currentUser.isPresent()) {
            return new LinkedHashSet<>(currentUser.get().roles());
        }
        return currentAuthentication()
            .stream()
            .flatMap(authentication -> authentication.getAuthorities().stream())
            .map(GrantedAuthority::getAuthority)
            .map(this::roleCode)
            .filter(role -> !role.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Optional<Long> linkedResearcherId() {
        return currentUser().map(PlatformUserPrincipal::researcherId);
    }

    public VisibilityScope defaultScope() {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN")) {
            return VisibilityScope.ADMIN_ALL;
        }
        if (roles.contains("RESEARCHER") && linkedResearcherId().isPresent()) {
            return VisibilityScope.MY_DATA;
        }
        return VisibilityScope.PUBLIC_VALIDATED;
    }

    private Optional<Authentication> currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(authentication);
    }

    private String roleCode(String authority) {
        if (authority == null) {
            return "";
        }
        if (authority.startsWith("ROLE_")) {
            return authority.substring("ROLE_".length());
        }
        return authority;
    }
}
