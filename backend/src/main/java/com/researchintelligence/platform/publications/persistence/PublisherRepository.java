package com.researchintelligence.platform.publications.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PublisherRepository extends JpaRepository<PublisherEntity, Long>, JpaSpecificationExecutor<PublisherEntity> {
}
