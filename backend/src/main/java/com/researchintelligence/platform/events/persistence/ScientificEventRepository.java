package com.researchintelligence.platform.events.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ScientificEventRepository extends JpaRepository<ScientificEventEntity, Long>, JpaSpecificationExecutor<ScientificEventEntity> {
}
