package com.researchintelligence.platform.publications.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<TopicEntity, Long> {

    Optional<TopicEntity> findByNormalizedName(String normalizedName);

    List<TopicEntity> findByNormalizedNameIn(Collection<String> normalizedNames);

    @Query("""
        select t
        from TopicEntity t
        where t.id <> :topicId
        and lower(trim(t.name)) = lower(trim(:name))
        order by t.name asc, t.id asc
        """)
    List<TopicEntity> findDuplicateNameCandidates(
        @Param("topicId") Long topicId,
        @Param("name") String name,
        Pageable pageable
    );
}
