package com.researchintelligence.platform.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.api.AiSuggestionResponse;
import com.researchintelligence.platform.ai.api.PublicSummaryAudience;
import com.researchintelligence.platform.ai.api.PublicSummaryEvidenceResponse;
import com.researchintelligence.platform.ai.api.PublicSummaryGenerateRequest;
import com.researchintelligence.platform.ai.api.PublicSummaryGenerateResponse;
import com.researchintelligence.platform.ai.api.PublicSummaryStyle;
import com.researchintelligence.platform.ai.api.PublicSummaryTargetType;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PublicSummaryGenerationService {

    private static final ValidationStatus VALIDATED = ValidationStatus.VALIDATED;

    private final AiSuggestionService aiSuggestionService;
    private final LlmService llmService;
    private final ResearcherRepository researcherRepository;
    private final ResearcherAffiliationRepository affiliationRepository;
    private final ResearchUnitRepository researchUnitRepository;
    private final PublicationRepository publicationRepository;
    private final PublicationAuthorRepository publicationAuthorRepository;
    private final PublicationTopicRepository publicationTopicRepository;
    private final TopicRepository topicRepository;
    private final VisibilityContext visibilityContext;
    private final ObjectMapper objectMapper;

    public PublicSummaryGenerationService(
        AiSuggestionService aiSuggestionService,
        LlmService llmService,
        ResearcherRepository researcherRepository,
        ResearcherAffiliationRepository affiliationRepository,
        ResearchUnitRepository researchUnitRepository,
        PublicationRepository publicationRepository,
        PublicationAuthorRepository publicationAuthorRepository,
        PublicationTopicRepository publicationTopicRepository,
        TopicRepository topicRepository,
        VisibilityContext visibilityContext,
        ObjectMapper objectMapper
    ) {
        this.aiSuggestionService = aiSuggestionService;
        this.llmService = llmService;
        this.researcherRepository = researcherRepository;
        this.affiliationRepository = affiliationRepository;
        this.researchUnitRepository = researchUnitRepository;
        this.publicationRepository = publicationRepository;
        this.publicationAuthorRepository = publicationAuthorRepository;
        this.publicationTopicRepository = publicationTopicRepository;
        this.topicRepository = topicRepository;
        this.visibilityContext = visibilityContext;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PublicSummaryGenerateResponse generate(PublicSummaryGenerateRequest request) {
        validate(request);
        PlatformUserPrincipal user = requireGenerator();
        authorize(request, user);

        PublicSummaryDraft draft = switch (request.targetType()) {
            case RESEARCHER -> researcherDraft(request.targetId(), request.style(), audience(request));
            case RESEARCH_UNIT -> researchUnitDraft(request.targetId(), request.style(), audience(request), false);
            case PUBLICATION -> publicationDraft(request.targetId(), request.style(), audience(request));
            case EXTERNAL_ORGANIZATION -> researchUnitDraft(request.targetId(), request.style(), audience(request), true);
        };
        List<String> warnings = new ArrayList<>(draft.warnings());
        String summary = generatedSummary(draft, request.style(), audience(request), warnings);
        AiSuggestionResponse suggestion = aiSuggestionService.create(new AiSuggestionCreateCommand(
            request.targetType().name(),
            request.targetId(),
            AiSuggestionType.PUBLIC_SUMMARY,
            writeJson(orderedMap(
                "targetType", request.targetType().name(),
                "targetId", request.targetId(),
                "field", reviewedField(request.targetType()),
                "summary", summary,
                "style", request.style().name(),
                "audience", audience(request).name(),
                "requiresHumanReview", true
            )),
            "Generated a PUBLIC_SUMMARY suggestion from public/validated evidence. No public field was updated automatically.",
            writeJson(orderedMap(
                "targetType", request.targetType().name(),
                "targetId", request.targetId(),
                "evidence", draft.evidence(),
                "warnings", distinct(warnings)
            )),
            llmService.provider(),
            llmService.model()
        ));
        return new PublicSummaryGenerateResponse(
            summary,
            draft.evidence(),
            suggestion.id(),
            distinct(warnings),
            llmService.provider(),
            llmService.model()
        );
    }

    private PublicSummaryDraft researcherDraft(Long researcherId, PublicSummaryStyle style, PublicSummaryAudience audience) {
        ResearcherEntity researcher = researcherRepository.findById(researcherId)
            .orElseThrow(() -> new ResourceNotFoundException("Researcher", researcherId));
        List<PublicSummaryEvidenceResponse> evidence = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (researcher.getValidationStatus() == VALIDATED && researcher.isActive()) {
            addEvidence(evidence, "researcher:" + researcher.getId(), "Nombre validado", displayName(researcher));
            addEvidence(evidence, "researcher:" + researcher.getId() + ":orcid", "ORCID", researcher.getOrcid());
        } else {
            warnings.add("El perfil del investigador no esta validado como evidencia publica.");
        }

        String affiliationName = currentValidatedPrimaryAffiliation(researcher.getId(), evidence, warnings);
        List<String> topics = topicNames(publicationRepository.findTopValidatedTopicsByResearcher(
            researcher.getId(),
            VALIDATED,
            PageRequest.of(0, 5)
        ), evidence, "topic", "Temas validados");
        List<PublicationEntity> publications = publicationRepository.findValidatedByResearcherId(
            researcher.getId(),
            VALIDATED,
            PageRequest.of(0, 3)
        );
        for (PublicationEntity publication : publications) {
            addEvidence(evidence, "publication:" + publication.getId(), "Publicacion validada", publicationTitle(publication));
        }
        if (affiliationName == null && publications.isEmpty() && topics.isEmpty()) {
            warnings.add("La evidencia publica validada del investigador es limitada; evita afirmaciones amplias.");
        }
        return new PublicSummaryDraft(
            PublicSummaryTargetType.RESEARCHER,
            researcher.getId(),
            displayName(researcher),
            evidence,
            warnings,
            researcherSummary(displayName(researcher), affiliationName, topics, publications, style, audience)
        );
    }

    private PublicSummaryDraft researchUnitDraft(Long unitId, PublicSummaryStyle style, PublicSummaryAudience audience, boolean external) {
        ResearchUnitEntity unit = researchUnitRepository.findById(unitId)
            .orElseThrow(() -> new ResourceNotFoundException("ResearchUnit", unitId));
        if (external && unit.getOrganizationScope() != OrganizationScope.EXTERNAL) {
            throw new BusinessRuleException("EXTERNAL_ORGANIZATION target requires an external research unit record.");
        }
        if (!external && unit.getOrganizationScope() == OrganizationScope.EXTERNAL) {
            throw new BusinessRuleException("Use EXTERNAL_ORGANIZATION for external organization records.");
        }
        List<PublicSummaryEvidenceResponse> evidence = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (unit.getValidationStatus() == VALIDATED && unit.isActive()) {
            addEvidence(evidence, "researchUnit:" + unit.getId(), external ? "Organizacion externa validada" : "Unidad validada", unit.getName());
            addEvidence(evidence, "researchUnit:" + unit.getId() + ":type", "Tipo", unit.getType() == null ? null : unit.getType().name());
            addEvidence(evidence, "researchUnit:" + unit.getId() + ":location", "Ubicacion", joinNonBlank(unit.getCity(), unit.getCountry()));
            addEvidence(evidence, "researchUnit:" + unit.getId() + ":publicDescription", "Descripcion publica revisada", unit.getPublicDescription());
        } else {
            warnings.add("La unidad u organizacion no esta validada como evidencia publica.");
        }
        if (!external && !unit.isVisibleInPortal()) {
            warnings.add("La unidad no esta marcada como visible en el portal publico.");
        }

        List<String> topics = external ? List.of() : topicNames(publicationRepository.findTopValidatedTopicsByResearchUnit(
            unit.getId(),
            VALIDATED,
            PageRequest.of(0, 5)
        ), evidence, "topic", "Temas validados");
        List<PublicationEntity> publications = external ? List.of() : publicationRepository.findValidatedByResearchUnitId(
            unit.getId(),
            VALIDATED,
            PageRequest.of(0, 3)
        );
        for (PublicationEntity publication : publications) {
            addEvidence(evidence, "publication:" + publication.getId(), "Publicacion validada", publicationTitle(publication));
        }
        if (topics.isEmpty() && publications.isEmpty() && isBlank(unit.getPublicDescription())) {
            warnings.add("La evidencia publica validada es limitada; el resumen debe mantenerse descriptivo.");
        }
        return new PublicSummaryDraft(
            external ? PublicSummaryTargetType.EXTERNAL_ORGANIZATION : PublicSummaryTargetType.RESEARCH_UNIT,
            unit.getId(),
            unit.getName(),
            evidence,
            warnings,
            unitSummary(unit, external, topics, publications, style, audience)
        );
    }

    private PublicSummaryDraft publicationDraft(Long publicationId, PublicSummaryStyle style, PublicSummaryAudience audience) {
        PublicationEntity publication = publicationRepository.findById(publicationId)
            .orElseThrow(() -> new ResourceNotFoundException("Publication", publicationId));
        List<PublicSummaryEvidenceResponse> evidence = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (publication.getValidationStatus() == VALIDATED) {
            addEvidence(evidence, "publication:" + publication.getId(), "Titulo validado", publication.getTitle());
            addEvidence(evidence, "publication:" + publication.getId() + ":year", "Ano", publication.getPublicationYear() == null ? null : publication.getPublicationYear().toString());
            addEvidence(evidence, "publication:" + publication.getId() + ":type", "Tipo", publication.getType() == null ? null : publication.getType().name());
            addEvidence(evidence, "publication:" + publication.getId() + ":source", "Fuente", publication.getSource());
            addEvidence(evidence, "publication:" + publication.getId() + ":doi", "DOI", publication.getDoi());
            addEvidence(evidence, "publication:" + publication.getId() + ":abstract", "Resumen validado", truncate(publication.getAbstractText(), 900));
            addEvidence(evidence, "publication:" + publication.getId() + ":publicSummary", "Resumen publico revisado", publication.getPublicSummary());
        } else {
            warnings.add("La publicacion no esta validada como evidencia publica.");
        }
        List<String> authors = authorNames(publication.getId());
        if (!authors.isEmpty()) {
            addEvidence(evidence, "publication:" + publication.getId() + ":authors", "Autores", String.join(", ", authors));
        }
        List<String> topics = topicNamesForPublication(publication.getId());
        if (!topics.isEmpty()) {
            addEvidence(evidence, "publication:" + publication.getId() + ":topics", "Temas", String.join(", ", topics));
        }
        if (isBlank(publication.getAbstractText()) && topics.isEmpty()) {
            warnings.add("La publicacion tiene poca evidencia textual validada; evita interpretar resultados o impacto.");
        }
        return new PublicSummaryDraft(
            PublicSummaryTargetType.PUBLICATION,
            publication.getId(),
            publication.getTitle(),
            evidence,
            warnings,
            publicationSummary(publication, authors, topics, style, audience)
        );
    }

    private String generatedSummary(
        PublicSummaryDraft draft,
        PublicSummaryStyle style,
        PublicSummaryAudience audience,
        List<String> warnings
    ) {
        String fallback = draft.fallbackSummary();
        if (draft.evidence().isEmpty()) {
            warnings.add("No hay evidencia publica validada suficiente para generar un resumen fiable.");
            return "No hay evidencia publica validada suficiente para redactar un resumen publico fiable.";
        }
        if (!"ollama".equalsIgnoreCase(llmService.provider())) {
            if ("mock".equalsIgnoreCase(llmService.provider())) {
                warnings.add("El proveedor LLM mock esta activo; se genero un resumen determinista basado en evidencia.");
            }
            return fallback;
        }
        try {
            LlmResponse response = llmService.answer(new LlmPrompt(summaryQuestion(draft, style, audience), evidenceContext(draft)));
            if (response.warnings() != null) {
                warnings.addAll(response.warnings());
            }
            String answer = normalizeWhitespace(response.answer());
            if (answer.isBlank()) {
                warnings.add("El proveedor de IA no devolvio contenido; se uso un resumen determinista.");
                return fallback;
            }
            return answer;
        } catch (BusinessRuleException exception) {
            warnings.add(ollamaWarning(exception.getMessage()));
            return fallback;
        }
    }

    private String summaryQuestion(PublicSummaryDraft draft, PublicSummaryStyle style, PublicSummaryAudience audience) {
        return """
            Redacta un resumen publico en espanol para %s "%s".
            Estilo: %s. Audiencia: %s.
            Usa solo la evidencia proporcionada. No inventes resultados, metricas, financiacion, cargos ni impacto.
            Si la evidencia es limitada, mantente descriptivo y prudente.
            Devuelve solo el texto del resumen, sin listas ni encabezados.
            """.formatted(draft.targetType().name(), draft.targetLabel(), style.name(), audience.name()).trim();
    }

    private String evidenceContext(PublicSummaryDraft draft) {
        return draft.evidence().stream()
            .map(evidence -> "[%s] %s: %s".formatted(evidence.reference(), evidence.label(), evidence.value()))
            .collect(Collectors.joining("\n"));
    }

    private void authorize(PublicSummaryGenerateRequest request, PlatformUserPrincipal user) {
        if (hasRole(user, "ADMIN")) {
            return;
        }
        if (!hasRole(user, "RESEARCHER") || user.researcherId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Public summary generation is not available to the current user.");
        }
        boolean owned = switch (request.targetType()) {
            case RESEARCHER -> request.targetId().equals(user.researcherId());
            case PUBLICATION -> publicationAuthorRepository.existsByPublicationIdAndResearcherId(request.targetId(), user.researcherId());
            case RESEARCH_UNIT, EXTERNAL_ORGANIZATION -> false;
        };
        if (!owned) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Public summary target does not belong to the current researcher.");
        }
    }

    private PlatformUserPrincipal requireGenerator() {
        PlatformUserPrincipal user = visibilityContext.currentUser()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is required to generate public summaries."));
        if (hasRole(user, "ADMIN") || hasRole(user, "RESEARCHER")) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Public users cannot generate public summaries.");
    }

    private String currentValidatedPrimaryAffiliation(
        Long researcherId,
        List<PublicSummaryEvidenceResponse> evidence,
        List<String> warnings
    ) {
        List<ResearcherAffiliationEntity> affiliations = affiliationRepository.findByResearcherIdOrderByPrimaryAffiliationDescStartDateDescIdAsc(researcherId);
        if (affiliations.isEmpty()) {
            warnings.add("No hay afiliacion validada disponible para el perfil.");
            return null;
        }
        Map<Long, ResearchUnitEntity> unitsById = researchUnitRepository.findAllById(affiliations.stream()
            .map(ResearcherAffiliationEntity::getResearchUnitId)
            .toList())
            .stream()
            .collect(Collectors.toMap(ResearchUnitEntity::getId, Function.identity(), (first, second) -> first));
        LocalDate today = LocalDate.now();
        for (ResearcherAffiliationEntity affiliation : affiliations) {
            ResearchUnitEntity unit = unitsById.get(affiliation.getResearchUnitId());
            if (affiliation.isPrimaryAffiliation()
                && isCurrent(affiliation, today)
                && affiliation.getValidationStatus() == VALIDATED
                && unit != null
                && unit.getValidationStatus() == VALIDATED
                && unit.isActive()
                && unit.isVisibleInPortal()
                && unit.getOrganizationScope() == OrganizationScope.INTERNAL) {
                addEvidence(
                    evidence,
                    "affiliation:" + affiliation.getId(),
                    "Afiliacion principal validada",
                    affiliation.getRole() == null ? unit.getName() : affiliation.getRole() + ", " + unit.getName()
                );
                return unit.getName();
            }
        }
        warnings.add("No hay afiliacion principal publica y validada para el perfil.");
        return null;
    }

    private List<String> topicNames(List<Object[]> rows, List<PublicSummaryEvidenceResponse> evidence, String prefix, String label) {
        List<String> names = new ArrayList<>();
        for (Object[] row : rows) {
            Long topicId = (Long) row[0];
            String name = String.valueOf(row[1]);
            Long count = (Long) row[2];
            names.add(name);
            addEvidence(evidence, prefix + ":" + topicId, label, name + " (" + count + " publicaciones validadas)");
        }
        return names;
    }

    private List<String> authorNames(Long publicationId) {
        List<PublicationAuthorEntity> authors = publicationAuthorRepository.findByPublicationIdOrderByAuthorOrderAsc(publicationId);
        if (authors.isEmpty()) {
            return List.of();
        }
        Map<Long, ResearcherEntity> researchersById = researcherRepository.findAllById(authors.stream()
            .map(PublicationAuthorEntity::getResearcherId)
            .filter(id -> id != null)
            .toList())
            .stream()
            .filter(researcher -> researcher.getValidationStatus() == VALIDATED)
            .collect(Collectors.toMap(ResearcherEntity::getId, Function.identity(), (first, second) -> first));
        return authors.stream()
            .map(author -> {
                if (author.getResearcherId() != null) {
                    ResearcherEntity researcher = researchersById.get(author.getResearcherId());
                    return researcher == null ? null : displayName(researcher);
                }
                return normalizeWhitespace(author.getExternalAuthorName());
            })
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList();
    }

    private List<String> topicNamesForPublication(Long publicationId) {
        List<PublicationTopicEntity> links = publicationTopicRepository.findByPublicationId(publicationId);
        if (links.isEmpty()) {
            return List.of();
        }
        Map<Long, TopicEntity> topicsById = topicRepository.findAllById(links.stream().map(PublicationTopicEntity::getTopicId).toList())
            .stream()
            .collect(Collectors.toMap(TopicEntity::getId, Function.identity(), (first, second) -> first));
        return links.stream()
            .map(link -> topicsById.get(link.getTopicId()))
            .filter(topic -> topic != null)
            .map(TopicEntity::getName)
            .distinct()
            .toList();
    }

    private String researcherSummary(
        String name,
        String affiliation,
        List<String> topics,
        List<PublicationEntity> publications,
        PublicSummaryStyle style,
        PublicSummaryAudience audience
    ) {
        String first = affiliation == null
            ? name + " cuenta con evidencia publica validada en la plataforma."
            : name + " es investigador/a vinculado/a a " + affiliation + ".";
        String topicSentence = topics.isEmpty()
            ? "La evidencia publica disponible no permite destacar lineas tematicas concretas."
            : "Su actividad validada se relaciona con " + joinReadable(topics, 4) + ".";
        String publicationSentence = publications.isEmpty()
            ? "No se han identificado publicaciones validadas suficientes para ampliar el perfil publico."
            : "Entre sus publicaciones validadas figura " + quote(publications.getFirst().getTitle()) + ".";
        return byStyle(first, topicSentence, publicationSentence, style);
    }

    private String unitSummary(
        ResearchUnitEntity unit,
        boolean external,
        List<String> topics,
        List<PublicationEntity> publications,
        PublicSummaryStyle style,
        PublicSummaryAudience audience
    ) {
        String kind = external ? "organizacion externa" : "unidad de investigacion";
        String location = joinNonBlank(unit.getCity(), unit.getCountry());
        String first = unit.getName() + " es una " + kind
            + (unit.getType() == null ? "" : " de tipo " + unit.getType().name())
            + (isBlank(location) ? "." : " ubicada en " + location + ".");
        if (!isBlank(unit.getPublicDescription())) {
            first = unit.getPublicDescription();
        }
        String topicSentence = topics.isEmpty()
            ? "La evidencia publica validada no permite destacar areas especificas."
            : "La actividad validada asociada se concentra en " + joinReadable(topics, 4) + ".";
        String publicationSentence = publications.isEmpty()
            ? "El resumen debe revisarse con informacion institucional adicional antes de publicarse."
            : "Una publicacion validada asociada es " + quote(publications.getFirst().getTitle()) + ".";
        return byStyle(first, topicSentence, publicationSentence, style);
    }

    private String publicationSummary(
        PublicationEntity publication,
        List<String> authors,
        List<String> topics,
        PublicSummaryStyle style,
        PublicSummaryAudience audience
    ) {
        String first = quote(publication.getTitle()) + " es una publicacion"
            + (publication.getPublicationYear() == null ? "" : " de " + publication.getPublicationYear())
            + (publication.getType() == null ? "." : " de tipo " + publication.getType().name() + ".");
        String topicSentence = topics.isEmpty()
            ? "La ficha no incluye temas validados suficientes para ampliar el resumen."
            : "La evidencia registrada la vincula con " + joinReadable(topics, 4) + ".";
        String authorSentence = authors.isEmpty()
            ? "Antes de publicarlo, conviene revisar autores y contexto disponible."
            : "La autoria registrada incluye a " + joinReadable(authors, 4) + ".";
        if (!isBlank(publication.getAbstractText()) && style != PublicSummaryStyle.SHORT) {
            topicSentence = "El resumen validado indica: " + truncate(normalizeWhitespace(publication.getAbstractText()), 260);
        }
        return byStyle(first, topicSentence, authorSentence, style);
    }

    private String byStyle(String first, String second, String third, PublicSummaryStyle style) {
        return switch (style) {
            case SHORT -> first;
            case STANDARD -> first + " " + second;
            case EXTENDED -> first + " " + second + " " + third;
        };
    }

    private void validate(PublicSummaryGenerateRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Public summary generation request is required.");
        }
        if (request.targetType() == null) {
            throw new BusinessRuleException("targetType is required.");
        }
        if (request.targetId() == null) {
            throw new BusinessRuleException("targetId is required.");
        }
        if (request.style() == null) {
            throw new BusinessRuleException("style is required.");
        }
    }

    private PublicSummaryAudience audience(PublicSummaryGenerateRequest request) {
        return request.audience() == null ? PublicSummaryAudience.PUBLIC : request.audience();
    }

    private String reviewedField(PublicSummaryTargetType targetType) {
        return switch (targetType) {
            case RESEARCHER -> "publicProfileSummary";
            case RESEARCH_UNIT, EXTERNAL_ORGANIZATION -> "publicDescription";
            case PUBLICATION -> "publicSummary";
        };
    }

    private boolean isCurrent(ResearcherAffiliationEntity affiliation, LocalDate today) {
        return affiliation.getEndDate() == null || !affiliation.getEndDate().isBefore(today);
    }

    private boolean hasRole(PlatformUserPrincipal user, String role) {
        return user.roles().contains(role);
    }

    private void addEvidence(List<PublicSummaryEvidenceResponse> evidence, String reference, String label, String value) {
        String normalized = normalizeWhitespace(value);
        if (!normalized.isBlank()) {
            evidence.add(new PublicSummaryEvidenceResponse(reference, label, normalized));
        }
    }

    private String displayName(ResearcherEntity researcher) {
        return isBlank(researcher.getDisplayName()) ? researcher.getFullName() : researcher.getDisplayName();
    }

    private String publicationTitle(PublicationEntity publication) {
        return publication.getPublicationYear() == null
            ? publication.getTitle()
            : publication.getTitle() + " (" + publication.getPublicationYear() + ")";
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

    private String joinNonBlank(String left, String right) {
        return List.of(normalizeWhitespace(left), normalizeWhitespace(right)).stream()
            .filter(value -> !value.isBlank())
            .collect(Collectors.joining(", "));
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

    private String ollamaWarning(String message) {
        if (message != null && message.toLowerCase(Locale.ROOT).contains("ollama")) {
            return "Ollama no esta disponible para generar el resumen; se uso una version determinista basada en evidencia.";
        }
        return "El proveedor de IA no esta disponible; se uso una version determinista basada en evidencia.";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException("Could not serialize public summary suggestion payload.");
        }
    }

    private Map<String, Object> orderedMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            map.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return map;
    }

    private record PublicSummaryDraft(
        PublicSummaryTargetType targetType,
        Long targetId,
        String targetLabel,
        List<PublicSummaryEvidenceResponse> evidence,
        List<String> warnings,
        String fallbackSummary
    ) {
    }
}
