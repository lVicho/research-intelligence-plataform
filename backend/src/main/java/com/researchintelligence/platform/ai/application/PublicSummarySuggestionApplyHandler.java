package com.researchintelligence.platform.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.ai.persistence.AiSuggestionEntity;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicSummarySuggestionApplyHandler implements AiSuggestionApplyHandler {

    private static final String TARGET_RESEARCHER = "RESEARCHER";
    private static final String TARGET_RESEARCH_UNIT = "RESEARCH_UNIT";
    private static final String TARGET_PUBLICATION = "PUBLICATION";
    private static final String TARGET_EXTERNAL_ORGANIZATION = "EXTERNAL_ORGANIZATION";

    private final ResearcherRepository researcherRepository;
    private final ResearchUnitRepository researchUnitRepository;
    private final PublicationRepository publicationRepository;
    private final ObjectMapper objectMapper;

    public PublicSummarySuggestionApplyHandler(
        ResearcherRepository researcherRepository,
        ResearchUnitRepository researchUnitRepository,
        PublicationRepository publicationRepository,
        ObjectMapper objectMapper
    ) {
        this.researcherRepository = researcherRepository;
        this.researchUnitRepository = researchUnitRepository;
        this.publicationRepository = publicationRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(AiSuggestionEntity suggestion) {
        return suggestion.getSuggestionType() == AiSuggestionType.PUBLIC_SUMMARY
            && suggestion.getTargetId() != null
            && supportedTarget(suggestion.getTargetType());
    }

    @Override
    @Transactional
    public AiSuggestionApplyResult apply(AiSuggestionEntity suggestion, String acceptedDataJson, PlatformUserPrincipal reviewer) {
        String summary = summaryFromPayload(acceptedDataJson);
        String targetType = normalizeTargetType(suggestion.getTargetType());
        switch (targetType) {
            case TARGET_RESEARCHER -> applyToResearcher(suggestion.getTargetId(), summary);
            case TARGET_RESEARCH_UNIT, TARGET_EXTERNAL_ORGANIZATION -> applyToResearchUnit(suggestion.getTargetId(), summary);
            case TARGET_PUBLICATION -> applyToPublication(suggestion.getTargetId(), summary);
            default -> throw new BusinessRuleException("Unsupported PUBLIC_SUMMARY target type: " + suggestion.getTargetType());
        }
        return new AiSuggestionApplyResult(true, "PUBLIC_SUMMARY_APPLY", "Accepted public summary was applied to " + targetType + ".");
    }

    private void applyToResearcher(Long researcherId, String summary) {
        ResearcherEntity researcher = researcherRepository.findById(researcherId)
            .orElseThrow(() -> new ResourceNotFoundException("Researcher", researcherId));
        researcher.setPublicProfileSummary(summary);
        researcherRepository.save(researcher);
    }

    private void applyToResearchUnit(Long researchUnitId, String summary) {
        ResearchUnitEntity unit = researchUnitRepository.findById(researchUnitId)
            .orElseThrow(() -> new ResourceNotFoundException("ResearchUnit", researchUnitId));
        unit.setPublicDescription(summary);
        researchUnitRepository.save(unit);
    }

    private void applyToPublication(Long publicationId, String summary) {
        PublicationEntity publication = publicationRepository.findById(publicationId)
            .orElseThrow(() -> new ResourceNotFoundException("Publication", publicationId));
        publication.setPublicSummary(summary);
        publicationRepository.save(publication);
    }

    private String summaryFromPayload(String acceptedDataJson) {
        try {
            JsonNode root = objectMapper.readTree(acceptedDataJson);
            String summary = text(root, "summary");
            if (summary == null) {
                summary = text(root, "publicSummary");
            }
            if (summary == null) {
                summary = text(root, "publicDescription");
            }
            if (summary == null) {
                summary = text(root, "publicProfileSummary");
            }
            if (summary == null || summary.isBlank()) {
                throw new BusinessRuleException("Accepted PUBLIC_SUMMARY payload must include a non-empty summary.");
            }
            return summary.trim();
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException("Accepted PUBLIC_SUMMARY payload is not valid JSON.");
        }
    }

    private String text(JsonNode root, String fieldName) {
        JsonNode node = root == null ? null : root.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private boolean supportedTarget(String targetType) {
        return switch (normalizeTargetType(targetType)) {
            case TARGET_RESEARCHER, TARGET_RESEARCH_UNIT, TARGET_PUBLICATION, TARGET_EXTERNAL_ORGANIZATION -> true;
            default -> false;
        };
    }

    private String normalizeTargetType(String targetType) {
        return targetType == null ? "" : targetType.trim().toUpperCase(Locale.ROOT);
    }
}
