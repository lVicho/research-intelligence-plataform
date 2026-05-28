package com.researchintelligence.platform.publications.persistence;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicationAuthorRepository extends JpaRepository<PublicationAuthorEntity, Long> {

    List<PublicationAuthorEntity> findByPublicationIdOrderByAuthorOrderAsc(Long publicationId);

    List<PublicationAuthorEntity> findByPublicationIdIn(Collection<Long> publicationIds);

    boolean existsByPublicationIdAndResearcherId(Long publicationId, Long researcherId);

    @Query("""
        select distinct pa.publicationId
        from PublicationAuthorEntity pa
        where pa.researcherId = :researcherId
        """)
    List<Long> findPublicationIdsByResearcherId(@Param("researcherId") Long researcherId);

    @Query("""
        select r.id, r.fullName, count(distinct pa.publicationId)
        from PublicationAuthorEntity own
        join PublicationAuthorEntity pa on pa.publicationId = own.publicationId
        join ResearcherEntity r on r.id = pa.researcherId
        join PublicationEntity p on p.id = pa.publicationId
        where own.researcherId = :researcherId
        and pa.researcherId is not null
        and pa.researcherId <> :researcherId
        and (:validationStatus is null or p.validationStatus = :validationStatus)
        and (:validationStatus is null or r.validationStatus = :validationStatus)
        group by r.id, r.fullName
        order by count(distinct pa.publicationId) desc, r.fullName asc
        """)
    List<Object[]> findInternalCoauthorsByResearcherId(
        @Param("researcherId") Long researcherId,
        @Param("validationStatus") ValidationStatus validationStatus
    );

    @Query("""
        select pa.externalAuthorName, count(distinct pa.publicationId)
        from PublicationAuthorEntity own
        join PublicationAuthorEntity pa on pa.publicationId = own.publicationId
        join PublicationEntity p on p.id = pa.publicationId
        where own.researcherId = :researcherId
        and pa.researcherId is null
        and pa.externalAuthorName is not null
        and (:validationStatus is null or p.validationStatus = :validationStatus)
        group by pa.externalAuthorName
        order by count(distinct pa.publicationId) desc, pa.externalAuthorName asc
        """)
    List<Object[]> findExternalCoauthorsByResearcherId(
        @Param("researcherId") Long researcherId,
        @Param("validationStatus") ValidationStatus validationStatus
    );

    @Modifying
    @Query("delete from PublicationAuthorEntity pa where pa.publicationId = :publicationId")
    void deleteByPublicationId(@Param("publicationId") Long publicationId);
}
