package com.researchintelligence.platform.ai.persistence;

import com.researchintelligence.platform.ai.domain.AiSuggestionStatus;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestionEntity, Long> {

    @Query(
        value = """
            select s
            from AiSuggestionEntity s
            where (:targetType is null or s.targetType = :targetType)
            and (:targetId is null or s.targetId = :targetId)
            and (:suggestionType is null or s.suggestionType = :suggestionType)
            and (:status is null or s.status = :status)
            and (
                :canReviewAll = true
                or (
                    :canReviewValidation = true
                    and s.suggestionType = :validationAssistanceType
                    and s.targetId is not null
                    and s.targetType in ('RESEARCH_UNIT', 'RESEARCHER', 'RESEARCHER_AFFILIATION', 'PUBLICATION', 'EVENT_PARTICIPATION')
                )
                or (
                    :researcherId is not null
                    and (
                        (s.targetType = 'RESEARCHER' and s.targetId = :researcherId)
                        or (
                            s.targetType = 'PUBLICATION'
                            and exists (
                                select pa.id
                                from PublicationAuthorEntity pa
                                where pa.publicationId = s.targetId
                                and pa.researcherId = :researcherId
                            )
                        )
                        or (
                            s.targetType = 'RESEARCHER_AFFILIATION'
                            and exists (
                                select a.id
                                from ResearcherAffiliationEntity a
                                where a.id = s.targetId
                                and a.researcherId = :researcherId
                            )
                        )
                        or (
                            s.targetType = 'EVENT_PARTICIPATION'
                            and exists (
                                select ep.id
                                from EventParticipationEntity ep
                                where ep.id = s.targetId
                                and ep.researcherId = :researcherId
                            )
                        )
                    )
                )
            )
            order by s.createdAt desc, s.id desc
            """,
        countQuery = """
            select count(s)
            from AiSuggestionEntity s
            where (:targetType is null or s.targetType = :targetType)
            and (:targetId is null or s.targetId = :targetId)
            and (:suggestionType is null or s.suggestionType = :suggestionType)
            and (:status is null or s.status = :status)
            and (
                :canReviewAll = true
                or (
                    :canReviewValidation = true
                    and s.suggestionType = :validationAssistanceType
                    and s.targetId is not null
                    and s.targetType in ('RESEARCH_UNIT', 'RESEARCHER', 'RESEARCHER_AFFILIATION', 'PUBLICATION', 'EVENT_PARTICIPATION')
                )
                or (
                    :researcherId is not null
                    and (
                        (s.targetType = 'RESEARCHER' and s.targetId = :researcherId)
                        or (
                            s.targetType = 'PUBLICATION'
                            and exists (
                                select pa.id
                                from PublicationAuthorEntity pa
                                where pa.publicationId = s.targetId
                                and pa.researcherId = :researcherId
                            )
                        )
                        or (
                            s.targetType = 'RESEARCHER_AFFILIATION'
                            and exists (
                                select a.id
                                from ResearcherAffiliationEntity a
                                where a.id = s.targetId
                                and a.researcherId = :researcherId
                            )
                        )
                        or (
                            s.targetType = 'EVENT_PARTICIPATION'
                            and exists (
                                select ep.id
                                from EventParticipationEntity ep
                                where ep.id = s.targetId
                                and ep.researcherId = :researcherId
                            )
                        )
                    )
                )
            )
            """
    )
    Page<AiSuggestionEntity> findVisible(
        @Param("targetType") String targetType,
        @Param("targetId") Long targetId,
        @Param("suggestionType") AiSuggestionType suggestionType,
        @Param("status") AiSuggestionStatus status,
        @Param("canReviewAll") boolean canReviewAll,
        @Param("canReviewValidation") boolean canReviewValidation,
        @Param("validationAssistanceType") AiSuggestionType validationAssistanceType,
        @Param("researcherId") Long researcherId,
        Pageable pageable
    );
}
