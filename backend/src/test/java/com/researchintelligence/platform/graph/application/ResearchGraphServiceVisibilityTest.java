package com.researchintelligence.platform.graph.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.graph.api.GraphResponse;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorEntity;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicEntity;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.domain.AffiliationType;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.domain.ResearchUnitType;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ResearchGraphServiceVisibilityTest {

    @Mock
    private ResearcherRepository researcherRepository;

    @Mock
    private ResearcherAffiliationRepository affiliationRepository;

    @Mock
    private ResearchUnitRepository researchUnitRepository;

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private PublicationAuthorRepository authorRepository;

    @Mock
    private PublicationTopicRepository publicationTopicRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private VisibilityContext visibilityContext;

    private ResearchGraphService service;
    private List<PublicationEntity> publications;
    private List<PublicationAuthorEntity> authors;
    private List<ResearchUnitEntity> units;
    private List<ResearcherAffiliationEntity> affiliations;

    @BeforeEach
    void setUp() {
        service = new ResearchGraphService(
            researcherRepository,
            affiliationRepository,
            researchUnitRepository,
            publicationRepository,
            authorRepository,
            publicationTopicRepository,
            topicRepository,
            visibilityContext
        );

        publications = List.of(
            publication(10L, "Validated activity", ValidationStatus.VALIDATED),
            publication(11L, "Pending activity", ValidationStatus.PENDING_VALIDATION),
            publication(12L, "Rejected activity", ValidationStatus.REJECTED)
        );
        authors = List.of(
            new PublicationAuthorEntity(10L, 1L, null, null, 1, true),
            new PublicationAuthorEntity(11L, 1L, null, null, 1, true),
            new PublicationAuthorEntity(12L, 1L, null, null, 1, true)
        );
        units = List.of(
            researchUnit(100L, "Validated unit", ValidationStatus.VALIDATED),
            researchUnit(101L, "Pending unit", ValidationStatus.PENDING_VALIDATION)
        );
        affiliations = List.of(
            affiliation(1L, 100L, ValidationStatus.VALIDATED),
            affiliation(1L, 101L, ValidationStatus.PENDING_VALIDATION)
        );

        ResearcherEntity researcher = researcher(1L, "Lucia Herrera", ValidationStatus.VALIDATED);
        when(researcherRepository.findById(1L)).thenReturn(Optional.of(researcher));
        when(researcherRepository.findAllById(any())).thenReturn(List.of(researcher));
        when(publicationRepository.findAuthoredByResearcherId(1L)).thenReturn(publications);
        when(authorRepository.findByPublicationIdIn(any())).thenAnswer(invocation -> authorsFor(invocation.getArgument(0)));
        when(affiliationRepository.findByResearcherIdOrderByPrimaryAffiliationDescStartDateDescIdAsc(1L)).thenReturn(affiliations);
        when(researchUnitRepository.findAllById(any())).thenAnswer(invocation -> unitsFor(invocation.getArgument(0)));
        when(publicationTopicRepository.findByPublicationIdIn(any())).thenAnswer(invocation -> topicsFor(invocation.getArgument(0)));
        when(topicRepository.findAllById(any())).thenReturn(List.of(topic(200L, "IA clinica")));
    }

    @Test
    void publicGraphExcludesPendingAndRejectedActivities() {
        GraphResponse response = service.researcherGraph(1L);

        List<String> nodeIds = response.nodes().stream().map(node -> node.id()).toList();
        assertTrue(nodeIds.contains("publication:10"));
        assertFalse(nodeIds.contains("publication:11"));
        assertFalse(nodeIds.contains("publication:12"));
        assertTrue(nodeIds.contains("research_unit:100"));
        assertFalse(nodeIds.contains("research_unit:101"));
        assertTrue(response.metadata().validationFilterApplied());
        assertTrue(response.metadata().visibilityScope().equals("PUBLIC_VALIDATED"));
    }

    @Test
    void adminGraphCanIncludeNonValidatedWhenParameterIsSupported() {
        when(visibilityContext.currentRoles()).thenReturn(Set.of("ADMIN"));

        GraphResponse response = service.researcherGraph(1L, null, true, true, false, true, false, 10, 10, 10, true);

        List<String> nodeIds = response.nodes().stream().map(node -> node.id()).toList();
        assertTrue(nodeIds.contains("publication:11"));
        assertTrue(nodeIds.contains("publication:12"));
        assertTrue(nodeIds.contains("research_unit:101"));
        assertFalse(response.metadata().validationFilterApplied());
        assertTrue(response.metadata().visibilityScope().equals("ADMIN_ALL"));
    }

    private List<PublicationAuthorEntity> authorsFor(Collection<Long> publicationIds) {
        return authors.stream()
            .filter(author -> publicationIds.contains(author.getPublicationId()))
            .toList();
    }

    private List<ResearchUnitEntity> unitsFor(Collection<Long> unitIds) {
        return units.stream()
            .filter(unit -> unitIds.contains(unit.getId()))
            .toList();
    }

    private List<PublicationTopicEntity> topicsFor(Collection<Long> publicationIds) {
        return publicationIds.stream()
            .map(publicationId -> new PublicationTopicEntity(publicationId, 200L))
            .toList();
    }

    private ResearcherEntity researcher(Long id, String name, ValidationStatus validationStatus) {
        ResearcherEntity researcher = new ResearcherEntity(name, name, null, null, true);
        researcher.setId(id);
        researcher.setValidationStatus(validationStatus);
        return researcher;
    }

    private PublicationEntity publication(Long id, String title, ValidationStatus validationStatus) {
        PublicationEntity publication = new PublicationEntity(
            title,
            "Abstract",
            2026,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            "Demo",
            null
        );
        publication.setId(id);
        publication.setValidationStatus(validationStatus);
        return publication;
    }

    private ResearchUnitEntity researchUnit(Long id, String name, ValidationStatus validationStatus) {
        ResearchUnitEntity unit = new ResearchUnitEntity(name, null, ResearchUnitType.CENTER, null, "Espana", null, null, true);
        unit.setId(id);
        unit.setValidationStatus(validationStatus);
        return unit;
    }

    private ResearcherAffiliationEntity affiliation(Long researcherId, Long unitId, ValidationStatus validationStatus) {
        ResearcherAffiliationEntity affiliation = new ResearcherAffiliationEntity(
            researcherId,
            unitId,
            "Investigadora",
            AffiliationType.MEMBER,
            null,
            null,
            true
        );
        affiliation.setValidationStatus(validationStatus);
        return affiliation;
    }

    private TopicEntity topic(Long id, String name) {
        TopicEntity topic = new TopicEntity(name, name.toLowerCase());
        ReflectionTestUtils.setField(topic, "id", id);
        return topic;
    }
}
