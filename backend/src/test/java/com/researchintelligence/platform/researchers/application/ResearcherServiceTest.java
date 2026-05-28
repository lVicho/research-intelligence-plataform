package com.researchintelligence.platform.researchers.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.audit.application.ActivityAuditService;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.auth.persistence.UserRepository;
import com.researchintelligence.platform.researchers.api.ResearcherAffiliationRequest;
import com.researchintelligence.platform.researchers.api.ResearcherResponse;
import com.researchintelligence.platform.researchers.api.ResearcherRequest;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.domain.AffiliationType;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResearcherServiceTest {

    @Mock
    private ResearcherRepository researcherRepository;

    @Mock
    private ResearcherAffiliationRepository affiliationRepository;

    @Mock
    private ResearchUnitRepository researchUnitRepository;

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private PublicationAuthorRepository publicationAuthorRepository;

    @Mock
    private PublicationTopicRepository publicationTopicRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VisibilityContext visibilityContext;

    @Mock
    private ActivityAuditService auditService;

    private ResearcherService service;

    @BeforeEach
    void setUp() {
        service = new ResearcherService(
            researcherRepository,
            affiliationRepository,
            researchUnitRepository,
            publicationRepository,
            publicationAuthorRepository,
            publicationTopicRepository,
            topicRepository,
            userRepository,
            visibilityContext,
            auditService
        );
        lenient().when(affiliationRepository.findByResearcherIdOrderByPrimaryAffiliationDescStartDateDescIdAsc(any())).thenReturn(List.of());
        lenient().when(publicationRepository.findAuthoredByResearcherId(any())).thenReturn(List.of());
        lenient().when(publicationAuthorRepository.findInternalCoauthorsByResearcherId(any(), any())).thenReturn(List.of());
        lenient().when(publicationAuthorRepository.findExternalCoauthorsByResearcherId(any(), any())).thenReturn(List.of());
    }

    @Test
    void rejectsSecondCurrentPrimaryAffiliation() {
        ResearcherEntity researcher = new ResearcherEntity("Maya Chen", "Maya Chen", "maya@example.edu", null, true);
        researcher.setId(7L);
        ResearchUnitEntity unit = new ResearchUnitEntity("Center for Clinical AI", "CCAI", ResearchUnitType.CENTER, null, "United States", "Boston", null, true);
        unit.setId(3L);

        when(researcherRepository.findById(7L)).thenReturn(Optional.of(researcher));
        when(researchUnitRepository.findById(3L)).thenReturn(Optional.of(unit));
        when(affiliationRepository.countCurrentPrimaryAffiliations(eq(7L), any(LocalDate.class))).thenReturn(1L);

        ResearcherAffiliationRequest request = new ResearcherAffiliationRequest(
            3L,
            "Research Lead",
            AffiliationType.LEADER,
            LocalDate.of(2024, 1, 1),
            null,
            true
        );

        assertThrows(BusinessRuleException.class, () -> service.addAffiliation(7L, request));
    }

    @Test
    void hidesPortalResearcherDetailWithoutVisiblePrimaryAffiliation() {
        ResearcherEntity researcher = new ResearcherEntity("Maya Chen", "Maya Chen", "maya@example.edu", null, true);
        researcher.setId(7L);
        researcher.setValidationStatus(ValidationStatus.VALIDATED);

        when(researcherRepository.findById(7L)).thenReturn(Optional.of(researcher));
        when(affiliationRepository.countCurrentPrimaryAffiliationsVisibleInPortal(eq(7L), eq(ValidationStatus.VALIDATED), any(LocalDate.class)))
            .thenReturn(0L);

        assertThrows(ResourceNotFoundException.class, () -> service.findPortalVisibleValidatedById(7L));
    }

    @Test
    void researcherCanViewOwnProfileWorkflowFields() {
        ResearcherEntity researcher = new ResearcherEntity("Maya Chen", "Maya", "maya@example.edu", "0000-0002-1825-0097", true);
        researcher.setId(7L);
        researcher.setValidationStatus(ValidationStatus.CHANGES_REQUESTED);
        researcher.setValidationComment("Please confirm email.");
        when(researcherRepository.findById(7L)).thenReturn(Optional.of(researcher));
        when(visibilityContext.defaultScope()).thenReturn(VisibilityScope.MY_DATA);
        when(visibilityContext.linkedResearcherId()).thenReturn(Optional.of(7L));
        when(visibilityContext.currentRoles()).thenReturn(Set.of("RESEARCHER"));

        ResearcherResponse response = service.findById(7L);

        assertEquals(ValidationStatus.CHANGES_REQUESTED, response.validationStatus());
        assertEquals("Please confirm email.", response.validationComment());
        assertEquals(true, response.canEdit());
        assertEquals(true, response.canSubmit());
    }

    @Test
    void researcherOwnProfileUpdateDoesNotChangeAdminOnlyActiveFlag() {
        ResearcherEntity researcher = new ResearcherEntity("Maya Chen", "Maya", "maya@example.edu", null, true);
        researcher.setId(7L);
        researcher.setValidationStatus(ValidationStatus.DRAFT);
        when(researcherRepository.findById(7L)).thenReturn(Optional.of(researcher));
        when(auditService.changes()).thenReturn(new LinkedHashMap<>());
        when(visibilityContext.defaultScope()).thenReturn(VisibilityScope.MY_DATA);
        when(visibilityContext.linkedResearcherId()).thenReturn(Optional.of(7L));
        when(visibilityContext.currentRoles()).thenReturn(Set.of("RESEARCHER"));

        ResearcherResponse response = service.updateOwn(7L, 7L, new ResearcherRequest(
            "Maya Chen Updated",
            "Maya",
            "maya.updated@example.edu",
            null,
            false
        ));

        assertEquals(true, researcher.isActive());
        assertEquals(true, response.active());
        assertEquals(ValidationStatus.DRAFT, response.validationStatus());
    }
}
