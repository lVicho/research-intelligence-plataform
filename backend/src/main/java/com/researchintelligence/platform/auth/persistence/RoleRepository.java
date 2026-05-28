package com.researchintelligence.platform.auth.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    Optional<RoleEntity> findByCode(String code);

    List<RoleEntity> findByCodeIn(Collection<String> codes);
}
