package com.researchintelligence.platform.ingestion.application;

import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PublicationIngestionMatcher {

    private final PublicationRepository publicationRepository;
    private final ResearcherRepository researcherRepository;

    public PublicationIngestionMatcher(PublicationRepository publicationRepository, ResearcherRepository researcherRepository) {
        this.publicationRepository = publicationRepository;
        this.researcherRepository = researcherRepository;
    }

    public Optional<PublicationEntity> findPublication(String doi, String title, Integer year) {
        String normalizedDoi = IngestionNormalizer.normalizeDoi(doi);
        if (!normalizedDoi.isBlank()) {
            return publicationRepository.findFirstByDoiIgnoreCase(normalizedDoi);
        }
        if (year == null) {
            return Optional.empty();
        }
        String normalizedTitle = IngestionNormalizer.normalizeText(title);
        if (normalizedTitle.isBlank()) {
            return Optional.empty();
        }
        return publicationRepository.findByPublicationYear(year)
            .stream()
            .filter(publication -> normalizedTitle.equals(IngestionNormalizer.normalizeText(publication.getTitle())))
            .findFirst();
    }

    public Optional<ResearcherEntity> findInternalAuthor(String orcid, String name) {
        String normalizedOrcid = IngestionNormalizer.normalizeOrcid(orcid);
        if (!normalizedOrcid.isBlank()) {
            return researcherRepository.findFirstByOrcidIgnoreCase(normalizedOrcid);
        }
        String normalizedName = IngestionNormalizer.normalizeText(name);
        if (normalizedName.isBlank()) {
            return Optional.empty();
        }
        List<ResearcherEntity> matches = researcherRepository.findAll()
            .stream()
            .filter(researcher -> normalizedName.equals(normalizedResearcherName(researcher)))
            .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private String normalizedResearcherName(ResearcherEntity researcher) {
        String displayName = IngestionNormalizer.blankToNull(researcher.getDisplayName());
        return IngestionNormalizer.normalizeText(displayName != null ? displayName : researcher.getFullName());
    }
}
