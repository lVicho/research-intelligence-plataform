package com.researchintelligence.platform.publications.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicationTopicRepository extends JpaRepository<PublicationTopicEntity, PublicationTopicId> {

    List<PublicationTopicEntity> findByPublicationId(Long publicationId);

    List<PublicationTopicEntity> findByPublicationIdIn(Collection<Long> publicationIds);

    @Query("""
        select pt.topicId, count(distinct pt.publicationId)
        from PublicationTopicEntity pt
        where pt.topicId in :topicIds
        group by pt.topicId
        """)
    List<Object[]> countPublicationsByTopicIds(@Param("topicIds") Collection<Long> topicIds);

    @Query("""
        select count(distinct pt.publicationId)
        from PublicationTopicEntity pt
        where pt.topicId in :topicIds
        """)
    long countDistinctPublicationsByTopicIds(@Param("topicIds") Collection<Long> topicIds);

    @Modifying
    @Query("delete from PublicationTopicEntity pt where pt.publicationId = :publicationId")
    void deleteByPublicationId(@Param("publicationId") Long publicationId);

    @Modifying
    @Query(value = """
        insert into publication_topics (publication_id, topic_id)
        select distinct pt.publication_id, :canonicalTopicId
        from publication_topics pt
        where pt.topic_id in (:sourceTopicIds)
        and not exists (
            select 1
            from publication_topics existing
            where existing.publication_id = pt.publication_id
            and existing.topic_id = :canonicalTopicId
        )
        """, nativeQuery = true)
    int insertMissingCanonicalLinks(
        @Param("canonicalTopicId") Long canonicalTopicId,
        @Param("sourceTopicIds") Collection<Long> sourceTopicIds
    );

    @Modifying
    @Query("delete from PublicationTopicEntity pt where pt.topicId in :topicIds")
    int deleteByTopicIdIn(@Param("topicIds") Collection<Long> topicIds);
}
