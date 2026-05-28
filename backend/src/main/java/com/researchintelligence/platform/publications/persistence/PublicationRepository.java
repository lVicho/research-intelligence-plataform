package com.researchintelligence.platform.publications.persistence;

import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicationRepository extends JpaRepository<PublicationEntity, Long>, JpaSpecificationExecutor<PublicationEntity> {

    List<PublicationEntity> findTop5ByOrderByCreatedAtDesc();

    long countByValidationStatus(ValidationStatus validationStatus);

    List<PublicationEntity> findByValidationStatusOrderByCreatedAtDesc(ValidationStatus validationStatus, Pageable pageable);

    @Query("""
        select p
        from PublicationEntity p
        where (:validationStatus is null or p.validationStatus = :validationStatus)
        order by p.createdAt desc
        """)
    List<PublicationEntity> findRecentByValidationStatus(
        @Param("validationStatus") ValidationStatus validationStatus,
        Pageable pageable
    );

    Optional<PublicationEntity> findFirstByDoiIgnoreCase(String doi);

    List<PublicationEntity> findByPublicationYear(Integer publicationYear);

    @Query("""
        select p
        from PublicationEntity p
        where p.id <> :publicationId
        and lower(trim(p.title)) = lower(trim(:title))
        and coalesce(p.publicationYear, -1) = coalesce(:publicationYear, -1)
        order by p.id asc
        """)
    List<PublicationEntity> findDuplicateCandidates(
        @Param("publicationId") Long publicationId,
        @Param("title") String title,
        @Param("publicationYear") Integer publicationYear,
        Pageable pageable
    );

    @Query("""
        select distinct p
        from PublicationEntity p
        where exists (
            select pa.id
            from PublicationAuthorEntity pa
            where pa.publicationId = p.id
            and pa.researcherId = :researcherId
        )
        order by p.publicationYear desc, p.title asc
        """)
    List<PublicationEntity> findAuthoredByResearcherId(@Param("researcherId") Long researcherId);

    @Query("""
        select distinct p
        from PublicationEntity p
        join PublicationAuthorEntity pa on pa.publicationId = p.id
        where pa.researcherId = :researcherId
        and p.validationStatus = :validationStatus
        order by coalesce(p.publicationYear, 0) desc, p.title asc
        """)
    List<PublicationEntity> findValidatedByResearcherId(
        @Param("researcherId") Long researcherId,
        @Param("validationStatus") ValidationStatus validationStatus,
        Pageable pageable
    );

    @Query("""
        select distinct p
        from PublicationEntity p
        join PublicationAuthorEntity pa on pa.publicationId = p.id
        join ResearcherEntity r on r.id = pa.researcherId
        join ResearcherAffiliationEntity a on a.researcherId = r.id
        join ResearchUnitEntity ru on ru.id = a.researchUnitId
        where a.researchUnitId = :researchUnitId
        and (a.endDate is null or a.endDate >= current_date)
        and p.validationStatus = :validationStatus
        and r.validationStatus = :validationStatus
        and a.validationStatus = :validationStatus
        and ru.validationStatus = :validationStatus
        order by coalesce(p.publicationYear, 0) desc, p.title asc
        """)
    List<PublicationEntity> findValidatedByResearchUnitId(
        @Param("researchUnitId") Long researchUnitId,
        @Param("validationStatus") ValidationStatus validationStatus,
        Pageable pageable
    );

    @Query("""
        select distinct p
        from PublicationEntity p
        join PublicationTopicEntity pt on pt.publicationId = p.id
        join TopicEntity t on t.id = pt.topicId
        where p.validationStatus = :validationStatus
        and (:topicId is null or t.id = :topicId)
        and (:topicText is null or lower(t.normalizedName) = :topicText or lower(t.name) like :topicPattern)
        order by coalesce(p.publicationYear, 0) desc, p.title asc
        """)
    List<PublicationEntity> findValidatedByTopic(
        @Param("topicId") Long topicId,
        @Param("topicText") String topicText,
        @Param("topicPattern") String topicPattern,
        @Param("validationStatus") ValidationStatus validationStatus,
        Pageable pageable
    );

    @Query("""
        select distinct p
        from PublicationEntity p
        where p.validationStatus = :validationStatus
        and (:text is null
            or lower(p.title) like :textPattern
            or lower(coalesce(p.abstractText, '')) like :textPattern
            or lower(coalesce(p.publicSummary, '')) like :textPattern
            or lower(coalesce(p.source, '')) like :textPattern)
        order by coalesce(p.publicationYear, 0) desc, p.title asc
        """)
    List<PublicationEntity> findValidatedByText(
        @Param("text") String text,
        @Param("textPattern") String textPattern,
        @Param("validationStatus") ValidationStatus validationStatus,
        Pageable pageable
    );

    @Query("""
        select distinct p
        from PublicationEntity p
        where (:text is null
            or lower(p.title) like :textPattern
            or lower(coalesce(p.abstractText, '')) like :textPattern
            or lower(coalesce(p.publicSummary, '')) like :textPattern
            or lower(coalesce(p.source, '')) like :textPattern
            or lower(coalesce(p.sourceDetail, '')) like :textPattern
            or lower(coalesce(p.doi, '')) like :textPattern
            or lower(coalesce(p.isbn, '')) like :textPattern
            or lower(coalesce(p.issn, '')) like :textPattern
            or lower(coalesce(p.languageCode, '')) like :textPattern)
        and (:yearFrom is null or p.publicationYear >= :yearFrom)
        and (:yearTo is null or p.publicationYear <= :yearTo)
        and (:type is null or p.type = :type)
        and (:status is null or p.status = :status)
        and (:researcherId is null or exists (
            select pa.id
            from PublicationAuthorEntity pa
            where pa.publicationId = p.id
            and pa.researcherId = :researcherId
        ))
        and (:researchUnitId is null or exists (
            select pa.id
            from PublicationAuthorEntity pa
            where pa.publicationId = p.id
            and pa.researcherId is not null
            and exists (
                select a.id
                from ResearcherAffiliationEntity a
                where a.researcherId = pa.researcherId
                and a.researchUnitId = :researchUnitId
                and (a.endDate is null or a.endDate >= current_date)
            )
        ))
        and (:topic is null or exists (
            select pt.publicationId
            from PublicationTopicEntity pt, TopicEntity t
            where pt.publicationId = p.id
            and t.id = pt.topicId
            and (lower(t.normalizedName) = :topic or lower(t.name) like :topicPattern)
        ))
        """)
    Page<PublicationEntity> search(
        @Param("text") String text,
        @Param("textPattern") String textPattern,
        @Param("yearFrom") Integer yearFrom,
        @Param("yearTo") Integer yearTo,
        @Param("type") PublicationType type,
        @Param("status") PublicationStatus status,
        @Param("researchUnitId") Long researchUnitId,
        @Param("researcherId") Long researcherId,
        @Param("topic") String topic,
        @Param("topicPattern") String topicPattern,
        Pageable pageable
    );

    @Query("""
        select min(p.publicationYear)
        from PublicationEntity p
        where p.publicationYear is not null
        """)
    Integer findMinPublicationYear();

    @Query("""
        select max(p.publicationYear)
        from PublicationEntity p
        where p.publicationYear is not null
        """)
    Integer findMaxPublicationYear();

    @Query("""
        select max(p.publicationYear)
        from PublicationEntity p
        where p.publicationYear is not null
        and (:validationStatus is null or p.validationStatus = :validationStatus)
        """)
    Integer findMaxPublicationYear(@Param("validationStatus") ValidationStatus validationStatus);

    @Query("""
        select p.publicationYear, count(p)
        from PublicationEntity p
        where p.publicationYear is not null
        group by p.publicationYear
        order by p.publicationYear asc
        """)
    List<Object[]> countPublicationsByYear();

    @Query("""
        select p.publicationYear, count(p)
        from PublicationEntity p
        where p.publicationYear is not null
        and (:validationStatus is null or p.validationStatus = :validationStatus)
        group by p.publicationYear
        order by p.publicationYear asc
        """)
    List<Object[]> countPublicationsByYear(@Param("validationStatus") ValidationStatus validationStatus);

    @Query("""
        select p.type, count(p)
        from PublicationEntity p
        group by p.type
        order by count(p) desc, p.type asc
        """)
    List<Object[]> countPublicationsByType();

    @Query("""
        select p.type, count(p)
        from PublicationEntity p
        where (:validationStatus is null or p.validationStatus = :validationStatus)
        group by p.type
        order by count(p) desc, p.type asc
        """)
    List<Object[]> countPublicationsByType(@Param("validationStatus") ValidationStatus validationStatus);

    @Query("""
        select p.status, count(p)
        from PublicationEntity p
        group by p.status
        order by count(p) desc, p.status asc
        """)
    List<Object[]> countPublicationsByStatus();

    @Query("""
        select p.status, count(p)
        from PublicationEntity p
        where (:validationStatus is null or p.validationStatus = :validationStatus)
        group by p.status
        order by count(p) desc, p.status asc
        """)
    List<Object[]> countPublicationsByStatus(@Param("validationStatus") ValidationStatus validationStatus);

    @Query("""
        select ru.id, ru.name, count(distinct p.id)
        from ResearchUnitEntity ru
        join ResearcherAffiliationEntity a on a.researchUnitId = ru.id
        join PublicationAuthorEntity pa on pa.researcherId = a.researcherId
        join PublicationEntity p on p.id = pa.publicationId
        where a.endDate is null or a.endDate >= current_date
        group by ru.id, ru.name
        order by count(distinct p.id) desc, ru.name asc
        """)
    List<Object[]> countPublicationsByResearchUnit();

    @Query("""
        select ru.id, ru.name, count(distinct p.id)
        from ResearchUnitEntity ru
        join ResearcherAffiliationEntity a on a.researchUnitId = ru.id
        join PublicationAuthorEntity pa on pa.researcherId = a.researcherId
        join PublicationEntity p on p.id = pa.publicationId
        join ResearcherEntity r on r.id = pa.researcherId
        where (a.endDate is null or a.endDate >= current_date)
        and (:validationStatus is null
            or (
                p.validationStatus = :validationStatus
                and r.validationStatus = :validationStatus
                and a.validationStatus = :validationStatus
                and ru.validationStatus = :validationStatus
            ))
        group by ru.id, ru.name
        order by count(distinct p.id) desc, ru.name asc
        """)
    List<Object[]> countPublicationsByResearchUnit(@Param("validationStatus") ValidationStatus validationStatus);

    @Query("""
        select r.id, r.fullName, count(distinct pa.publicationId)
        from ResearcherEntity r
        join PublicationAuthorEntity pa on pa.researcherId = r.id
        group by r.id, r.fullName
        order by count(distinct pa.publicationId) desc, r.fullName asc
        """)
    List<Object[]> findTopResearchersByPublicationCount(Pageable pageable);

    @Query("""
        select r.id, r.fullName, count(distinct pa.publicationId)
        from ResearcherEntity r
        join PublicationAuthorEntity pa on pa.researcherId = r.id
        join PublicationEntity p on p.id = pa.publicationId
        where (:validationStatus is null
            or (
                p.validationStatus = :validationStatus
                and r.validationStatus = :validationStatus
            ))
        group by r.id, r.fullName
        order by count(distinct pa.publicationId) desc, r.fullName asc
        """)
    List<Object[]> findTopResearchersByPublicationCount(
        @Param("validationStatus") ValidationStatus validationStatus,
        Pageable pageable
    );

    @Query("""
        select t.id, t.name, count(distinct pt.publicationId)
        from TopicEntity t
        join PublicationTopicEntity pt on pt.topicId = t.id
        group by t.id, t.name
        order by count(distinct pt.publicationId) desc, t.name asc
        """)
    List<Object[]> findTopTopicsByPublicationCount(Pageable pageable);

    @Query("""
        select t.id, t.name, count(distinct p.id)
        from TopicEntity t
        join PublicationTopicEntity pt on pt.topicId = t.id
        join PublicationEntity p on p.id = pt.publicationId
        where (:validationStatus is null or p.validationStatus = :validationStatus)
        group by t.id, t.name
        order by count(distinct p.id) desc, t.name asc
        """)
    List<Object[]> findTopTopicsByPublicationCount(
        @Param("validationStatus") ValidationStatus validationStatus,
        Pageable pageable
    );

    @Query("""
        select t.id, t.name, count(distinct p.id)
        from TopicEntity t
        join PublicationTopicEntity pt on pt.topicId = t.id
        join PublicationEntity p on p.id = pt.publicationId
        where p.validationStatus = :validationStatus
        group by t.id, t.name
        order by count(distinct p.id) desc, t.name asc
        """)
    List<Object[]> findTopValidatedTopicsByPublicationCount(
        @Param("validationStatus") ValidationStatus validationStatus,
        Pageable pageable
    );

    @Query("""
        select t.id, t.name, count(distinct p.id)
        from TopicEntity t
        join PublicationTopicEntity pt on pt.topicId = t.id
        join PublicationEntity p on p.id = pt.publicationId
        join PublicationAuthorEntity pa on pa.publicationId = p.id
        where pa.researcherId = :researcherId
        and p.validationStatus = :validationStatus
        group by t.id, t.name
        order by count(distinct p.id) desc, t.name asc
        """)
    List<Object[]> findTopValidatedTopicsByResearcher(
        @Param("researcherId") Long researcherId,
        @Param("validationStatus") ValidationStatus validationStatus,
        Pageable pageable
    );

    @Query("""
        select t.id, t.name, count(distinct p.id)
        from TopicEntity t
        join PublicationTopicEntity pt on pt.topicId = t.id
        join PublicationEntity p on p.id = pt.publicationId
        join PublicationAuthorEntity pa on pa.publicationId = p.id
        join ResearcherEntity r on r.id = pa.researcherId
        join ResearcherAffiliationEntity a on a.researcherId = r.id
        join ResearchUnitEntity ru on ru.id = a.researchUnitId
        where ru.id = :researchUnitId
        and (a.endDate is null or a.endDate >= current_date)
        and p.validationStatus = :validationStatus
        and r.validationStatus = :validationStatus
        and a.validationStatus = :validationStatus
        and ru.validationStatus = :validationStatus
        group by t.id, t.name
        order by count(distinct p.id) desc, t.name asc
        """)
    List<Object[]> findTopValidatedTopicsByResearchUnit(
        @Param("researchUnitId") Long researchUnitId,
        @Param("validationStatus") ValidationStatus validationStatus,
        Pageable pageable
    );

    @Query("""
        select t.id, t.name, count(distinct p.id)
        from TopicEntity t
        join PublicationTopicEntity pt on pt.topicId = t.id
        join PublicationEntity p on p.id = pt.publicationId
        where (:validationStatus is null or p.validationStatus = :validationStatus)
        group by t.id, t.name
        order by count(distinct p.id) desc, t.name asc
        """)
    List<Object[]> countPublicationsByTopic(@Param("validationStatus") ValidationStatus validationStatus);

    @Query("""
        select t.id, t.name, count(distinct p.id)
        from TopicEntity t
        join PublicationTopicEntity pt on pt.topicId = t.id
        join PublicationEntity p on p.id = pt.publicationId
        where p.publicationYear is not null
        and p.publicationYear >= :yearFrom
        and p.publicationYear <= :yearTo
        and (:validationStatus is null or p.validationStatus = :validationStatus)
        group by t.id, t.name
        order by count(distinct p.id) desc, t.name asc
        """)
    List<Object[]> countPublicationsByTopicInYearRange(
        @Param("validationStatus") ValidationStatus validationStatus,
        @Param("yearFrom") Integer yearFrom,
        @Param("yearTo") Integer yearTo
    );

    @Query(
        value = """
            with publication_units as (
                select distinct
                    p.id as publication_id,
                    a.research_unit_id as research_unit_id
                from publications p
                join publication_authors pa on pa.publication_id = p.id
                join researchers r on r.id = pa.researcher_id
                join researcher_affiliations a on a.researcher_id = pa.researcher_id
                join research_units ru on ru.id = a.research_unit_id
                where (a.end_date is null or a.end_date >= current_date)
                and (:validationStatus is null or (
                    p.validation_status = cast(:validationStatus as varchar)
                    and r.validation_status = cast(:validationStatus as varchar)
                    and a.validation_status = cast(:validationStatus as varchar)
                    and ru.validation_status = cast(:validationStatus as varchar)
                ))
            )
            select
                pu1.research_unit_id as unit_a_id,
                rua.name as unit_a_name,
                pu2.research_unit_id as unit_b_id,
                rub.name as unit_b_name,
                count(distinct pu1.publication_id) as shared_publication_count
            from publication_units pu1
            join publication_units pu2 on pu1.publication_id = pu2.publication_id
                and pu1.research_unit_id < pu2.research_unit_id
            join research_units rua on rua.id = pu1.research_unit_id
            join research_units rub on rub.id = pu2.research_unit_id
            group by pu1.research_unit_id, rua.name, pu2.research_unit_id, rub.name
            order by count(distinct pu1.publication_id) desc, rua.name asc, rub.name asc
            """,
        nativeQuery = true
    )
    List<Object[]> findCollaborationPairs(@Param("validationStatus") String validationStatus, Pageable pageable);

    @Query(
        value = """
            with publication_units as (
                select distinct
                    p.id as publication_id,
                    a.research_unit_id as research_unit_id
                from publications p
                join publication_authors pa on pa.publication_id = p.id
                join researchers r on r.id = pa.researcher_id
                join researcher_affiliations a on a.researcher_id = pa.researcher_id
                join research_units ru on ru.id = a.research_unit_id
                where (a.end_date is null or a.end_date >= current_date)
                and (:validationStatus is null or (
                    p.validation_status = cast(:validationStatus as varchar)
                    and r.validation_status = cast(:validationStatus as varchar)
                    and a.validation_status = cast(:validationStatus as varchar)
                    and ru.validation_status = cast(:validationStatus as varchar)
                ))
            )
            select count(*)
            from (
                select publication_id
                from publication_units
                group by publication_id
                having count(distinct research_unit_id) > 1
            ) cross_publications
            """,
        nativeQuery = true
    )
    long countCrossUnitCollaborations(@Param("validationStatus") String validationStatus);

    @Query("""
        select t.id, t.name, count(distinct pt.publicationId)
        from TopicEntity t
        join PublicationTopicEntity pt on pt.topicId = t.id
        group by t.id, t.name
        order by t.name asc
        """)
    List<Object[]> countTopicsByPublicationCount();
}
