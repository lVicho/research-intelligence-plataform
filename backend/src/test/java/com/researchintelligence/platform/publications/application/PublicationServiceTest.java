package com.researchintelligence.platform.publications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.UserRepository;
import com.researchintelligence.platform.publications.api.PublicationAuthorRequest;
import com.researchintelligence.platform.publications.api.PublicationRequest;
import com.researchintelligence.platform.publications.api.PublicationResponse;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorEntity;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.PublisherRepository;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.publications.persistence.VenueRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicationServiceTest {

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private PublicationAuthorRepository authorRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private PublicationTopicRepository publicationTopicRepository;

    @Mock
    private ResearcherRepository researcherRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private PublisherRepository publisherRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VisibilityContext visibilityContext;

    @Mock
    private ActivityAuditService auditService;

    private PublicationService service;

    @BeforeEach
    void setUp() {
        service = new PublicationService(
            publicationRepository,
            authorRepository,
            topicRepository,
            publicationTopicRepository,
            researcherRepository,
            venueRepository,
            publisherRepository,
            userRepository,
            visibilityContext,
            auditService
        );
    }

    @Test
    void rejectsDuplicateAuthorOrderValues() {
        PublicationRequest request = publicationRequest(List.of(
            new PublicationAuthorRequest(null, "External Author One", null, 1, true),
            new PublicationAuthorRequest(null, "External Author Two", null, 1, false)
        ));

        assertThrows(BusinessRuleException.class, () -> service.create(request));
    }

    @Test
    void rejectsAuthorThatIsBothInternalAndExternal() {
        PublicationRequest request = publicationRequest(List.of(
            new PublicationAuthorRequest(5L, "External Author One", null, 1, true)
        ));

        assertThrows(BusinessRuleException.class, () -> service.create(request));
    }

    @Test
    void createsPublicationWithEditableMetadata() {
        PublicationRequest request = metadataRequest("Clinical Foundation Models");
        when(venueRepository.existsById(10L)).thenReturn(true);
        when(publisherRepository.existsById(20L)).thenReturn(true);
        when(publicationRepository.save(any(PublicationEntity.class))).thenAnswer(invocation -> {
            PublicationEntity publication = invocation.getArgument(0);
            publication.setId(99L);
            return publication;
        });
        when(authorRepository.findByPublicationIdOrderByAuthorOrderAsc(99L)).thenReturn(List.of(
            new PublicationAuthorEntity(99L, null, "External Author", "External Organization", 1, true)
        ));
        when(publicationTopicRepository.findByPublicationId(99L)).thenReturn(List.of());
        when(visibilityContext.defaultScope()).thenReturn(VisibilityScope.ADMIN_ALL);
        when(visibilityContext.linkedResearcherId()).thenReturn(Optional.empty());
        when(visibilityContext.currentRoles()).thenReturn(Set.of("ADMIN"));

        PublicationResponse response = service.create(request);

        ArgumentCaptor<PublicationEntity> publicationCaptor = ArgumentCaptor.forClass(PublicationEntity.class);
        verify(publicationRepository).save(publicationCaptor.capture());
        PublicationEntity saved = publicationCaptor.getValue();
        assertEquals("Public summary for clinical teams.", saved.getPublicSummary());
        assertEquals(LocalDate.of(2026, 4, 12), saved.getPublicationDate());
        assertEquals(10L, saved.getVenueId());
        assertEquals(20L, saved.getPublisherId());
        assertEquals("978-1-4028-9462-6", saved.getIsbn());
        assertEquals("2049-3630", saved.getIssn());
        assertEquals("es", saved.getLanguageCode());
        assertEquals("Imported from institutional repository batch 2026-04.", saved.getSourceDetail());
        assertEquals(ValidationStatus.PENDING_VALIDATION, saved.getValidationStatus());
        assertEquals(saved.getPublicSummary(), response.publicSummary());
        assertEquals(saved.getPublicationDate(), response.publicationDate());
        assertTrue(response.canEdit());
    }

    @Test
    void updatesPublicationMetadataAndKeepsValidationVisibilityGate() {
        PublicationEntity existing = new PublicationEntity(
            "Old title",
            "Old abstract",
            2025,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Old source",
            null
        );
        existing.setId(100L);
        existing.setValidationStatus(ValidationStatus.VALIDATED);
        PublicationRequest request = metadataRequest("Updated clinical publication");
        when(publicationRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(venueRepository.existsById(10L)).thenReturn(true);
        when(publisherRepository.existsById(20L)).thenReturn(true);
        when(auditService.changes()).thenReturn(new LinkedHashMap<>());
        when(authorRepository.findByPublicationIdOrderByAuthorOrderAsc(100L)).thenReturn(List.of());
        when(publicationTopicRepository.findByPublicationId(100L)).thenReturn(List.of());
        when(visibilityContext.defaultScope()).thenReturn(VisibilityScope.ADMIN_ALL);
        when(visibilityContext.linkedResearcherId()).thenReturn(Optional.empty());
        when(visibilityContext.currentRoles()).thenReturn(Set.of("ADMIN"));

        PublicationResponse response = service.update(100L, request);

        assertEquals("Updated clinical publication", existing.getTitle());
        assertEquals("Public summary for clinical teams.", existing.getPublicSummary());
        assertEquals(LocalDate.of(2026, 4, 12), existing.getPublicationDate());
        assertEquals("Imported from institutional repository batch 2026-04.", existing.getSourceDetail());
        assertEquals(10L, existing.getVenueId());
        assertEquals(20L, existing.getPublisherId());
        assertEquals("978-1-4028-9462-6", existing.getIsbn());
        assertEquals("2049-3630", existing.getIssn());
        assertEquals("es", existing.getLanguageCode());
        assertEquals(ValidationStatus.VALIDATED, existing.getValidationStatus());
        assertEquals(ValidationStatus.VALIDATED, response.validationStatus());
        assertFalse(response.canSubmit());
        verify(auditService).recordUpdated(
            ValidationEntityType.PUBLICATION,
            100L,
            ValidationStatus.VALIDATED,
            ValidationStatus.VALIDATED,
            new LinkedHashMap<>()
        );
    }

    @Test
    void normalizesTopicNames() {
        assertEquals("clinical ai", TopicNormalizer.normalize("  Clinical   AI  "));
        assertEquals("Clinical AI", TopicNormalizer.displayName("  Clinical   AI  "));
    }

    private PublicationRequest publicationRequest(List<PublicationAuthorRequest> authors) {
        return new PublicationRequest(
            "Clinical Foundation Models",
            "Abstract",
            null,
            2025,
            null,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Journal of Clinical AI Systems",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            authors,
            List.of("Clinical AI")
        );
    }

    private PublicationRequest metadataRequest(String title) {
        return new PublicationRequest(
            title,
            "Technical abstract",
            "Public summary for clinical teams.",
            2026,
            LocalDate.of(2026, 4, 12),
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            "10.1234/clinical.2026.001",
            "Institutional repository",
            "Imported from institutional repository batch 2026-04.",
            "https://example.test/publications/clinical-foundation-models",
            10L,
            20L,
            "978-1-4028-9462-6",
            "2049-3630",
            "es",
            List.of(new PublicationAuthorRequest(null, "External Author", "External Organization", 1, true)),
            List.of()
        );
    }
}
