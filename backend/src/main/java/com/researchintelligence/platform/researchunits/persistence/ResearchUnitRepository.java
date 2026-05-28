package com.researchintelligence.platform.researchunits.persistence;

import com.researchintelligence.platform.researchunits.domain.OrganizationScope;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResearchUnitRepository extends JpaRepository<ResearchUnitEntity, Long> {

    long countByActiveTrue();

    long countByValidationStatus(ValidationStatus validationStatus);

    long countByActiveTrueAndValidationStatus(ValidationStatus validationStatus);

    List<ResearchUnitEntity> findByParentIdAndValidationStatusOrderByNameAsc(Long parentId, ValidationStatus validationStatus);

    @Query("""
        select ru
        from ResearchUnitEntity ru
        where ru.id <> :researchUnitId
        and ru.organizationScope = :organizationScope
        and lower(trim(ru.name)) = lower(trim(:name))
        order by ru.name asc, ru.id asc
        """)
    List<ResearchUnitEntity> findDuplicateNameCandidates(
        @Param("researchUnitId") Long researchUnitId,
        @Param("name") String name,
        @Param("organizationScope") OrganizationScope organizationScope,
        Pageable pageable
    );

    @Query("""
        select ru
        from ResearchUnitEntity ru
        where lower(trim(ru.name)) = lower(trim(:name))
        or lower(trim(coalesce(ru.shortName, ''))) = lower(trim(:name))
        order by ru.name asc, ru.id asc
        """)
    List<ResearchUnitEntity> findOrganizationNameCandidates(
        @Param("name") String name,
        Pageable pageable
    );

    @Query("""
        select count(distinct ru.id)
        from ResearchUnitEntity ru
        where ru.validationStatus = :validationStatus
        and (
            ru.active = true
            or exists (
                select p.id
                from PublicationEntity p
                join PublicationAuthorEntity pa on pa.publicationId = p.id
                join ResearcherEntity r on r.id = pa.researcherId
                join ResearcherAffiliationEntity a on a.researcherId = r.id
                where a.researchUnitId = ru.id
                and (a.endDate is null or a.endDate >= current_date)
                and p.validationStatus = :validationStatus
                and r.validationStatus = :validationStatus
                and a.validationStatus = :validationStatus
            )
            or exists (
                select ep.id
                from EventParticipationEntity ep
                join ScientificEventEntity se on se.id = ep.eventId
                join ResearcherEntity eventResearcher on eventResearcher.id = ep.researcherId
                where ep.researchUnitId = ru.id
                and ep.validationStatus = :validationStatus
                and se.validationStatus = :validationStatus
                and eventResearcher.validationStatus = :validationStatus
            )
        )
        """)
    long countPublicValidated(@Param("validationStatus") ValidationStatus validationStatus);

    @Query("""
        select count(distinct ru.id)
        from ResearchUnitEntity ru
        where ru.validationStatus = :validationStatus
        and ru.visibleInPortal = true
        and ru.organizationScope = :organizationScope
        and ru.active = true
        """)
    long countPortalVisibleValidated(
        @Param("validationStatus") ValidationStatus validationStatus,
        @Param("organizationScope") OrganizationScope organizationScope
    );

    @Query("""
        select count(distinct ru.id)
        from ResearchUnitEntity ru
        where ru.id = :id
        and ru.validationStatus = :validationStatus
        and ru.visibleInPortal = true
        and ru.organizationScope = :organizationScope
        and ru.active = true
        """)
    long countPortalVisibleValidatedById(
        @Param("id") Long id,
        @Param("validationStatus") ValidationStatus validationStatus,
        @Param("organizationScope") OrganizationScope organizationScope
    );

    @Query("""
        select count(distinct ru.id)
        from ResearchUnitEntity ru
        where ru.id = :id
        and ru.validationStatus = :validationStatus
        and (
            ru.active = true
            or exists (
                select p.id
                from PublicationEntity p
                join PublicationAuthorEntity pa on pa.publicationId = p.id
                join ResearcherEntity r on r.id = pa.researcherId
                join ResearcherAffiliationEntity a on a.researcherId = r.id
                where a.researchUnitId = ru.id
                and (a.endDate is null or a.endDate >= current_date)
                and p.validationStatus = :validationStatus
                and r.validationStatus = :validationStatus
                and a.validationStatus = :validationStatus
            )
            or exists (
                select ep.id
                from EventParticipationEntity ep
                join ScientificEventEntity se on se.id = ep.eventId
                join ResearcherEntity eventResearcher on eventResearcher.id = ep.researcherId
                where ep.researchUnitId = ru.id
                and ep.validationStatus = :validationStatus
                and se.validationStatus = :validationStatus
                and eventResearcher.validationStatus = :validationStatus
            )
        )
        """)
    long countPublicValidatedById(
        @Param("id") Long id,
        @Param("validationStatus") ValidationStatus validationStatus
    );

    @Query("""
        select distinct ru
        from ResearchUnitEntity ru
        where ru.validationStatus = :validationStatus
        and (:type is null or ru.type = :type)
        and (:text is null
            or lower(ru.name) like :textPattern
            or lower(coalesce(ru.shortName, '')) like :textPattern
            or lower(coalesce(ru.city, '')) like :textPattern
            or lower(coalesce(ru.country, '')) like :textPattern)
        and (
            ru.active = true
            or exists (
                select p.id
                from PublicationEntity p
                join PublicationAuthorEntity pa on pa.publicationId = p.id
                join ResearcherEntity r on r.id = pa.researcherId
                join ResearcherAffiliationEntity a on a.researcherId = r.id
                where a.researchUnitId = ru.id
                and (a.endDate is null or a.endDate >= current_date)
                and p.validationStatus = :validationStatus
                and r.validationStatus = :validationStatus
                and a.validationStatus = :validationStatus
            )
            or exists (
                select ep.id
                from EventParticipationEntity ep
                join ScientificEventEntity se on se.id = ep.eventId
                join ResearcherEntity eventResearcher on eventResearcher.id = ep.researcherId
                where ep.researchUnitId = ru.id
                and ep.validationStatus = :validationStatus
                and se.validationStatus = :validationStatus
                and eventResearcher.validationStatus = :validationStatus
            )
        )
        """)
    Page<ResearchUnitEntity> searchPublicValidated(
        @Param("text") String text,
        @Param("textPattern") String textPattern,
        @Param("type") ResearchUnitType type,
        @Param("validationStatus") ValidationStatus validationStatus,
        Pageable pageable
    );

    @Query("""
        select distinct ru
        from ResearchUnitEntity ru
        where ru.validationStatus = :validationStatus
        and ru.visibleInPortal = true
        and ru.organizationScope = :organizationScope
        and ru.active = true
        and (:type is null or ru.type = :type)
        and (:text is null
            or lower(ru.name) like :textPattern
            or lower(coalesce(ru.shortName, '')) like :textPattern
            or lower(coalesce(ru.city, '')) like :textPattern
            or lower(coalesce(ru.country, '')) like :textPattern)
        """)
    Page<ResearchUnitEntity> searchPortalVisibleValidated(
        @Param("text") String text,
        @Param("textPattern") String textPattern,
        @Param("type") ResearchUnitType type,
        @Param("validationStatus") ValidationStatus validationStatus,
        @Param("organizationScope") OrganizationScope organizationScope,
        Pageable pageable
    );
}
