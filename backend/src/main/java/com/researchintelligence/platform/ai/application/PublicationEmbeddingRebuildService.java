package com.researchintelligence.platform.ai.application;

import com.researchintelligence.platform.ai.api.PublicationEmbeddingRebuildResponse;
import com.researchintelligence.platform.ai.persistence.PublicationEmbeddingRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PublicationEmbeddingRebuildService {

    private final AiProperties properties;
    private final EmbeddingService embeddingService;
    private final PublicationRepository publicationRepository;
    private final PublicationEmbeddingRepository embeddingRepository;

    public PublicationEmbeddingRebuildService(
        AiProperties properties,
        EmbeddingService embeddingService,
        PublicationRepository publicationRepository,
        PublicationEmbeddingRepository embeddingRepository
    ) {
        this.properties = properties;
        this.embeddingService = embeddingService;
        this.publicationRepository = publicationRepository;
        this.embeddingRepository = embeddingRepository;
    }

    public PublicationEmbeddingRebuildResponse rebuild() {
        int dimension = properties.getEmbeddingDimension();
        List<Long> missingIds = embeddingRepository.findPublicationIdsMissingEmbeddings(embeddingService.provider(), embeddingService.model(), dimension);
        Map<Long, PublicationEntity> publicationsById = publicationRepository.findAllById(missingIds)
            .stream()
            .collect(Collectors.toMap(PublicationEntity::getId, Function.identity()));
        List<String> warnings = new ArrayList<>();
        int processed = 0;
        int stored = 0;
        for (Long publicationId : missingIds) {
            PublicationEntity publication = publicationsById.get(publicationId);
            if (publication == null) {
                continue;
            }
            EmbeddingResponse response = embeddingService.embed(embeddingInput(publication));
            validateDimension(response, dimension, publication.getId());
            embeddingRepository.upsert(
                publication.getId(),
                embeddingService.provider(),
                embeddingService.model(),
                dimension,
                EmbeddingVectorFormatter.toPgVector(response.vector())
            );
            processed++;
            stored++;
            warnings.addAll(response.warnings());
        }
        if ("mock".equals(embeddingService.provider())) {
            warnings.add("Mock embedding provider is active; stored vectors are deterministic placeholders.");
        }
        long totalPublications = publicationRepository.count();
        return new PublicationEmbeddingRebuildResponse(
            totalPublications > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalPublications,
            processed,
            stored,
            true,
            embeddingService.provider(),
            embeddingService.model(),
            dimension,
            "Publication embedding rebuild completed.",
            warnings.stream().distinct().toList()
        );
    }

    private void validateDimension(EmbeddingResponse response, int expectedDimension, Long publicationId) {
        int actualDimension = response.vector().size();
        if (actualDimension != expectedDimension) {
            throw new BusinessRuleException(
                "Embedding dimension mismatch for publication " + publicationId + ": expected " + expectedDimension + " but provider returned " + actualDimension + "."
            );
        }
    }

    private String embeddingInput(PublicationEntity publication) {
        String title = normalizeWhitespace(publication.getTitle());
        String abstractText = normalizeWhitespace(publication.getAbstractText());
        if (abstractText.isBlank()) {
            return "Title: " + title;
        }
        return "Title: " + title + ". Abstract: " + abstractText;
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
