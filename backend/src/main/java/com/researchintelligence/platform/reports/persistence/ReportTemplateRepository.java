package com.researchintelligence.platform.reports.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplateEntity, Long> {

    List<ReportTemplateEntity> findAllByOrderByNameAsc();
}
