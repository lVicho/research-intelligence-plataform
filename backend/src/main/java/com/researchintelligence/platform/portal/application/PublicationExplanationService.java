package com.researchintelligence.platform.portal.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.application.LlmPrompt;
import com.researchintelligence.platform.ai.application.LlmResponse;
import com.researchintelligence.platform.ai.application.LlmService;
import com.researchintelligence.platform.portal.api.PortalPublicationExplanationReferenceResponse;
import com.researchintelligence.platform.portal.api.PublicationExplanationRequest;
import com.researchintelligence.platform.portal.api.PublicationExplanationResponse;
import com.researchintelligence.platform.portal.api.PublicationExplanationStyle;
import com.researchintelligence.platform.publications.api.RelatedPublicationResponse;
import com.researchintelligence.platform.publications.api.RelatedPublicationsResponse;
import com.researchintelligence.platform.publications.application.RelatedPublicationService;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorEntity;
import com.researchintelligence.platform.publications.persistence.PublicationAuthorRepository;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicEntity;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.domain.OrganizationScope;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PublicationExplanationService {

    private static final ValidationStatus VALIDATED = ValidationStatus.VALIDATED;
    private static final int RELATED_PUBLICATION_LIMIT = 5;
    private static final double RELATED_PUBLICATION_MIN_SCORE = 0.25;

    private final PublicationRepository publicationRepository;
    private final PublicationAuthorRepository authorRepository;
    private final PublicationTopicRepository publicationTopicRepository;
    private final TopicRepository topicRepository;
    private final ResearcherRepository researcherRepository;
    private final ResearcherAffiliationRepository affiliationRepository;
    private final ResearchUnitRepository researchUnitRepository;
    private final RelatedPublicationService relatedPublicationService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public PublicationExplanationService(
        PublicationRepository publicationRepository,
        PublicationAuthorRepository authorRepository,
        PublicationTopicRepository publicationTopicRepository,
        TopicRepository topicRepository,
        ResearcherRepository researcherRepository,
        ResearcherAffiliationRepository affiliationRepository,
        ResearchUnitRepository researchUnitRepository,
        RelatedPublicationService relatedPublicationService,
        LlmService llmService,
        ObjectMapper objectMapper
    ) {
        this.publicationRepository = publicationRepository;
        this.authorRepository = authorRepository;
        this.publicationTopicRepository = publicationTopicRepository;
        this.topicRepository = topicRepository;
        this.researcherRepository = researcherRepository;
        this.affiliationRepository = affiliationRepository;
        this.researchUnitRepository = researchUnitRepository;
        this.relatedPublicationService = relatedPublicationService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    public PublicationExplanationResponse explain(Long publicationId, PublicationExplanationRequest request) {
        PublicationEntity publication = publicationRepository.findById(publicationId)
            .filter(candidate -> candidate.getValidationStatus() == VALIDATED)
            .orElseThrow(() -> new ResourceNotFoundException("Publication", publicationId));

        PublicationExplanationStyle style = request == null || request.style() == null
            ? PublicationExplanationStyle.PLAIN
            : request.style();
        String language = normalizeLanguage(request == null ? null : request.language());
        ExplanationEvidence evidence = evidence(publication);
        List<String> warnings = new ArrayList<>(evidence.warnings());

        GeneratedSections generatedSections = generatedSections(publication, evidence, style, language, warnings);
        return new PublicationExplanationResponse(
            publication.getTitle(),
            generatedSections.plainSummary(),
            generatedSections.problemAddressed(),
            generatedSections.whyItMatters(),
            generatedSections.approach(),
            evidence.relatedTopics(),
            evidence.relatedResearchers(),
            evidence.relatedUnits(),
            evidence.relatedPublications(),
            distinct(warnings),
            llmService.provider(),
            llmService.model()
        );
    }

    private ExplanationEvidence evidence(PublicationEntity publication) {
        List<String> warnings = new ArrayList<>();
        List<PortalPublicationExplanationReferenceResponse> topics = topicReferences(publication.getId());
        AuthorEvidence authorEvidence = authorEvidence(publication.getId());
        List<PortalPublicationExplanationReferenceResponse> units = unitReferences(authorEvidence.internalResearchers());
        RelatedPublicationEvidence relatedPublicationEvidence = relatedPublications(publication.getId());

        boolean missingAbstract = isBlank(publication.getAbstractText());
        if (missingAbstract) {
            warnings.add("La publicación no tiene resumen validado disponible; la explicación se limita a título, temas y metadatos públicos.");
        }
        if (missingAbstract || (isBlank(publication.getPublicSummary()) && topics.isEmpty())) {
            warnings.add("El contexto público validado es débil; evita interpretar métodos, resultados o impacto.");
        }
        warnings.addAll(relatedPublicationEvidence.warnings());

        return new ExplanationEvidence(
            topics,
            authorEvidence.internalResearchers().stream()
                .map(researcher -> new PortalPublicationExplanationReferenceResponse(researcher.getId(), displayName(researcher)))
                .toList(),
            authorEvidence.authorNames(),
            units,
            relatedPublicationEvidence.references(),
            warnings
        );
    }

    private GeneratedSections generatedSections(
        PublicationEntity publication,
        ExplanationEvidence evidence,
        PublicationExplanationStyle style,
        String language,
        List<String> warnings
    ) {
        GeneratedSections fallback = fallbackSections(publication, evidence, style);
        if (!"ollama".equalsIgnoreCase(llmService.provider())) {
            if ("mock".equalsIgnoreCase(llmService.provider())) {
                warnings.add("El proveedor LLM mock está activo; se devolvió una explicación determinista basada en evidencia validada.");
            }
            return fallback;
        }

        try {
            LlmResponse response = llmService.answer(new LlmPrompt(
                explanationQuestion(publication, style, language),
                context(publication, evidence)
            ));
            if (response.warnings() != null) {
                warnings.addAll(response.warnings());
            }
            GeneratedSections parsed = parseSections(response.answer());
            if (parsed.isUsable()) {
                return parsed;
            }
            warnings.add("El proveedor de IA no devolvió una estructura útil; se usó una explicación determinista basada en evidencia validada.");
            return fallback;
        } catch (BusinessRuleException | JsonProcessingException exception) {
            warnings.add(providerUnavailableWarning(exception.getMessage()));
            return fallback;
        }
    }

    private GeneratedSections fallbackSections(
        PublicationEntity publication,
        ExplanationEvidence evidence,
        PublicationExplanationStyle style
    ) {
        String descriptor = "La publicación validada " + quote(publication.getTitle())
            + (publication.getPublicationYear() == null ? "" : " (" + publication.getPublicationYear() + ")")
            + (publication.getType() == null ? "" : " está registrada como " + publication.getType().name())
            + ".";
        String topicSentence = evidence.relatedTopics().isEmpty()
            ? "No hay temas validados suficientes para ampliar la descripción pública."
            : "La ficha pública la relaciona con " + joinReadable(labels(evidence.relatedTopics()), 4) + ".";
        String abstractSentence = isBlank(publication.getAbstractText())
            ? "No hay resumen validado disponible para explicar contenido, metodo o resultados."
            : "El resumen validado disponible indica: " + truncate(publication.getAbstractText(), style == PublicationExplanationStyle.TECHNICAL ? 480 : 320);
        String plainSummary = descriptor + " " + abstractSentence + " " + topicSentence;
        String problemAddressed = isBlank(publication.getAbstractText())
            ? "No se puede determinar con suficiente precisión a partir de la evidencia pública validada."
            : "Debe interpretarse desde el resumen validado, sin añadir objetivos no registrados: " + truncate(publication.getAbstractText(), 260);
        String whyItMatters = evidence.relatedTopics().isEmpty()
            ? "La evidencia pública no permite afirmar impacto; solo confirma que la publicación está validada para consulta en el portal."
            : "Ayuda a contextualizar actividad pública validada en " + joinReadable(labels(evidence.relatedTopics()), 4) + ", sin inferir impacto adicional.";
        String approach = "No hay información metodológica separada en la ficha pública. Solo deben usarse el resumen, los temas y los metadatos validados.";
        return new GeneratedSections(plainSummary, problemAddressed, whyItMatters, approach);
    }

    private String explanationQuestion(PublicationEntity publication, PublicationExplanationStyle style, String language) {
        return """
            Genera una explicación pública segura para la publicación "%s".
            Idioma solicitado: %s. Estilo: %s.
            Usa exclusivamente el contexto validado proporcionado.
            No inventes métodos, resultados, hallazgos, impacto, citas, autores, unidades ni métricas.
            Si el resumen o el contexto no permiten responder un campo, dilo claramente en ese campo.
            No sobreafirmes relevancia ni impacto.
            Devuelve solo JSON valido con estas claves string: plainSummary, problemAddressed, whyItMatters, approach.
            """.formatted(publication.getTitle(), language, style.name()).trim();
    }

    private String context(PublicationEntity publication, ExplanationEvidence evidence) {
        return """
            Titulo: %s
            Resumen validado: %s
            Resumen público revisado: %s
            Ano: %s
            Tipo: %s
            Estado de publicación: %s
            Fuente: %s
            DOI: %s
            Idioma registrado: %s
            Temas validados: %s
            Autores registrados: %s
            Investigadores internos validados: %s
            Unidades públicas relacionadas: %s
            Publicaciones públicas relacionadas: %s
            """.formatted(
            value(publication.getTitle()),
            value(publication.getAbstractText()),
            value(publication.getPublicSummary()),
            value(publication.getPublicationYear()),
            value(publication.getType()),
            value(publication.getStatus()),
            value(publication.getSource()),
            value(publication.getDoi()),
            value(publication.getLanguageCode()),
            commaList(labels(evidence.relatedTopics())),
            commaList(evidence.authorNames()),
            commaList(labels(evidence.relatedResearchers())),
            commaList(labels(evidence.relatedUnits())),
            commaList(labels(evidence.relatedPublications()))
        ).trim();
    }

    private GeneratedSections parseSections(String rawAnswer) throws JsonProcessingException {
        String json = extractJson(rawAnswer);
        JsonNode root = objectMapper.readTree(json);
        return new GeneratedSections(
            text(root, "plainSummary"),
            text(root, "problemAddressed"),
            text(root, "whyItMatters"),
            text(root, "approach")
        );
    }

    private String extractJson(String rawAnswer) {
        String normalized = normalizeWhitespace(rawAnswer);
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return normalized;
        }
        return normalized.substring(start, end + 1);
    }

    private String text(JsonNode root, String fieldName) {
        JsonNode node = root == null ? null : root.get(fieldName);
        return node == null || node.isNull() ? "" : normalizeWhitespace(node.asText());
    }

    private List<PortalPublicationExplanationReferenceResponse> topicReferences(Long publicationId) {
        List<PublicationTopicEntity> links = publicationTopicRepository.findByPublicationId(publicationId);
        if (links.isEmpty()) {
            return List.of();
        }
        Map<Long, TopicEntity> topicsById = topicRepository.findAllById(links.stream().map(PublicationTopicEntity::getTopicId).toList())
            .stream()
            .collect(Collectors.toMap(TopicEntity::getId, Function.identity(), (first, second) -> first));
        return links.stream()
            .map(link -> topicsById.get(link.getTopicId()))
            .filter(topic -> topic != null && !isBlank(topic.getName()))
            .map(topic -> new PortalPublicationExplanationReferenceResponse(topic.getId(), topic.getName()))
            .collect(Collectors.toMap(
                PortalPublicationExplanationReferenceResponse::id,
                Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new
            ))
            .values()
            .stream()
            .toList();
    }

    private AuthorEvidence authorEvidence(Long publicationId) {
        List<PublicationAuthorEntity> authors = authorRepository.findByPublicationIdOrderByAuthorOrderAsc(publicationId);
        if (authors.isEmpty()) {
            return new AuthorEvidence(List.of(), List.of());
        }
        Map<Long, ResearcherEntity> researchersById = researcherRepository.findAllById(authors.stream()
            .map(PublicationAuthorEntity::getResearcherId)
            .filter(id -> id != null)
            .toList())
            .stream()
            .filter(researcher -> researcher.getValidationStatus() == VALIDATED && researcher.isActive())
            .collect(Collectors.toMap(ResearcherEntity::getId, Function.identity(), (first, second) -> first));

        List<ResearcherEntity> internalResearchers = new ArrayList<>();
        List<String> authorNames = new ArrayList<>();
        for (PublicationAuthorEntity author : authors) {
            if (author.getResearcherId() != null) {
                ResearcherEntity researcher = researchersById.get(author.getResearcherId());
                if (researcher != null) {
                    internalResearchers.add(researcher);
                    authorNames.add(displayName(researcher));
                }
            } else if (!isBlank(author.getExternalAuthorName())) {
                authorNames.add(normalizeWhitespace(author.getExternalAuthorName()));
            }
        }
        return new AuthorEvidence(
            internalResearchers.stream()
                .collect(Collectors.toMap(ResearcherEntity::getId, Function.identity(), (first, second) -> first, LinkedHashMap::new))
                .values()
                .stream()
                .toList(),
            distinct(authorNames)
        );
    }

    private List<PortalPublicationExplanationReferenceResponse> unitReferences(List<ResearcherEntity> researchers) {
        if (researchers.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        List<Long> researcherIds = researchers.stream().map(ResearcherEntity::getId).toList();
        List<ResearcherAffiliationEntity> affiliations = affiliationRepository.findByResearcherIdIn(researcherIds)
            .stream()
            .filter(affiliation -> affiliation.getValidationStatus() == VALIDATED)
            .filter(affiliation -> affiliation.getEndDate() == null || !affiliation.getEndDate().isBefore(today))
            .toList();
        if (affiliations.isEmpty()) {
            return List.of();
        }
        Map<Long, ResearchUnitEntity> unitsById = researchUnitRepository.findAllById(affiliations.stream()
            .map(ResearcherAffiliationEntity::getResearchUnitId)
            .toList())
            .stream()
            .filter(unit -> unit.getValidationStatus() == VALIDATED)
            .filter(ResearchUnitEntity::isActive)
            .filter(ResearchUnitEntity::isVisibleInPortal)
            .filter(unit -> unit.getOrganizationScope() == OrganizationScope.INTERNAL)
            .collect(Collectors.toMap(ResearchUnitEntity::getId, Function.identity(), (first, second) -> first));
        return affiliations.stream()
            .map(affiliation -> unitsById.get(affiliation.getResearchUnitId()))
            .filter(unit -> unit != null)
            .sorted(Comparator.comparing(ResearchUnitEntity::getName))
            .map(unit -> new PortalPublicationExplanationReferenceResponse(unit.getId(), unit.getName()))
            .collect(Collectors.toMap(
                PortalPublicationExplanationReferenceResponse::id,
                Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new
            ))
            .values()
            .stream()
            .toList();
    }

    private RelatedPublicationEvidence relatedPublications(Long publicationId) {
        RelatedPublicationsResponse response = relatedPublicationService.findRelated(
            publicationId,
            RELATED_PUBLICATION_LIMIT,
            RELATED_PUBLICATION_MIN_SCORE,
            false
        );
        List<PortalPublicationExplanationReferenceResponse> references = response.relatedPublications().stream()
            .map(RelatedPublicationResponse::publication)
            .map(publication -> new PortalPublicationExplanationReferenceResponse(publication.id(), publication.title()))
            .toList();
        return new RelatedPublicationEvidence(references, response.warnings() == null ? List.of() : response.warnings());
    }

    private String providerUnavailableWarning(String message) {
        if (message != null && message.toLowerCase(Locale.ROOT).contains("ollama")) {
            return "Ollama no está disponible para generar la explicación; se usó una versión determinista basada en evidencia validada.";
        }
        return "El proveedor de IA no devolvió una explicación útil; se usó una versión determinista basada en evidencia validada.";
    }

    private String normalizeLanguage(String language) {
        String normalized = normalizeWhitespace(language);
        return normalized.isBlank() ? "espanol" : normalized;
    }

    private String displayName(ResearcherEntity researcher) {
        return isBlank(researcher.getDisplayName()) ? researcher.getFullName() : researcher.getDisplayName();
    }

    private List<String> labels(List<PortalPublicationExplanationReferenceResponse> references) {
        return references.stream().map(PortalPublicationExplanationReferenceResponse::label).toList();
    }

    private String joinReadable(List<String> values, int limit) {
        List<String> selected = values.stream()
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .limit(limit)
            .toList();
        if (selected.isEmpty()) {
            return "";
        }
        if (selected.size() == 1) {
            return selected.getFirst();
        }
        return String.join(", ", selected.subList(0, selected.size() - 1)) + " y " + selected.getLast();
    }

    private String commaList(List<String> values) {
        List<String> selected = values.stream()
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList();
        return selected.isEmpty() ? "No disponible" : String.join(", ", selected);
    }

    private String quote(String value) {
        return "\"" + normalizeWhitespace(value) + "\"";
    }

    private String truncate(String value, int maxLength) {
        String normalized = normalizeWhitespace(value);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)).trim() + ".";
    }

    private String value(Object value) {
        if (value == null) {
            return "No disponible";
        }
        String normalized = normalizeWhitespace(String.valueOf(value));
        return normalized.isBlank() ? "No disponible" : normalized;
    }

    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<String> distinct(Collection<String> values) {
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new))
            .stream()
            .toList();
    }

    private record AuthorEvidence(
        List<ResearcherEntity> internalResearchers,
        List<String> authorNames
    ) {
    }

    private record RelatedPublicationEvidence(
        List<PortalPublicationExplanationReferenceResponse> references,
        List<String> warnings
    ) {
    }

    private record ExplanationEvidence(
        List<PortalPublicationExplanationReferenceResponse> relatedTopics,
        List<PortalPublicationExplanationReferenceResponse> relatedResearchers,
        List<String> authorNames,
        List<PortalPublicationExplanationReferenceResponse> relatedUnits,
        List<PortalPublicationExplanationReferenceResponse> relatedPublications,
        List<String> warnings
    ) {
    }

    private record GeneratedSections(
        String plainSummary,
        String problemAddressed,
        String whyItMatters,
        String approach
    ) {

        private boolean isUsable() {
            return !plainSummary.isBlank()
                && !problemAddressed.isBlank()
                && !whyItMatters.isBlank()
                && !approach.isBlank();
        }
    }
}
