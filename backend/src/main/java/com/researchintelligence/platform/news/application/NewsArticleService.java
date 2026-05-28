package com.researchintelligence.platform.news.application;

import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.news.api.NewsArticleRequest;
import com.researchintelligence.platform.news.api.NewsArticleResponse;
import com.researchintelligence.platform.news.api.PortalNewsArticleResponse;
import com.researchintelligence.platform.news.api.PortalNewsArticleSummaryResponse;
import com.researchintelligence.platform.news.domain.NewsArticleStatus;
import com.researchintelligence.platform.news.persistence.NewsArticleEntity;
import com.researchintelligence.platform.news.persistence.NewsArticleRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.domain.OrganizationScope;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class NewsArticleService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final ValidationStatus VALIDATED = ValidationStatus.VALIDATED;

    private final NewsArticleRepository repository;
    private final PublicationRepository publicationRepository;
    private final ResearcherRepository researcherRepository;
    private final ResearcherAffiliationRepository affiliationRepository;
    private final ResearchUnitRepository researchUnitRepository;
    private final VisibilityContext visibilityContext;

    public NewsArticleService(
        NewsArticleRepository repository,
        PublicationRepository publicationRepository,
        ResearcherRepository researcherRepository,
        ResearcherAffiliationRepository affiliationRepository,
        ResearchUnitRepository researchUnitRepository,
        VisibilityContext visibilityContext
    ) {
        this.repository = repository;
        this.publicationRepository = publicationRepository;
        this.researcherRepository = researcherRepository;
        this.affiliationRepository = affiliationRepository;
        this.researchUnitRepository = researchUnitRepository;
        this.visibilityContext = visibilityContext;
    }

    public PageResponse<NewsArticleResponse> searchAdmin(NewsArticleStatus status, String text, int page, int size) {
        requireAdmin();
        return PageResponse.from(repository.searchAdmin(
            status,
            normalizedFilter(text),
            pattern(text),
            pageRequest(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")))
        ).map(this::toAdminResponse));
    }

    public NewsArticleResponse findAdminById(Long id) {
        requireAdmin();
        return toAdminResponse(repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("NewsArticle", id)));
    }

    @Transactional
    public NewsArticleResponse create(NewsArticleRequest request) {
        requireAdmin();
        validateRequest(request);
        NewsArticleEntity entity = new NewsArticleEntity(
            normalizeRequired(request.title(), "title"),
            normalizeRequired(request.summary(), "summary"),
            normalizeRequiredText(request.body(), "body"),
            editableStatus(request.status(), NewsArticleStatus.DRAFT)
        );
        applyRequest(entity, request);
        validateRelatedRecordsExist(entity);
        return toAdminResponse(repository.save(entity));
    }

    @Transactional
    public NewsArticleResponse update(Long id, NewsArticleRequest request) {
        requireAdmin();
        validateRequest(request);
        NewsArticleEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("NewsArticle", id));
        applyRequest(entity, request);
        if (request.status() != null) {
            entity.setStatus(editableStatus(request.status(), entity.getStatus()));
            if (entity.getStatus() != NewsArticleStatus.PUBLISHED) {
                entity.setPublishedAt(null);
            }
        }
        validateRelatedRecordsExist(entity);
        if (entity.getStatus() == NewsArticleStatus.PUBLISHED) {
            validatePublicEvidence(entity);
        }
        return toAdminResponse(entity);
    }

    @Transactional
    public NewsArticleResponse publish(Long id) {
        requireAdmin();
        NewsArticleEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("NewsArticle", id));
        if (entity.getStatus() == NewsArticleStatus.ARCHIVED) {
            throw new BusinessRuleException("Archived news cannot be published without moving it back to draft first.");
        }
        validatePublicEvidence(entity);
        entity.setStatus(NewsArticleStatus.PUBLISHED);
        entity.setPublishedAt(Instant.now());
        return toAdminResponse(entity);
    }

    @Transactional
    public NewsArticleResponse archive(Long id) {
        requireAdmin();
        NewsArticleEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("NewsArticle", id));
        entity.setStatus(NewsArticleStatus.ARCHIVED);
        return toAdminResponse(entity);
    }

    public PageResponse<PortalNewsArticleSummaryResponse> searchPublic(String text, int page, int size) {
        return PageResponse.from(repository.searchPublic(
            NewsArticleStatus.PUBLISHED,
            normalizedFilter(text),
            pattern(text),
            pageRequest(page, size, Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id")))
        ).map(this::toPublicSummaryResponse));
    }

    public PortalNewsArticleResponse findPublicById(Long id) {
        NewsArticleEntity entity = repository.findByIdAndStatus(id, NewsArticleStatus.PUBLISHED)
            .orElseThrow(() -> new ResourceNotFoundException("NewsArticle", id));
        return toPublicResponse(entity);
    }

    private void applyRequest(NewsArticleEntity entity, NewsArticleRequest request) {
        entity.setTitle(normalizeRequired(request.title(), "title"));
        entity.setSummary(normalizeRequired(request.summary(), "summary"));
        entity.setBody(normalizeRequiredText(request.body(), "body"));
        entity.setImageUrl(normalizeOptional(request.imageUrl()));
        entity.setImageAlt(normalizeOptional(request.imageAlt()));
        entity.setImageSuggestion(normalizeOptionalText(request.imageSuggestion()));
        entity.replaceRelatedPublicationIds(normalizedIds(request.relatedPublicationIds()));
        entity.replaceRelatedResearcherIds(normalizedIds(request.relatedResearcherIds()));
        entity.replaceRelatedUnitIds(normalizedIds(request.relatedUnitIds()));
    }

    private void validateRequest(NewsArticleRequest request) {
        if (request == null) {
            throw new BusinessRuleException("News article request is required.");
        }
        normalizeRequired(request.title(), "title");
        normalizeRequired(request.summary(), "summary");
        normalizeRequiredText(request.body(), "body");
    }

    private NewsArticleStatus editableStatus(NewsArticleStatus requested, NewsArticleStatus fallback) {
        NewsArticleStatus status = requested == null ? fallback : requested;
        if (status == NewsArticleStatus.PUBLISHED || status == NewsArticleStatus.ARCHIVED) {
            throw new BusinessRuleException("Use the publish or archive workflow to set final news statuses.");
        }
        return status;
    }

    private void validateRelatedRecordsExist(NewsArticleEntity entity) {
        requireAllFound("Publication", entity.getRelatedPublicationIds(), ids(publicationRepository.findAllById(entity.getRelatedPublicationIds())));
        requireAllFound("Researcher", entity.getRelatedResearcherIds(), ids(researcherRepository.findAllById(entity.getRelatedResearcherIds())));
        Set<Long> foundUnitIds = researchUnitRepository.findAllById(entity.getRelatedUnitIds())
            .stream()
            .map(unit -> unit.getId())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        requireAllFound("ResearchUnit", entity.getRelatedUnitIds(), foundUnitIds);
    }

    private void validatePublicEvidence(NewsArticleEntity entity) {
        List<Long> invalidPublicationIds = publicationRepository.findAllById(entity.getRelatedPublicationIds())
            .stream()
            .filter(publication -> publication.getValidationStatus() != VALIDATED)
            .map(PublicationEntity::getId)
            .toList();
        if (!invalidPublicationIds.isEmpty()) {
            throw new BusinessRuleException("Published news can only reference validated publications.");
        }
        for (Long publicationId : entity.getRelatedPublicationIds()) {
            if (!publicRelatedPublicationIds(Set.of(publicationId)).contains(publicationId)) {
                throw new BusinessRuleException("Published news can only reference validated publications.");
            }
        }
        for (Long researcherId : entity.getRelatedResearcherIds()) {
            if (!publicRelatedResearcherIds(Set.of(researcherId)).contains(researcherId)) {
                throw new BusinessRuleException("Published news can only reference public validated researchers.");
            }
        }
        for (Long unitId : entity.getRelatedUnitIds()) {
            if (!publicRelatedUnitIds(Set.of(unitId)).contains(unitId)) {
                throw new BusinessRuleException("Published news can only reference public validated research units.");
            }
        }
    }

    private NewsArticleResponse toAdminResponse(NewsArticleEntity entity) {
        return new NewsArticleResponse(
            entity.getId(),
            entity.getTitle(),
            entity.getSummary(),
            entity.getBody(),
            entity.getStatus(),
            entity.getImageUrl(),
            entity.getImageAlt(),
            entity.getImageSuggestion(),
            entity.getPublishedAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getCreatedByUserId(),
            entity.getUpdatedByUserId(),
            sortedIds(entity.getRelatedPublicationIds()),
            sortedIds(entity.getRelatedResearcherIds()),
            sortedIds(entity.getRelatedUnitIds())
        );
    }

    private PortalNewsArticleSummaryResponse toPublicSummaryResponse(NewsArticleEntity entity) {
        return new PortalNewsArticleSummaryResponse(
            entity.getId(),
            entity.getTitle(),
            entity.getSummary(),
            entity.getImageUrl(),
            entity.getImageAlt(),
            entity.getPublishedAt(),
            publicRelatedPublicationIds(entity.getRelatedPublicationIds()),
            publicRelatedResearcherIds(entity.getRelatedResearcherIds()),
            publicRelatedUnitIds(entity.getRelatedUnitIds())
        );
    }

    private PortalNewsArticleResponse toPublicResponse(NewsArticleEntity entity) {
        return new PortalNewsArticleResponse(
            entity.getId(),
            entity.getTitle(),
            entity.getSummary(),
            entity.getBody(),
            entity.getImageUrl(),
            entity.getImageAlt(),
            entity.getPublishedAt(),
            publicRelatedPublicationIds(entity.getRelatedPublicationIds()),
            publicRelatedResearcherIds(entity.getRelatedResearcherIds()),
            publicRelatedUnitIds(entity.getRelatedUnitIds())
        );
    }

    private List<Long> publicRelatedPublicationIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return publicationRepository.findAllById(ids).stream()
            .filter(publication -> publication.getValidationStatus() == VALIDATED)
            .map(PublicationEntity::getId)
            .sorted()
            .toList();
    }

    private List<Long> publicRelatedResearcherIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        return researcherRepository.findAllById(ids).stream()
            .filter(researcher -> researcher.getValidationStatus() == VALIDATED)
            .filter(ResearcherEntity::isActive)
            .map(ResearcherEntity::getId)
            .filter(id -> affiliationRepository.countCurrentPrimaryAffiliationsVisibleInPortal(id, VALIDATED, today) > 0)
            .sorted()
            .toList();
    }

    private List<Long> publicRelatedUnitIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
            .filter(id -> researchUnitRepository.countPortalVisibleValidatedById(id, VALIDATED, OrganizationScope.INTERNAL) > 0)
            .sorted()
            .toList();
    }

    private PlatformUserPrincipal requireAdmin() {
        PlatformUserPrincipal user = visibilityContext.currentUser()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is required to manage news."));
        if (!user.roles().contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can manage news.");
        }
        return user;
    }

    private PageRequest pageRequest(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }

    private Set<Long> normalizedIds(Collection<Long> values) {
        if (values == null || values.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return values.stream()
            .filter(id -> id != null)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> ids(Collection<? extends Object> entities) {
        return entities.stream()
            .map(entity -> {
                if (entity instanceof PublicationEntity publication) {
                    return publication.getId();
                }
                if (entity instanceof ResearcherEntity researcher) {
                    return researcher.getId();
                }
                throw new IllegalArgumentException("Unsupported entity type.");
            })
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void requireAllFound(String entityName, Set<Long> requestedIds, Set<Long> foundIds) {
        if (!foundIds.containsAll(requestedIds)) {
            throw new ResourceNotFoundException(entityName, requestedIds.stream()
                .filter(id -> !foundIds.contains(id))
                .findFirst()
                .orElse(null));
        }
    }

    private List<Long> sortedIds(Collection<Long> ids) {
        return ids.stream().sorted().toList();
    }

    private String normalizedFilter(String value) {
        return normalizeOptional(value);
    }

    private String pattern(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? "%" : "%" + normalized.toLowerCase(java.util.Locale.ROOT) + "%";
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BusinessRuleException(fieldName + " is required.");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        String normalized = value == null ? null : value.trim().replaceAll("\\s+", " ");
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new BusinessRuleException(fieldName + " is required.");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        String normalized = value == null ? null : value.trim();
        return normalized == null || normalized.isBlank() ? null : normalized;
    }
}
