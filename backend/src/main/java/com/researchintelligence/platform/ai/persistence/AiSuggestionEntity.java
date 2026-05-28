package com.researchintelligence.platform.ai.persistence;

import com.researchintelligence.platform.ai.domain.AiSuggestionStatus;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "ai_suggestions")
@EntityListeners(AuditingEntityListener.class)
public class AiSuggestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggestion_type", nullable = false)
    private AiSuggestionType suggestionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiSuggestionStatus status = AiSuggestionStatus.GENERATED;

    @Column(name = "proposed_data_json", nullable = false, columnDefinition = "text")
    private String proposedDataJson;

    @Column(nullable = false, columnDefinition = "text")
    private String explanation;

    @Column(name = "evidence_json", columnDefinition = "text")
    private String evidenceJson;

    @Column(name = "model_provider", nullable = false)
    private String modelProvider;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by_user_id", updatable = false)
    private Long createdByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "review_comment")
    private String reviewComment;

    protected AiSuggestionEntity() {
    }

    public AiSuggestionEntity(
        String targetType,
        Long targetId,
        AiSuggestionType suggestionType,
        String proposedDataJson,
        String explanation,
        String evidenceJson,
        String modelProvider,
        String modelName
    ) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.suggestionType = suggestionType;
        this.proposedDataJson = proposedDataJson;
        this.explanation = explanation;
        this.evidenceJson = evidenceJson;
        this.modelProvider = modelProvider;
        this.modelName = modelName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public AiSuggestionType getSuggestionType() {
        return suggestionType;
    }

    public AiSuggestionStatus getStatus() {
        return status;
    }

    public void setStatus(AiSuggestionStatus status) {
        this.status = status;
    }

    public String getProposedDataJson() {
        return proposedDataJson;
    }

    public void setProposedDataJson(String proposedDataJson) {
        this.proposedDataJson = proposedDataJson;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public String getModelName() {
        return modelName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Long getReviewedByUserId() {
        return reviewedByUserId;
    }

    public void setReviewedByUserId(Long reviewedByUserId) {
        this.reviewedByUserId = reviewedByUserId;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }
}
