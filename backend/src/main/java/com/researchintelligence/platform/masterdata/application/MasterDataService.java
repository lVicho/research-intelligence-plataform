package com.researchintelligence.platform.masterdata.application;

import com.researchintelligence.platform.masterdata.api.MasterDataItemResponse;
import com.researchintelligence.platform.masterdata.persistence.MasterDataCategory;
import com.researchintelligence.platform.masterdata.persistence.MasterDataJdbcRepository;
import com.researchintelligence.platform.masterdata.persistence.MasterDataRow;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MasterDataService {

    private final MasterDataJdbcRepository repository;

    public MasterDataService(MasterDataJdbcRepository repository) {
        this.repository = repository;
    }

    public List<MasterDataItemResponse> findPublicationTypes() {
        return findActive(MasterDataCategory.PUBLICATION_TYPES);
    }

    public List<MasterDataItemResponse> findPublicationStatuses() {
        return findActive(MasterDataCategory.PUBLICATION_STATUSES);
    }

    public List<MasterDataItemResponse> findVenueTypes() {
        return findActive(MasterDataCategory.VENUE_TYPES);
    }

    public List<MasterDataItemResponse> findEventTypes() {
        return findActive(MasterDataCategory.EVENT_TYPES);
    }

    public List<MasterDataItemResponse> findEventParticipationTypes() {
        return findActive(MasterDataCategory.EVENT_PARTICIPATION_TYPES);
    }

    private List<MasterDataItemResponse> findActive(MasterDataCategory category) {
        return repository.findActive(category)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private MasterDataItemResponse toResponse(MasterDataRow row) {
        return new MasterDataItemResponse(
            row.id(),
            row.code(),
            row.labelEs(),
            row.descriptionEs(),
            row.active(),
            row.sortOrder(),
            row.createdAt(),
            row.updatedAt()
        );
    }
}
