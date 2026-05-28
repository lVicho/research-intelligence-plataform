package com.researchintelligence.platform.researchers.application;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.audit.application.AuditFieldChange;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.auth.persistence.UserRepository;
import com.researchintelligence.platform.publications.api.PublicationSummaryResponse;
import com.researchintelligence.platform.publications.api.TopicResponse;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicEntity;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.api.ResearcherAffiliationRequest;
import com.researchintelligence.platform.researchers.api.ResearcherAffiliationPublicResponse;
import com.researchintelligence.platform.researchers.api.ResearcherAffiliationResponse;
import com.researchintelligence.platform.researchers.api.ResearcherCoauthorResponse;
import com.researchintelligence.platform.researchers.api.ResearcherRequest;
import com.researchintelligence.platform.researchers.api.ResearcherResponse;
import com.researchintelligence.platform.researchers.api.ResearcherSummaryResponse;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ResearcherService {

    private final ResearcherRepository researcherRepository;
    private final ResearcherAffiliationRepository affiliationRepository;
    private final ResearchUnitRepository researchUnitRepository;
    private final PublicationRepository publicationRepository;
    private final PublicationAuthorRepository publicationAuthorRepository;
    private final PublicationTopicRepository publicationTopicRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final VisibilityContext visibilityContext;
    private final ActivityAuditService auditService;

    public ResearcherService(
        ResearcherRepository researcherRepository,
        ResearcherAffiliationRepository affiliationRepository,
        ResearchUnitRepository researchUnitRepository,
        PublicationRepository publicationRepository,
        PublicationAuthorRepository publicationAuthorRepository,
        PublicationTopicRepository publicationTopicRepository,
        TopicRepository topicRepository,
        UserRepository userRepository,
        VisibilityContext visibilityContext,
        ActivityAuditService auditService
    ) {
        this.researcherRepository = researcherRepository;
        this.affiliationRepository = affiliationRepository;
        this.researchUnitRepository = researchUnitRepository;
        this.publicationRepository = publicationRepository;
        this.publicationAuthorRepository = publicationAuthorRepository;
        this.publicationTopicRepository = publicationTopicRepository;
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
        this.visibilityContext = visibilityContext;
        this.auditService = auditService;
    }

    public PageResponse<ResearcherSummaryResponse> search(int page, int size, String text, Long researchUnitId, Boolean active) {
        return search(page, size, text, researchUnitId, active, null, defaultVisibilityScope(), false);
    }

    public PageResponse<ResearcherSummaryResponse> searchPublicValidated(
        int page,
        int size,
        String text,
        Long researchUnitId,
        String topic
    ) {
        return search(page, size, text, researchUnitId, true, topic, VisibilityScope.PUBLIC_VALIDATED, false);
    }

    public PageResponse<ResearcherSummaryResponse> searchPortalVisibleValidated(
        int page,
        int size,
        String text,
        Long researchUnitId,
        String topic
    ) {
        return search(page, size, text, researchUnitId, true, topic, VisibilityScope.PUBLIC_VALIDATED, true);
    }

    private PageResponse<ResearcherSummaryResponse> search(
        int page,
        int size,
        String text,
        Long researchUnitId,
        Boolean active,
        String topic,
        VisibilityScope visibilityScope,
        boolean portalVisibleOnly
    ) {
        String normalizedText = blankToNull(text);
        String textPattern = normalizedText == null ? "%" : "%" + normalizedText + "%";
        String normalizedTopic = blankToNull(topic);
        if (normalizedTopic != null) {
            normalizedTopic = normalizedTopic.toLowerCase();
        }
        String topicPattern = normalizedTopic == null ? "%" : "%" + normalizedTopic + "%";
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("fullName").ascending());
        Page<ResearcherEntity> researchers = portalVisibleOnly
            ? researcherRepository.searchPortalVisible(
                normalizedText,
                textPattern,
                researchUnitId,
                active,
                validationStatusFilter(visibilityScope),
                normalizedTopic,
                topicPattern,
                pageable
            )
            : researcherRepository.search(
                normalizedText,
                textPattern,
                researchUnitId,
                active,
                validationStatusFilter(visibilityScope),
                normalizedTopic,
                topicPattern,
                pageable
            );
        Long linkedResearcherId = visibilityContext.linkedResearcherId().orElse(null);
        List<ResearcherEntity> visibleResearchers = researchers.getContent().stream()
            .filter(researcher -> isVisibleResearcher(researcher, visibilityScope, linkedResearcherId))
            .toList();
        Map<Long, String> primaryAffiliations = primaryAffiliationNames(visibleResearchers.stream().map(ResearcherEntity::getId).toList(), visibilityScope, linkedResearcherId);
        Map<Long, String> userDisplayNames = userDisplayNames(workflowUserIds(visibleResearchers));
        List<ResearcherSummaryResponse> content = visibleResearchers.stream()
            .map(researcher -> ResearcherMapper.toSummary(
                researcher,
                primaryAffiliations.get(researcher.getId()),
                submittedBy(researcher, userDisplayNames, visibilityScope, linkedResearcherId),
                validatedBy(researcher, userDisplayNames, visibilityScope, linkedResearcherId),
                canEditResearcher(researcher, visibilityScope, linkedResearcherId),
                canSubmitResearcher(researcher, visibilityScope, linkedResearcherId),
                canValidate(researcher.getValidationStatus())
            ))
            .toList();
        return new PageResponse<>(
            content,
            researchers.getNumber(),
            researchers.getSize(),
            researchers.getTotalElements(),
            researchers.getTotalPages(),
            researchers.isLast()
        );
    }

    public ResearcherResponse findById(Long id) {
        VisibilityScope visibilityScope = defaultVisibilityScope();
        ResearcherEntity researcher = findVisibleResearcher(id, visibilityScope, visibilityContext.linkedResearcherId().orElse(null));
        return toDetailResponse(researcher, visibilityScope);
    }

    public ResearcherResponse findPublicValidatedById(Long id) {
        ResearcherEntity researcher = findVisibleResearcher(id, VisibilityScope.PUBLIC_VALIDATED, null);
        return toDetailResponse(researcher, VisibilityScope.PUBLIC_VALIDATED);
    }

    public ResearcherResponse findPortalVisibleValidatedById(Long id) {
        ResearcherEntity researcher = findVisibleResearcher(id, VisibilityScope.PUBLIC_VALIDATED, null);
        if (!hasPortalVisiblePrimaryAffiliation(id)) {
            throw new ResourceNotFoundException("Researcher", id);
        }
        return toDetailResponse(researcher, VisibilityScope.PUBLIC_VALIDATED);
    }

    public java.util.List<ResearcherAffiliationPublicResponse> findAffiliations(Long researcherId) {
        VisibilityScope visibilityScope = defaultVisibilityScope();
        findVisibleResearcher(researcherId, visibilityScope, visibilityContext.linkedResearcherId().orElse(null));
        LocalDate today = LocalDate.now();
        java.util.List<ResearcherAffiliationEntity> affiliations = affiliationRepository.findByResearcherIdOrderByPrimaryAffiliationDescStartDateDescIdAsc(researcherId);
        Map<Long, String> researchUnitNames = researchUnitNames(affiliations.stream().map(ResearcherAffiliationEntity::getResearchUnitId).toList());
        Long linkedResearcherId = visibilityContext.linkedResearcherId().orElse(null);
        return affiliations.stream()
            .filter(affiliation -> isVisibleAffiliation(affiliation, visibilityScope, linkedResearcherId))
            .map(affiliation -> ResearcherMapper.toPublicAffiliationResponse(affiliation, researchUnitNames, today))
            .toList();
    }

    @Transactional
    public ResearcherResponse create(ResearcherRequest request) {
        ResearcherEntity saved = researcherRepository.save(ResearcherMapper.toEntity(request));
        auditService.recordCreated(ValidationEntityType.RESEARCHER, saved.getId(), saved.getValidationStatus());
        return toDetailResponse(saved);
    }

    @Transactional
    public ResearcherResponse update(Long id, ResearcherRequest request) {
        ResearcherEntity researcher = findResearcher(id);
        ValidationStatus previousStatus = researcher.getValidationStatus();
        boolean wasActive = researcher.isActive();
        Map<String, AuditFieldChange> changes = researcherChanges(researcher, request);
        ResearcherMapper.updateEntity(researcher, request);
        auditService.recordUpdated(ValidationEntityType.RESEARCHER, id, previousStatus, researcher.getValidationStatus(), changes);
        if (wasActive && !researcher.isActive()) {
            auditService.recordArchived(ValidationEntityType.RESEARCHER, id, researcher.getValidationStatus());
        }
        return toDetailResponse(researcher);
    }

    @Transactional
    public ResearcherResponse updateOwn(Long currentResearcherId, Long id, ResearcherRequest request) {
        if (!id.equals(currentResearcherId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Researcher profile does not belong to the current user.");
        }
        ResearcherEntity researcher = findResearcher(id);
        if (!isResearcherEditable(researcher.getValidationStatus())) {
            throw new BusinessRuleException("Only draft profiles or profiles with requested changes can be edited.");
        }
        ValidationStatus previousStatus = researcher.getValidationStatus();
        Map<String, AuditFieldChange> changes = researcherOwnProfileChanges(researcher, request);
        ResearcherMapper.updateOwnProfileEntity(researcher, request);
        researcher.setValidationStatus(ValidationStatus.DRAFT);
        auditService.recordUpdated(ValidationEntityType.RESEARCHER, id, previousStatus, researcher.getValidationStatus(), changes);
        return toDetailResponse(researcher);
    }

    @Transactional
    public ResearcherAffiliationResponse addAffiliation(Long researcherId, ResearcherAffiliationRequest request) {
        findResearcher(researcherId);
        ResearchUnitEntity researchUnit = researchUnitRepository.findById(request.researchUnitId())
            .orElseThrow(() -> new ResourceNotFoundException("Research unit", request.researchUnitId()));
        validateAffiliationDates(request);
        validatePrimaryAffiliation(researcherId, request);

        ResearcherAffiliationEntity saved = affiliationRepository.save(ResearcherMapper.toAffiliationEntity(researcherId, request));
        auditService.recordCreated(ValidationEntityType.RESEARCHER_AFFILIATION, saved.getId(), saved.getValidationStatus());
        return toAffiliationResponse(saved, Map.of(researchUnit.getId(), researchUnit.getName()));
    }

    @Transactional
    public ResearcherAffiliationResponse updateAffiliation(Long researcherId, Long affiliationId, ResearcherAffiliationRequest request) {
        findResearcher(researcherId);
        ResearcherAffiliationEntity affiliation = findAffiliation(researcherId, affiliationId);
        ResearchUnitEntity researchUnit = researchUnitRepository.findById(request.researchUnitId())
            .orElseThrow(() -> new ResourceNotFoundException("Research unit", request.researchUnitId()));
        validateAffiliationDates(request);
        validatePrimaryAffiliation(researcherId, affiliationId, request);

        ValidationStatus previousStatus = affiliation.getValidationStatus();
        Map<String, AuditFieldChange> changes = affiliationChanges(affiliation, request);
        ResearcherMapper.updateAffiliationEntity(affiliation, request);
        auditService.recordUpdated(ValidationEntityType.RESEARCHER_AFFILIATION, affiliationId, previousStatus, affiliation.getValidationStatus(), changes);
        return toAffiliationResponse(affiliation, Map.of(researchUnit.getId(), researchUnit.getName()));
    }

    @Transactional
    public ResearcherAffiliationResponse updateOwnAffiliation(Long currentResearcherId, Long researcherId, Long affiliationId, ResearcherAffiliationRequest request) {
        if (!researcherId.equals(currentResearcherId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Affiliation does not belong to the current user.");
        }
        findResearcher(researcherId);
        ResearcherAffiliationEntity affiliation = findAffiliation(researcherId, affiliationId);
        if (!isResearcherEditable(affiliation.getValidationStatus())) {
            throw new BusinessRuleException("Only draft affiliations or affiliations with requested changes can be edited.");
        }
        ResearchUnitEntity researchUnit = researchUnitRepository.findById(request.researchUnitId())
            .orElseThrow(() -> new ResourceNotFoundException("Research unit", request.researchUnitId()));
        validateAffiliationDates(request);
        validatePrimaryAffiliation(researcherId, affiliationId, request);

        ValidationStatus previousStatus = affiliation.getValidationStatus();
        Map<String, AuditFieldChange> changes = affiliationChanges(affiliation, request);
        ResearcherMapper.updateAffiliationEntity(affiliation, request);
        affiliation.setValidationStatus(ValidationStatus.DRAFT);
        auditService.recordUpdated(ValidationEntityType.RESEARCHER_AFFILIATION, affiliationId, previousStatus, affiliation.getValidationStatus(), changes);
        return toAffiliationResponse(affiliation, Map.of(researchUnit.getId(), researchUnit.getName()));
    }

    @Transactional
    public void deleteAffiliation(Long researcherId, Long affiliationId) {
        findResearcher(researcherId);
        ResearcherAffiliationEntity affiliation = findAffiliation(researcherId, affiliationId);
        affiliationRepository.delete(affiliation);
    }

    private ResearcherEntity findResearcher(Long id) {
        return researcherRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Researcher", id));
    }

    private ResearcherEntity findVisibleResearcher(Long id, VisibilityScope visibilityScope, Long linkedResearcherId) {
        ResearcherEntity researcher = findResearcher(id);
        if (!isVisibleResearcher(researcher, visibilityScope, linkedResearcherId)) {
            throw new ResourceNotFoundException("Researcher", id);
        }
        return researcher;
    }

    private ResearcherAffiliationEntity findAffiliation(Long researcherId, Long affiliationId) {
        return affiliationRepository.findByIdAndResearcherId(affiliationId, researcherId)
            .orElseThrow(() -> new ResourceNotFoundException("Researcher affiliation", affiliationId));
    }

    private void validateAffiliationDates(ResearcherAffiliationRequest request) {
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new BusinessRuleException("Affiliation end date cannot be before start date.");
        }
    }

    private Map<String, AuditFieldChange> researcherChanges(ResearcherEntity researcher, ResearcherRequest request) {
        Map<String, AuditFieldChange> changes = auditService.changes();
        auditService.addChange(changes, "fullName", researcher.getFullName(), request.fullName());
        auditService.addChange(changes, "displayName", researcher.getDisplayName(), request.displayName());
        auditService.addChange(changes, "email", researcher.getEmail(), request.email());
        auditService.addChange(changes, "orcid", researcher.getOrcid(), request.orcid());
        auditService.addChange(changes, "active", researcher.isActive(), request.active() == null || request.active());
        return changes;
    }

    private Map<String, AuditFieldChange> researcherOwnProfileChanges(ResearcherEntity researcher, ResearcherRequest request) {
        Map<String, AuditFieldChange> changes = auditService.changes();
        auditService.addChange(changes, "fullName", researcher.getFullName(), request.fullName());
        auditService.addChange(changes, "displayName", researcher.getDisplayName(), request.displayName());
        auditService.addChange(changes, "email", researcher.getEmail(), request.email());
        auditService.addChange(changes, "orcid", researcher.getOrcid(), request.orcid());
        return changes;
    }

    private Map<String, AuditFieldChange> affiliationChanges(ResearcherAffiliationEntity affiliation, ResearcherAffiliationRequest request) {
        Map<String, AuditFieldChange> changes = auditService.changes();
        auditService.addChange(changes, "researchUnitId", affiliation.getResearchUnitId(), request.researchUnitId());
        auditService.addChange(changes, "role", affiliation.getRole(), request.role());
        auditService.addChange(changes, "affiliationType", affiliation.getAffiliationType(), request.affiliationType());
        auditService.addChange(changes, "startDate", affiliation.getStartDate(), request.startDate());
        auditService.addChange(changes, "endDate", affiliation.getEndDate(), request.endDate());
        auditService.addChange(changes, "primaryAffiliation", affiliation.isPrimaryAffiliation(), request.primaryAffiliation() != null && request.primaryAffiliation());
        return changes;
    }

    private boolean isResearcherEditable(ValidationStatus status) {
        return status == ValidationStatus.DRAFT || status == ValidationStatus.CHANGES_REQUESTED;
    }

    private void validatePrimaryAffiliation(Long researcherId, ResearcherAffiliationRequest request) {
        validatePrimaryAffiliation(researcherId, null, request);
    }

    private void validatePrimaryAffiliation(Long researcherId, Long affiliationId, ResearcherAffiliationRequest request) {
        boolean primary = request.primaryAffiliation() != null && request.primaryAffiliation();
        if (!primary) {
            return;
        }
        LocalDate today = LocalDate.now();
        boolean requestIsCurrent = request.endDate() == null || !request.endDate().isBefore(today);
        long existingPrimaryCount = affiliationId == null
            ? affiliationRepository.countCurrentPrimaryAffiliations(researcherId, today)
            : affiliationRepository.countCurrentPrimaryAffiliationsExcluding(researcherId, affiliationId, today);
        if (requestIsCurrent && existingPrimaryCount > 0) {
            throw new BusinessRuleException("Researcher already has a current primary affiliation.");
        }
    }

    private ResearcherResponse toDetailResponse(ResearcherEntity researcher) {
        return toDetailResponse(researcher, defaultVisibilityScope());
    }

    private ResearcherResponse toDetailResponse(ResearcherEntity researcher, VisibilityScope visibilityScope) {
        Long linkedResearcherId = visibilityContext.linkedResearcherId().orElse(null);
        List<ResearcherAffiliationResponse> affiliations = findAffiliationDetails(researcher.getId(), visibilityScope, linkedResearcherId);
        List<ResearcherAffiliationResponse> currentAffiliations = affiliations.stream()
            .filter(ResearcherAffiliationResponse::current)
            .toList();
        List<ResearcherAffiliationResponse> pastAffiliations = affiliations.stream()
            .filter(affiliation -> !affiliation.current())
            .toList();
        ResearcherAffiliationResponse primaryAffiliation = currentAffiliations.stream()
            .filter(ResearcherAffiliationResponse::primaryAffiliation)
            .findFirst()
            .orElse(null);
        List<PublicationSummaryResponse> authoredPublications = authoredPublications(researcher.getId(), visibilityScope, linkedResearcherId);
        List<TopicResponse> topics = topicsForPublications(authoredPublications.stream()
            .map(PublicationSummaryResponse::id)
            .toList());
        Map<Long, String> userDisplayNames = userDisplayNames(workflowUserIds(List.of(researcher)));

        return new ResearcherResponse(
            researcher.getId(),
            researcher.getFullName(),
            researcher.getDisplayName(),
            researcher.getEmail(),
            researcher.getOrcid(),
            researcher.isActive(),
            researcher.getValidationStatus(),
            researcher.getValidationComment(),
            researcher.getCreatedAt(),
            submittedBy(researcher, userDisplayNames, visibilityScope, linkedResearcherId),
            researcher.getValidatedAt(),
            validatedBy(researcher, userDisplayNames, visibilityScope, linkedResearcherId),
            canEditResearcher(researcher, visibilityScope, linkedResearcherId),
            canSubmitResearcher(researcher, visibilityScope, linkedResearcherId),
            canValidate(researcher.getValidationStatus()),
            affiliations,
            currentAffiliations,
            pastAffiliations,
            primaryAffiliation,
            authoredPublications,
            topics,
            coauthors(researcher.getId(), visibilityScope),
            researcher.getCreatedAt(),
            researcher.getUpdatedAt(),
            researcher.getCreatedByUserId(),
            researcher.getUpdatedByUserId()
        );
    }

    private List<PublicationSummaryResponse> authoredPublications(Long researcherId, VisibilityScope visibilityScope, Long linkedResearcherId) {
        List<PublicationEntity> publications = publicationRepository.findAuthoredByResearcherId(researcherId);
        Map<Long, List<String>> topicsByPublicationId = topicNamesByPublicationId(publications.stream()
            .map(PublicationEntity::getId)
            .toList());
        Map<Long, String> userDisplayNames = userDisplayNames(publicationWorkflowUserIds(publications));
        return publications.stream()
            .filter(publication -> isVisiblePublication(publication, researcherId, visibilityScope, linkedResearcherId))
            .map(publication -> new PublicationSummaryResponse(
                publication.getId(),
                publication.getTitle(),
                publication.getPublicationYear(),
                publication.getType(),
                publication.getStatus(),
                publication.getDoi(),
                publication.getSource(),
                publication.getVenueId(),
                publication.getPublisherId(),
                publication.getIsbn(),
                publication.getIssn(),
                publication.getLanguageCode(),
                publication.getValidationStatus(),
                publication.getValidationComment(),
                publication.getCreatedAt(),
                submittedBy(publication, userDisplayNames, researcherId, visibilityScope, linkedResearcherId),
                publication.getValidatedAt(),
                validatedBy(publication, userDisplayNames, researcherId, visibilityScope, linkedResearcherId),
                canEditPublication(publication, researcherId, visibilityScope, linkedResearcherId),
                canSubmitPublication(publication, researcherId, visibilityScope, linkedResearcherId),
                canValidate(publication.getValidationStatus()),
                publication.getCreatedAt(),
                topicsByPublicationId.getOrDefault(publication.getId(), List.of())
            ))
            .toList();
    }

    private List<ResearcherAffiliationResponse> findAffiliationDetails(Long researcherId, VisibilityScope visibilityScope, Long linkedResearcherId) {
        LocalDate today = LocalDate.now();
        List<ResearcherAffiliationEntity> affiliations = affiliationRepository.findByResearcherIdOrderByPrimaryAffiliationDescStartDateDescIdAsc(researcherId);
        Map<Long, String> researchUnitNames = researchUnitNames(affiliations.stream().map(ResearcherAffiliationEntity::getResearchUnitId).toList());
        Map<Long, String> userDisplayNames = userDisplayNames(affiliationWorkflowUserIds(affiliations));
        return affiliations.stream()
            .filter(affiliation -> isVisibleAffiliation(affiliation, visibilityScope, linkedResearcherId))
            .map(affiliation -> ResearcherMapper.toAffiliationResponse(
                affiliation,
                researchUnitNames,
                today,
                submittedBy(affiliation, userDisplayNames, visibilityScope, linkedResearcherId),
                validatedBy(affiliation, userDisplayNames, visibilityScope, linkedResearcherId),
                canEditAffiliation(affiliation, visibilityScope, linkedResearcherId),
                canSubmitAffiliation(affiliation, visibilityScope, linkedResearcherId),
                canValidate(affiliation.getValidationStatus())
            ))
            .toList();
    }

    private ResearcherAffiliationResponse toAffiliationResponse(
        ResearcherAffiliationEntity affiliation,
        Map<Long, String> researchUnitNames
    ) {
        VisibilityScope visibilityScope = defaultVisibilityScope();
        Long linkedResearcherId = visibilityContext.linkedResearcherId().orElse(null);
        Map<Long, String> userDisplayNames = userDisplayNames(affiliationWorkflowUserIds(List.of(affiliation)));
        return ResearcherMapper.toAffiliationResponse(
            affiliation,
            researchUnitNames,
            LocalDate.now(),
            submittedBy(affiliation, userDisplayNames, visibilityScope, linkedResearcherId),
            validatedBy(affiliation, userDisplayNames, visibilityScope, linkedResearcherId),
            canEditAffiliation(affiliation, visibilityScope, linkedResearcherId),
            canSubmitAffiliation(affiliation, visibilityScope, linkedResearcherId),
            canValidate(affiliation.getValidationStatus())
        );
    }

    private List<TopicResponse> topicsForPublications(Collection<Long> publicationIds) {
        if (publicationIds.isEmpty()) {
            return List.of();
        }
        List<PublicationTopicEntity> links = publicationTopicRepository.findByPublicationIdIn(publicationIds);
        if (links.isEmpty()) {
            return List.of();
        }
        return topicRepository.findAllById(links.stream().map(PublicationTopicEntity::getTopicId).distinct().toList())
            .stream()
            .sorted(Comparator.comparing(TopicEntity::getName))
            .map(topic -> new TopicResponse(topic.getId(), topic.getName(), topic.getNormalizedName()))
            .toList();
    }

    private Map<Long, List<String>> topicNamesByPublicationId(Collection<Long> publicationIds) {
        if (publicationIds.isEmpty()) {
            return Map.of();
        }
        List<PublicationTopicEntity> links = publicationTopicRepository.findByPublicationIdIn(publicationIds);
        Map<Long, TopicEntity> topicsById = topicRepository.findAllById(links.stream().map(PublicationTopicEntity::getTopicId).toList())
            .stream()
            .collect(Collectors.toMap(TopicEntity::getId, Function.identity()));
        return links.stream()
            .filter(link -> topicsById.containsKey(link.getTopicId()))
            .collect(Collectors.groupingBy(
                PublicationTopicEntity::getPublicationId,
                Collectors.mapping(link -> topicsById.get(link.getTopicId()).getName(), Collectors.toList())
            ));
    }

    private List<ResearcherCoauthorResponse> coauthors(Long researcherId, VisibilityScope visibilityScope) {
        List<ResearcherCoauthorResponse> coauthors = new ArrayList<>();
        ValidationStatus validationStatusFilter = validationStatusFilter(visibilityScope);
        publicationAuthorRepository.findInternalCoauthorsByResearcherId(researcherId, validationStatusFilter)
            .forEach(row -> coauthors.add(new ResearcherCoauthorResponse((Long) row[0], (String) row[1], true, (Long) row[2])));
        publicationAuthorRepository.findExternalCoauthorsByResearcherId(researcherId, validationStatusFilter)
            .forEach(row -> coauthors.add(new ResearcherCoauthorResponse(null, (String) row[0], false, (Long) row[1])));
        return coauthors.stream()
            .sorted(Comparator
                .comparingLong(ResearcherCoauthorResponse::sharedPublicationCount)
                .reversed()
                .thenComparing(ResearcherCoauthorResponse::name))
            .toList();
    }

    private Map<Long, String> primaryAffiliationNames(Collection<Long> researcherIds, VisibilityScope visibilityScope, Long linkedResearcherId) {
        if (researcherIds.isEmpty()) {
            return Map.of();
        }
        java.util.List<ResearcherAffiliationEntity> primaryAffiliations = affiliationRepository.findCurrentPrimaryByResearcherIds(researcherIds, LocalDate.now());
        Map<Long, String> unitNames = researchUnitNames(primaryAffiliations.stream().map(ResearcherAffiliationEntity::getResearchUnitId).toList());
        return primaryAffiliations.stream()
            .filter(affiliation -> isVisibleAffiliation(affiliation, visibilityScope, linkedResearcherId))
            .collect(Collectors.toMap(
                ResearcherAffiliationEntity::getResearcherId,
                affiliation -> unitNames.get(affiliation.getResearchUnitId()),
                (first, second) -> first
            ));
    }

    private Map<Long, String> researchUnitNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return researchUnitRepository.findAllById(ids)
            .stream()
            .collect(Collectors.toMap(ResearchUnitEntity::getId, ResearchUnitEntity::getName, (first, second) -> first));
    }

    private String submittedBy(
        ResearcherEntity researcher,
        Map<Long, String> userDisplayNames,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        if (researcher.getCreatedByUserId() == null) {
            return null;
        }
        return canReadWorkflowActors(researcher, visibilityScope, linkedResearcherId)
            ? userDisplayNames.get(researcher.getCreatedByUserId())
            : null;
    }

    private String validatedBy(
        ResearcherEntity researcher,
        Map<Long, String> userDisplayNames,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        if (researcher.getValidatedByUserId() == null) {
            return null;
        }
        return canReadWorkflowActors(researcher, visibilityScope, linkedResearcherId)
            ? userDisplayNames.get(researcher.getValidatedByUserId())
            : null;
    }

    private String submittedBy(
        ResearcherAffiliationEntity affiliation,
        Map<Long, String> userDisplayNames,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        if (affiliation.getCreatedByUserId() == null) {
            return null;
        }
        return canReadWorkflowActors(affiliation, visibilityScope, linkedResearcherId)
            ? userDisplayNames.get(affiliation.getCreatedByUserId())
            : null;
    }

    private String validatedBy(
        ResearcherAffiliationEntity affiliation,
        Map<Long, String> userDisplayNames,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        if (affiliation.getValidatedByUserId() == null) {
            return null;
        }
        return canReadWorkflowActors(affiliation, visibilityScope, linkedResearcherId)
            ? userDisplayNames.get(affiliation.getValidatedByUserId())
            : null;
    }

    private String submittedBy(
        PublicationEntity publication,
        Map<Long, String> userDisplayNames,
        Long researcherId,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        if (publication.getCreatedByUserId() == null) {
            return null;
        }
        return canReadWorkflowActors(publication, researcherId, visibilityScope, linkedResearcherId)
            ? userDisplayNames.get(publication.getCreatedByUserId())
            : null;
    }

    private String validatedBy(
        PublicationEntity publication,
        Map<Long, String> userDisplayNames,
        Long researcherId,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        if (publication.getValidatedByUserId() == null) {
            return null;
        }
        return canReadWorkflowActors(publication, researcherId, visibilityScope, linkedResearcherId)
            ? userDisplayNames.get(publication.getValidatedByUserId())
            : null;
    }

    private boolean canEditResearcher(ResearcherEntity researcher, VisibilityScope visibilityScope, Long linkedResearcherId) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN")) {
            return true;
        }
        return roles.contains("RESEARCHER")
            && visibilityScope == VisibilityScope.MY_DATA
            && linkedResearcherId != null
            && linkedResearcherId.equals(researcher.getId())
            && isResearcherEditable(researcher.getValidationStatus());
    }

    private boolean canSubmitResearcher(ResearcherEntity researcher, VisibilityScope visibilityScope, Long linkedResearcherId) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN")) {
            return isResearcherEditable(researcher.getValidationStatus());
        }
        return canEditResearcher(researcher, visibilityScope, linkedResearcherId);
    }

    private boolean canEditAffiliation(ResearcherAffiliationEntity affiliation, VisibilityScope visibilityScope, Long linkedResearcherId) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN")) {
            return true;
        }
        return roles.contains("RESEARCHER")
            && visibilityScope == VisibilityScope.MY_DATA
            && linkedResearcherId != null
            && linkedResearcherId.equals(affiliation.getResearcherId())
            && isResearcherEditable(affiliation.getValidationStatus());
    }

    private boolean canSubmitAffiliation(ResearcherAffiliationEntity affiliation, VisibilityScope visibilityScope, Long linkedResearcherId) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN")) {
            return isResearcherEditable(affiliation.getValidationStatus());
        }
        return canEditAffiliation(affiliation, visibilityScope, linkedResearcherId);
    }

    private boolean canEditPublication(
        PublicationEntity publication,
        Long researcherId,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN")) {
            return true;
        }
        return roles.contains("RESEARCHER")
            && visibilityScope == VisibilityScope.MY_DATA
            && linkedResearcherId != null
            && linkedResearcherId.equals(researcherId)
            && isResearcherEditable(publication.getValidationStatus());
    }

    private boolean canSubmitPublication(
        PublicationEntity publication,
        Long researcherId,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN")) {
            return isResearcherEditable(publication.getValidationStatus());
        }
        return canEditPublication(publication, researcherId, visibilityScope, linkedResearcherId);
    }

    private boolean canValidate(ValidationStatus validationStatus) {
        Set<String> roles = currentRoles();
        return validationStatus == ValidationStatus.PENDING_VALIDATION
            && (roles.contains("ADMIN") || roles.contains("VALIDATOR"));
    }

    private boolean canReadWorkflowActors(ResearcherEntity researcher, VisibilityScope visibilityScope, Long linkedResearcherId) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN") || roles.contains("VALIDATOR")) {
            return true;
        }
        return roles.contains("RESEARCHER")
            && visibilityScope == VisibilityScope.MY_DATA
            && linkedResearcherId != null
            && linkedResearcherId.equals(researcher.getId());
    }

    private boolean canReadWorkflowActors(ResearcherAffiliationEntity affiliation, VisibilityScope visibilityScope, Long linkedResearcherId) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN") || roles.contains("VALIDATOR")) {
            return true;
        }
        return roles.contains("RESEARCHER")
            && visibilityScope == VisibilityScope.MY_DATA
            && linkedResearcherId != null
            && linkedResearcherId.equals(affiliation.getResearcherId());
    }

    private boolean canReadWorkflowActors(
        PublicationEntity publication,
        Long researcherId,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN") || roles.contains("VALIDATOR")) {
            return true;
        }
        return roles.contains("RESEARCHER")
            && visibilityScope == VisibilityScope.MY_DATA
            && linkedResearcherId != null
            && linkedResearcherId.equals(researcherId);
    }

    private Set<String> currentRoles() {
        Set<String> roles = visibilityContext.currentRoles();
        return roles == null ? Set.of() : roles;
    }

    private Set<Long> workflowUserIds(Collection<ResearcherEntity> researchers) {
        return researchers.stream()
            .flatMap(researcher -> java.util.stream.Stream.of(researcher.getCreatedByUserId(), researcher.getValidatedByUserId()))
            .filter(id -> id != null)
            .collect(Collectors.toSet());
    }

    private Set<Long> affiliationWorkflowUserIds(Collection<ResearcherAffiliationEntity> affiliations) {
        return affiliations.stream()
            .flatMap(affiliation -> java.util.stream.Stream.of(affiliation.getCreatedByUserId(), affiliation.getValidatedByUserId()))
            .filter(id -> id != null)
            .collect(Collectors.toSet());
    }

    private Set<Long> publicationWorkflowUserIds(Collection<PublicationEntity> publications) {
        return publications.stream()
            .flatMap(publication -> java.util.stream.Stream.of(publication.getCreatedByUserId(), publication.getValidatedByUserId()))
            .filter(id -> id != null)
            .collect(Collectors.toSet());
    }

    private Map<Long, String> userDisplayNames(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds)
            .stream()
            .collect(Collectors.toMap(UserEntity::getId, UserEntity::getDisplayName, (first, second) -> first));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean hasPortalVisiblePrimaryAffiliation(Long researcherId) {
        return affiliationRepository.countCurrentPrimaryAffiliationsVisibleInPortal(
            researcherId,
            ValidationStatus.VALIDATED,
            LocalDate.now()
        ) > 0;
    }

    private VisibilityScope defaultVisibilityScope() {
        VisibilityScope scope = visibilityContext.defaultScope();
        return scope == null ? VisibilityScope.PUBLIC_VALIDATED : scope;
    }

    private ValidationStatus validationStatusFilter(VisibilityScope visibilityScope) {
        return visibilityScope == VisibilityScope.ADMIN_ALL || visibilityScope == VisibilityScope.MY_DATA ? null : ValidationStatus.VALIDATED;
    }

    private boolean isVisibleResearcher(ResearcherEntity researcher, VisibilityScope visibilityScope, Long linkedResearcherId) {
        if (visibilityScope == VisibilityScope.ADMIN_ALL || researcher.getValidationStatus() == ValidationStatus.VALIDATED) {
            return true;
        }
        return visibilityScope == VisibilityScope.MY_DATA
            && linkedResearcherId != null
            && linkedResearcherId.equals(researcher.getId());
    }

    private boolean isVisibleAffiliation(ResearcherAffiliationEntity affiliation, VisibilityScope visibilityScope, Long linkedResearcherId) {
        if (visibilityScope == VisibilityScope.ADMIN_ALL) {
            return true;
        }
        if (visibilityScope == VisibilityScope.MY_DATA
            && linkedResearcherId != null
            && linkedResearcherId.equals(affiliation.getResearcherId())) {
            return true;
        }
        return affiliation.getValidationStatus() == ValidationStatus.VALIDATED
            && researchUnitRepository.findById(affiliation.getResearchUnitId())
                .map(unit -> unit.getValidationStatus() == ValidationStatus.VALIDATED)
                .orElse(false);
    }

    private boolean isVisiblePublication(PublicationEntity publication, Long researcherId, VisibilityScope visibilityScope, Long linkedResearcherId) {
        if (visibilityScope == VisibilityScope.ADMIN_ALL || publication.getValidationStatus() == ValidationStatus.VALIDATED) {
            return true;
        }
        return visibilityScope == VisibilityScope.MY_DATA
            && linkedResearcherId != null
            && linkedResearcherId.equals(researcherId);
    }
}
