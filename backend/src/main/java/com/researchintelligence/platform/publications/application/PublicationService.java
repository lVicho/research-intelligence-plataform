package com.researchintelligence.platform.publications.application;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.audit.application.AuditFieldChange;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.auth.persistence.UserRepository;
import com.researchintelligence.platform.publications.api.FilterCountResponse;
import com.researchintelligence.platform.publications.api.PublicationAuthorRequest;
import com.researchintelligence.platform.publications.api.PublicationAuthorResponse;
import com.researchintelligence.platform.publications.api.PublicationFilterMetadataResponse;
import com.researchintelligence.platform.publications.api.PublicationRequest;
import com.researchintelligence.platform.publications.api.PublicationResponse;
import com.researchintelligence.platform.publications.api.PublicationSummaryResponse;
import com.researchintelligence.platform.publications.api.TopicResponse;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorEntity;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationSpecifications;
import com.researchintelligence.platform.publications.persistence.PublicationTopicEntity;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.PublisherRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.publications.persistence.VenueRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PublicationService {

    private final PublicationRepository publicationRepository;
    private final PublicationAuthorRepository authorRepository;
    private final TopicRepository topicRepository;
    private final PublicationTopicRepository publicationTopicRepository;
    private final ResearcherRepository researcherRepository;
    private final VenueRepository venueRepository;
    private final PublisherRepository publisherRepository;
    private final UserRepository userRepository;
    private final VisibilityContext visibilityContext;
    private final ActivityAuditService auditService;

    public PublicationService(
        PublicationRepository publicationRepository,
        PublicationAuthorRepository authorRepository,
        TopicRepository topicRepository,
        PublicationTopicRepository publicationTopicRepository,
        ResearcherRepository researcherRepository,
        VenueRepository venueRepository,
        PublisherRepository publisherRepository,
        UserRepository userRepository,
        VisibilityContext visibilityContext,
        ActivityAuditService auditService
    ) {
        this.publicationRepository = publicationRepository;
        this.authorRepository = authorRepository;
        this.topicRepository = topicRepository;
        this.publicationTopicRepository = publicationTopicRepository;
        this.researcherRepository = researcherRepository;
        this.venueRepository = venueRepository;
        this.publisherRepository = publisherRepository;
        this.userRepository = userRepository;
        this.visibilityContext = visibilityContext;
        this.auditService = auditService;
    }

    public PageResponse<PublicationSummaryResponse> search(
        int page,
        int size,
        String text,
        Integer yearFrom,
        Integer yearTo,
        PublicationType type,
        PublicationStatus status,
        Long researchUnitId,
        Long researcherId,
        String topic
    ) {
        return search(page, size, text, yearFrom, yearTo, type, status, researchUnitId, researcherId, topic, "year", "desc");
    }

    public PageResponse<PublicationSummaryResponse> search(
        int page,
        int size,
        String text,
        Integer yearFrom,
        Integer yearTo,
        PublicationType type,
        PublicationStatus status,
        Long researchUnitId,
        Long researcherId,
        String topic,
        String sortBy,
        String sortDirection
    ) {
        return search(
            page,
            size,
            text,
            yearFrom,
            yearTo,
            type,
            status,
            researchUnitId,
            researcherId,
            topic,
            sortBy,
            sortDirection,
            defaultVisibilityScope(),
            visibilityContext.linkedResearcherId().orElse(null)
        );
    }

    public PageResponse<PublicationSummaryResponse> searchPublicValidated(
        int page,
        int size,
        String text,
        Integer yearFrom,
        Integer yearTo,
        PublicationType type,
        PublicationStatus status,
        Long researchUnitId,
        Long researcherId,
        String topic,
        String sortBy,
        String sortDirection
    ) {
        return search(
            page,
            size,
            text,
            yearFrom,
            yearTo,
            type,
            status,
            researchUnitId,
            researcherId,
            topic,
            sortBy,
            sortDirection,
            VisibilityScope.PUBLIC_VALIDATED,
            null
        );
    }

    private PageResponse<PublicationSummaryResponse> search(
        int page,
        int size,
        String text,
        Integer yearFrom,
        Integer yearTo,
        PublicationType type,
        PublicationStatus status,
        Long researchUnitId,
        Long researcherId,
        String topic,
        String sortBy,
        String sortDirection,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        String normalizedText = blankToNull(text);
        String textPattern = normalizedText == null ? "%" : "%" + normalizedText.toLowerCase() + "%";
        String normalizedTopic = blankToNull(topic);
        if (normalizedTopic != null) {
            normalizedTopic = normalizedTopic.toLowerCase();
        }
        String topicPattern = normalizedTopic == null ? "%" : "%" + normalizedTopic + "%";
        PageRequest pageable = createPageable(page, size, sortBy, sortDirection);
        Specification<PublicationEntity> specification = PublicationSpecifications.matches(
            normalizedText,
            textPattern,
            yearFrom,
            yearTo,
            type,
            status,
            researchUnitId,
            researcherId,
            normalizedTopic,
            topicPattern,
            relationshipValidationStatus(visibilityScope)
        ).and(PublicationSpecifications.visibleTo(
            visibilityScope,
            linkedResearcherId
        ));
        Page<PublicationEntity> publications = publicationRepository.findAll(specification, pageable);
        List<PublicationEntity> visiblePublications = publications.getContent().stream()
            .filter(publication -> isVisible(publication, visibilityScope, linkedResearcherId))
            .toList();
        Map<Long, List<String>> topicsByPublicationId = topicNamesByPublicationId(visiblePublications.stream().map(PublicationEntity::getId).toList());
        Map<Long, String> userDisplayNames = userDisplayNames(workflowUserIds(visiblePublications));
        List<PublicationSummaryResponse> content = visiblePublications.stream()
            .map(publication -> PublicationMapper.toSummary(
                publication,
                topicsByPublicationId.getOrDefault(publication.getId(), List.of()),
                submittedBy(publication, userDisplayNames, visibilityScope, linkedResearcherId),
                validatedBy(publication, userDisplayNames, visibilityScope, linkedResearcherId),
                canEditPublication(publication, visibilityScope, linkedResearcherId),
                canSubmitPublication(publication, visibilityScope, linkedResearcherId),
                canValidate(publication.getValidationStatus())
            ))
            .toList();
        return new PageResponse<>(
            content,
            publications.getNumber(),
            publications.getSize(),
            publications.getTotalElements(),
            publications.getTotalPages(),
            publications.isLast()
        );
    }

    public PublicationFilterMetadataResponse filterMetadata() {
        return new PublicationFilterMetadataResponse(
            publicationRepository.findMinPublicationYear(),
            publicationRepository.findMaxPublicationYear(),
            countRows(publicationRepository.countPublicationsByType(), false),
            countRows(publicationRepository.countPublicationsByStatus(), false),
            countRows(publicationRepository.countPublicationsByResearchUnit(), true),
            countRows(publicationRepository.countTopicsByPublicationCount(), true)
        );
    }

    public PublicationResponse findById(Long id) {
        PublicationEntity publication = findVisiblePublication(id, defaultVisibilityScope(), visibilityContext.linkedResearcherId().orElse(null));
        return toResponse(publication);
    }

    public PublicationResponse findPublicValidatedById(Long id) {
        PublicationEntity publication = findVisiblePublication(id, VisibilityScope.PUBLIC_VALIDATED, null);
        return toResponse(publication);
    }

    @Transactional
    public PublicationResponse create(PublicationRequest request) {
        validateRequest(request);
        PublicationEntity publication = publicationRepository.save(PublicationMapper.toEntity(request));
        replaceAuthors(publication.getId(), request.authors());
        replaceTopics(publication.getId(), request.topics());
        auditService.recordCreated(ValidationEntityType.PUBLICATION, publication.getId(), publication.getValidationStatus());
        return toResponse(publication);
    }

    @Transactional
    public PublicationResponse update(Long id, PublicationRequest request) {
        validateRequest(request);
        PublicationEntity publication = findPublication(id);
        ValidationStatus previousStatus = publication.getValidationStatus();
        Map<String, AuditFieldChange> changes = publicationChanges(publication, request);
        PublicationMapper.updateEntity(publication, request);
        authorRepository.deleteByPublicationId(publication.getId());
        publicationTopicRepository.deleteByPublicationId(publication.getId());
        replaceAuthors(publication.getId(), request.authors());
        replaceTopics(publication.getId(), request.topics());
        auditService.recordUpdated(ValidationEntityType.PUBLICATION, id, previousStatus, publication.getValidationStatus(), changes);
        return toResponse(publication);
    }

    @Transactional
    public PublicationResponse updateOwn(Long researcherId, Long id, PublicationRequest request) {
        validateRequest(request);
        PublicationEntity publication = findPublication(id);
        if (!authorRepository.existsByPublicationIdAndResearcherId(id, researcherId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Publication does not belong to the current researcher.");
        }
        if (!isResearcherEditable(publication.getValidationStatus())) {
            throw new BusinessRuleException("Only draft publications or publications with requested changes can be edited.");
        }
        if (request.authors().stream().noneMatch(author -> researcherId.equals(author.researcherId()))) {
            throw new BusinessRuleException("Researcher-owned publication updates must keep the current researcher as an author.");
        }
        ValidationStatus previousStatus = publication.getValidationStatus();
        Map<String, AuditFieldChange> changes = publicationChanges(publication, request);
        PublicationMapper.updateEntity(publication, request);
        publication.setValidationStatus(ValidationStatus.DRAFT);
        authorRepository.deleteByPublicationId(publication.getId());
        publicationTopicRepository.deleteByPublicationId(publication.getId());
        replaceAuthors(publication.getId(), request.authors());
        replaceTopics(publication.getId(), request.topics());
        auditService.recordUpdated(ValidationEntityType.PUBLICATION, id, previousStatus, publication.getValidationStatus(), changes);
        return toResponse(publication);
    }

    @Transactional
    public PublicationResponse submit(Long id, PlatformUserPrincipal user) {
        PublicationEntity publication = findPublication(id);
        authorizeSubmit(publication, user);
        if (!isResearcherEditable(publication.getValidationStatus())) {
            throw new BusinessRuleException("Only draft publications or publications with requested changes can be submitted.");
        }
        ValidationStatus previousStatus = publication.getValidationStatus();
        publication.setValidationStatus(ValidationStatus.PENDING_VALIDATION);
        publication.setValidatedByUserId(null);
        publication.setValidatedAt(null);
        auditService.recordStatusChange(
            ValidationEntityType.PUBLICATION,
            id,
            previousStatus,
            ValidationStatus.PENDING_VALIDATION,
            null
        );
        return toResponse(publication);
    }

    private PublicationResponse toResponse(PublicationEntity publication) {
        List<PublicationAuthorEntity> authors = authorRepository.findByPublicationIdOrderByAuthorOrderAsc(publication.getId());
        Map<Long, String> researcherNames = researcherNames(authors.stream()
            .map(PublicationAuthorEntity::getResearcherId)
            .filter(id -> id != null)
            .toList());
        List<PublicationAuthorResponse> authorResponses = authors.stream()
            .map(author -> PublicationMapper.toAuthorResponse(author, researcherNames))
            .toList();
        List<TopicResponse> topicResponses = topicsForPublication(publication.getId()).stream()
            .map(PublicationMapper::toTopicResponse)
            .toList();
        VisibilityScope visibilityScope = defaultVisibilityScope();
        Long linkedResearcherId = visibilityContext.linkedResearcherId().orElse(null);
        Map<Long, String> userDisplayNames = userDisplayNames(workflowUserIds(List.of(publication)));
        return PublicationMapper.toResponse(
            publication,
            authorResponses,
            topicResponses,
            submittedBy(publication, userDisplayNames, visibilityScope, linkedResearcherId),
            validatedBy(publication, userDisplayNames, visibilityScope, linkedResearcherId),
            canEditPublication(publication, visibilityScope, linkedResearcherId),
            canSubmitPublication(publication, visibilityScope, linkedResearcherId),
            canValidate(publication.getValidationStatus())
        );
    }

    private PublicationEntity findPublication(Long id) {
        return publicationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Publication", id));
    }

    private PublicationEntity findVisiblePublication(Long id, VisibilityScope visibilityScope, Long linkedResearcherId) {
        Specification<PublicationEntity> specification = PublicationSpecifications.hasId(id)
            .and(PublicationSpecifications.visibleTo(
                visibilityScope,
                linkedResearcherId
            ));
        return publicationRepository.findOne(specification)
            .orElseThrow(() -> new ResourceNotFoundException("Publication", id));
    }

    private void validateRequest(PublicationRequest request) {
        validateYearRange(request.year());
        validatePublicationChannel(request);
        validateAuthors(request.authors());
    }

    private void validatePublicationChannel(PublicationRequest request) {
        if (request.venueId() != null && !venueRepository.existsById(request.venueId())) {
            throw new ResourceNotFoundException("Venue", request.venueId());
        }
        if (request.publisherId() != null && !publisherRepository.existsById(request.publisherId())) {
            throw new ResourceNotFoundException("Publisher", request.publisherId());
        }
    }

    private boolean isResearcherEditable(ValidationStatus status) {
        return status == ValidationStatus.DRAFT || status == ValidationStatus.CHANGES_REQUESTED;
    }

    private void authorizeSubmit(PublicationEntity publication, PlatformUserPrincipal user) {
        if (user == null || (!user.roles().contains("ADMIN") && !user.roles().contains("RESEARCHER"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is required to submit publications.");
        }
        if (user.roles().contains("ADMIN")) {
            return;
        }
        Long linkedResearcherId = user.researcherId();
        if (linkedResearcherId == null
            || !authorRepository.existsByPublicationIdAndResearcherId(publication.getId(), linkedResearcherId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Publication does not belong to the current researcher.");
        }
    }

    private void validateYearRange(Integer year) {
        if (year != null && (year < 1500 || year > 2200)) {
            throw new BusinessRuleException("Publication year must be between 1500 and 2200.");
        }
    }

    private void validateAuthors(List<PublicationAuthorRequest> authors) {
        if (authors == null || authors.isEmpty()) {
            throw new BusinessRuleException("Publication must have at least one author.");
        }
        if (authors.stream().anyMatch(author -> author.authorOrder() == null || author.authorOrder() < 1)) {
            throw new BusinessRuleException("Publication author order values must be positive.");
        }

        Set<Integer> uniqueOrders = authors.stream()
            .map(PublicationAuthorRequest::authorOrder)
            .collect(Collectors.toSet());
        if (uniqueOrders.size() != authors.size()) {
            throw new BusinessRuleException("Publication author order values must be unique.");
        }

        List<Long> internalResearcherIds = new ArrayList<>();
        for (PublicationAuthorRequest author : authors) {
            boolean hasResearcher = author.researcherId() != null;
            boolean hasExternalName = author.externalAuthorName() != null && !author.externalAuthorName().isBlank();
            if (hasResearcher == hasExternalName) {
                throw new BusinessRuleException("Each author must be either an internal researcher or an external author.");
            }
            if (hasResearcher) {
                internalResearcherIds.add(author.researcherId());
            }
        }

        if (!internalResearcherIds.isEmpty()) {
            Set<Long> foundIds = researcherRepository.findAllById(internalResearcherIds)
                .stream()
                .map(ResearcherEntity::getId)
                .collect(Collectors.toSet());
            internalResearcherIds.stream()
                .filter(id -> !foundIds.contains(id))
                .findFirst()
                .ifPresent(id -> {
                    throw new ResourceNotFoundException("Researcher", id);
                });
        }
    }

    private Map<String, AuditFieldChange> publicationChanges(PublicationEntity publication, PublicationRequest request) {
        Map<String, AuditFieldChange> changes = auditService.changes();
        auditService.addChange(changes, "title", publication.getTitle(), request.title());
        auditService.addChange(changes, "abstractText", publication.getAbstractText(), request.abstractText());
        auditService.addChange(changes, "publicSummary", publication.getPublicSummary(), request.publicSummary());
        auditService.addChange(changes, "publicationYear", publication.getPublicationYear(), request.year());
        auditService.addChange(changes, "publicationDate", publication.getPublicationDate(), request.publicationDate());
        auditService.addChange(changes, "type", publication.getType(), request.type());
        auditService.addChange(changes, "status", publication.getStatus(), request.status());
        auditService.addChange(changes, "doi", publication.getDoi(), request.doi());
        auditService.addChange(changes, "source", publication.getSource(), request.source());
        auditService.addChange(changes, "sourceDetail", publication.getSourceDetail(), request.sourceDetail());
        auditService.addChange(changes, "url", publication.getUrl(), request.url());
        auditService.addChange(changes, "venueId", publication.getVenueId(), request.venueId());
        auditService.addChange(changes, "publisherId", publication.getPublisherId(), request.publisherId());
        auditService.addChange(changes, "isbn", publication.getIsbn(), request.isbn());
        auditService.addChange(changes, "issn", publication.getIssn(), request.issn());
        auditService.addChange(changes, "languageCode", publication.getLanguageCode(), request.languageCode());
        return changes;
    }

    private void replaceAuthors(Long publicationId, List<PublicationAuthorRequest> authors) {
        List<PublicationAuthorEntity> entities = authors.stream()
            .sorted(Comparator.comparing(PublicationAuthorRequest::authorOrder))
            .map(author -> new PublicationAuthorEntity(
                publicationId,
                author.researcherId(),
                blankToNull(author.externalAuthorName()),
                blankToNull(author.externalAffiliation()),
                author.authorOrder(),
                author.correspondingAuthor() != null && author.correspondingAuthor()
            ))
            .toList();
        authorRepository.saveAll(entities);
    }

    private void replaceTopics(Long publicationId, List<String> topicNames) {
        List<TopicEntity> topics = resolveTopics(topicNames);
        List<PublicationTopicEntity> links = topics.stream()
            .map(topic -> new PublicationTopicEntity(publicationId, topic.getId()))
            .toList();
        publicationTopicRepository.saveAll(links);
    }

    private List<TopicEntity> resolveTopics(List<String> topicNames) {
        if (topicNames == null || topicNames.isEmpty()) {
            return List.of();
        }
        Map<String, String> displayNamesByNormalized = new LinkedHashMap<>();
        for (String topicName : topicNames) {
            String normalized = TopicNormalizer.normalize(topicName);
            if (!normalized.isBlank()) {
                displayNamesByNormalized.putIfAbsent(normalized, TopicNormalizer.displayName(topicName));
            }
        }
        if (displayNamesByNormalized.isEmpty()) {
            return List.of();
        }

        Map<String, TopicEntity> existing = topicRepository.findByNormalizedNameIn(displayNamesByNormalized.keySet())
            .stream()
            .collect(Collectors.toMap(TopicEntity::getNormalizedName, Function.identity()));
        List<TopicEntity> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : displayNamesByNormalized.entrySet()) {
            TopicEntity topic = existing.get(entry.getKey());
            if (topic == null) {
                topic = topicRepository.save(new TopicEntity(entry.getValue(), entry.getKey()));
            }
            result.add(topic);
        }
        return result;
    }

    private List<TopicEntity> topicsForPublication(Long publicationId) {
        List<Long> topicIds = publicationTopicRepository.findByPublicationId(publicationId)
            .stream()
            .map(PublicationTopicEntity::getTopicId)
            .toList();
        if (topicIds.isEmpty()) {
            return List.of();
        }
        Map<Long, TopicEntity> topicsById = topicRepository.findAllById(topicIds)
            .stream()
            .collect(Collectors.toMap(TopicEntity::getId, Function.identity()));
        return topicIds.stream()
            .map(topicsById::get)
            .filter(topic -> topic != null)
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
            .collect(Collectors.groupingBy(
                PublicationTopicEntity::getPublicationId,
                Collectors.mapping(link -> topicsById.get(link.getTopicId()).getName(), Collectors.toList())
            ));
    }

    private Map<Long, String> researcherNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return researcherRepository.findAllById(ids)
            .stream()
            .collect(Collectors.toMap(ResearcherEntity::getId, ResearcherEntity::getFullName, (first, second) -> first));
    }

    private PageRequest createPageable(int page, int size, String sortBy, String sortDirection) {
        String property = switch (blankToNull(sortBy) == null ? "year" : sortBy) {
            case "title" -> "title";
            case "type" -> "type";
            case "status" -> "status";
            case "publicationDate" -> "publicationDate";
            case "createdAt" -> "createdAt";
            default -> "publicationYear";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "title"));
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
    }

    private VisibilityScope defaultVisibilityScope() {
        VisibilityScope scope = visibilityContext.defaultScope();
        return scope == null ? VisibilityScope.PUBLIC_VALIDATED : scope;
    }

    private ValidationStatus relationshipValidationStatus(VisibilityScope visibilityScope) {
        return visibilityScope == VisibilityScope.PUBLIC_VALIDATED ? ValidationStatus.VALIDATED : null;
    }

    private boolean isVisible(PublicationEntity publication, VisibilityScope visibilityScope, Long linkedResearcherId) {
        VisibilityScope effectiveScope = visibilityScope == null ? VisibilityScope.PUBLIC_VALIDATED : visibilityScope;
        if (effectiveScope == VisibilityScope.ADMIN_ALL) {
            return true;
        }
        if (publication.getValidationStatus() == ValidationStatus.VALIDATED) {
            return true;
        }
        return effectiveScope == VisibilityScope.MY_DATA
            && linkedResearcherId != null
            && authorRepository.existsByPublicationIdAndResearcherId(publication.getId(), linkedResearcherId);
    }

    private boolean canEditPublication(PublicationEntity publication, VisibilityScope visibilityScope, Long linkedResearcherId) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN")) {
            return true;
        }
        return roles.contains("RESEARCHER")
            && visibilityScope == VisibilityScope.MY_DATA
            && linkedResearcherId != null
            && isResearcherEditable(publication.getValidationStatus())
            && authorRepository.existsByPublicationIdAndResearcherId(publication.getId(), linkedResearcherId);
    }

    private boolean canSubmitPublication(PublicationEntity publication, VisibilityScope visibilityScope, Long linkedResearcherId) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN")) {
            return isResearcherEditable(publication.getValidationStatus());
        }
        return roles.contains("RESEARCHER")
            && visibilityScope == VisibilityScope.MY_DATA
            && linkedResearcherId != null
            && isResearcherEditable(publication.getValidationStatus())
            && authorRepository.existsByPublicationIdAndResearcherId(publication.getId(), linkedResearcherId);
    }

    private boolean canValidate(ValidationStatus validationStatus) {
        Set<String> roles = currentRoles();
        return validationStatus == ValidationStatus.PENDING_VALIDATION
            && (roles.contains("ADMIN") || roles.contains("VALIDATOR"));
    }

    private String submittedBy(
        PublicationEntity publication,
        Map<Long, String> userDisplayNames,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        if (publication.getCreatedByUserId() == null) {
            return null;
        }
        return canReadWorkflowActors(publication, visibilityScope, linkedResearcherId)
            ? userDisplayNames.get(publication.getCreatedByUserId())
            : null;
    }

    private String validatedBy(
        PublicationEntity publication,
        Map<Long, String> userDisplayNames,
        VisibilityScope visibilityScope,
        Long linkedResearcherId
    ) {
        if (publication.getValidatedByUserId() == null) {
            return null;
        }
        return canReadWorkflowActors(publication, visibilityScope, linkedResearcherId)
            ? userDisplayNames.get(publication.getValidatedByUserId())
            : null;
    }

    private boolean canReadWorkflowActors(PublicationEntity publication, VisibilityScope visibilityScope, Long linkedResearcherId) {
        Set<String> roles = currentRoles();
        if (roles.contains("ADMIN") || roles.contains("VALIDATOR")) {
            return true;
        }
        return roles.contains("RESEARCHER")
            && visibilityScope == VisibilityScope.MY_DATA
            && linkedResearcherId != null
            && authorRepository.existsByPublicationIdAndResearcherId(publication.getId(), linkedResearcherId);
    }

    private Set<String> currentRoles() {
        Set<String> roles = visibilityContext.currentRoles();
        return roles == null ? Set.of() : roles;
    }

    private Set<Long> workflowUserIds(Collection<PublicationEntity> publications) {
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

    private List<FilterCountResponse> countRows(List<Object[]> rows, boolean hasId) {
        return rows.stream()
            .map(row -> {
                Long id = hasId ? (Long) row[0] : null;
                String value = String.valueOf(hasId ? row[1] : row[0]);
                String label = value;
                long count = (Long) (hasId ? row[2] : row[1]);
                return new FilterCountResponse(id, value, label, count);
            })
            .toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
