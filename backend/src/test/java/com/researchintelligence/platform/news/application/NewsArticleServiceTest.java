package com.researchintelligence.platform.news.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.RoleEntity;
import com.researchintelligence.platform.auth.persistence.UserEntity;
import com.researchintelligence.platform.news.api.NewsArticleRequest;
import com.researchintelligence.platform.news.api.NewsArticleResponse;
import com.researchintelligence.platform.news.api.PortalNewsArticleResponse;
import com.researchintelligence.platform.news.api.PortalNewsArticleSummaryResponse;
import com.researchintelligence.platform.news.domain.NewsArticleStatus;
import com.researchintelligence.platform.news.persistence.NewsArticleEntity;
import com.researchintelligence.platform.news.persistence.NewsArticleRepository;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.domain.OrganizationScope;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NewsArticleServiceTest {

    @Mock
    private NewsArticleRepository repository;

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private ResearcherRepository researcherRepository;

    @Mock
    private ResearcherAffiliationRepository affiliationRepository;

    @Mock
    private ResearchUnitRepository researchUnitRepository;

    @Mock
    private VisibilityContext visibilityContext;

    private NewsArticleService service;

    @BeforeEach
    void setUp() {
        service = new NewsArticleService(
            repository,
            publicationRepository,
            researcherRepository,
            affiliationRepository,
            researchUnitRepository,
            visibilityContext
        );
        lenient().when(visibilityContext.currentUser()).thenReturn(Optional.of(principal("ADMIN")));
        lenient().when(publicationRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(researcherRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(researchUnitRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(repository.save(any(NewsArticleEntity.class))).thenAnswer(invocation -> {
            NewsArticleEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return entity;
        });
    }

    @Test
    void adminCanCreateDraftNewsArticle() {
        NewsArticleResponse response = service.create(request(NewsArticleStatus.DRAFT, List.of(), List.of(), List.of()));

        assertEquals(100L, response.id());
        assertEquals("Noticia de investigacion", response.title());
        assertEquals(NewsArticleStatus.DRAFT, response.status());
        assertEquals("Cuerpo en espanol para revision administrativa.", response.body());
        verify(repository).save(any(NewsArticleEntity.class));
    }

    @Test
    void publishMovesArticleToPublishedWithTimestamp() {
        NewsArticleEntity article = article(10L, NewsArticleStatus.PENDING_REVIEW);
        article.getRelatedPublicationIds().add(20L);
        article.getRelatedResearcherIds().add(30L);
        article.getRelatedUnitIds().add(40L);
        when(repository.findById(10L)).thenReturn(Optional.of(article));
        when(publicationRepository.findAllById(article.getRelatedPublicationIds())).thenReturn(List.of(publication(20L, ValidationStatus.VALIDATED)));
        when(researcherRepository.findAllById(article.getRelatedResearcherIds())).thenReturn(List.of(researcher(30L, ValidationStatus.VALIDATED, true)));
        when(affiliationRepository.countCurrentPrimaryAffiliationsVisibleInPortal(eq(30L), eq(ValidationStatus.VALIDATED), any())).thenReturn(1L);
        when(researchUnitRepository.countPortalVisibleValidatedById(40L, ValidationStatus.VALIDATED, OrganizationScope.INTERNAL)).thenReturn(1L);

        NewsArticleResponse response = service.publish(10L);

        assertEquals(NewsArticleStatus.PUBLISHED, response.status());
        assertNotNull(response.publishedAt());
        assertEquals(List.of(20L), response.relatedPublicationIds());
        assertEquals(List.of(30L), response.relatedResearcherIds());
        assertEquals(List.of(40L), response.relatedUnitIds());
    }

    @Test
    void archiveMovesArticleOutOfPublicWorkflow() {
        NewsArticleEntity article = article(10L, NewsArticleStatus.PUBLISHED);
        article.setPublishedAt(Instant.parse("2026-05-25T10:00:00Z"));
        when(repository.findById(10L)).thenReturn(Optional.of(article));

        NewsArticleResponse response = service.archive(10L);

        assertEquals(NewsArticleStatus.ARCHIVED, response.status());
    }

    @Test
    void publicSearchRequestsPublishedNewsOnly() {
        NewsArticleEntity published = article(11L, NewsArticleStatus.PUBLISHED);
        published.setPublishedAt(Instant.parse("2026-05-25T10:00:00Z"));
        when(repository.searchPublic(eq(NewsArticleStatus.PUBLISHED), isNull(), eq("%"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(published), PageRequest.of(0, 20), 1));

        PageResponse<PortalNewsArticleSummaryResponse> response = service.searchPublic(null, 0, 20);

        assertEquals(List.of("Noticia de investigacion"), response.content().stream().map(PortalNewsArticleSummaryResponse::title).toList());
        verify(repository).searchPublic(eq(NewsArticleStatus.PUBLISHED), isNull(), eq("%"), any(Pageable.class));
    }

    @Test
    void publicDetailRequestsPublishedNewsOnly() {
        when(repository.findByIdAndStatus(12L, NewsArticleStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findPublicById(12L));
        verify(repository).findByIdAndStatus(12L, NewsArticleStatus.PUBLISHED);
    }

    @Test
    void publishRejectsNonValidatedPublicationEvidence() {
        NewsArticleEntity article = article(12L, NewsArticleStatus.PENDING_REVIEW);
        article.getRelatedPublicationIds().add(99L);
        when(repository.findById(12L)).thenReturn(Optional.of(article));
        when(publicationRepository.findAllById(article.getRelatedPublicationIds()))
            .thenReturn(List.of(publication(99L, ValidationStatus.PENDING_VALIDATION)));

        assertThrows(BusinessRuleException.class, () -> service.publish(12L));
    }

    @Test
    void publicDetailFiltersNonValidatedRelatedEvidence() {
        NewsArticleEntity article = article(13L, NewsArticleStatus.PUBLISHED);
        article.getRelatedPublicationIds().add(99L);
        article.setPublishedAt(Instant.parse("2026-05-25T10:00:00Z"));
        when(repository.findByIdAndStatus(13L, NewsArticleStatus.PUBLISHED)).thenReturn(Optional.of(article));
        when(publicationRepository.findAllById(article.getRelatedPublicationIds()))
            .thenReturn(List.of(publication(99L, ValidationStatus.REJECTED)));

        PortalNewsArticleResponse response = service.findPublicById(13L);

        assertTrue(response.relatedPublicationIds().isEmpty());
    }

    @Test
    void nonAdminCannotManageNews() {
        when(visibilityContext.currentUser()).thenReturn(Optional.of(principal("RESEARCHER")));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.create(request(NewsArticleStatus.DRAFT, List.of(), List.of(), List.of()))
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    private NewsArticleRequest request(
        NewsArticleStatus status,
        List<Long> publicationIds,
        List<Long> researcherIds,
        List<Long> unitIds
    ) {
        return new NewsArticleRequest(
            "Noticia de investigacion",
            "Resumen en espanol para el portal.",
            "Cuerpo en espanol para revision administrativa.",
            status,
            null,
            null,
            null,
            publicationIds,
            researcherIds,
            unitIds
        );
    }

    private NewsArticleEntity article(Long id, NewsArticleStatus status) {
        NewsArticleEntity article = new NewsArticleEntity(
            "Noticia de investigacion",
            "Resumen en espanol.",
            "Cuerpo en espanol.",
            status
        );
        article.setId(id);
        return article;
    }

    private PublicationEntity publication(Long id, ValidationStatus validationStatus) {
        PublicationEntity publication = new PublicationEntity(
            "Publicacion validada",
            "Resumen validado",
            2025,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Demo Journal",
            null
        );
        publication.setId(id);
        publication.setValidationStatus(validationStatus);
        return publication;
    }

    private ResearcherEntity researcher(Long id, ValidationStatus validationStatus, boolean active) {
        ResearcherEntity researcher = new ResearcherEntity("Maya Chen", "Maya Chen", "maya@example.test", null, active);
        researcher.setId(id);
        researcher.setValidationStatus(validationStatus);
        return researcher;
    }

    @SuppressWarnings("unused")
    private ResearchUnitEntity unit(Long id) {
        ResearchUnitEntity unit = new ResearchUnitEntity("Grupo de IA Clinica", null, ResearchUnitType.RESEARCH_GROUP, null, "Espana", "Madrid", null, true);
        unit.setId(id);
        unit.setValidationStatus(ValidationStatus.VALIDATED);
        unit.setOrganizationScope(OrganizationScope.INTERNAL);
        unit.setVisibleInPortal(true);
        return unit;
    }

    private PlatformUserPrincipal principal(String role) {
        UserEntity user = new UserEntity(role.toLowerCase() + "@example.test", "Test User", "{noop}password", true, null);
        user.setId(1L);
        user.setRoles(new LinkedHashSet<>(List.of(new RoleEntity(role, role, role))));
        return new PlatformUserPrincipal(user);
    }
}
