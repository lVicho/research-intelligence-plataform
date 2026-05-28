package com.researchintelligence.platform.news.persistence;

import com.researchintelligence.platform.news.domain.NewsArticleStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsArticleRepository extends JpaRepository<NewsArticleEntity, Long> {

    @Query("""
        select n
        from NewsArticleEntity n
        where (:status is null or n.status = :status)
        and (:text is null
            or lower(n.title) like :textPattern
            or lower(n.summary) like :textPattern
            or lower(n.body) like :textPattern)
        """)
    Page<NewsArticleEntity> searchAdmin(
        @Param("status") NewsArticleStatus status,
        @Param("text") String text,
        @Param("textPattern") String textPattern,
        Pageable pageable
    );

    @Query("""
        select n
        from NewsArticleEntity n
        where n.status = :status
        and (:text is null
            or lower(n.title) like :textPattern
            or lower(n.summary) like :textPattern
            or lower(n.body) like :textPattern)
        """)
    Page<NewsArticleEntity> searchPublic(
        @Param("status") NewsArticleStatus status,
        @Param("text") String text,
        @Param("textPattern") String textPattern,
        Pageable pageable
    );

    Optional<NewsArticleEntity> findByIdAndStatus(Long id, NewsArticleStatus status);
}
