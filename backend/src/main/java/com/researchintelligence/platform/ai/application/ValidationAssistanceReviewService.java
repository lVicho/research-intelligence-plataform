package com.researchintelligence.platform.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchintelligence.platform.ai.api.AiSuggestionResponse;
import com.researchintelligence.platform.ai.api.ValidationAssistanceCheckResponse;
import com.researchintelligence.platform.ai.api.ValidationAssistanceRecommendation;
import com.researchintelligence.platform.ai.api.ValidationAssistanceReviewRequest;
import com.researchintelligence.platform.ai.api.ValidationAssistanceReviewResponse;
import com.researchintelligence.platform.ai.api.ValidationAssistanceSeverity;
import com.researchintelligence.platform.ai.domain.AiSuggestionType;
import com.researchintelligence.platform.auth.application.PlatformUserPrincipal;
import com.researchintelligence.platform.auth.application.VisibilityContext;
import com.researchintelligence.platform.events.persistence.EventParticipationEntity;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.events.persistence.ScientificEventEntity;
import com.researchintelligence.platform.events.persistence.ScientificEventRepository;
import com.researchintelligence.platform.publications.domain.PublicationStatus;
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
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.validation.domain.ValidationEntityType;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ValidationAssistanceReviewService {

    private static final Pattern DOI_PATTERN = Pattern.compile("^10\\.\\d{4,9}/\\S+$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_CANDIDATES = 5;
    private static final int MAX_TOPIC_SUGGESTIONS = 5;

    private final PublicationRepository publicationRepository;
    private final PublicationAuthorRepository publicationAuthorRepository;
    private final PublicationTopicRepository publicationTopicRepository;
    private final TopicRepository topicRepository;
    private final ResearcherRepository researcherRepository;
    private final ResearchUnitRepository researchUnitRepository;
    private final ResearcherAffiliationRepository affiliationRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final ScientificEventRepository scientificEventRepository;
    private final AiSuggestionService aiSuggestionService;
    private final LlmService llmService;
    private final VisibilityContext visibilityContext;
    private final ObjectMapper objectMapper;

    public ValidationAssistanceReviewService(
        PublicationRepository publicationRepository,
        PublicationAuthorRepository publicationAuthorRepository,
        PublicationTopicRepository publicationTopicRepository,
        TopicRepository topicRepository,
        ResearcherRepository researcherRepository,
        ResearchUnitRepository researchUnitRepository,
        ResearcherAffiliationRepository affiliationRepository,
        EventParticipationRepository eventParticipationRepository,
        ScientificEventRepository scientificEventRepository,
        AiSuggestionService aiSuggestionService,
        LlmService llmService,
        VisibilityContext visibilityContext,
        ObjectMapper objectMapper
    ) {
        this.publicationRepository = publicationRepository;
        this.publicationAuthorRepository = publicationAuthorRepository;
        this.publicationTopicRepository = publicationTopicRepository;
        this.topicRepository = topicRepository;
        this.researcherRepository = researcherRepository;
        this.researchUnitRepository = researchUnitRepository;
        this.affiliationRepository = affiliationRepository;
        this.eventParticipationRepository = eventParticipationRepository;
        this.scientificEventRepository = scientificEventRepository;
        this.aiSuggestionService = aiSuggestionService;
        this.llmService = llmService;
        this.visibilityContext = visibilityContext;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ValidationAssistanceReviewResponse review(ValidationAssistanceReviewRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Validation review request is required.");
        }
        requireValidatorOrAdmin();
        ReviewDraft draft = switch (request.entityType()) {
            case PUBLICATION -> reviewPublication(request.entityId());
            case EVENT_PARTICIPATION -> reviewEventParticipation(request.entityId());
            case RESEARCHER_AFFILIATION -> reviewResearcherAffiliation(request.entityId());
            case RESEARCH_UNIT, RESEARCHER, SCIENTIFIC_EVENT, VENUE, PUBLISHER, TOPIC, AI_SUGGESTION ->
                throw new BusinessRuleException("AI validation review is not supported for this entity type yet.");
        };

        checkLocalModelAvailability(draft);
        if (draft.checks().stream().noneMatch(check -> check.severity() != ValidationAssistanceSeverity.INFO)) {
            draft.checks().add(info(
                "NO_BLOCKING_VALIDATION_ISSUES",
                "Sin incidencias bloqueantes",
                "No se detectaron campos obligatorios ausentes ni inconsistencias claras con los datos disponibles.",
                "El validador puede continuar con la decision final.",
                orderedMap("uncertain", false)
            ));
        }

        ValidationAssistanceRecommendation recommendation = recommendation(draft.checks());
        double confidence = confidence(recommendation, draft.checks());
        String suggestedValidationComment = suggestedValidationComment(recommendation, draft.checks());
        AiSuggestionResponse createdSuggestion = createSuggestion(draft, recommendation, confidence, suggestedValidationComment);
        return new ValidationAssistanceReviewResponse(
            recommendation,
            confidence,
            List.copyOf(draft.checks()),
            suggestedValidationComment,
            createdSuggestion.id()
        );
    }

    private ReviewDraft reviewPublication(Long publicationId) {
        PublicationEntity publication = publicationRepository.findById(publicationId)
            .orElseThrow(() -> new ResourceNotFoundException("Publication", publicationId));
        requirePending(publication.getValidationStatus(), ValidationEntityType.PUBLICATION, publicationId);

        List<ValidationAssistanceCheckResponse> checks = new ArrayList<>();
        if (isBlank(publication.getTitle())) {
            checks.add(blocker(
                "MISSING_TITLE",
                "Falta titulo",
                "La publicacion no tiene titulo.",
                "Solicite completar el titulo antes de validar.",
                fieldEvidence("title", publication.getTitle(), false)
            ));
        }
        if (publication.getType() == null) {
            checks.add(blocker(
                "MISSING_PUBLICATION_TYPE",
                "Falta tipo de publicacion",
                "La publicacion no indica su tipo academico.",
                "Solicite seleccionar el tipo correcto.",
                fieldEvidence("type", null, false)
            ));
        }
        if (publication.getStatus() == null) {
            checks.add(blocker(
                "MISSING_PUBLICATION_STATUS",
                "Falta estado academico",
                "La publicacion no indica si esta publicada, aceptada, en prensa u otro estado.",
                "Solicite completar el estado academico.",
                fieldEvidence("status", null, false)
            ));
        }
        if (publication.getPublicationYear() == null) {
            checks.add(warning(
                "MISSING_PUBLICATION_YEAR",
                "Falta ano de publicacion",
                "El ano ayuda a revisar duplicados y coherencia temporal.",
                "Solicite completar el ano si esta disponible.",
                fieldEvidence("publicationYear", null, false)
            ));
        }
        if (isBlank(publication.getSource())) {
            checks.add(warning(
                "MISSING_SOURCE",
                "Falta fuente",
                "La revista, congreso, editorial o repositorio no esta informado.",
                "Solicite completar la fuente o dejar constancia si no aplica.",
                fieldEvidence("source", publication.getSource(), false)
            ));
        }
        if (isBlank(publication.getAbstractText())) {
            checks.add(blocker(
                "MISSING_ABSTRACT",
                "Falta resumen",
                "La publicacion no tiene resumen o descripcion tecnica.",
                "Solicite un resumen revisado por la persona responsable.",
                fieldEvidence("abstractText", publication.getAbstractText(), false)
            ));
        }
        if (isBlank(publication.getPublicSummary())) {
            checks.add(warning(
                "MISSING_PUBLIC_SUMMARY",
                "Falta resumen publico",
                "No hay resumen publico para lectura no especializada.",
                "Revise si esta publicacion debe tener resumen publico antes de aparecer en el portal.",
                fieldEvidence("publicSummary", publication.getPublicSummary(), false)
            ));
        }

        checkPublicationIdentifiers(publication, checks);
        checkPublicationAuthors(publication, checks);
        checkPublicationTopics(publication, checks);
        checkDuplicatePublications(publication, checks);
        checkPublicationDateAndStatus(publication, checks);

        return new ReviewDraft(ValidationEntityType.PUBLICATION, publicationId, publication.getTitle(), checks);
    }

    private void checkPublicationIdentifiers(PublicationEntity publication, List<ValidationAssistanceCheckResponse> checks) {
        if (isBlank(publication.getDoi())) {
            checks.add(warning(
                "MISSING_DOI",
                "Falta DOI",
                "No se informa DOI. La asistencia no inventa identificadores externos.",
                "Si la publicacion tiene DOI, verifiquelo en una fuente externa confiable antes de validarlo.",
                orderedMap(
                    "field", "doi",
                    "currentValue", null,
                    "externalLookupPerformed", false,
                    "uncertain", true
                )
            ));
        } else if (!DOI_PATTERN.matcher(publication.getDoi().trim()).matches()) {
            checks.add(blocker(
                "DOI_FORMAT_INVALID",
                "Formato DOI sospechoso",
                "El DOI no coincide con el formato basico 10.xxxx/sufijo. No se ha comprobado su existencia externa.",
                "Corrija el formato o confirme el identificador en una fuente externa.",
                orderedMap(
                    "field", "doi",
                    "currentValue", publication.getDoi(),
                    "formatOnly", true,
                    "externalLookupPerformed", false,
                    "uncertain", false
                )
            ));
        }

        if (!isBlank(publication.getUrl()) && !isHttpUrl(publication.getUrl())) {
            checks.add(blocker(
                "URL_FORMAT_INVALID",
                "URL no valida",
                "La URL no tiene formato HTTP/HTTPS valido.",
                "Revise o elimine la URL antes de validar.",
                fieldEvidence("url", publication.getUrl(), false)
            ));
        }
    }

    private void checkPublicationAuthors(PublicationEntity publication, List<ValidationAssistanceCheckResponse> checks) {
        List<PublicationAuthorEntity> authors = publicationAuthorRepository.findByPublicationIdOrderByAuthorOrderAsc(publication.getId());
        if (authors.isEmpty()) {
            checks.add(blocker(
                "MISSING_AUTHORS",
                "Faltan autores",
                "La publicacion no tiene autores registrados.",
                "Solicite completar al menos un autor antes de validar.",
                orderedMap("authorCount", 0, "uncertain", false)
            ));
            return;
        }

        long internalAuthorCount = authors.stream().filter(author -> author.getResearcherId() != null).count();
        if (internalAuthorCount == 0) {
            checks.add(warning(
                "NO_INTERNAL_AUTHORS",
                "Sin autores internos",
                "Todos los autores estan registrados como externos.",
                "Revise si algun autor externo corresponde a un investigador interno.",
                orderedMap("authorCount", authors.size(), "internalAuthorCount", 0, "uncertain", true)
            ));
        }

        List<Map<String, Object>> matchingAuthors = authors.stream()
            .filter(author -> author.getResearcherId() == null)
            .map(this::externalAuthorMatchEvidence)
            .filter(match -> !((List<?>) match.get("candidateResearchers")).isEmpty())
            .toList();
        if (!matchingAuthors.isEmpty()) {
            checks.add(warning(
                "EXTERNAL_AUTHOR_INTERNAL_MATCH",
                "Posible autor interno registrado como externo",
                "Uno o mas autores externos se parecen a investigadores internos existentes.",
                "Revise manualmente las coincidencias; no convierta autores externos automaticamente.",
                orderedMap("matches", matchingAuthors, "uncertain", true)
            ));
        }

        List<Map<String, Object>> organizationMatches = authors.stream()
            .filter(author -> author.getResearcherId() == null)
            .filter(author -> !isBlank(author.getExternalAffiliation()))
            .map(this::externalOrganizationMatchEvidence)
            .filter(match -> !((List<?>) match.get("candidateOrganizations")).isEmpty())
            .toList();
        if (!organizationMatches.isEmpty()) {
            checks.add(warning(
                "EXTERNAL_ORGANIZATION_DUPLICATE",
                "Posible organizacion duplicada",
                "Una afiliacion externa coincide con una unidad u organizacion ya registrada.",
                "Revise si debe normalizarse contra una organizacion existente.",
                orderedMap("matches", organizationMatches, "uncertain", true)
            ));
        }
    }

    private void checkPublicationTopics(PublicationEntity publication, List<ValidationAssistanceCheckResponse> checks) {
        List<PublicationTopicEntity> topicLinks = publicationTopicRepository.findByPublicationId(publication.getId());
        if (topicLinks.isEmpty()) {
            checks.add(blocker(
                "MISSING_TOPICS",
                "Faltan temas",
                "La publicacion no tiene temas asociados.",
                "Solicite agregar temas validados o confirme que no aplica.",
                orderedMap("topicCount", 0, "uncertain", false)
            ));
        }

        List<Map<String, Object>> topicSuggestions = topicSuggestions(publication.getTitle(), publication.getAbstractText());
        if (topicLinks.isEmpty() && !topicSuggestions.isEmpty()) {
            checks.add(info(
                "TOPIC_SUGGESTIONS",
                "Sugerencias de temas",
                "Se encontraron temas existentes que aparecen en el titulo o resumen. Son sugerencias, no cambios automaticos.",
                "Revise los temas sugeridos y agregue solo los que correspondan.",
                orderedMap("suggestedTopics", topicSuggestions, "source", "existing_topic_label_match", "uncertain", true)
            ));
        }
    }

    private void checkDuplicatePublications(PublicationEntity publication, List<ValidationAssistanceCheckResponse> checks) {
        if (isBlank(publication.getTitle())) {
            return;
        }
        List<Map<String, Object>> candidates = publicationRepository.findDuplicateCandidates(
            publication.getId(),
            publication.getTitle(),
            publication.getPublicationYear(),
            PageRequest.of(0, 10)
        ).stream()
            .map(candidate -> orderedMap(
                "publicationId", candidate.getId(),
                "title", candidate.getTitle(),
                "year", candidate.getPublicationYear(),
                "doi", candidate.getDoi()
            ))
            .toList();
        if (!candidates.isEmpty()) {
            checks.add(warning(
                "POSSIBLE_DUPLICATE_PUBLICATION",
                "Posible publicacion duplicada",
                "Existen registros con el mismo titulo normalizado y ano de publicacion.",
                "Revise los candidatos antes de validar o solicite consolidacion.",
                orderedMap("candidates", candidates, "matchingRule", "same_title_and_year", "uncertain", true)
            ));
        }
    }

    private void checkPublicationDateAndStatus(PublicationEntity publication, List<ValidationAssistanceCheckResponse> checks) {
        int currentYear = Year.now().getValue();
        if (publication.getPublicationYear() != null && publication.getPublicationYear() > currentYear + 1) {
            checks.add(warning(
                "PUBLICATION_YEAR_IN_FUTURE",
                "Ano de publicacion futuro",
                "El ano de publicacion esta mas de un ano por delante del ano actual.",
                "Revise si el ano o el estado academico son correctos.",
                orderedMap("publicationYear", publication.getPublicationYear(), "currentYear", currentYear, "uncertain", false)
            ));
        }
        if (publication.getPublicationDate() != null
            && publication.getPublicationYear() != null
            && publication.getPublicationDate().getYear() != publication.getPublicationYear()) {
            checks.add(warning(
                "PUBLICATION_DATE_YEAR_MISMATCH",
                "Fecha y ano no coinciden",
                "La fecha de publicacion pertenece a un ano distinto del campo ano.",
                "Revise cual de los dos valores es correcto.",
                orderedMap(
                    "publicationDate", publication.getPublicationDate(),
                    "publicationYear", publication.getPublicationYear(),
                    "uncertain", false
                )
            ));
        }
        if (publication.getStatus() == PublicationStatus.DRAFT) {
            checks.add(warning(
                "PUBLICATION_STATUS_DRAFT",
                "Estado academico en borrador",
                "La actividad esta pendiente de validacion, pero el estado academico de la publicacion es DRAFT.",
                "Confirme si debe validarse como actividad en borrador o solicitar cambios.",
                orderedMap("status", publication.getStatus().name(), "uncertain", true)
            ));
        }
        if ((publication.getStatus() == PublicationStatus.ACCEPTED || publication.getStatus() == PublicationStatus.IN_PRESS)
            && publication.getPublicationYear() != null
            && publication.getPublicationYear() < currentYear - 3) {
            checks.add(warning(
                "PUBLICATION_STATUS_YEAR_MISMATCH",
                "Estado y ano poco consistentes",
                "Una publicacion aceptada o en prensa con un ano antiguo puede necesitar actualizacion de estado.",
                "Confirme si ya esta publicada o si el ano es correcto.",
                orderedMap(
                    "status", publication.getStatus().name(),
                    "publicationYear", publication.getPublicationYear(),
                    "currentYear", currentYear,
                    "uncertain", true
                )
            ));
        }
    }

    private ReviewDraft reviewEventParticipation(Long participationId) {
        EventParticipationEntity participation = eventParticipationRepository.findById(participationId)
            .orElseThrow(() -> new ResourceNotFoundException("EventParticipation", participationId));
        requirePending(participation.getValidationStatus(), ValidationEntityType.EVENT_PARTICIPATION, participationId);
        ScientificEventEntity event = scientificEventRepository.findById(participation.getEventId())
            .orElseThrow(() -> new ResourceNotFoundException("ScientificEvent", participation.getEventId()));

        List<ValidationAssistanceCheckResponse> checks = new ArrayList<>();
        if (isBlank(participation.getTitle())) {
            checks.add(blocker(
                "MISSING_TITLE",
                "Falta titulo",
                "La participacion no tiene titulo.",
                "Solicite completar el titulo antes de validar.",
                fieldEvidence("title", participation.getTitle(), false)
            ));
        }
        if (isBlank(participation.getParticipationTypeCode())) {
            checks.add(blocker(
                "MISSING_PARTICIPATION_TYPE",
                "Falta tipo de participacion",
                "La participacion no indica su tipo.",
                "Solicite seleccionar el tipo de participacion.",
                fieldEvidence("participationTypeCode", participation.getParticipationTypeCode(), false)
            ));
        }
        if (isBlank(participation.getDescription())) {
            checks.add(blocker(
                "MISSING_DESCRIPTION",
                "Falta descripcion",
                "La participacion no tiene descripcion.",
                "Solicite una descripcion breve de la actividad.",
                fieldEvidence("description", participation.getDescription(), false)
            ));
        }
        if (participation.getParticipationDate() == null) {
            checks.add(warning(
                "MISSING_PARTICIPATION_DATE",
                "Falta fecha de participacion",
                "La fecha ayuda a validar coherencia con el evento.",
                "Solicite completar la fecha si esta disponible.",
                fieldEvidence("participationDate", null, false)
            ));
        }
        if (participation.getResearchUnitId() == null) {
            checks.add(warning(
                "MISSING_RESEARCH_UNIT",
                "Falta unidad asociada",
                "La participacion no esta vinculada a una unidad de investigacion.",
                "Revise si debe asociarse a la unidad vigente del investigador.",
                fieldEvidence("researchUnitId", null, true)
            ));
        }
        if (!isBlank(participation.getEvidenceUrl()) && !isHttpUrl(participation.getEvidenceUrl())) {
            checks.add(blocker(
                "EVIDENCE_URL_FORMAT_INVALID",
                "URL de evidencia no valida",
                "La URL de evidencia no tiene formato HTTP/HTTPS valido.",
                "Revise o elimine la URL de evidencia antes de validar.",
                fieldEvidence("evidenceUrl", participation.getEvidenceUrl(), false)
            ));
        }
        checkEventDates(participation, event, checks);
        if (event.getValidationStatus() != ValidationStatus.VALIDATED) {
            checks.add(warning(
                "RELATED_EVENT_NOT_VALIDATED",
                "Evento no validado",
                "El evento relacionado aun no esta validado.",
                "Revise el evento antes de validar la participacion.",
                orderedMap("eventId", event.getId(), "eventValidationStatus", event.getValidationStatus(), "uncertain", false)
            ));
        }
        List<Map<String, Object>> suggestions = topicSuggestions(participation.getTitle(), participation.getDescription());
        if (!suggestions.isEmpty()) {
            checks.add(info(
                "TOPIC_SUGGESTIONS",
                "Sugerencias de temas",
                "Se encontraron temas existentes en el titulo o descripcion de la participacion.",
                "Use estas sugerencias solo como ayuda para revisar la actividad.",
                orderedMap("suggestedTopics", suggestions, "source", "existing_topic_label_match", "uncertain", true)
            ));
        }
        return new ReviewDraft(ValidationEntityType.EVENT_PARTICIPATION, participationId, participation.getTitle(), checks);
    }

    private void checkEventDates(
        EventParticipationEntity participation,
        ScientificEventEntity event,
        List<ValidationAssistanceCheckResponse> checks
    ) {
        LocalDate participationDate = participation.getParticipationDate();
        int currentYear = Year.now().getValue();
        if (participationDate != null && participationDate.getYear() > currentYear + 2) {
            checks.add(warning(
                "PARTICIPATION_DATE_IN_FUTURE",
                "Fecha de participacion futura",
                "La fecha de participacion esta mas de dos anos por delante del ano actual.",
                "Revise la fecha o el estado de la actividad.",
                orderedMap("participationDate", participationDate, "currentYear", currentYear, "uncertain", false)
            ));
        }
        if (participationDate != null && event.getStartDate() != null && participationDate.isBefore(event.getStartDate())) {
            checks.add(warning(
                "PARTICIPATION_BEFORE_EVENT",
                "Participacion antes del evento",
                "La fecha de participacion es anterior al inicio del evento.",
                "Revise la fecha de participacion o las fechas del evento.",
                orderedMap("participationDate", participationDate, "eventStartDate", event.getStartDate(), "uncertain", false)
            ));
        }
        if (participationDate != null && event.getEndDate() != null && participationDate.isAfter(event.getEndDate())) {
            checks.add(warning(
                "PARTICIPATION_AFTER_EVENT",
                "Participacion despues del evento",
                "La fecha de participacion es posterior al fin del evento.",
                "Revise la fecha de participacion o las fechas del evento.",
                orderedMap("participationDate", participationDate, "eventEndDate", event.getEndDate(), "uncertain", false)
            ));
        }
    }

    private ReviewDraft reviewResearcherAffiliation(Long affiliationId) {
        ResearcherAffiliationEntity affiliation = affiliationRepository.findById(affiliationId)
            .orElseThrow(() -> new ResourceNotFoundException("ResearcherAffiliation", affiliationId));
        requirePending(affiliation.getValidationStatus(), ValidationEntityType.RESEARCHER_AFFILIATION, affiliationId);
        ResearcherEntity researcher = researcherRepository.findById(affiliation.getResearcherId())
            .orElseThrow(() -> new ResourceNotFoundException("Researcher", affiliation.getResearcherId()));
        ResearchUnitEntity unit = researchUnitRepository.findById(affiliation.getResearchUnitId())
            .orElseThrow(() -> new ResourceNotFoundException("ResearchUnit", affiliation.getResearchUnitId()));

        List<ValidationAssistanceCheckResponse> checks = new ArrayList<>();
        if (affiliation.getAffiliationType() == null) {
            checks.add(blocker(
                "MISSING_AFFILIATION_TYPE",
                "Falta tipo de afiliacion",
                "La afiliacion no indica su tipo.",
                "Solicite seleccionar el tipo de afiliacion.",
                fieldEvidence("affiliationType", null, false)
            ));
        }
        if (isBlank(affiliation.getRole())) {
            checks.add(warning(
                "MISSING_ROLE",
                "Falta rol",
                "La afiliacion no describe el rol del investigador en la unidad.",
                "Solicite completar el rol si es relevante para el catalogo institucional.",
                fieldEvidence("role", affiliation.getRole(), false)
            ));
        }
        if (affiliation.getStartDate() == null) {
            checks.add(warning(
                "MISSING_START_DATE",
                "Falta fecha de inicio",
                "La afiliacion no tiene fecha de inicio.",
                "Solicite completar la fecha si esta disponible.",
                fieldEvidence("startDate", null, false)
            ));
        }
        if (affiliation.getStartDate() != null
            && affiliation.getEndDate() != null
            && affiliation.getEndDate().isBefore(affiliation.getStartDate())) {
            checks.add(blocker(
                "AFFILIATION_DATE_RANGE_INVALID",
                "Fechas de afiliacion inconsistentes",
                "La fecha de fin es anterior a la fecha de inicio.",
                "Corrija el rango de fechas antes de validar.",
                orderedMap("startDate", affiliation.getStartDate(), "endDate", affiliation.getEndDate(), "uncertain", false)
            ));
        }
        LocalDate today = LocalDate.now();
        if (affiliation.isPrimaryAffiliation()
            && affiliation.getEndDate() != null
            && affiliation.getEndDate().isBefore(today)) {
            checks.add(warning(
                "ENDED_PRIMARY_AFFILIATION",
                "Afiliacion principal finalizada",
                "La afiliacion esta marcada como principal, pero su fecha de fin ya paso.",
                "Revise si debe dejar de ser principal o actualizarse como historica.",
                orderedMap("endDate", affiliation.getEndDate(), "today", today, "uncertain", true)
            ));
        }
        if (affiliation.getAffiliationType() == AffiliationType.FORMER_MEMBER && affiliation.getEndDate() == null) {
            checks.add(warning(
                "FORMER_MEMBER_WITHOUT_END_DATE",
                "Miembro anterior sin fecha de fin",
                "La afiliacion esta marcada como anterior, pero no tiene fecha de fin.",
                "Confirme si el tipo o la fecha de fin son correctos.",
                orderedMap("affiliationType", affiliation.getAffiliationType().name(), "endDate", null, "uncertain", true)
            ));
        }
        if (affiliation.getStartDate() != null && affiliation.getStartDate().isAfter(today.plusYears(1))) {
            checks.add(warning(
                "AFFILIATION_START_DATE_IN_FUTURE",
                "Fecha de inicio futura",
                "La fecha de inicio esta mas de un ano en el futuro.",
                "Revise si la afiliacion ya debe entrar en validacion.",
                orderedMap("startDate", affiliation.getStartDate(), "today", today, "uncertain", false)
            ));
        }
        if (researcher.getValidationStatus() != ValidationStatus.VALIDATED) {
            checks.add(warning(
                "RELATED_RESEARCHER_NOT_VALIDATED",
                "Investigador no validado",
                "El investigador asociado aun no esta validado.",
                "Revise el investigador antes de validar la afiliacion.",
                orderedMap("researcherId", researcher.getId(), "researcherValidationStatus", researcher.getValidationStatus(), "uncertain", false)
            ));
        }
        if (unit.getValidationStatus() != ValidationStatus.VALIDATED) {
            checks.add(warning(
                "RELATED_RESEARCH_UNIT_NOT_VALIDATED",
                "Unidad no validada",
                "La unidad asociada aun no esta validada.",
                "Revise la unidad antes de validar la afiliacion.",
                orderedMap("researchUnitId", unit.getId(), "unitValidationStatus", unit.getValidationStatus(), "uncertain", false)
            ));
        }
        return new ReviewDraft(
            ValidationEntityType.RESEARCHER_AFFILIATION,
            affiliationId,
            researcher.getFullName() + " / " + unit.getName(),
            checks
        );
    }

    private Map<String, Object> externalAuthorMatchEvidence(PublicationAuthorEntity author) {
        String authorName = normalizeWhitespace(author.getExternalAuthorName());
        return orderedMap(
            "publicationAuthorId", author.getId(),
            "externalAuthorName", authorName,
            "externalAffiliation", author.getExternalAffiliation(),
            "candidateResearchers", researcherCandidates(authorName).stream()
                .map(candidate -> orderedMap(
                    "researcherId", candidate.getId(),
                    "fullName", candidate.getFullName(),
                    "displayName", candidate.getDisplayName(),
                    "orcid", candidate.getOrcid()
                ))
                .toList()
        );
    }

    private Map<String, Object> externalOrganizationMatchEvidence(PublicationAuthorEntity author) {
        String affiliationName = normalizeWhitespace(author.getExternalAffiliation());
        return orderedMap(
            "publicationAuthorId", author.getId(),
            "externalAffiliation", affiliationName,
            "candidateOrganizations", researchUnitRepository.findOrganizationNameCandidates(
                affiliationName,
                PageRequest.of(0, MAX_CANDIDATES)
            ).stream()
                .map(unit -> orderedMap(
                    "researchUnitId", unit.getId(),
                    "name", unit.getName(),
                    "shortName", unit.getShortName(),
                    "organizationScope", unit.getOrganizationScope()
                ))
                .toList()
        );
    }

    private List<ResearcherEntity> researcherCandidates(String authorName) {
        if (isBlank(authorName)) {
            return List.of();
        }
        String normalizedName = normalizeSearchText(authorName);
        return researcherRepository.search(
            authorName,
            "%" + authorName.toLowerCase(Locale.ROOT) + "%",
            null,
            null,
            null,
            null,
            null,
            PageRequest.of(0, 10)
        ).stream()
            .filter(candidate -> normalizeSearchText(candidate.getFullName()).equals(normalizedName)
                || normalizeSearchText(candidate.getDisplayName()).equals(normalizedName)
                || shareLastToken(candidate.getFullName(), authorName))
            .limit(MAX_CANDIDATES)
            .toList();
    }

    private List<Map<String, Object>> topicSuggestions(String title, String body) {
        String text = normalizeSearchText(title + " " + body);
        if (text.length() < 12) {
            return List.of();
        }
        return topicRepository.findAll()
            .stream()
            .map(topic -> new TopicSuggestion(topic, topicMatchScore(topic, text)))
            .filter(suggestion -> suggestion.score() > 0)
            .sorted(Comparator.comparingInt(TopicSuggestion::score).reversed()
                .thenComparing(suggestion -> suggestion.topic().getName()))
            .limit(MAX_TOPIC_SUGGESTIONS)
            .map(suggestion -> orderedMap(
                "topicId", suggestion.topic().getId(),
                "label", suggestion.topic().getName(),
                "reason", "Existing topic label appears in submitted metadata."
            ))
            .toList();
    }

    private int topicMatchScore(TopicEntity topic, String normalizedText) {
        String label = normalizeSearchText(topic.getName());
        if (label.isBlank()) {
            return 0;
        }
        if (containsTokenSequence(normalizedText, label)) {
            return label.split("\\s+").length + 2;
        }
        List<String> terms = meaningfulTerms(label);
        long matches = terms.stream().filter(term -> containsTokenSequence(normalizedText, term)).count();
        return terms.size() >= 2 && matches == terms.size() ? (int) matches : 0;
    }

    private boolean containsTokenSequence(String normalizedText, String normalizedNeedle) {
        return (" " + normalizedText + " ").contains(" " + normalizedNeedle + " ");
    }

    private List<String> meaningfulTerms(String value) {
        Set<String> stopWords = Set.of(
            "a", "an", "and", "de", "del", "el", "en", "for", "in", "la", "las", "los", "of", "on", "the", "to", "y"
        );
        return List.of(value.split("\\s+"))
            .stream()
            .filter(term -> term.length() >= 3)
            .filter(term -> !stopWords.contains(term))
            .distinct()
            .toList();
    }

    private void checkLocalModelAvailability(ReviewDraft draft) {
        if (!"ollama".equalsIgnoreCase(llmService.provider())) {
            return;
        }
        try {
            llmService.answer(new LlmPrompt(
                "Check whether these validation assistance checks are internally consistent. Return only OK. Do not add facts.",
                modelContext(draft)
            ));
        } catch (BusinessRuleException exception) {
            draft.checks().add(info(
                "AI_REVIEW_MODEL_UNAVAILABLE",
                "Modelo local no disponible",
                "Ollama no respondio durante la revision. Se uso la revision determinista con los datos locales disponibles.",
                "Puede continuar con la revision manual o reintentar cuando Ollama este disponible.",
                orderedMap(
                    "provider", llmService.provider(),
                    "model", llmService.model(),
                    "fallback", "deterministic_validation_checks",
                    "message", exception.getMessage(),
                    "uncertain", false
                )
            ));
        }
    }

    private String modelContext(ReviewDraft draft) {
        return """
            Entity type: %s
            Entity id: %d
            Title: %s
            Check codes: %s
            """.formatted(
            draft.entityType().name(),
            draft.entityId(),
            draft.title(),
            draft.checks().stream().map(ValidationAssistanceCheckResponse::code).collect(Collectors.joining(", "))
        );
    }

    private AiSuggestionResponse createSuggestion(
        ReviewDraft draft,
        ValidationAssistanceRecommendation recommendation,
        double confidence,
        String suggestedValidationComment
    ) {
        Map<String, Object> payload = orderedMap(
            "recommendation", recommendation,
            "confidence", confidence,
            "checks", draft.checks(),
            "suggestedValidationComment", suggestedValidationComment,
            "requiresHumanDecision", true,
            "doesNotValidateAutomatically", true
        );
        Map<String, Object> evidence = orderedMap(
            "entityType", draft.entityType().name(),
            "entityId", draft.entityId(),
            "title", draft.title(),
            "dataSource", "local_validation_metadata",
            "externalLookupsPerformed", false
        );
        return aiSuggestionService.create(new AiSuggestionCreateCommand(
            draft.entityType().name(),
            draft.entityId(),
            AiSuggestionType.VALIDATION_ASSISTANCE,
            writeJson(payload),
            "Generated validation assistance hints. The validator remains responsible for the final decision.",
            writeJson(evidence),
            llmService.provider(),
            llmService.model()
        ));
    }

    private ValidationAssistanceRecommendation recommendation(List<ValidationAssistanceCheckResponse> checks) {
        if (checks.stream().anyMatch(check -> check.severity() == ValidationAssistanceSeverity.BLOCKER)) {
            return ValidationAssistanceRecommendation.REQUEST_CHANGES;
        }
        if (checks.stream().anyMatch(check -> check.severity() == ValidationAssistanceSeverity.WARNING)) {
            return ValidationAssistanceRecommendation.REVIEW_MANUALLY;
        }
        return ValidationAssistanceRecommendation.VALIDATE;
    }

    private double confidence(
        ValidationAssistanceRecommendation recommendation,
        List<ValidationAssistanceCheckResponse> checks
    ) {
        double value = switch (recommendation) {
            case VALIDATE -> 0.84;
            case REQUEST_CHANGES -> 0.78;
            case REVIEW_MANUALLY -> 0.66;
        };
        long uncertainChecks = checks.stream().filter(this::isUncertain).count();
        value -= Math.min(0.18, uncertainChecks * 0.03);
        return round(value);
    }

    private boolean isUncertain(ValidationAssistanceCheckResponse check) {
        Object uncertain = check.evidence() == null ? null : check.evidence().get("uncertain");
        return Boolean.TRUE.equals(uncertain);
    }

    private String suggestedValidationComment(
        ValidationAssistanceRecommendation recommendation,
        List<ValidationAssistanceCheckResponse> checks
    ) {
        List<String> mainChecks = checks.stream()
            .filter(check -> check.severity() != ValidationAssistanceSeverity.INFO)
            .limit(3)
            .map(ValidationAssistanceCheckResponse::title)
            .toList();
        return switch (recommendation) {
            case REQUEST_CHANGES -> "Solicitar cambios: " + String.join("; ", mainChecks) + ".";
            case REVIEW_MANUALLY -> "Revisar manualmente antes de decidir: " + String.join("; ", mainChecks) + ".";
            case VALIDATE -> "No se detectan incidencias bloqueantes con los datos locales disponibles. Decision final a cargo del validador.";
        };
    }

    private PlatformUserPrincipal requireValidatorOrAdmin() {
        PlatformUserPrincipal user = visibilityContext.currentUser()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication is required to request AI validation review."));
        if (hasRole(user, "ADMIN") || hasRole(user, "VALIDATOR")) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins and validators can request AI validation review.");
    }

    private void requirePending(ValidationStatus status, ValidationEntityType entityType, Long entityId) {
        if (status != ValidationStatus.PENDING_VALIDATION) {
            throw new BusinessRuleException("AI validation review is available only for pending validation items: " + entityType + " " + entityId + ".");
        }
    }

    private ValidationAssistanceCheckResponse blocker(
        String code,
        String title,
        String description,
        String suggestedAction,
        Map<String, Object> evidence
    ) {
        return check(code, ValidationAssistanceSeverity.BLOCKER, title, description, suggestedAction, evidence);
    }

    private ValidationAssistanceCheckResponse warning(
        String code,
        String title,
        String description,
        String suggestedAction,
        Map<String, Object> evidence
    ) {
        return check(code, ValidationAssistanceSeverity.WARNING, title, description, suggestedAction, evidence);
    }

    private ValidationAssistanceCheckResponse info(
        String code,
        String title,
        String description,
        String suggestedAction,
        Map<String, Object> evidence
    ) {
        return check(code, ValidationAssistanceSeverity.INFO, title, description, suggestedAction, evidence);
    }

    private ValidationAssistanceCheckResponse check(
        String code,
        ValidationAssistanceSeverity severity,
        String title,
        String description,
        String suggestedAction,
        Map<String, Object> evidence
    ) {
        return new ValidationAssistanceCheckResponse(code, severity, title, description, suggestedAction, evidence);
    }

    private Map<String, Object> fieldEvidence(String field, Object value, boolean uncertain) {
        return orderedMap("field", field, "currentValue", value, "uncertain", uncertain);
    }

    private boolean hasRole(PlatformUserPrincipal user, String role) {
        return user.roles().contains(role);
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value.trim());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private boolean shareLastToken(String left, String right) {
        String leftLast = lastToken(normalizeSearchText(left));
        String rightLast = lastToken(normalizeSearchText(right));
        return !leftLast.isBlank() && leftLast.equals(rightLast);
    }

    private String lastToken(String value) {
        String[] tokens = value.split("\\s+");
        return tokens.length == 0 ? "" : tokens[tokens.length - 1];
    }

    private String normalizeSearchText(String value) {
        String normalized = Normalizer.normalize(normalizeWhitespace(value).toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "").replaceAll("[^\\p{Alnum}]+", " ").trim();
    }

    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private double round(double value) {
        return Math.round(Math.min(Math.max(value, 0.0), 1.0) * 100.0) / 100.0;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessRuleException("Could not serialize validation assistance suggestion payload.");
        }
    }

    private Map<String, Object> orderedMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            Object value = keysAndValues[index + 1];
            if (value instanceof Collection<?> collection) {
                value = List.copyOf(collection);
            }
            map.put((String) keysAndValues[index], value);
        }
        return map;
    }

    private record ReviewDraft(
        ValidationEntityType entityType,
        Long entityId,
        String title,
        List<ValidationAssistanceCheckResponse> checks
    ) {
    }

    private record TopicSuggestion(TopicEntity topic, int score) {
    }
}
