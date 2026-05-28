package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.ai.api.AiSuggestionEditAndAcceptRequest;
import com.researchintelligence.platform.ai.api.AiSuggestionResponse;
import com.researchintelligence.platform.ai.api.AiSuggestionReviewRequest;
import com.researchintelligence.platform.ai.domain.AiSuggestionStatus;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.ai.persistence.AiSuggestionEntity;
import com.researchintelligence.platform.ai.persistence.AiSuggestionRepository;
import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.audit.application.AuditFieldChange;
import com.researchintelligence.platform.audit.domain.ActivityAuditAction;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AiSuggestionService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String TARGET_RESEARCHER = "RESEARCHER";
    private static final String TARGET_PUBLICATION = "PUBLICATION";
    private static final String TARGET_RESEARCHER_AFFILIATION = "RESEARCHER_AFFILIATION";
    private static final String TARGET_EVENT_PARTICIPATION = "EVENT_PARTICIPATION";

    private final AiSuggestionRepository repository;
    private final VisibilityContext visibilityContext;
    private final ActivityAuditService auditService;
    private final AiSuggestionApplyDispatcher applyDispatcher;
    private final PublicationAuthorRepository publicationAuthorRepository;
    private final ResearcherAffiliationRepository researcherAffiliationRepository;
    private final EventParticipationRepository eventParticipationRepository;

    public AiSuggestionService(
        AiSuggestionRepository repository,
        VisibilityContext visibilityContext,
        ActivityAuditService auditService,
        AiSuggestionApplyDispatcher applyDispatcher,
        PublicationAuthorRepository publicationAuthorRepository,
        ResearcherAffiliationRepository researcherAffiliationRepository,
        EventParticipationRepository eventParticipationRepository
    ) {
        this.repository = repository;
        this.visibilityContext = visibilityContext;
        this.auditService = auditService;
        this.applyDispatcher = applyDispatcher;
        this.publicationAuthorRepository = publicationAuthorRepository;
        this.researcherAffiliationRepository = researcherAffiliationRepository;
        this.eventParticipationRepository = eventParticipationRepository;
    }

    @Transactional
    public AiSuggestionResponse create(AiSuggestionCreateCommand command) {
        validateCreateCommand(command);
        AiSuggestionEntity saved = repository.save(new AiSuggestionEntity(
            normalizeTargetType(command.targetType()),
            command.targetId(),
            command.suggestionType(),
            normalizeRequired(command.proposedDataJson(), "proposedDataJson"),
            normalizeRequired(command.explanation(), "explanation"),
            normalizeOptional(command.evidenceJson()),
            normalizeRequired(command.modelProvider(), "modelProvider"),
            normalizeRequired(command.modelName(), "modelName")
        ));
        Map<String, AuditFieldChange> changes = auditService.changes();
        auditService.addChange(changes, "status", null, AiSuggestionStatus.GENERATED.name());
        auditService.addChange(changes, "suggestionType", null, saved.getSuggestionType().name());
        auditService.addChange(changes, "targetType", null, saved.getTargetType());
        auditSuggestion(saved, ActivityAuditAction.GENERATED, "AI suggestion generated.", changes);
        return toResponse(saved);
    }

    public PageResponse<AiSuggestionResponse> findSuggestions(
        String targetType,
        Long targetId,
        AiSuggestionType suggestionType,
        AiSuggestionStatus status,
        int page,
        int size
    ) {
        PlatformUserPrincipal user = requireReviewer();
        ReviewScope scope = reviewScope(user);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageResponse.from(repository.findVisible(
            normalizeFilter(targetType),
            targetId,
            suggestionType,
            status,
            scope.canReviewAll(),
            scope.canReviewValidation(),
            AiSuggestionType.VALIDATION_ASSISTANCE,
            scope.researcherId(),
            PageRequest.of(safePage, safeSize)
        ).map(this::toResponse));
    }

    public AiSuggestionResponse findById(Long id) {
        AiSuggestionEntity suggestion = findVisibleEntity(id);
        return toResponse(suggestion);
    }

    @Transactional
    public AiSuggestionResponse accept(Long id, AiSuggestionReviewRequest request) {
        AiSuggestionEntity suggestion = findVisibleEntity(id);
        requireGenerated(suggestion);
        PlatformUserPrincipal reviewer = requireReviewer();
        AiSuggestionApplyResult applyResult = applyDispatcher.apply(suggestion, suggestion.getProposedDataJson(), reviewer);
        AiSuggestionStatus previousStatus = suggestion.getStatus();
        markReviewed(suggestion, reviewer, AiSuggestionStatus.ACCEPTED, request == null ? null : request.reviewComment());
        auditReview(suggestion, ActivityAuditAction.ACCEPTED, previousStatus, applyResult, applyResult.message(), false);
        return toResponse(suggestion);
    }

    @Transactional
    public AiSuggestionResponse reject(Long id, AiSuggestionReviewRequest request) {
        AiSuggestionEntity suggestion = findVisibleEntity(id);
        requireGenerated(suggestion);
        PlatformUserPrincipal reviewer = requireReviewer();
        AiSuggestionStatus previousStatus = suggestion.getStatus();
        markReviewed(suggestion, reviewer, AiSuggestionStatus.REJECTED, request == null ? null : request.reviewComment());
        auditReview(suggestion, ActivityAuditAction.REJECTED, previousStatus, AiSuggestionApplyResult.noOp(), "AI suggestion rejected.", false);
        return toResponse(suggestion);
    }

    @Transactional
    public AiSuggestionResponse editAndAccept(Long id, AiSuggestionEditAndAcceptRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Edit-and-accept request is required.");
        }
        String editedDataJson = normalizeRequired(request.proposedDataJson(), "proposedDataJson");
        AiSuggestionEntity suggestion = findVisibleEntity(id);
        requireGenerated(suggestion);
        PlatformUserPrincipal reviewer = requireReviewer();
        AiSuggestionStatus previousStatus = suggestion.getStatus();
        suggestion.setProposedDataJson(editedDataJson);
        AiSuggestionApplyResult applyResult = applyDispatcher.apply(suggestion, editedDataJson, reviewer);
        markReviewed(suggestion, reviewer, AiSuggestionStatus.EDITED, request.reviewComment());
        auditReview(suggestion, ActivityAuditAction.EDITED_ACCEPTED, previousStatus, applyResult, applyResult.message(), true);
        return toResponse(suggestion);
    }

    private AiSuggestionEntity findVisibleEntity(Long id) {
        PlatformUserPrincipal reviewer = requireReviewer();
        AiSuggestionEntity suggestion = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AiSuggestion", id));
        if (!canReview(suggestion, reviewer)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI suggestion is not available to the current user.");
        }
        return suggestion;
    }

    private void markReviewed(
        AiSuggestionEntity suggestion,
        PlatformUserPrincipal reviewer,
        AiSuggestionStatus status,
        String reviewComment
    ) {
        suggestion.setStatus(status);
        suggestion.setReviewedAt(Instant.now());
        suggestion.setReviewedByUserId(reviewer.id());
        suggestion.setReviewComment(normalizeOptional(reviewComment));
    }

    private void auditReview(
        AiSuggestionEntity suggestion,
        ActivityAuditAction action,
        AiSuggestionStatus previousStatus,
        AiSuggestionApplyResult applyResult,
        String comment,
        boolean edited
    ) {
        Map<String, AuditFieldChange> changes = auditService.changes();
        auditService.addChange(changes, "status", previousStatus, suggestion.getStatus());
        auditService.addChange(changes, "reviewedByUserId", null, suggestion.getReviewedByUserId());
        auditService.addChange(changes, "reviewComment", null, suggestion.getReviewComment());
        auditService.addChange(changes, "applyHandler", null, applyResult.handler());
        auditService.addChange(changes, "applied", null, applyResult.applied());
        if (edited) {
            auditService.addChange(changes, "proposedDataJson", "AI generated payload", "Human edited payload");
        }
        auditSuggestion(suggestion, action, comment, changes);
    }

    private void auditSuggestion(
        AiSuggestionEntity suggestion,
        ActivityAuditAction action,
        String comment,
        Map<String, AuditFieldChange> changes
    ) {
        auditService.recordAction(ValidationEntityType.AI_SUGGESTION, suggestion.getId(), action, comment, changes);
    }

    private boolean canReview(AiSuggestionEntity suggestion, PlatformUserPrincipal user) {
        if (user.roles().contains("ADMIN")) {
            return true;
        }
        if (user.roles().contains("VALIDATOR") && isValidationWorkflowSuggestion(suggestion)) {
            return true;
        }
        if (user.roles().contains("RESEARCHER") && user.researcherId() != null) {
            return isOwnedByResearcher(suggestion, user.researcherId());
        }
        return false;
    }

    private boolean isValidationWorkflowSuggestion(AiSuggestionEntity suggestion) {
        return suggestion.getSuggestionType() == AiSuggestionType.VALIDATION_ASSISTANCE
            && suggestion.getTargetId() != null
            && isValidationWorkflowTarget(suggestion.getTargetType());
    }

    private boolean isValidationWorkflowTarget(String targetType) {
        return switch (targetType) {
            case "RESEARCH_UNIT", TARGET_RESEARCHER, TARGET_RESEARCHER_AFFILIATION, TARGET_PUBLICATION, TARGET_EVENT_PARTICIPATION -> true;
            default -> false;
        };
    }

    private boolean isOwnedByResearcher(AiSuggestionEntity suggestion, Long researcherId) {
        Long targetId = suggestion.getTargetId();
        if (targetId == null) {
            return false;
        }
        return switch (suggestion.getTargetType()) {
            case TARGET_RESEARCHER -> targetId.equals(researcherId);
            case TARGET_PUBLICATION -> publicationAuthorRepository.existsByPublicationIdAndResearcherId(targetId, researcherId);
            case TARGET_RESEARCHER_AFFILIATION -> researcherAffiliationRepository.findByIdAndResearcherId(targetId, researcherId).isPresent();
            case TARGET_EVENT_PARTICIPATION -> eventParticipationRepository.findByIdAndResearcherId(targetId, researcherId).isPresent();
            default -> false;
        };
    }

    private PlatformUserPrincipal requireReviewer() {
        PlatformUserPrincipal user = visibilityContext.currentUser()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is required to review AI suggestions."));
        if (user.roles().contains("ADMIN") || user.roles().contains("VALIDATOR") || user.roles().contains("RESEARCHER")) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI suggestions are not available to the current user.");
    }

    private ReviewScope reviewScope(PlatformUserPrincipal user) {
        return new ReviewScope(
            user.roles().contains("ADMIN"),
            user.roles().contains("VALIDATOR"),
            user.roles().contains("RESEARCHER") ? user.researcherId() : null
        );
    }

    private void requireGenerated(AiSuggestionEntity suggestion) {
        if (suggestion.getStatus() != AiSuggestionStatus.GENERATED) {
            throw new BusinessRuleException("Only generated AI suggestions can be reviewed.");
        }
    }

    private void validateCreateCommand(AiSuggestionCreateCommand command) {
        if (command == null) {
            throw new BusinessRuleException("AI suggestion command is required.");
        }
        if (command.suggestionType() == null) {
            throw new BusinessRuleException("suggestionType is required.");
        }
    }

    private AiSuggestionResponse toResponse(AiSuggestionEntity entity) {
        return new AiSuggestionResponse(
            entity.getId(),
            entity.getTargetType(),
            entity.getTargetId(),
            entity.getSuggestionType(),
            entity.getStatus(),
            entity.getProposedDataJson(),
            entity.getExplanation(),
            entity.getEvidenceJson(),
            entity.getModelProvider(),
            entity.getModelName(),
            entity.getCreatedAt(),
            entity.getCreatedByUserId(),
            entity.getReviewedAt(),
            entity.getReviewedByUserId(),
            entity.getReviewComment()
        );
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BusinessRuleException(fieldName + " is required.");
        }
        return normalized;
    }

    private String normalizeFilter(String value) {
        return normalizeOptional(value) == null ? null : normalizeTargetType(value);
    }

    private String normalizeOptional(String value) {
        String normalized = value == null ? null : value.trim();
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String normalizeTargetType(String targetType) {
        String normalized = normalizeOptional(targetType);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private record ReviewScope(
        boolean canReviewAll,
        boolean canReviewValidation,
        Long researcherId
    ) {
    }
}
