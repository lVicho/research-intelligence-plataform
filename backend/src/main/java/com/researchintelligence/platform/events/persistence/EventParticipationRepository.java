package com.researchintelligence.platform.events.persistence;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventParticipationRepository extends JpaRepository<EventParticipationEntity, Long>, JpaSpecificationExecutor<EventParticipationEntity> {

    Optional<EventParticipationEntity> findByIdAndResearcherId(Long id, Long researcherId);

    @Query("""
        select count(ep)
        from EventParticipationEntity ep
        join ScientificEventEntity se on se.id = ep.eventId
        join ResearcherEntity r on r.id = ep.researcherId
        left join ResearchUnitEntity ru on ru.id = ep.researchUnitId
        where ep.validationStatus = :validationStatus
        and se.validationStatus = :validationStatus
        and r.validationStatus = :validationStatus
        and (ep.researchUnitId is null or ru.validationStatus = :validationStatus)
        """)
    long countPublicValidated(@Param("validationStatus") ValidationStatus validationStatus);

    @Query("""
        select ep
        from EventParticipationEntity ep
        join ScientificEventEntity se on se.id = ep.eventId
        join ResearcherEntity r on r.id = ep.researcherId
        left join ResearchUnitEntity ru on ru.id = ep.researchUnitId
        where ep.researcherId = :researcherId
        and ep.validationStatus = :validationStatus
        and se.validationStatus = :validationStatus
        and r.validationStatus = :validationStatus
        and (ep.researchUnitId is null or ru.validationStatus = :validationStatus)
        order by ep.participationDate desc, ep.createdAt desc
        """)
    java.util.List<EventParticipationEntity> findPublicValidatedByResearcherId(
        @Param("researcherId") Long researcherId,
        @Param("validationStatus") ValidationStatus validationStatus,
        Pageable pageable
    );

    @Query("""
        select ep
        from EventParticipationEntity ep
        join ScientificEventEntity se on se.id = ep.eventId
        join ResearcherEntity r on r.id = ep.researcherId
        join ResearchUnitEntity ru on ru.id = ep.researchUnitId
        where ep.researchUnitId = :researchUnitId
        and ep.validationStatus = :validationStatus
        and se.validationStatus = :validationStatus
        and r.validationStatus = :validationStatus
        and ru.validationStatus = :validationStatus
        order by ep.participationDate desc, ep.createdAt desc
        """)
    java.util.List<EventParticipationEntity> findPublicValidatedByResearchUnitId(
        @Param("researchUnitId") Long researchUnitId,
        @Param("validationStatus") ValidationStatus validationStatus,
        Pageable pageable
    );

    @Query("""
        select ep.validationStatus, count(ep)
        from EventParticipationEntity ep
        group by ep.validationStatus
        order by count(ep) desc, ep.validationStatus asc
        """)
    java.util.List<Object[]> countActivitiesByValidationStatus();
}
