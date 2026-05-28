package com.researchintelligence.platform.researchers.persistence;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResearcherAffiliationRepository extends JpaRepository<ResearcherAffiliationEntity, Long> {

    List<ResearcherAffiliationEntity> findByResearcherIdOrderByPrimaryAffiliationDescStartDateDescIdAsc(Long researcherId);

    List<ResearcherAffiliationEntity> findByResearcherIdIn(Collection<Long> researcherIds);

    java.util.Optional<ResearcherAffiliationEntity> findByIdAndResearcherId(Long id, Long researcherId);

    @Query("""
        select a
        from ResearcherAffiliationEntity a
        where a.researcherId in :researcherIds
        and a.primaryAffiliation = true
        and (a.endDate is null or a.endDate >= :today)
        """)
    List<ResearcherAffiliationEntity> findCurrentPrimaryByResearcherIds(
        @Param("researcherIds") Collection<Long> researcherIds,
        @Param("today") LocalDate today
    );

    @Query("""
        select count(a)
        from ResearcherAffiliationEntity a
        where a.researcherId = :researcherId
        and a.primaryAffiliation = true
        and (a.endDate is null or a.endDate >= :today)
        """)
    long countCurrentPrimaryAffiliations(@Param("researcherId") Long researcherId, @Param("today") LocalDate today);

    @Query("""
        select count(a)
        from ResearcherAffiliationEntity a
        where a.researcherId = :researcherId
        and a.id <> :affiliationId
        and a.primaryAffiliation = true
        and (a.endDate is null or a.endDate >= :today)
        """)
    long countCurrentPrimaryAffiliationsExcluding(
        @Param("researcherId") Long researcherId,
        @Param("affiliationId") Long affiliationId,
        @Param("today") LocalDate today
    );

    @Query("""
        select count(a)
        from ResearcherAffiliationEntity a
        join ResearcherEntity r on r.id = a.researcherId
        join ResearchUnitEntity ru on ru.id = a.researchUnitId
        where a.researcherId = :researcherId
        and a.primaryAffiliation = true
        and (a.endDate is null or a.endDate >= :today)
        and a.validationStatus = :validationStatus
        and r.validationStatus = :validationStatus
        and ru.validationStatus = :validationStatus
        and ru.visibleInPortal = true
        and ru.organizationScope = com.researchintelligence.platform.researchunits.domain.OrganizationScope.INTERNAL
        and ru.active = true
        """)
    long countCurrentPrimaryAffiliationsVisibleInPortal(
        @Param("researcherId") Long researcherId,
        @Param("validationStatus") ValidationStatus validationStatus,
        @Param("today") LocalDate today
    );

    @Query("""
        select count(a)
        from ResearcherAffiliationEntity a
        where a.endDate is null or a.endDate >= :today
        """)
    long countCurrentAffiliations(@Param("today") LocalDate today);

    @Query("""
        select ru.type, count(distinct a.researcherId)
        from ResearchUnitEntity ru
        join ResearcherAffiliationEntity a on a.researchUnitId = ru.id
        where a.endDate is null or a.endDate >= :today
        group by ru.type
        order by count(distinct a.researcherId) desc, ru.type asc
        """)
    List<Object[]> countCurrentResearchersByResearchUnitType(@Param("today") LocalDate today);

    @Query("""
        select ru.type, count(distinct a.researcherId)
        from ResearchUnitEntity ru
        join ResearcherAffiliationEntity a on a.researchUnitId = ru.id
        join ResearcherEntity r on r.id = a.researcherId
        where (a.endDate is null or a.endDate >= :today)
        and (:validationStatus is null
            or (
                a.validationStatus = :validationStatus
                and r.validationStatus = :validationStatus
                and ru.validationStatus = :validationStatus
            ))
        group by ru.type
        order by count(distinct a.researcherId) desc, ru.type asc
        """)
    List<Object[]> countCurrentResearchersByResearchUnitType(
        @Param("validationStatus") ValidationStatus validationStatus,
        @Param("today") LocalDate today
    );

    @Query("""
        select ru.id, ru.name, count(distinct r.id)
        from ResearchUnitEntity ru
        join ResearcherAffiliationEntity a on a.researchUnitId = ru.id
        join ResearcherEntity r on r.id = a.researcherId
        where r.active = true
        and (a.endDate is null or a.endDate >= :today)
        and (:validationStatus is null
            or (
                a.validationStatus = :validationStatus
                and r.validationStatus = :validationStatus
                and ru.validationStatus = :validationStatus
            ))
        group by ru.id, ru.name
        order by count(distinct r.id) desc, ru.name asc
        """)
    List<Object[]> countActiveResearchersByResearchUnit(
        @Param("validationStatus") ValidationStatus validationStatus,
        @Param("today") LocalDate today
    );
}
