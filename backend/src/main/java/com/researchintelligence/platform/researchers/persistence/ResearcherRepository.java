package com.researchintelligence.platform.researchers.persistence;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResearcherRepository extends JpaRepository<ResearcherEntity, Long> {

    long countByActiveTrue();

    long countByValidationStatus(ValidationStatus validationStatus);

    long countByActiveTrueAndValidationStatus(ValidationStatus validationStatus);

    Optional<ResearcherEntity> findFirstByOrcidIgnoreCase(String orcid);

    @Query("""
        select count(distinct r.id)
        from ResearcherEntity r
        where r.active = true
        and r.validationStatus = :validationStatus
        and exists (
            select a.id
            from ResearcherAffiliationEntity a
            join ResearchUnitEntity ru on ru.id = a.researchUnitId
            where a.researcherId = r.id
            and a.primaryAffiliation = true
            and (a.endDate is null or a.endDate >= :today)
            and a.validationStatus = :validationStatus
            and ru.validationStatus = :validationStatus
            and ru.visibleInPortal = true
            and ru.organizationScope = com.researchintelligence.platform.researchunits.domain.OrganizationScope.INTERNAL
            and ru.active = true
        )
        """)
    long countActivePortalVisibleValidated(
        @Param("validationStatus") ValidationStatus validationStatus,
        @Param("today") java.time.LocalDate today
    );

    @Query("""
        select distinct r
        from ResearcherEntity r
        where (:text is null
            or lower(r.fullName) like lower(coalesce(:textPattern, ''))
            or lower(coalesce(r.displayName, '')) like lower(coalesce(:textPattern, ''))
            or lower(coalesce(r.email, '')) like lower(coalesce(:textPattern, ''))
            or lower(coalesce(r.orcid, '')) like lower(coalesce(:textPattern, '')))
        and (:active is null or r.active = :active)
        and (:validationStatus is null or r.validationStatus = :validationStatus)
        and (:researchUnitId is null or exists (
            select a.id
            from ResearcherAffiliationEntity a
            join ResearchUnitEntity ru on ru.id = a.researchUnitId
            where a.researcherId = r.id
            and a.researchUnitId = :researchUnitId
            and (a.endDate is null or a.endDate >= current_date)
            and (:validationStatus is null or a.validationStatus = :validationStatus)
            and (:validationStatus is null or ru.validationStatus = :validationStatus)
        ))
        and (:topic is null or exists (
            select pa.id
            from PublicationAuthorEntity pa
            join PublicationEntity p on p.id = pa.publicationId
            join PublicationTopicEntity pt on pt.publicationId = p.id
            join TopicEntity t on t.id = pt.topicId
            where pa.researcherId = r.id
            and (:validationStatus is null or p.validationStatus = :validationStatus)
            and (lower(t.normalizedName) = :topic or lower(t.name) like :topicPattern)
        ))
        """)
    Page<ResearcherEntity> search(
        @Param("text") String text,
        @Param("textPattern") String textPattern,
        @Param("researchUnitId") Long researchUnitId,
        @Param("active") Boolean active,
        @Param("validationStatus") ValidationStatus validationStatus,
        @Param("topic") String topic,
        @Param("topicPattern") String topicPattern,
        Pageable pageable
    );

    @Query("""
        select distinct r
        from ResearcherEntity r
        where (:text is null
            or lower(r.fullName) like lower(coalesce(:textPattern, ''))
            or lower(coalesce(r.displayName, '')) like lower(coalesce(:textPattern, ''))
            or lower(coalesce(r.email, '')) like lower(coalesce(:textPattern, ''))
            or lower(coalesce(r.orcid, '')) like lower(coalesce(:textPattern, '')))
        and (:active is null or r.active = :active)
        and (:validationStatus is null or r.validationStatus = :validationStatus)
        and exists (
            select aPrimary.id
            from ResearcherAffiliationEntity aPrimary
            join ResearchUnitEntity ruPrimary on ruPrimary.id = aPrimary.researchUnitId
            where aPrimary.researcherId = r.id
            and aPrimary.primaryAffiliation = true
            and (aPrimary.endDate is null or aPrimary.endDate >= current_date)
            and ruPrimary.visibleInPortal = true
            and ruPrimary.organizationScope = com.researchintelligence.platform.researchunits.domain.OrganizationScope.INTERNAL
            and ruPrimary.active = true
            and (:validationStatus is null or aPrimary.validationStatus = :validationStatus)
            and (:validationStatus is null or ruPrimary.validationStatus = :validationStatus)
        )
        and (:researchUnitId is null or exists (
            select a.id
            from ResearcherAffiliationEntity a
            join ResearchUnitEntity ru on ru.id = a.researchUnitId
            where a.researcherId = r.id
            and a.researchUnitId = :researchUnitId
            and (a.endDate is null or a.endDate >= current_date)
            and ru.visibleInPortal = true
            and ru.organizationScope = com.researchintelligence.platform.researchunits.domain.OrganizationScope.INTERNAL
            and ru.active = true
            and (:validationStatus is null or a.validationStatus = :validationStatus)
            and (:validationStatus is null or ru.validationStatus = :validationStatus)
        ))
        and (:topic is null or exists (
            select pa.id
            from PublicationAuthorEntity pa
            join PublicationEntity p on p.id = pa.publicationId
            join PublicationTopicEntity pt on pt.publicationId = p.id
            join TopicEntity t on t.id = pt.topicId
            where pa.researcherId = r.id
            and (:validationStatus is null or p.validationStatus = :validationStatus)
            and (lower(t.normalizedName) = :topic or lower(t.name) like :topicPattern)
        ))
        """)
    Page<ResearcherEntity> searchPortalVisible(
        @Param("text") String text,
        @Param("textPattern") String textPattern,
        @Param("researchUnitId") Long researchUnitId,
        @Param("active") Boolean active,
        @Param("validationStatus") ValidationStatus validationStatus,
        @Param("topic") String topic,
        @Param("topicPattern") String topicPattern,
        Pageable pageable
    );
}
