package com.researchintelligence.platform.ingestion.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicationIngestionMatcherTest {

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private ResearcherRepository researcherRepository;

    @Test
    void matchesPublicationByNormalizedTitleAndYearWhenDoiIsMissing() {
        PublicationEntity publication = new PublicationEntity(
            "AI for Clinical Decision Support",
            null,
            2025,
            PublicationType.ARTICLE,
            PublicationStatus.PUBLISHED,
            null,
            null,
            null
        );
        when(publicationRepository.findByPublicationYear(2025)).thenReturn(List.of(publication));
        PublicationIngestionMatcher matcher = new PublicationIngestionMatcher(publicationRepository, researcherRepository);

        Optional<PublicationEntity> result = matcher.findPublication(null, " ai FOR clinical-decision support ", 2025);

        assertTrue(result.isPresent());
        assertEquals(publication, result.get());
    }

    @Test
    void matchesResearcherByOrcidBeforeName() {
        ResearcherEntity researcher = new ResearcherEntity("Maria Garcia", "M. Garcia", null, "0000-0002-1825-0097", true);
        when(researcherRepository.findFirstByOrcidIgnoreCase("0000-0002-1825-0097")).thenReturn(Optional.of(researcher));
        PublicationIngestionMatcher matcher = new PublicationIngestionMatcher(publicationRepository, researcherRepository);

        Optional<ResearcherEntity> result = matcher.findInternalAuthor("https://orcid.org/0000-0002-1825-0097", "Different Name");

        assertTrue(result.isPresent());
        assertEquals(researcher, result.get());
    }

    @Test
    void matchesSingleResearcherByNormalizedDisplayNameWhenOrcidIsMissing() {
        ResearcherEntity researcher = new ResearcherEntity("Maria Garcia Lopez", "Maria Garcia-Lopez", null, null, true);
        when(researcherRepository.findAll()).thenReturn(List.of(researcher));
        PublicationIngestionMatcher matcher = new PublicationIngestionMatcher(publicationRepository, researcherRepository);

        Optional<ResearcherEntity> result = matcher.findInternalAuthor(null, " maria garcia lopez ");

        assertTrue(result.isPresent());
        assertEquals(researcher, result.get());
    }
}
