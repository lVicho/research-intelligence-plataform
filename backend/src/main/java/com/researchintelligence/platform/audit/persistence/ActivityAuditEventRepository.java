package com.researchintelligence.platform.audit.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityAuditEventRepository extends JpaRepository<ActivityAuditEventEntity, Long> {
}
