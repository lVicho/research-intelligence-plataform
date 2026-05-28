package com.researchintelligence.platform.auth.application;

import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class PlatformUserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final boolean enabled;
    private final Long researcherId;
    private final List<String> roles;
    private final List<GrantedAuthority> authorities;

    public PlatformUserPrincipal(UserEntity user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.displayName = user.getDisplayName();
        this.passwordHash = user.getPasswordHash();
        this.enabled = user.isEnabled();
        this.researcherId = user.getResearcherId();
        this.roles = user.getRoles()
            .stream()
            .map(RoleEntity::getCode)
            .sorted()
            .toList();
        this.authorities = roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .map(GrantedAuthority.class::cast)
            .toList();
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public Long researcherId() {
        return researcherId;
    }

    public List<String> roles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
