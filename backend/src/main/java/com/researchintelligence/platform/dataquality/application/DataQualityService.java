package com.researchintelligence.platform.dataquality.application;

import com.researchintelligence.platform.dataquality.api.DataQualityIssueResponse;
import com.researchintelligence.platform.dataquality.api.DataQualitySummaryResponse;
import com.researchintelligence.platform.dataquality.domain.DataQualityEntityType;
import com.researchintelligence.platform.dataquality.domain.DataQualityIssueType;
import com.researchintelligence.platform.dataquality.domain.DataQualitySeverity;
import com.researchintelligence.platform.dataquality.persistence.DataQualityIssueRow;
import com.researchintelligence.platform.dataquality.persistence.DataQualityRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DataQualityService {

    private final DataQualityRepository repository;

    public DataQualityService(DataQualityRepository repository) {
        this.repository = repository;
    }

    public DataQualitySummaryResponse summary() {
        Map<DataQualityIssueType, Long> counts = repository.countByIssueType();
        return new DataQualitySummaryResponse(
            count(counts, DataQualityIssueType.PUBLICATIONS_WITHOUT_DOI),
            count(counts, DataQualityIssueType.PUBLICATIONS_WITHOUT_ABSTRACT),
            count(counts, DataQualityIssueType.PUBLICATIONS_WITHOUT_PUBLIC_SUMMARY),
            count(counts, DataQualityIssueType.PUBLICATIONS_WITHOUT_TOPICS),
            count(counts, DataQualityIssueType.PUBLICATION_TITLE_CASING_ISSUES),
            count(counts, DataQualityIssueType.RESEARCHERS_WITHOUT_ORCID),
            count(counts, DataQualityIssueType.PUBLICATIONS_WITH_EXTERNAL_AUTHORS),
            count(counts, DataQualityIssueType.UNRESOLVED_EXTERNAL_AUTHORS),
            count(counts, DataQualityIssueType.ACTIVITIES_PENDING_VALIDATION),
            count(counts, DataQualityIssueType.VENUES_WITHOUT_IDENTIFIER),
            count(counts, DataQualityIssueType.EVENTS_WITHOUT_DATES),
            count(counts, DataQualityIssueType.EXTERNAL_ORGANIZATION_DUPLICATE_CANDIDATES),
            count(counts, DataQualityIssueType.DUPLICATE_TOPIC_CANDIDATES),
            count(counts, DataQualityIssueType.DUPLICATE_PUBLICATION_CANDIDATES)
        );
    }

    public PageResponse<DataQualityIssueResponse> issues(
        DataQualityIssueType issueType,
        DataQualitySeverity severity,
        DataQualityEntityType entityType,
        ValidationStatus validationStatus,
        int page,
        int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<DataQualityIssueRow> rows = repository.search(issueType, severity, entityType, validationStatus, safePage, safeSize);
        return PageResponse.from(rows.map(this::toResponse));
    }

    private DataQualityIssueResponse toResponse(DataQualityIssueRow row) {
        return new DataQualityIssueResponse(
            row.issueType(),
            row.severity(),
            row.entityType(),
            row.entityId(),
            row.title(),
            row.description(),
            row.suggestedAction()
        );
    }

    private long count(Map<DataQualityIssueType, Long> counts, DataQualityIssueType issueType) {
        return counts.getOrDefault(issueType, 0L);
    }
}
