package com.researchintelligence.platform.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.ai.api.AiSuggestionEditAndAcceptRequest;
import com.researchintelligence.platform.ai.api.AiSuggestionResponse;
import com.researchintelligence.platform.ai.api.AiSuggestionReviewRequest;
import com.researchintelligence.platform.ai.domain.AiSuggestionStatus;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.ai.persistence.AiSuggestionEntity;
import com.researchintelligence.platform.ai.persistence.AiSuggestionRepository;
import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.audit.domain.ActivityAuditAction;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AiSuggestionServiceTest {

    @Mock
    private AiSuggestionRepository repository;

    @Mock
    private VisibilityContext visibilityContext;

    @Mock
    private ActivityAuditService auditService;

    @Mock
    private AiSuggestionApplyDispatcher applyDispatcher;

    @Mock
    private PublicationAuthorRepository publicationAuthorRepository;

    @Mock
    private ResearcherAffiliationRepository researcherAffiliationRepository;

    @Mock
    private EventParticipationRepository eventParticipationRepository;

    private AiSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new AiSuggestionService(
            repository,
            visibilityContext,
            auditService,
            applyDispatcher,
            publicationAuthorRepository,
            researcherAffiliationRepository,
            eventParticipationRepository
        );
        lenient().when(auditService.changes()).thenReturn(new LinkedHashMap<>());
    }

    @Test
    void createsSuggestionAndRecordsGeneratedAuditEvent() {
        when(repository.save(any(AiSuggestionEntity.class))).thenAnswer(invocation -> {
            AiSuggestionEntity entity = invocation.getArgument(0);
            entity.setId(88L);
            return entity;
        });

        AiSuggestionResponse response = service.create(new AiSuggestionCreateCommand(
            " publication ",
            10L,
            AiSuggestionType.PUBLIC_SUMMARY,
            "{\"summary\":\"Texto propuesto\"}",
            "Generated from abstract evidence.",
            "{\"publicationIds\":[10]}",
            "mock",
            "mock-llm"
        ));

        assertEquals(88L, response.id());
        assertEquals("PUBLICATION", response.targetType());
        assertEquals(AiSuggestionStatus.GENERATED, response.status());
        verify(auditService).recordAction(
            eq(ValidationEntityType.AI_SUGGESTION),
            eq(88L),
            eq(ActivityAuditAction.GENERATED),
            eq("AI suggestion generated."),
            anyMap()
        );
    }

    @Test
    void listsSuggestionsWithFiltersForAdmin() {
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(1L, null, "ADMIN")));
        AiSuggestionEntity suggestion = suggestion(20L, "PUBLICATION", 10L, AiSuggestionType.PUBLIC_SUMMARY);
        when(repository.findVisible(
            eq("PUBLICATION"),
            eq(10L),
            eq(AiSuggestionType.PUBLIC_SUMMARY),
            eq(AiSuggestionStatus.GENERATED),
            eq(true),
            eq(false),
            eq(AiSuggestionType.VALIDATION_ASSISTANCE),
            eq(null),
            any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(suggestion), PageRequest.of(0, 10), 1));

        PageResponse<AiSuggestionResponse> response = service.findSuggestions(
            "publication",
            10L,
            AiSuggestionType.PUBLIC_SUMMARY,
            AiSuggestionStatus.GENERATED,
            0,
            10
        );

        assertEquals(1, response.totalElements());
        assertEquals(20L, response.content().getFirst().id());
    }

    @Test
    void acceptsGeneratedSuggestionAndRecordsAuditEvent() {
        PlatformUserPrincipal admin = principal(1L, null, "ADMIN");
        AiSuggestionEntity suggestion = suggestion(20L, "PUBLICATION", 10L, AiSuggestionType.PUBLIC_SUMMARY);
        when(visibilityContext.currentUser()).thenReturn(Optional.of(admin));
        when(repository.findById(20L)).thenReturn(Optional.of(suggestion));
        when(applyDispatcher.apply(suggestion, "{\"summary\":\"Texto propuesto\"}", admin)).thenReturn(AiSuggestionApplyResult.noOp());

        AiSuggestionResponse response = service.accept(20L, new AiSuggestionReviewRequest("Looks good."));

        assertEquals(AiSuggestionStatus.ACCEPTED, response.status());
        assertEquals(1L, response.reviewedByUserId());
        assertNotNull(response.reviewedAt());
        verify(applyDispatcher).apply(suggestion, "{\"summary\":\"Texto propuesto\"}", admin);
        verify(auditService).recordAction(
            eq(ValidationEntityType.AI_SUGGESTION),
            eq(20L),
            eq(ActivityAuditAction.ACCEPTED),
            eq(AiSuggestionApplyResult.noOp().message()),
            any()
        );
    }

    @Test
    void rejectsGeneratedSuggestionAndRecordsAuditEvent() {
        PlatformUserPrincipal admin = principal(1L, null, "ADMIN");
        AiSuggestionEntity suggestion = suggestion(21L, "PUBLICATION", 10L, AiSuggestionType.PUBLIC_SUMMARY);
        when(visibilityContext.currentUser()).thenReturn(Optional.of(admin));
        when(repository.findById(21L)).thenReturn(Optional.of(suggestion));

        AiSuggestionResponse response = service.reject(21L, new AiSuggestionReviewRequest("Not useful."));

        assertEquals(AiSuggestionStatus.REJECTED, response.status());
        assertEquals("Not useful.", response.reviewComment());
        verify(applyDispatcher, never()).apply(any(), any(), any());
        verify(auditService).recordAction(
            eq(ValidationEntityType.AI_SUGGESTION),
            eq(21L),
            eq(ActivityAuditAction.REJECTED),
            eq("AI suggestion rejected."),
            any()
        );
    }

    @Test
    void editAndAcceptStoresHumanEditedPayloadAndRecordsAuditEvent() {
        PlatformUserPrincipal admin = principal(1L, null, "ADMIN");
        AiSuggestionEntity suggestion = suggestion(22L, "PUBLICATION", 10L, AiSuggestionType.PUBLIC_SUMMARY);
        when(visibilityContext.currentUser()).thenReturn(Optional.of(admin));
        when(repository.findById(22L)).thenReturn(Optional.of(suggestion));
        when(applyDispatcher.apply(suggestion, "{\"summary\":\"Texto revisado\"}", admin)).thenReturn(AiSuggestionApplyResult.noOp());

        AiSuggestionResponse response = service.editAndAccept(
            22L,
            new AiSuggestionEditAndAcceptRequest("{\"summary\":\"Texto revisado\"}", "Accepted with edits.")
        );

        assertEquals(AiSuggestionStatus.EDITED, response.status());
        assertEquals("{\"summary\":\"Texto revisado\"}", response.proposedDataJson());
        verify(applyDispatcher).apply(suggestion, "{\"summary\":\"Texto revisado\"}", admin);
        verify(auditService).recordAction(
            eq(ValidationEntityType.AI_SUGGESTION),
            eq(22L),
            eq(ActivityAuditAction.EDITED_ACCEPTED),
            eq(AiSuggestionApplyResult.noOp().message()),
            any()
        );
    }

    @Test
    void cannotReviewAlreadyReviewedSuggestion() {
        PlatformUserPrincipal admin = principal(1L, null, "ADMIN");
        AiSuggestionEntity suggestion = suggestion(23L, "PUBLICATION", 10L, AiSuggestionType.PUBLIC_SUMMARY);
        suggestion.setStatus(AiSuggestionStatus.ACCEPTED);
        when(visibilityContext.currentUser()).thenReturn(Optional.of(admin));
        when(repository.findById(23L)).thenReturn(Optional.of(suggestion));

        assertThrows(BusinessRuleException.class, () -> service.reject(23L, new AiSuggestionReviewRequest("No.")));
    }

    @Test
    void researcherCanAcceptSuggestionForOwnPublication() {
        PlatformUserPrincipal researcher = principal(2L, 7L, "RESEARCHER");
        AiSuggestionEntity suggestion = suggestion(24L, "PUBLICATION", 10L, AiSuggestionType.PUBLICATION_EXPLANATION);
        when(visibilityContext.currentUser()).thenReturn(Optional.of(researcher));
        when(repository.findById(24L)).thenReturn(Optional.of(suggestion));
        when(publicationAuthorRepository.existsByPublicationIdAndResearcherId(10L, 7L)).thenReturn(true);
        when(applyDispatcher.apply(suggestion, "{\"summary\":\"Texto propuesto\"}", researcher)).thenReturn(AiSuggestionApplyResult.noOp());

        AiSuggestionResponse response = service.accept(24L, new AiSuggestionReviewRequest(null));

        assertEquals(AiSuggestionStatus.ACCEPTED, response.status());
    }

    @Test
    void researcherCannotAcceptSuggestionForOtherPublication() {
        PlatformUserPrincipal researcher = principal(2L, 7L, "RESEARCHER");
        AiSuggestionEntity suggestion = suggestion(25L, "PUBLICATION", 10L, AiSuggestionType.PUBLICATION_EXPLANATION);
        when(visibilityContext.currentUser()).thenReturn(Optional.of(researcher));
        when(repository.findById(25L)).thenReturn(Optional.of(suggestion));
        when(publicationAuthorRepository.existsByPublicationIdAndResearcherId(10L, 7L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.accept(25L, new AiSuggestionReviewRequest(null))
        );

        assertEquals(403, exception.getStatusCode().value());
        verify(applyDispatcher, never()).apply(any(), any(), any());
    }

    @Test
    void validatorCanReviewValidationAssistanceSuggestion() {
        PlatformUserPrincipal validator = principal(3L, null, "VALIDATOR");
        AiSuggestionEntity suggestion = suggestion(26L, "PUBLICATION", 10L, AiSuggestionType.VALIDATION_ASSISTANCE);
        when(visibilityContext.currentUser()).thenReturn(Optional.of(validator));
        when(repository.findById(26L)).thenReturn(Optional.of(suggestion));
        when(applyDispatcher.apply(suggestion, "{\"summary\":\"Texto propuesto\"}", validator)).thenReturn(AiSuggestionApplyResult.noOp());

        AiSuggestionResponse response = service.accept(26L, new AiSuggestionReviewRequest(null));

        assertEquals(AiSuggestionStatus.ACCEPTED, response.status());
    }

    @Test
    void validatorCannotReviewNonValidationSuggestion() {
        PlatformUserPrincipal validator = principal(3L, null, "VALIDATOR");
        AiSuggestionEntity suggestion = suggestion(27L, "PUBLICATION", 10L, AiSuggestionType.PUBLIC_SUMMARY);
        when(visibilityContext.currentUser()).thenReturn(Optional.of(validator));
        when(repository.findById(27L)).thenReturn(Optional.of(suggestion));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.accept(27L, new AiSuggestionReviewRequest(null))
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void publicUserCannotReadRawSuggestion() {
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal(4L, null, "PUBLIC_USER")));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.findById(28L));

        assertEquals(403, exception.getStatusCode().value());
        verify(repository, never()).findById(28L);
    }

    private AiSuggestionEntity suggestion(Long id, String targetType, Long targetId, AiSuggestionType suggestionType) {
        AiSuggestionEntity entity = new AiSuggestionEntity(
            targetType,
            targetId,
            suggestionType,
            "{\"summary\":\"Texto propuesto\"}",
            "Suggested from available evidence.",
            null,
            "mock",
            "mock-llm"
        );
        entity.setId(id);
        return entity;
    }

    private PlatformUserPrincipal principal(Long userId, Long researcherId, String role) {
        UserEntity user = new UserEntity("user@example.test", "Test User", "{noop}password", true, researcherId);
        user.setId(userId);
        user.setRoles(new LinkedHashSet<>(List.of(new RoleEntity(role, role, role))));
        return new PlatformUserPrincipal(user);
    }
}
