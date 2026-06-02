package com.researchintelligence.platform.portal.application;

import com.researchintelligence.platform.ai.api.CopilotAnswerRequest;
import com.researchintelligence.platform.ai.api.CopilotAnswerResponse;
import com.researchintelligence.platform.ai.api.CopilotCitationResponse;
import com.researchintelligence.platform.ai.api.CopilotRetrieveRequest;
import com.researchintelligence.platform.ai.api.CopilotRetrieveResponse;
import com.researchintelligence.platform.ai.api.CopilotRetrievedPublicationResponse;
import com.researchintelligence.platform.ai.application.CopilotService;
import com.researchintelligence.platform.ai.application.RetrievalMode;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderFiltersRequest;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderPublicationResponse;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderResultResponse;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderSearchRequest;
import com.researchintelligence.platform.expertfinder.api.ExpertFinderSearchResponse;
import com.researchintelligence.platform.expertfinder.application.ExpertFinderService;
import com.researchintelligence.platform.portal.api.PortalContextAssistantPublicationEvidenceResponse;
import com.researchintelligence.platform.portal.api.PortalContextAssistantRequest;
import com.researchintelligence.platform.portal.api.PortalContextAssistantResearcherEvidenceResponse;
import com.researchintelligence.platform.portal.api.PortalContextAssistantResponse;
import com.researchintelligence.platform.portal.api.PortalContextAssistantScope;
import com.researchintelligence.platform.portal.api.PortalContextAssistantSearchRequest;
import com.researchintelligence.platform.portal.api.PortalContextAssistantUnitEvidenceResponse;
import com.researchintelligence.platform.publications.api.PublicationAuthorResponse;
import com.researchintelligence.platform.publications.api.PublicationResponse;
import com.researchintelligence.platform.publications.api.PublicationSummaryResponse;
import com.researchintelligence.platform.publications.api.RelatedPublicationResponse;
import com.researchintelligence.platform.publications.api.RelatedPublicationsResponse;
import com.researchintelligence.platform.publications.api.TopicResponse;
import com.researchintelligence.platform.publications.application.PublicationService;
import com.researchintelligence.platform.publications.application.RelatedPublicationService;
import com.researchintelligence.platform.researchers.api.ResearcherAffiliationResponse;
import com.researchintelligence.platform.researchers.api.ResearcherResponse;
import com.researchintelligence.platform.researchers.application.ResearcherService;
import com.researchintelligence.platform.researchunits.api.ResearchUnitResponse;
import com.researchintelligence.platform.researchunits.application.ResearchUnitService;
import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.application.BusinessRuleException;
import com.researchintelligence.platform.shared.application.ResourceNotFoundException;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PortalContextAssistantService {

    private static final int DEFAULT_EVIDENCE_LIMIT = 12;
    private static final int MAX_EVIDENCE_LIMIT = 30;
    private static final int MAX_SCOPE_RECORDS = 500;
    private static final int PAGE_SIZE = 100;
    private static final String SUMMARY_WARNING = "El contexto se ha resumido para mantener la respuesta legible.";
    private static final String RELEVANT_EVIDENCE_WARNING = "Se han usado las evidencias públicas más relevantes de este conjunto.";
    private static final String PUBLIC_VISIBILITY_SUMMARY = "El asistente solo utiliza información pública validada del portal.";

    private final PublicationService publicationService;
    private final ResearcherService researcherService;
    private final ResearchUnitService researchUnitService;
    private final RelatedPublicationService relatedPublicationService;
    private final ExpertFinderService expertFinderService;
    private final CopilotService copilotService;

    public PortalContextAssistantService(
        PublicationService publicationService,
        ResearcherService researcherService,
        ResearchUnitService researchUnitService,
        RelatedPublicationService relatedPublicationService,
        ExpertFinderService expertFinderService,
        CopilotService copilotService
    ) {
        this.publicationService = publicationService;
        this.researcherService = researcherService;
        this.researchUnitService = researchUnitService;
        this.relatedPublicationService = relatedPublicationService;
        this.expertFinderService = expertFinderService;
        this.copilotService = copilotService;
    }

    public PortalContextAssistantResponse ask(PortalContextAssistantRequest request) {
        ContextResolution resolution = resolve(request);
        CopilotAnswerResponse answer;
        try {
            answer = copilotService.answer(new CopilotAnswerRequest(
                scopedQuestion(request, resolution),
                resolution.publications(),
                false
            ));
        } catch (RuntimeException exception) {
            return unavailableResponse(resolution, exception);
        }

        List<String> warnings = mergedWarnings(resolution.warnings(), answer.warnings());
        return new PortalContextAssistantResponse(
            answer.answer(),
            answer.citedPublications().stream().map(this::toPublicationEvidence).toList(),
            mentionedResearchers(answer.answer(), resolution.researchers()),
            mentionedUnits(answer.answer(), resolution.units()),
            resolution.evidenceSummary(),
            warnings,
            answer.provider(),
            answer.model(),
            VisibilityScope.PUBLIC_VALIDATED.name(),
            true
        );
    }

    private ContextResolution resolve(PortalContextAssistantRequest request) {
        int evidenceLimit = evidenceLimit(request.maxEvidence());
        return switch (request.contextScope()) {
            case PUBLICATION_DETAIL -> resolvePublicationDetail(requiredTargetId(request), request, evidenceLimit);
            case RESEARCHER_PROFILE -> resolveResearcherProfile(requiredTargetId(request), request, evidenceLimit);
            case UNIT_PROFILE -> resolveUnitProfile(requiredTargetId(request), request, evidenceLimit);
            case PUBLICATION_SEARCH_RESULTS -> resolvePublicationSearch(request, evidenceLimit);
            case EXPERT_FINDER_RESULTS -> resolveExpertFinder(request, evidenceLimit);
        };
    }

    private ContextResolution resolvePublicationDetail(Long publicationId, PortalContextAssistantRequest request, int evidenceLimit) {
        PublicationResponse publication = publicationService.findPublicValidatedById(publicationId);
        List<PublicationEvidenceCandidate> candidates = new ArrayList<>();
        candidates.add(new PublicationEvidenceCandidate(publication, 100.0, "Publicación actual del portal."));
        RelatedPublicationsResponse related = relatedPublicationService.findRelated(publicationId, Math.min(evidenceLimit, 8), null, false);
        for (RelatedPublicationResponse relatedPublication : related.relatedPublications()) {
            PublicationResponse detail = findPublicPublication(relatedPublication.publication().id());
            if (detail != null) {
                candidates.add(new PublicationEvidenceCandidate(detail, relatedPublication.finalScore(), "Publicación relacionada pública."));
            }
        }
        List<CopilotRetrievedPublicationResponse> selected = selectPublications(candidates, request.question(), null, evidenceLimit);
        List<PublicationResponse> selectedDetails = selectedPublicationDetails(candidates, selected);
        List<String> warnings = new ArrayList<>(related.warnings());
        if (candidates.size() > selected.size()) {
            warnings.add(SUMMARY_WARNING);
            warnings.add(RELEVANT_EVIDENCE_WARNING);
        }
        return new ContextResolution(
            "esta publicación",
            selected,
            mergeResearchers(researcherEvidence(publication), researcherEvidenceFromPublications(selectedDetails)),
            mergeUnits(unitEvidenceFromResearchers(publication), unitEvidenceFromPublications(selectedDetails)),
            List.of(
                PUBLIC_VISIBILITY_SUMMARY,
                "Contexto: publicación " + publication.id() + " y relaciones públicas validadas.",
                "Evidencias de publicación usadas: " + selected.size() + " de " + candidates.size() + "."
            ),
            warnings
        );
    }

    private ContextResolution resolveResearcherProfile(Long researcherId, PortalContextAssistantRequest request, int evidenceLimit) {
        ResearcherResponse researcher = researcherService.findPortalVisibleValidatedById(researcherId);
        PublicationCollection collection = collectPublicationDetails(new PortalContextAssistantSearchRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            researcherId,
            null
        ));
        List<PublicationEvidenceCandidate> candidates = collection.publications().stream()
            .map(publication -> new PublicationEvidenceCandidate(publication, 0.0, "Publicación pública validada del investigador."))
            .toList();
        List<CopilotRetrievedPublicationResponse> selected = selectPublications(candidates, request.question(), null, evidenceLimit);
        List<PublicationResponse> selectedDetails = selectedPublicationDetails(candidates, selected);
        List<String> warnings = boundedWarnings(collection.totalElements(), collection.bounded(), selected.size());
        return new ContextResolution(
            researcher.displayName() == null || researcher.displayName().isBlank() ? researcher.fullName() : researcher.displayName(),
            selected,
            mergeResearchers(List.of(researcherEvidence(researcher)), researcherEvidenceFromPublications(selectedDetails)),
            mergeUnits(unitEvidence(researcher.primaryAffiliation()), unitEvidenceFromPublications(selectedDetails)),
            List.of(
                PUBLIC_VISIBILITY_SUMMARY,
                "Contexto: perfil público del investigador " + researcher.fullName() + ".",
                "Publicaciones públicas validadas en el alcance completo: " + collection.totalElements() + ".",
                "Evidencias de publicación usadas: " + selected.size() + "."
            ),
            warnings
        );
    }

    private ContextResolution resolveUnitProfile(Long unitId, PortalContextAssistantRequest request, int evidenceLimit) {
        ResearchUnitResponse unit = researchUnitService.findPortalVisibleValidatedById(unitId);
        PublicationCollection collection = collectPublicationDetails(new PortalContextAssistantSearchRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            unitId,
            null,
            null
        ));
        List<PublicationEvidenceCandidate> candidates = collection.publications().stream()
            .map(publication -> new PublicationEvidenceCandidate(publication, 0.0, "Publicación pública validada vinculada a la unidad."))
            .toList();
        List<CopilotRetrievedPublicationResponse> selected = selectPublications(candidates, request.question(), null, evidenceLimit);
        List<PublicationResponse> selectedDetails = selectedPublicationDetails(candidates, selected);
        List<String> warnings = boundedWarnings(collection.totalElements(), collection.bounded(), selected.size());
        return new ContextResolution(
            unit.name(),
            selected,
            researcherEvidenceFromPublications(selectedDetails),
            mergeUnits(List.of(unitEvidence(unit)), unitEvidenceFromPublications(selectedDetails)),
            List.of(
                PUBLIC_VISIBILITY_SUMMARY,
                "Contexto: perfil público de la unidad " + unit.name() + ".",
                "Publicaciones públicas validadas en el alcance completo: " + collection.totalElements() + ".",
                "Evidencias de publicación usadas: " + selected.size() + "."
            ),
            warnings
        );
    }

    private ContextResolution resolvePublicationSearch(PortalContextAssistantRequest request, int evidenceLimit) {
        PortalContextAssistantSearchRequest search = safeSearch(request.searchRequest());
        if (isSemanticMode(search.mode()) && !hasStructuredFilters(search)) {
            return resolveSemanticPublicationSearch(request, search, evidenceLimit);
        }
        PublicationCollection collection = collectPublicationDetails(search);
        List<PublicationEvidenceCandidate> candidates = collection.publications().stream()
            .map(publication -> new PublicationEvidenceCandidate(publication, 0.0, "Resultado público validado reconstruido desde filtros."))
            .toList();
        List<CopilotRetrievedPublicationResponse> selected = selectPublications(candidates, request.question(), search.query(), evidenceLimit);
        List<PublicationResponse> selectedDetails = selectedPublicationDetails(candidates, selected);
        List<String> warnings = boundedWarnings(collection.totalElements(), collection.bounded(), selected.size());
        return new ContextResolution(
            "estos resultados",
            selected,
            researcherEvidenceFromPublications(selectedDetails),
            unitEvidenceFromPublications(selectedDetails),
            List.of(
                PUBLIC_VISIBILITY_SUMMARY,
                "Contexto: búsqueda pública reconstruida en servidor.",
                "Resultados públicos validados en el alcance completo: " + collection.totalElements() + ".",
                "Evidencias de publicación usadas: " + selected.size() + "."
            ),
            warnings
        );
    }

    private ContextResolution resolveSemanticPublicationSearch(
        PortalContextAssistantRequest request,
        PortalContextAssistantSearchRequest search,
        int evidenceLimit
    ) {
        String query = blankToDefault(search.query(), request.question());
        CopilotRetrieveResponse retrieval = copilotService.retrieve(new CopilotRetrieveRequest(
            query,
            evidenceLimit,
            null,
            RetrievalMode.BALANCED,
            false
        ));
        List<PublicationResponse> selectedDetails = publicationDetails(retrieval.retrievedPublications());
        List<CopilotRetrievedPublicationResponse> enrichedPublications = enrichRetrievedPublications(
            retrieval.retrievedPublications(),
            selectedDetails
        );
        List<String> warnings = new ArrayList<>(retrieval.warnings());
        warnings.add(RELEVANT_EVIDENCE_WARNING);
        return new ContextResolution(
            "estos resultados inteligentes",
            enrichedPublications,
            researcherEvidenceFromPublications(selectedDetails),
            unitEvidenceFromPublications(selectedDetails),
            List.of(
                PUBLIC_VISIBILITY_SUMMARY,
                "Contexto: búsqueda inteligente reconstruida en servidor.",
                "Evidencias semánticas públicas usadas: " + enrichedPublications.size() + "."
            ),
            warnings
        );
    }

    private ContextResolution resolveExpertFinder(PortalContextAssistantRequest request, int evidenceLimit) {
        PortalContextAssistantSearchRequest search = safeSearch(request.searchRequest());
        String query = blankToDefault(search.query(), request.question());
        ExpertFinderSearchResponse expertResponse = expertFinderService.search(new ExpertFinderSearchRequest(
            query,
            Math.min(50, Math.max(evidenceLimit, 10)),
            retrievalMode(search.mode()),
            new ExpertFinderFiltersRequest(search.researchUnitId(), search.topic(), true)
        ));
        Map<Long, PublicationEvidenceCandidate> candidatesById = new LinkedHashMap<>();
        List<PortalContextAssistantResearcherEvidenceResponse> researchers = new ArrayList<>();
        for (ExpertFinderResultResponse result : expertResponse.results()) {
            researchers.add(new PortalContextAssistantResearcherEvidenceResponse(
                result.researcher().id(),
                result.researcher().displayName() == null ? result.researcher().fullName() : result.researcher().displayName(),
                result.researcher().primaryResearchUnitName(),
                "/portal/investigadores/" + result.researcher().id()
            ));
            for (ExpertFinderPublicationResponse publication : result.representativePublications()) {
                PublicationResponse detail = findPublicPublication(publication.id());
                if (detail != null) {
                    candidatesById.putIfAbsent(
                        detail.id(),
                        new PublicationEvidenceCandidate(
                            detail,
                            publication.semanticSimilarity() == null ? result.score() : publication.semanticSimilarity(),
                            "Evidencia pública del buscador de expertos."
                        )
                    );
                }
            }
        }
        List<CopilotRetrievedPublicationResponse> selected = selectPublications(
            new ArrayList<>(candidatesById.values()),
            request.question(),
            query,
            evidenceLimit
        );
        List<PublicationResponse> selectedDetails = selectedPublicationDetails(new ArrayList<>(candidatesById.values()), selected);
        List<String> warnings = new ArrayList<>(expertResponse.warnings());
        if (candidatesById.size() > selected.size()) {
            warnings.add(SUMMARY_WARNING);
            warnings.add(RELEVANT_EVIDENCE_WARNING);
        }
        return new ContextResolution(
            "estos expertos",
            selected,
            mergeResearchers(researchers.stream().limit(evidenceLimit).toList(), researcherEvidenceFromPublications(selectedDetails)),
            mergeUnits(unitEvidenceFromExperts(expertResponse.results()).stream().limit(evidenceLimit).toList(), unitEvidenceFromPublications(selectedDetails)),
            List.of(
                PUBLIC_VISIBILITY_SUMMARY,
                "Contexto: guía de expertos reconstruida con el servicio backend.",
                "Expertos públicos considerados en la respuesta: " + expertResponse.results().size() + ".",
                "Evidencias de publicación usadas: " + selected.size() + "."
            ),
            warnings
        );
    }

    private PublicationCollection collectPublicationDetails(PortalContextAssistantSearchRequest search) {
        Map<Long, PublicationResponse> publicationsById = new LinkedHashMap<>();
        long totalElements = 0;
        int page = 0;
        boolean bounded = false;
        while (publicationsById.size() < MAX_SCOPE_RECORDS) {
            PageResponse<PublicationSummaryResponse> response = publicationService.searchPublicValidated(
                page,
                PAGE_SIZE,
                blankToNull(search.query()),
                search.yearFrom(),
                search.yearTo(),
                search.type(),
                search.status(),
                search.researchUnitId(),
                search.researcherId(),
                blankToNull(search.topic()),
                "year",
                "desc"
            );
            totalElements = response.totalElements();
            for (PublicationSummaryResponse summary : response.content()) {
                PublicationResponse detail = findPublicPublication(summary.id());
                if (detail != null) {
                    publicationsById.putIfAbsent(detail.id(), detail);
                }
            }
            if (response.last() || response.content().isEmpty()) {
                break;
            }
            page++;
        }
        if (totalElements > publicationsById.size()) {
            bounded = true;
        }
        return new PublicationCollection(new ArrayList<>(publicationsById.values()), totalElements, bounded);
    }

    private List<CopilotRetrievedPublicationResponse> selectPublications(
        List<PublicationEvidenceCandidate> candidates,
        String question,
        String query,
        int limit
    ) {
        Set<String> terms = searchTerms(question + " " + (query == null ? "" : query));
        Map<Long, ResearcherResponse> researcherCache = new LinkedHashMap<>();
        return candidates.stream()
            .collect(
                LinkedHashMap<Long, PublicationEvidenceCandidate>::new,
                (map, candidate) -> map.putIfAbsent(candidate.publication().id(), candidate),
                LinkedHashMap::putAll
            )
            .values()
            .stream()
            .sorted(Comparator
                .comparing((PublicationEvidenceCandidate candidate) -> relevanceScore(candidate, terms)).reversed()
                .thenComparing(candidate -> candidate.publication().year() == null ? 0 : candidate.publication().year(), Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.publication().title()))
            .limit(limit)
            .map(candidate -> toRetrievedPublication(candidate, relevanceScore(candidate, terms), researcherCache))
            .toList();
    }

    private double relevanceScore(PublicationEvidenceCandidate candidate, Set<String> terms) {
        double score = candidate.baseScore() == null ? 0.0 : candidate.baseScore();
        String haystack = normalize(String.join(" ",
            candidate.publication().title(),
            nullToBlank(candidate.publication().abstractText()),
            nullToBlank(candidate.publication().publicSummary()),
            nullToBlank(candidate.publication().source()),
            candidate.publication().topics().stream().map(TopicResponse::name).toList().toString(),
            candidate.publication().authors().stream().map(this::authorName).toList().toString(),
            candidate.publication().authors().stream().map(PublicationAuthorResponse::externalAffiliation).toList().toString()
        ));
        for (String term : terms) {
            if (haystack.contains(term)) {
                score += 1.0;
            }
        }
        if (candidate.publication().year() != null) {
            score += Math.max(candidate.publication().year(), 0) / 10000.0;
        }
        return score;
    }

    private CopilotRetrievedPublicationResponse toRetrievedPublication(
        PublicationEvidenceCandidate candidate,
        double relevanceScore,
        Map<Long, ResearcherResponse> researcherCache
    ) {
        return toRetrievedPublication(
            candidate.publication(),
            relevanceScore,
            true,
            false,
            candidate.reason(),
            researcherCache
        );
    }

    private CopilotRetrievedPublicationResponse toRetrievedPublication(
        PublicationResponse publication,
        Double relevanceScore,
        boolean passedThreshold,
        boolean lowSimilarity,
        String reason,
        Map<Long, ResearcherResponse> researcherCache
    ) {
        return new CopilotRetrievedPublicationResponse(
            publication.id(),
            publication.title(),
            firstNonBlank(publication.publicSummary(), publication.abstractText()),
            publication.year(),
            publication.doi(),
            publication.source(),
            publication.url(),
            publication.authors().stream().map(this::authorName).filter(name -> name != null && !name.isBlank()).toList(),
            publicationUnitNames(publication, researcherCache),
            externalAffiliations(publication),
            publication.topics().stream().map(TopicResponse::name).toList(),
            relevanceScore == null ? null : Math.min(relevanceScore, 1.0),
            passedThreshold,
            lowSimilarity,
            reason
        );
    }

    private PortalContextAssistantPublicationEvidenceResponse toPublicationEvidence(CopilotCitationResponse citation) {
        return new PortalContextAssistantPublicationEvidenceResponse(
            citation.id(),
            citation.citationIndex(),
            citation.title(),
            citation.year(),
            citation.authors(),
            citation.topics(),
            citation.doi(),
            citation.source(),
            citation.url(),
            citation.similarityScore(),
            "/portal/publicaciones/" + citation.id()
        );
    }

    private List<PublicationResponse> selectedPublicationDetails(
        List<PublicationEvidenceCandidate> candidates,
        List<CopilotRetrievedPublicationResponse> selected
    ) {
        Map<Long, PublicationResponse> publicationsById = new LinkedHashMap<>();
        for (PublicationEvidenceCandidate candidate : candidates) {
            publicationsById.putIfAbsent(candidate.publication().id(), candidate.publication());
        }
        return selected.stream()
            .map(publication -> publicationsById.get(publication.id()))
            .filter(publication -> publication != null)
            .toList();
    }

    private List<PublicationResponse> publicationDetails(List<CopilotRetrievedPublicationResponse> publications) {
        Map<Long, PublicationResponse> publicationsById = new LinkedHashMap<>();
        for (CopilotRetrievedPublicationResponse publication : publications) {
            PublicationResponse detail = findPublicPublication(publication.id());
            if (detail != null) {
                publicationsById.putIfAbsent(detail.id(), detail);
            }
        }
        return new ArrayList<>(publicationsById.values());
    }

    private List<CopilotRetrievedPublicationResponse> enrichRetrievedPublications(
        List<CopilotRetrievedPublicationResponse> publications,
        List<PublicationResponse> details
    ) {
        Map<Long, PublicationResponse> detailsById = new LinkedHashMap<>();
        for (PublicationResponse detail : details) {
            detailsById.putIfAbsent(detail.id(), detail);
        }
        Map<Long, ResearcherResponse> researcherCache = new LinkedHashMap<>();
        return publications.stream()
            .map(publication -> {
                PublicationResponse detail = detailsById.get(publication.id());
                if (detail == null) {
                    return publication;
                }
                return toRetrievedPublication(
                    detail,
                    publication.similarityScore(),
                    publication.passedThreshold(),
                    publication.lowSimilarity(),
                    publication.retrievalReason(),
                    researcherCache
                );
            })
            .toList();
    }

    private List<PortalContextAssistantResearcherEvidenceResponse> researcherEvidenceFromPublications(List<PublicationResponse> publications) {
        Map<Long, PortalContextAssistantResearcherEvidenceResponse> researchers = new LinkedHashMap<>();
        for (PublicationResponse publication : publications) {
            for (PortalContextAssistantResearcherEvidenceResponse researcher : researcherEvidence(publication)) {
                researchers.putIfAbsent(researcher.id(), researcher);
            }
        }
        return new ArrayList<>(researchers.values());
    }

    private List<PortalContextAssistantUnitEvidenceResponse> unitEvidenceFromPublications(List<PublicationResponse> publications) {
        Map<Long, PortalContextAssistantUnitEvidenceResponse> units = new LinkedHashMap<>();
        for (PublicationResponse publication : publications) {
            for (PortalContextAssistantUnitEvidenceResponse unit : unitEvidenceFromResearchers(publication)) {
                units.putIfAbsent(unit.id(), unit);
            }
        }
        return new ArrayList<>(units.values());
    }

    @SafeVarargs
    private final List<PortalContextAssistantResearcherEvidenceResponse> mergeResearchers(
        List<PortalContextAssistantResearcherEvidenceResponse>... groups
    ) {
        Map<Long, PortalContextAssistantResearcherEvidenceResponse> researchers = new LinkedHashMap<>();
        for (List<PortalContextAssistantResearcherEvidenceResponse> group : groups) {
            for (PortalContextAssistantResearcherEvidenceResponse researcher : group) {
                researchers.putIfAbsent(researcher.id(), researcher);
            }
        }
        return new ArrayList<>(researchers.values());
    }

    @SafeVarargs
    private final List<PortalContextAssistantUnitEvidenceResponse> mergeUnits(
        List<PortalContextAssistantUnitEvidenceResponse>... groups
    ) {
        Map<Long, PortalContextAssistantUnitEvidenceResponse> units = new LinkedHashMap<>();
        for (List<PortalContextAssistantUnitEvidenceResponse> group : groups) {
            for (PortalContextAssistantUnitEvidenceResponse unit : group) {
                units.putIfAbsent(unit.id(), unit);
            }
        }
        return new ArrayList<>(units.values());
    }

    private List<String> publicationUnitNames(
        PublicationResponse publication,
        Map<Long, ResearcherResponse> researcherCache
    ) {
        Map<Long, String> units = new LinkedHashMap<>();
        for (PublicationAuthorResponse author : publication.authors()) {
            ResearcherResponse researcher = publicResearcher(author.researcherId(), researcherCache);
            if (researcher == null) {
                continue;
            }
            for (ResearcherAffiliationResponse affiliation : contextAffiliations(researcher)) {
                if (affiliation.researchUnitId() != null && affiliation.researchUnitName() != null && !affiliation.researchUnitName().isBlank()) {
                    units.putIfAbsent(affiliation.researchUnitId(), affiliation.researchUnitName());
                }
            }
        }
        return new ArrayList<>(units.values());
    }

    private List<String> externalAffiliations(PublicationResponse publication) {
        return distinctNonBlank(publication.authors().stream()
            .map(PublicationAuthorResponse::externalAffiliation)
            .toList());
    }

    private List<ResearcherAffiliationResponse> contextAffiliations(ResearcherResponse researcher) {
        if (researcher.currentAffiliations() != null && !researcher.currentAffiliations().isEmpty()) {
            return researcher.currentAffiliations();
        }
        if (researcher.primaryAffiliation() != null) {
            return List.of(researcher.primaryAffiliation());
        }
        if (researcher.affiliations() != null && !researcher.affiliations().isEmpty()) {
            return researcher.affiliations();
        }
        return List.of();
    }

    private ResearcherResponse publicResearcher(Long researcherId, Map<Long, ResearcherResponse> researcherCache) {
        if (researcherId == null) {
            return null;
        }
        if (researcherCache.containsKey(researcherId)) {
            return researcherCache.get(researcherId);
        }
        try {
            ResearcherResponse researcher = researcherService.findPortalVisibleValidatedById(researcherId);
            if (researcher == null) {
                researcherCache.put(researcherId, null);
                return null;
            }
            researcherCache.put(researcherId, researcher);
            return researcher;
        } catch (ResourceNotFoundException ignored) {
            researcherCache.put(researcherId, null);
            return null;
        }
    }

    private List<PortalContextAssistantResearcherEvidenceResponse> researcherEvidence(PublicationResponse publication) {
        Map<Long, PortalContextAssistantResearcherEvidenceResponse> researchers = new LinkedHashMap<>();
        for (PublicationAuthorResponse author : publication.authors()) {
            if (author.researcherId() == null) {
                continue;
            }
            try {
                ResearcherResponse researcher = researcherService.findPortalVisibleValidatedById(author.researcherId());
                if (researcher == null) {
                    continue;
                }
                researchers.putIfAbsent(researcher.id(), researcherEvidence(researcher));
            } catch (ResourceNotFoundException ignored) {
                // Public context ignores internal authors without a public validated profile.
            }
        }
        return new ArrayList<>(researchers.values());
    }

    private PortalContextAssistantResearcherEvidenceResponse researcherEvidence(ResearcherResponse researcher) {
        return new PortalContextAssistantResearcherEvidenceResponse(
            researcher.id(),
            researcher.displayName() == null || researcher.displayName().isBlank() ? researcher.fullName() : researcher.displayName(),
            researcher.primaryAffiliation() == null ? null : researcher.primaryAffiliation().researchUnitName(),
            "/portal/investigadores/" + researcher.id()
        );
    }

    private List<PortalContextAssistantUnitEvidenceResponse> unitEvidenceFromResearchers(PublicationResponse publication) {
        Map<Long, PortalContextAssistantUnitEvidenceResponse> units = new LinkedHashMap<>();
        Map<Long, ResearcherResponse> researcherCache = new LinkedHashMap<>();
        for (PublicationAuthorResponse author : publication.authors()) {
            ResearcherResponse researcher = publicResearcher(author.researcherId(), researcherCache);
            if (researcher == null) {
                continue;
            }
            for (ResearcherAffiliationResponse affiliation : contextAffiliations(researcher)) {
                for (PortalContextAssistantUnitEvidenceResponse unit : unitEvidence(affiliation)) {
                    units.putIfAbsent(unit.id(), unit);
                }
            }
        }
        return new ArrayList<>(units.values());
    }

    private List<PortalContextAssistantUnitEvidenceResponse> unitEvidence(ResearcherAffiliationResponse affiliation) {
        if (affiliation == null || affiliation.researchUnitId() == null) {
            return List.of();
        }
        try {
            ResearchUnitResponse unit = researchUnitService.findPortalVisibleValidatedById(affiliation.researchUnitId());
            return List.of(unitEvidence(unit));
        } catch (ResourceNotFoundException ignored) {
            return List.of();
        }
    }

    private PortalContextAssistantUnitEvidenceResponse unitEvidence(ResearchUnitResponse unit) {
        return new PortalContextAssistantUnitEvidenceResponse(
            unit.id(),
            unit.name(),
            unit.type() == null ? null : unit.type().name(),
            "/portal/unidades/" + unit.id()
        );
    }

    private List<PortalContextAssistantUnitEvidenceResponse> unitEvidenceFromExperts(List<ExpertFinderResultResponse> experts) {
        Map<Long, PortalContextAssistantUnitEvidenceResponse> units = new LinkedHashMap<>();
        for (ExpertFinderResultResponse expert : experts) {
            Long unitId = expert.researcher().primaryResearchUnitId();
            if (unitId == null) {
                continue;
            }
            try {
                ResearchUnitResponse unit = researchUnitService.findPortalVisibleValidatedById(unitId);
                units.putIfAbsent(unit.id(), unitEvidence(unit));
            } catch (ResourceNotFoundException ignored) {
                // Expert finder can rank researchers without a public unit link.
            }
        }
        return new ArrayList<>(units.values());
    }

    private PortalContextAssistantResponse unavailableResponse(ContextResolution resolution, RuntimeException exception) {
        List<String> warnings = mergedWarnings(
            resolution.warnings(),
            List.of("No se ha podido generar la respuesta. Revisa la disponibilidad del proveedor de IA local.")
        );
        String provider = "unavailable";
        String model = "unavailable";
        if (exception instanceof BusinessRuleException) {
            warnings = mergedWarnings(warnings, List.of(exception.getMessage()));
        }
        return new PortalContextAssistantResponse(
            "No se ha podido generar la respuesta con el proveedor de IA configurado.",
            List.of(),
            List.of(),
            List.of(),
            resolution.evidenceSummary(),
            warnings,
            provider,
            model,
            VisibilityScope.PUBLIC_VALIDATED.name(),
            true
        );
    }

    private List<String> boundedWarnings(long totalElements, boolean bounded, int selectedSize) {
        List<String> warnings = new ArrayList<>();
        if (totalElements == 0) {
            warnings.add("No se han encontrado evidencias públicas suficientes para este contexto.");
        }
        if (bounded || totalElements > selectedSize) {
            warnings.add(SUMMARY_WARNING);
            warnings.add(RELEVANT_EVIDENCE_WARNING);
        }
        return warnings;
    }

    private List<String> mergedWarnings(List<String> first, List<String> second) {
        Set<String> warnings = new LinkedHashSet<>();
        if (first != null) {
            warnings.addAll(first.stream().filter(value -> value != null && !value.isBlank()).toList());
        }
        if (second != null) {
            warnings.addAll(second.stream().filter(value -> value != null && !value.isBlank()).toList());
        }
        return new ArrayList<>(warnings);
    }

    private String scopedQuestion(PortalContextAssistantRequest request, ContextResolution resolution) {
        return """
            Responde en español usando solo el contexto público validado de %s.
            No inventes datos. Si el contexto no alcanza, dilo de forma clara.
            Devuelve Markdown simple, nunca HTML.
            Cita cada publicación usada con su marcador individual exacto [pub:ID] justo después de la frase respaldada.
            No agrupes varios IDs dentro de un mismo corchete: usa [pub:1] [pub:2], no [pub:1, pub:2].
            Si mencionas una publicación concreta por título o por resultado, debe aparecer su marcador [pub:ID].
            Estructura la respuesta con un título breve y 2-4 secciones claras, usando listas solo cuando comparen opciones o hitos.
            Evita responder en un único párrafo largo.
            No uses negrita para simular títulos; escribe títulos en líneas separadas.
            Si mencionas investigadores o unidades, usa exactamente estos nombres cuando proceda:
            Investigadores: %s
            Unidades: %s

            Pregunta: %s
            """.formatted(
            resolution.contextTitle(),
            namedReferences(resolution.researchers().stream().map(PortalContextAssistantResearcherEvidenceResponse::name).toList()),
            namedReferences(resolution.units().stream().map(PortalContextAssistantUnitEvidenceResponse::name).toList()),
            request.question()
        );
    }

    private List<PortalContextAssistantResearcherEvidenceResponse> mentionedResearchers(
        String answer,
        List<PortalContextAssistantResearcherEvidenceResponse> researchers
    ) {
        return researchers.stream()
            .filter(researcher -> isMentioned(answer, researcher.name()))
            .toList();
    }

    private List<PortalContextAssistantUnitEvidenceResponse> mentionedUnits(
        String answer,
        List<PortalContextAssistantUnitEvidenceResponse> units
    ) {
        return units.stream()
            .filter(unit -> isMentioned(answer, unit.name()))
            .toList();
    }

    private boolean isMentioned(String answer, String label) {
        String normalizedLabel = normalize(label).trim();
        return normalizedLabel.length() >= 3 && normalize(answer).contains(normalizedLabel);
    }

    private String namedReferences(List<String> names) {
        List<String> visibleNames = names.stream()
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .limit(20)
            .toList();
        return visibleNames.isEmpty() ? "sin referencias directas" : String.join(", ", visibleNames);
    }

    private List<String> distinctNonBlank(List<String> values) {
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }

    private Long requiredTargetId(PortalContextAssistantRequest request) {
        if (request.targetId() == null) {
            throw new BusinessRuleException("targetId is required for context scope " + request.contextScope() + ".");
        }
        return request.targetId();
    }

    private int evidenceLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_EVIDENCE_LIMIT;
        }
        return Math.min(Math.max(requestedLimit, 1), MAX_EVIDENCE_LIMIT);
    }

    private PublicationResponse findPublicPublication(Long publicationId) {
        try {
            return publicationService.findPublicValidatedById(publicationId);
        } catch (ResourceNotFoundException ignored) {
            return null;
        }
    }

    private PortalContextAssistantSearchRequest safeSearch(PortalContextAssistantSearchRequest search) {
        if (search == null) {
            return new PortalContextAssistantSearchRequest(null, null, null, null, null, null, null, null, null);
        }
        return search;
    }

    private boolean isSemanticMode(String mode) {
        String normalized = normalize(mode);
        return normalized.equals("semantic") || normalized.equals("semantica") || normalized.equals("intelligent") || normalized.equals("inteligente");
    }

    private boolean hasStructuredFilters(PortalContextAssistantSearchRequest search) {
        return search.yearFrom() != null
            || search.yearTo() != null
            || search.type() != null
            || search.status() != null
            || search.researchUnitId() != null
            || search.researcherId() != null
            || blankToNull(search.topic()) != null;
    }

    private RetrievalMode retrievalMode(String mode) {
        String normalized = normalize(mode);
        if (normalized.equals("strict")) {
            return RetrievalMode.STRICT;
        }
        if (normalized.equals("broad")) {
            return RetrievalMode.BROAD;
        }
        return RetrievalMode.BALANCED;
    }

    private Set<String> searchTerms(String value) {
        Set<String> terms = new LinkedHashSet<>();
        for (String term : normalize(value).split("[^\\p{Alnum}]+")) {
            if (term.length() >= 4 || term.equals("ia")) {
                terms.add(term);
            }
        }
        return terms;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private String authorName(PublicationAuthorResponse author) {
        if (author.researcherName() != null && !author.researcherName().isBlank()) {
            return author.researcherName();
        }
        return author.externalAuthorName();
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = blankToNull(first);
        return normalizedFirst == null ? second : normalizedFirst;
    }

    private String blankToDefault(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private record ContextResolution(
        String contextTitle,
        List<CopilotRetrievedPublicationResponse> publications,
        List<PortalContextAssistantResearcherEvidenceResponse> researchers,
        List<PortalContextAssistantUnitEvidenceResponse> units,
        List<String> evidenceSummary,
        List<String> warnings
    ) {
    }

    private record PublicationEvidenceCandidate(
        PublicationResponse publication,
        Double baseScore,
        String reason
    ) {
    }

    private record PublicationCollection(
        List<PublicationResponse> publications,
        long totalElements,
        boolean bounded
    ) {
    }
}
