package com.researchintelligence.platform.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.api.AiSuggestionResponse;
import com.researchintelligence.platform.ai.api.NewsDraftEvidenceResponse;
import com.researchintelligence.platform.ai.api.NewsDraftGenerateRequest;
import com.researchintelligence.platform.ai.api.NewsDraftGenerateResponse;
import com.researchintelligence.platform.ai.api.NewsDraftRelatedIdsRequest;
import com.researchintelligence.platform.ai.api.NewsDraftSourceType;
import com.researchintelligence.platform.ai.api.NewsDraftTone;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class NewsDraftGenerationService {

    private static final ValidationStatus VALIDATED = ValidationStatus.VALIDATED;

    private final AiSuggestionService aiSuggestionService;
    private final LlmService llmService;
    private final PublicationRepository publicationRepository;
    private final PublicationAuthorRepository publicationAuthorRepository;
    private final PublicationTopicRepository publicationTopicRepository;
    private final TopicRepository topicRepository;
    private final ResearcherRepository researcherRepository;
    private final ResearcherAffiliationRepository affiliationRepository;
    private final ResearchUnitRepository researchUnitRepository;
    private final VisibilityContext visibilityContext;
    private final ObjectMapper objectMapper;

    public NewsDraftGenerationService(
        AiSuggestionService aiSuggestionService,
        LlmService llmService,
        PublicationRepository publicationRepository,
        PublicationAuthorRepository publicationAuthorRepository,
        PublicationTopicRepository publicationTopicRepository,
        TopicRepository topicRepository,
        ResearcherRepository researcherRepository,
        ResearcherAffiliationRepository affiliationRepository,
        ResearchUnitRepository researchUnitRepository,
        VisibilityContext visibilityContext,
        ObjectMapper objectMapper
    ) {
        this.aiSuggestionService = aiSuggestionService;
        this.llmService = llmService;
        this.publicationRepository = publicationRepository;
        this.publicationAuthorRepository = publicationAuthorRepository;
        this.publicationTopicRepository = publicationTopicRepository;
        this.topicRepository = topicRepository;
        this.researcherRepository = researcherRepository;
        this.affiliationRepository = affiliationRepository;
        this.researchUnitRepository = researchUnitRepository;
        this.visibilityContext = visibilityContext;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NewsDraftGenerateResponse generateDraft(NewsDraftGenerateRequest request) {
        validate(request);
        requireAdmin();

        DraftContext context = draftContext(request);
        if (context.evidence().isEmpty()) {
            throw new BusinessRuleException("News draft generation requires validated evidence.");
        }
        NewsDraft draft = generatedDraft(context, tone(request));
        AiSuggestionResponse suggestion = aiSuggestionService.create(new AiSuggestionCreateCommand(
            request.sourceType().name(),
            request.sourceId(),
            AiSuggestionType.NEWS_DRAFT,
            writeJson(orderedMap(
                "suggestedTitle", draft.suggestedTitle(),
                "suggestedSummary", draft.suggestedSummary(),
                "suggestedBody", draft.suggestedBody(),
                "imageSuggestion", draft.imageSuggestion(),
                "imageAltSuggestion", draft.imageAltSuggestion(),
                "status", "DRAFT",
                "requiresHumanReview", true,
                "relatedPublicationIds", context.publicationIds(),
                "relatedResearcherIds", context.researcherIds(),
                "relatedUnitIds", context.unitIds()
            )),
            "Generated a NEWS_DRAFT suggestion from validated evidence. No public news article was created or published automatically.",
            writeJson(orderedMap(
                "sourceType", request.sourceType().name(),
                "sourceId", request.sourceId(),
                "query", normalizeOptional(request.query()),
                "tone", tone(request).name(),
                "evidence", context.evidence()
            )),
            llmService.provider(),
            llmService.model()
        ));
        return new NewsDraftGenerateResponse(
            draft.suggestedTitle(),
            draft.suggestedSummary(),
            draft.suggestedBody(),
            draft.imageSuggestion(),
            draft.imageAltSuggestion(),
            context.evidence(),
            suggestion.id(),
            AiSuggestionType.NEWS_DRAFT
        );
    }

    private DraftContext draftContext(NewsDraftGenerateRequest request) {
        EvidenceCollector evidence = new EvidenceCollector();
        Set<Long> publicationIds = new LinkedHashSet<>();
        Set<Long> researcherIds = new LinkedHashSet<>();
        Set<Long> unitIds = new LinkedHashSet<>();
        String sourceLabel = switch (request.sourceType()) {
            case PUBLICATION -> publicationEvidence(requireSourceId(request), evidence, publicationIds, researcherIds);
            case RESEARCH_UNIT -> unitEvidence(requireSourceId(request), evidence, publicationIds, researcherIds, unitIds);
            case RESEARCHER -> researcherEvidence(requireSourceId(request), evidence, publicationIds, researcherIds, unitIds);
            case TOPIC -> topicEvidence(request, evidence, publicationIds, researcherIds);
            case CUSTOM_QUERY -> customQueryEvidence(request, evidence, publicationIds, researcherIds);
        };
        addRelatedEvidence(request.relatedIds(), evidence, publicationIds, researcherIds, unitIds);
        return new DraftContext(
            sourceLabel,
            evidence.items(),
            sortedIds(publicationIds),
            sortedIds(researcherIds),
            sortedIds(unitIds)
        );
    }

    private String publicationEvidence(
        Long publicationId,
        EvidenceCollector evidence,
        Set<Long> publicationIds,
        Set<Long> researcherIds
    ) {
        PublicationEntity publication = publicationRepository.findById(publicationId)
            .orElseThrow(() -> new ResourceNotFoundException("Publication", publicationId));
        requireValidatedPublication(publication);
        publicationIds.add(publication.getId());
        evidence.add("publication:" + publication.getId(), "PUBLICATION", publication.getId(), "Titulo validado", publication.getTitle());
        evidence.add("publication:" + publication.getId() + ":year", "PUBLICATION", publication.getId(), "Ano", publication.getPublicationYear() == null ? null : publication.getPublicationYear().toString());
        evidence.add("publication:" + publication.getId() + ":type", "PUBLICATION", publication.getId(), "Tipo", publication.getType() == null ? null : publication.getType().name());
        evidence.add("publication:" + publication.getId() + ":source", "PUBLICATION", publication.getId(), "Fuente", publication.getSource());
        evidence.add("publication:" + publication.getId() + ":abstract", "PUBLICATION", publication.getId(), "Resumen validado", truncate(publication.getAbstractText(), 650));
        evidence.add("publication:" + publication.getId() + ":publicSummary", "PUBLICATION", publication.getId(), "Resumen publico revisado", publication.getPublicSummary());
        addPublicationAuthors(publication.getId(), evidence, researcherIds);
        addPublicationTopics(publication.getId(), evidence);
        return publication.getTitle();
    }

    private String researcherEvidence(
        Long researcherId,
        EvidenceCollector evidence,
        Set<Long> publicationIds,
        Set<Long> researcherIds,
        Set<Long> unitIds
    ) {
        ResearcherEntity researcher = researcherRepository.findById(researcherId)
            .orElseThrow(() -> new ResourceNotFoundException("Researcher", researcherId));
        requirePublicResearcher(researcher);
        researcherIds.add(researcher.getId());
        String name = displayName(researcher);
        evidence.add("researcher:" + researcher.getId(), "RESEARCHER", researcher.getId(), "Investigador validado", name);
        evidence.add("researcher:" + researcher.getId() + ":orcid", "RESEARCHER", researcher.getId(), "ORCID", researcher.getOrcid());
        addCurrentPublicUnits(researcher.getId(), evidence, unitIds);
        for (PublicationEntity publication : publicationRepository.findValidatedByResearcherId(researcher.getId(), VALIDATED, PageRequest.of(0, 3))) {
            publicationEvidence(publication.getId(), evidence, publicationIds, researcherIds);
        }
        return name;
    }

    private String unitEvidence(
        Long unitId,
        EvidenceCollector evidence,
        Set<Long> publicationIds,
        Set<Long> researcherIds,
        Set<Long> unitIds
    ) {
        ResearchUnitEntity unit = researchUnitRepository.findById(unitId)
            .orElseThrow(() -> new ResourceNotFoundException("ResearchUnit", unitId));
        requirePublicUnit(unit);
        unitIds.add(unit.getId());
        evidence.add("researchUnit:" + unit.getId(), "RESEARCH_UNIT", unit.getId(), "Unidad validada", unit.getName());
        evidence.add("researchUnit:" + unit.getId() + ":type", "RESEARCH_UNIT", unit.getId(), "Tipo", unit.getType() == null ? null : unit.getType().name());
        evidence.add("researchUnit:" + unit.getId() + ":location", "RESEARCH_UNIT", unit.getId(), "Ubicacion", joinNonBlank(unit.getCity(), unit.getCountry()));
        evidence.add("researchUnit:" + unit.getId() + ":description", "RESEARCH_UNIT", unit.getId(), "Descripcion publica revisada", unit.getPublicDescription());
        for (Object[] row : publicationRepository.findTopValidatedTopicsByResearchUnit(unit.getId(), VALIDATED, PageRequest.of(0, 5))) {
            evidence.add("topic:" + row[0], "TOPIC", (Long) row[0], "Tema validado", row[1] + " (" + row[2] + " publicaciones validadas)");
        }
        for (PublicationEntity publication : publicationRepository.findValidatedByResearchUnitId(unit.getId(), VALIDATED, PageRequest.of(0, 3))) {
            publicationEvidence(publication.getId(), evidence, publicationIds, researcherIds);
        }
        return unit.getName();
    }

    private String topicEvidence(
        NewsDraftGenerateRequest request,
        EvidenceCollector evidence,
        Set<Long> publicationIds,
        Set<Long> researcherIds
    ) {
        TopicEntity topic = topicFor(request);
        evidence.add("topic:" + topic.getId(), "TOPIC", topic.getId(), "Tema registrado", topic.getName());
        for (PublicationEntity publication : publicationRepository.findValidatedByTopic(
            topic.getId(),
            null,
            "%",
            VALIDATED,
            PageRequest.of(0, 5)
        )) {
            publicationEvidence(publication.getId(), evidence, publicationIds, researcherIds);
        }
        return topic.getName();
    }

    private String customQueryEvidence(
        NewsDraftGenerateRequest request,
        EvidenceCollector evidence,
        Set<Long> publicationIds,
        Set<Long> researcherIds
    ) {
        String query = normalizeRequiredText(request.query(), "query");
        List<PublicationEntity> publications = publicationRepository.findValidatedByText(
            query.toLowerCase(Locale.ROOT),
            "%" + query.toLowerCase(Locale.ROOT) + "%",
            VALIDATED,
            PageRequest.of(0, 5)
        );
        if (publications.isEmpty()) {
            throw new BusinessRuleException("Custom query did not find validated publication evidence.");
        }
        for (PublicationEntity publication : publications) {
            publicationEvidence(publication.getId(), evidence, publicationIds, researcherIds);
        }
        return query;
    }

    private void addRelatedEvidence(
        NewsDraftRelatedIdsRequest relatedIds,
        EvidenceCollector evidence,
        Set<Long> publicationIds,
        Set<Long> researcherIds,
        Set<Long> unitIds
    ) {
        if (relatedIds == null) {
            return;
        }
        for (Long publicationId : normalizedIds(relatedIds.publicationIds())) {
            publicationEvidence(publicationId, evidence, publicationIds, researcherIds);
        }
        for (Long researcherId : normalizedIds(relatedIds.researcherIds())) {
            researcherEvidence(researcherId, evidence, publicationIds, researcherIds, unitIds);
        }
        for (Long unitId : normalizedIds(relatedIds.unitIds())) {
            unitEvidence(unitId, evidence, publicationIds, researcherIds, unitIds);
        }
    }

    private void addPublicationAuthors(Long publicationId, EvidenceCollector evidence, Set<Long> researcherIds) {
        List<PublicationAuthorEntity> authors = publicationAuthorRepository.findByPublicationIdOrderByAuthorOrderAsc(publicationId);
        if (authors.isEmpty()) {
            return;
        }
        Map<Long, ResearcherEntity> researchersById = researcherRepository.findAllById(authors.stream()
            .map(PublicationAuthorEntity::getResearcherId)
            .filter(id -> id != null)
            .toList())
            .stream()
            .filter(researcher -> researcher.getValidationStatus() == VALIDATED)
            .filter(ResearcherEntity::isActive)
            .filter(researcher -> affiliationRepository.countCurrentPrimaryAffiliationsVisibleInPortal(researcher.getId(), VALIDATED, LocalDate.now()) > 0)
            .collect(Collectors.toMap(ResearcherEntity::getId, Function.identity(), (first, second) -> first));
        List<String> authorNames = new ArrayList<>();
        for (PublicationAuthorEntity author : authors) {
            if (author.getResearcherId() != null) {
                ResearcherEntity researcher = researchersById.get(author.getResearcherId());
                if (researcher != null) {
                    researcherIds.add(researcher.getId());
                    authorNames.add(displayName(researcher));
                }
            } else {
                String externalName = normalizeOptional(author.getExternalAuthorName());
                if (externalName != null) {
                    authorNames.add(externalName);
                }
            }
        }
        evidence.add("publication:" + publicationId + ":authors", "PUBLICATION", publicationId, "Autores registrados", joinReadable(authorNames, 8));
    }

    private void addPublicationTopics(Long publicationId, EvidenceCollector evidence) {
        List<PublicationTopicEntity> links = publicationTopicRepository.findByPublicationId(publicationId);
        if (links.isEmpty()) {
            return;
        }
        Map<Long, TopicEntity> topicsById = topicRepository.findAllById(links.stream().map(PublicationTopicEntity::getTopicId).toList())
            .stream()
            .collect(Collectors.toMap(TopicEntity::getId, Function.identity(), (first, second) -> first));
        List<String> topicNames = links.stream()
            .map(link -> topicsById.get(link.getTopicId()))
            .filter(topic -> topic != null)
            .map(TopicEntity::getName)
            .toList();
        evidence.add("publication:" + publicationId + ":topics", "PUBLICATION", publicationId, "Temas registrados", joinReadable(topicNames, 8));
    }

    private void addCurrentPublicUnits(Long researcherId, EvidenceCollector evidence, Set<Long> unitIds) {
        LocalDate today = LocalDate.now();
        List<Long> currentUnitIds = affiliationRepository.findByResearcherIdOrderByPrimaryAffiliationDescStartDateDescIdAsc(researcherId)
            .stream()
            .filter(affiliation -> affiliation.isPrimaryAffiliation())
            .filter(affiliation -> affiliation.getValidationStatus() == VALIDATED)
            .filter(affiliation -> affiliation.getEndDate() == null || !affiliation.getEndDate().isBefore(today))
            .map(affiliation -> affiliation.getResearchUnitId())
            .distinct()
            .toList();
        Map<Long, ResearchUnitEntity> unitsById = researchUnitRepository.findAllById(currentUnitIds)
            .stream()
            .filter(unit -> unit.getValidationStatus() == VALIDATED)
            .filter(ResearchUnitEntity::isActive)
            .filter(ResearchUnitEntity::isVisibleInPortal)
            .filter(unit -> unit.getOrganizationScope() == OrganizationScope.INTERNAL)
            .collect(Collectors.toMap(ResearchUnitEntity::getId, Function.identity(), (first, second) -> first));
        for (ResearchUnitEntity unit : unitsById.values()) {
            unitIds.add(unit.getId());
            evidence.add("researchUnit:" + unit.getId(), "RESEARCH_UNIT", unit.getId(), "Unidad validada", unit.getName());
        }
    }

    private NewsDraft generatedDraft(DraftContext context, NewsDraftTone tone) {
        NewsDraft fallback = deterministicDraft(context, tone);
        if (!"ollama".equalsIgnoreCase(llmService.provider())) {
            return fallback;
        }
        try {
            LlmResponse response = llmService.answer(new LlmPrompt(newsDraftQuestion(context, tone), evidenceContext(context.evidence())));
            Map<String, Object> parsed = objectMapper.readValue(response.answer(), new TypeReference<>() {
            });
            return new NewsDraft(
                truncate(stringValue(parsed.get("suggestedTitle"), fallback.suggestedTitle()), 255),
                stringValue(parsed.get("suggestedSummary"), fallback.suggestedSummary()),
                stringValue(parsed.get("suggestedBody"), fallback.suggestedBody()),
                stringValue(parsed.get("imageSuggestion"), fallback.imageSuggestion()),
                stringValue(parsed.get("imageAltSuggestion"), fallback.imageAltSuggestion())
            );
        } catch (RuntimeException | JsonProcessingException exception) {
            return fallback;
        }
    }

    private NewsDraft deterministicDraft(DraftContext context, NewsDraftTone tone) {
        String label = context.sourceLabel();
        String title = tone == NewsDraftTone.OUTREACH
            ? "La investigacion acerca al publico: " + label
            : "Nueva noticia institucional sobre " + label;
        String summary = "Borrador basado en evidencia validada sobre " + label + ". Requiere revision editorial antes de publicarse.";
        String evidenceSentence = context.evidence().stream()
            .limit(4)
            .map(item -> item.label() + ": " + item.value())
            .collect(Collectors.joining("; "));
        String body = """
            La institucion prepara esta noticia a partir de evidencia revisada en la plataforma sobre %s.

            El borrador se apoya exclusivamente en informacion validada: %s.

            Antes de publicarlo, administracion debe revisar la redaccion, el enfoque institucional y la seleccion final de evidencias. Este texto no debe publicarse automaticamente.
            """.formatted(label, evidenceSentence).trim();
        String imageAlt = "Imagen conceptual sobre " + label + " en un contexto de investigacion institucional.";
        String imageSuggestion = """
            Concepto visual: equipo de investigacion y datos institucionales relacionados con %s.
            Texto alternativo: %s
            Palabras de busqueda: investigacion universitaria, %s, ciencia, datos validados.
            Prompt opcional: Ilustracion editorial sobria de investigacion universitaria sobre %s, sin logotipos ni personas identificables.
            """.formatted(label, imageAlt, label, label).trim();
        return new NewsDraft(truncate(title, 255), summary, body, imageSuggestion, imageAlt);
    }

    private String newsDraftQuestion(DraftContext context, NewsDraftTone tone) {
        return """
            Redacta un borrador de noticia en espanol para el portal publico.
            Tono: %s.
            Usa solo la evidencia proporcionada. No inventes publicaciones, investigadores, unidades, metricas, financiacion ni impacto.
            La IA solo genera un borrador; incluye una redaccion prudente y apta para revision administrativa.
            Devuelve solo JSON valido con estas claves: suggestedTitle, suggestedSummary, suggestedBody, imageSuggestion, imageAltSuggestion.
            imageSuggestion debe incluir concepto visual, alt text, palabras de busqueda y un prompt opcional para generador de imagenes. No generes imagenes.
            Tema fuente: %s.
            """.formatted(tone.name(), context.sourceLabel()).trim();
    }

    private String evidenceContext(List<NewsDraftEvidenceResponse> evidence) {
        return evidence.stream()
            .map(item -> "[%s] %s: %s".formatted(item.reference(), item.label(), item.value()))
            .collect(Collectors.joining("\n"));
    }

    private void requireValidatedPublication(PublicationEntity publication) {
        if (publication.getValidationStatus() != VALIDATED) {
            throw new BusinessRuleException("News draft generation can only use validated publications as evidence.");
        }
    }

    private void requirePublicResearcher(ResearcherEntity researcher) {
        if (researcher.getValidationStatus() != VALIDATED || !researcher.isActive()) {
            throw new BusinessRuleException("News draft generation can only use active validated researchers as evidence.");
        }
        if (affiliationRepository.countCurrentPrimaryAffiliationsVisibleInPortal(researcher.getId(), VALIDATED, LocalDate.now()) == 0) {
            throw new BusinessRuleException("News draft generation can only use researchers visible in the public portal.");
        }
    }

    private void requirePublicUnit(ResearchUnitEntity unit) {
        if (unit.getValidationStatus() != VALIDATED
            || !unit.isActive()
            || !unit.isVisibleInPortal()
            || unit.getOrganizationScope() != OrganizationScope.INTERNAL) {
            throw new BusinessRuleException("News draft generation can only use public validated research units as evidence.");
        }
    }

    private TopicEntity topicFor(NewsDraftGenerateRequest request) {
        if (request.sourceId() != null) {
            return topicRepository.findById(request.sourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Topic", request.sourceId()));
        }
        String normalized = normalizeRequiredText(request.query(), "query").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return topicRepository.findByNormalizedName(normalized)
            .orElseThrow(() -> new BusinessRuleException("Topic query must match an existing topic before news draft generation."));
    }

    private Long requireSourceId(NewsDraftGenerateRequest request) {
        if (request.sourceId() == null) {
            throw new BusinessRuleException("sourceId is required for " + request.sourceType() + " news draft generation.");
        }
        return request.sourceId();
    }

    private void validate(NewsDraftGenerateRequest request) {
        if (request == null) {
            throw new BusinessRuleException("News draft generation request is required.");
        }
        if (request.sourceType() == null) {
            throw new BusinessRuleException("sourceType is required.");
        }
        if (request.sourceType() == NewsDraftSourceType.CUSTOM_QUERY) {
            normalizeRequiredText(request.query(), "query");
        }
        if (request.sourceType() == NewsDraftSourceType.TOPIC && request.sourceId() == null) {
            normalizeRequiredText(request.query(), "query");
        }
    }

    private NewsDraftTone tone(NewsDraftGenerateRequest request) {
        return request.tone() == null ? NewsDraftTone.INSTITUTIONAL : request.tone();
    }

    private PlatformUserPrincipal requireAdmin() {
        PlatformUserPrincipal user = visibilityContext.currentUser()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is required to generate news drafts."));
        if (!user.roles().contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can generate news drafts.");
        }
        return user;
    }

    private Set<Long> normalizedIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return ids.stream()
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<Long> sortedIds(Collection<Long> ids) {
        return ids.stream().sorted().toList();
    }

    private String displayName(ResearcherEntity researcher) {
        return isBlank(researcher.getDisplayName()) ? researcher.getFullName() : researcher.getDisplayName();
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
        return List.of(normalizeOptional(left), normalizeOptional(right)).stream()
            .filter(value -> value != null)
            .collect(Collectors.joining(", "));
    }

    private String stringValue(Object value, String fallback) {
        String normalized = value == null ? null : normalizeOptionalText(String.valueOf(value));
        return normalized == null ? fallback : normalized;
    }

    private String truncate(String value, int maxLength) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)).trim() + ".";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeOptional(String value) {
        String normalized = value == null ? null : value.trim().replaceAll("\\s+", " ");
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new BusinessRuleException(fieldName + " is required.");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        String normalized = value == null ? null : value.trim();
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException("Could not serialize news draft suggestion payload.");
        }
    }

    private Map<String, Object> orderedMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            map.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return map;
    }

    private record DraftContext(
        String sourceLabel,
        List<NewsDraftEvidenceResponse> evidence,
        List<Long> publicationIds,
        List<Long> researcherIds,
        List<Long> unitIds
    ) {
    }

    private record NewsDraft(
        String suggestedTitle,
        String suggestedSummary,
        String suggestedBody,
        String imageSuggestion,
        String imageAltSuggestion
    ) {
    }

    private static final class EvidenceCollector {
        private final List<NewsDraftEvidenceResponse> items = new ArrayList<>();
        private final Set<String> references = new LinkedHashSet<>();

        void add(String reference, String entityType, Long entityId, String label, String value) {
            String normalized = value == null ? null : value.trim().replaceAll("\\s+", " ");
            if (normalized == null || normalized.isBlank() || !references.add(reference)) {
                return;
            }
            items.add(new NewsDraftEvidenceResponse(reference, entityType, entityId, label, normalized));
        }

        List<NewsDraftEvidenceResponse> items() {
            return List.copyOf(items);
        }
    }
}
