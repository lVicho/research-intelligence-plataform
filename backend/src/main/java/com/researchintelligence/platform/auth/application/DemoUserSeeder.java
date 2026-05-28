package com.researchintelligence.platform.auth.application;

import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.RoleRepository;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.auth.persistence.UserRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoUserSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "demo123";

    private final UserRepository users;
    private final RoleRepository roles;
    private final ResearcherRepository researchers;
    private final PasswordEncoder passwordEncoder;

    public DemoUserSeeder(
        UserRepository users,
        RoleRepository roles,
        ResearcherRepository researchers,
        PasswordEncoder passwordEncoder
    ) {
        this.users = users;
        this.roles = roles;
        this.researchers = researchers;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long researcherId = researchers.findById(1L)
            .map(researcher -> researcher.getId())
            .orElse(null);
        Long mayaChenId = researchers.findById(9001L)
            .map(researcher -> researcher.getId())
            .orElse(researcherId);
        Long carlaSerraId = researchers.findById(9006L)
            .map(researcher -> researcher.getId())
            .orElse(researcherId);
        Long inesCarvalhoId = researchers.findById(9003L)
            .map(researcher -> researcher.getId())
            .orElse(researcherId);

        seedUser("admin@demo.local", "Administrador demo", List.of("ADMIN"), null);
        seedUser("validator@demo.local", "Validador demo", List.of("VALIDATOR"), null);
        seedUser("researcher@demo.local", "Investigador demo", List.of("RESEARCHER"), researcherId);
        seedUser("researcher1@demo.local", "Maya Chen demo", List.of("RESEARCHER"), mayaChenId);
        seedUser("researcher2@demo.local", "Carla Serra demo", List.of("RESEARCHER"), carlaSerraId);
        seedUser("researcher3@demo.local", "Ines Carvalho demo", List.of("RESEARCHER"), inesCarvalhoId);
    }

    private void seedUser(String email, String displayName, List<String> roleCodes, Long researcherId) {
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        UserEntity user = new UserEntity(email, displayName, passwordEncoder.encode(DEMO_PASSWORD), true, researcherId);
        user.setRoles(new LinkedHashSet<>(findRoles(roleCodes)));
        users.save(user);
    }

    private Set<RoleEntity> findRoles(List<String> roleCodes) {
        Set<RoleEntity> foundRoles = new LinkedHashSet<>(roles.findByCodeIn(roleCodes));
        if (foundRoles.size() != roleCodes.size()) {
            throw new IllegalStateException("Missing seeded role for demo users");
        }
        return foundRoles;
    }
}
