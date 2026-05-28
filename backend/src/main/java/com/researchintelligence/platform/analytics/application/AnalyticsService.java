package com.researchintelligence.platform.analytics.application;

import com.researchintelligence.platform.analytics.api.AnalyticsSummaryResponse;
import com.researchintelligence.platform.analytics.api.CollaborationPairResponse;
import com.researchintelligence.platform.analytics.api.CollaborationsResponse;
import com.researchintelligence.platform.analytics.api.InstitutionalOverviewResponse;
import com.researchintelligence.platform.analytics.api.NamedCountResponse;
import com.researchintelligence.platform.analytics.api.ResearcherPublicationCountResponse;
import com.researchintelligence.platform.analytics.api.TopicTrendResponse;
import com.researchintelligence.platform.analytics.api.TopicTrendsResponse;
import com.researchintelligence.platform.analytics.api.YearCountResponse;
import com.researchintelligence.platform.dataquality.application.DataQualityService;
import com.researchintelligence.platform.events.persistence.EventParticipationRepository;
import com.researchintelligence.platform.publications.api.PublicationSummaryResponse;
import com.researchintelligence.platform.publications.persistence.PublicationEntity;
import com.researchintelligence.platform.publications.persistence.PublicationRepository;
import com.researchintelligence.platform.publications.persistence.PublicationTopicEntity;
import com.researchintelligence.platform.publications.persistence.PublicationTopicRepository;
import com.researchintelligence.platform.publications.persistence.TopicEntity;
import com.researchintelligence.platform.publications.persistence.TopicRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationRepository;
import com.researchintelligence.platform.researchers.persistence.ResearcherRepository;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitRepository;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final int DEFAULT_TREND_WINDOW_YEARS = 3;
    private static final int DEFAULT_TOPICS_LIMIT = 10;
    private static final int DEFAULT_COLLABORATION_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_WINDOW_YEARS = 10;

    private final ResearchUnitRepository researchUnitRepository;
    private final ResearcherRepository researcherRepository;
    private final ResearcherAffiliationRepository affiliationRepository;
    private final PublicationRepository publicationRepository;
    private final TopicRepository topicRepository;
    private final PublicationTopicRepository publicationTopicRepository;
    private final EventParticipationRepository eventParticipationRepository;
    private final DataQualityService dataQualityService;

    public AnalyticsService(
        ResearchUnitRepository researchUnitRepository,
        ResearcherRepository researcherRepository,
        ResearcherAffiliationRepository affiliationRepository,
        PublicationRepository publicationRepository,
        TopicRepository topicRepository,
        PublicationTopicRepository publicationTopicRepository,
        EventParticipationRepository eventParticipationRepository,
        DataQualityService dataQualityService
    ) {
        this.researchUnitRepository = researchUnitRepository;
        this.researcherRepository = researcherRepository;
        this.affiliationRepository = affiliationRepository;
        this.publicationRepository = publicationRepository;
        this.topicRepository = topicRepository;
        this.publicationTopicRepository = publicationTopicRepository;
        this.eventParticipationRepository = eventParticipationRepository;
        this.dataQualityService = dataQualityService;
    }

    public AnalyticsSummaryResponse summary() {
        return summary(null, null);
    }

    public AnalyticsSummaryResponse summary(ValidationStatus validationStatus, Boolean includeNonValidated) {
        ValidationStatus effectiveValidationStatus = effectiveValidationStatus(validationStatus, includeNonValidated);
        List<PublicationEntity> recentPublications = publicationRepository.findRecentByValidationStatus(
            effectiveValidationStatus,
            PageRequest.of(0, 5)
        );
        Map<Long, List<String>> topicsByPublicationId = topicNamesByPublicationId(recentPublications.stream()
            .map(PublicationEntity::getId)
            .toList());
        return new AnalyticsSummaryResponse(
            totalResearchUnits(effectiveValidationStatus),
            totalResearchers(effectiveValidationStatus),
            activeResearchers(effectiveValidationStatus),
            totalPublications(effectiveValidationStatus),
            publicationRepository.countPublicationsByYear(effectiveValidationStatus).stream()
                .map(row -> new YearCountResponse((Integer) row[0], (Long) row[1]))
                .toList(),
            namedCountRows(publicationRepository.countPublicationsByType(effectiveValidationStatus), false),
            namedCountRows(publicationRepository.countPublicationsByStatus(effectiveValidationStatus), false),
            namedCountRows(publicationRepository.countPublicationsByResearchUnit(effectiveValidationStatus), true),
            publicationRepository.findTopResearchersByPublicationCount(effectiveValidationStatus, PageRequest.of(0, 10)).stream()
                .map(row -> new ResearcherPublicationCountResponse((Long) row[0], (String) row[1], (Long) row[2]))
                .toList(),
            namedCountRows(publicationRepository.findTopTopicsByPublicationCount(effectiveValidationStatus, PageRequest.of(0, 10)), true),
            recentPublications.stream()
                .map(publication -> new PublicationSummaryResponse(
                    publication.getId(),
                    publication.getTitle(),
                    publication.getPublicationYear(),
                    publication.getType(),
                    publication.getStatus(),
                    publication.getDoi(),
                    publication.getSource(),
                    publication.getVenueId(),
                    publication.getPublisherId(),
                    publication.getIsbn(),
                    publication.getIssn(),
                    publication.getLanguageCode(),
                    publication.getValidationStatus(),
                    publication.getValidationComment(),
                    publication.getCreatedAt(),
                    null,
                    publication.getValidatedAt(),
                    null,
                    false,
                    false,
                    false,
                    publication.getCreatedAt(),
                    topicsByPublicationId.getOrDefault(publication.getId(), List.of())
                ))
                .toList(),
            namedCountRows(affiliationRepository.countCurrentResearchersByResearchUnitType(effectiveValidationStatus, LocalDate.now()), false)
        );
    }

    public InstitutionalOverviewResponse institutionalOverview(ValidationStatus validationStatus, Boolean includeNonValidated) {
        ValidationStatus effectiveValidationStatus = effectiveValidationStatus(validationStatus, includeNonValidated);
        TopicTrendsResponse trends = topicTrends(
            validationStatus,
            includeNonValidated,
            DEFAULT_TREND_WINDOW_YEARS,
            DEFAULT_TOPICS_LIMIT
        );
        CollaborationsResponse collaborations = collaborations(validationStatus, includeNonValidated, DEFAULT_COLLABORATION_LIMIT);
        return new InstitutionalOverviewResponse(
            publicationRepository.countByValidationStatus(ValidationStatus.VALIDATED),
            totalResearchers(effectiveValidationStatus),
            totalResearchUnits(effectiveValidationStatus),
            publicationRepository.countPublicationsByYear(effectiveValidationStatus).stream()
                .map(row -> new YearCountResponse((Integer) row[0], (Long) row[1]))
                .toList(),
            namedCountRows(publicationRepository.countPublicationsByType(effectiveValidationStatus), false),
            namedCountRows(publicationRepository.countPublicationsByStatus(effectiveValidationStatus), false),
            namedCountRows(eventParticipationRepository.countActivitiesByValidationStatus(), false),
            namedCountRows(publicationRepository.countPublicationsByResearchUnit(effectiveValidationStatus), true),
            namedCountRows(publicationRepository.countPublicationsByTopic(effectiveValidationStatus), true),
            namedCountRows(affiliationRepository.countActiveResearchersByResearchUnit(effectiveValidationStatus, LocalDate.now()), true),
            trends.topTopics(),
            trends.emergingTopics(),
            collaborations.collaborationPairs(),
            collaborations.crossUnitCollaborations(),
            dataQualityService.summary()
        );
    }

    public TopicTrendsResponse topicTrends(
        ValidationStatus validationStatus,
        Boolean includeNonValidated,
        Integer recentWindowYears,
        Integer limit
    ) {
        ValidationStatus effectiveValidationStatus = effectiveValidationStatus(validationStatus, includeNonValidated);
        int safeWindowYears = safeWindowYears(recentWindowYears);
        int safeLimit = safeLimit(limit, DEFAULT_TOPICS_LIMIT);
        List<NamedCountResponse> topTopics = namedCountRows(
            publicationRepository.findTopTopicsByPublicationCount(effectiveValidationStatus, PageRequest.of(0, safeLimit)),
            true
        );

        Integer latestYear = publicationRepository.findMaxPublicationYear(effectiveValidationStatus);
        if (latestYear == null) {
            return new TopicTrendsResponse(null, null, null, null, topTopics, List.of());
        }

        int recentStartYear = latestYear - safeWindowYears + 1;
        int previousEndYear = recentStartYear - 1;
        int previousStartYear = previousEndYear - safeWindowYears + 1;
        Map<Long, TopicCount> recentCounts = topicCountById(
            publicationRepository.countPublicationsByTopicInYearRange(effectiveValidationStatus, recentStartYear, latestYear)
        );
        Map<Long, TopicCount> previousCounts = previousEndYear < 1500
            ? Map.of()
            : topicCountById(publicationRepository.countPublicationsByTopicInYearRange(
                effectiveValidationStatus,
                previousStartYear,
                previousEndYear
            ));

        List<TopicTrendResponse> emergingTopics = recentCounts.values()
            .stream()
            .map(recent -> toTopicTrend(recent, previousCounts))
            .filter(trend -> trend.delta() > 0)
            .sorted(Comparator
                .comparingLong(TopicTrendResponse::delta).reversed()
                .thenComparing(TopicTrendResponse::growthRate, Comparator.reverseOrder())
                .thenComparing(TopicTrendResponse::recentPublicationCount, Comparator.reverseOrder())
                .thenComparing(TopicTrendResponse::topicName))
            .limit(safeLimit)
            .toList();

        return new TopicTrendsResponse(
            latestYear,
            recentStartYear,
            previousStartYear,
            previousEndYear,
            topTopics,
            emergingTopics
        );
    }

    public CollaborationsResponse collaborations(
        ValidationStatus validationStatus,
        Boolean includeNonValidated,
        Integer limit
    ) {
        ValidationStatus effectiveValidationStatus = effectiveValidationStatus(validationStatus, includeNonValidated);
        int safeLimit = safeLimit(limit, DEFAULT_COLLABORATION_LIMIT);
        String validationStatusName = effectiveValidationStatus == null ? null : effectiveValidationStatus.name();
        List<CollaborationPairResponse> pairs = publicationRepository.findCollaborationPairs(
            validationStatusName,
            PageRequest.of(0, safeLimit)
        ).stream()
            .map(this::toCollaborationPair)
            .toList();
        long crossUnitCollaborations = publicationRepository.countCrossUnitCollaborations(validationStatusName);
        return new CollaborationsResponse(crossUnitCollaborations, pairs);
    }

    private ValidationStatus effectiveValidationStatus(ValidationStatus validationStatus, Boolean includeNonValidated) {
        if (validationStatus != null) {
            return validationStatus;
        }
        if (Boolean.TRUE.equals(includeNonValidated)) {
            return null;
        }
        return ValidationStatus.VALIDATED;
    }

    private long totalResearchUnits(ValidationStatus validationStatus) {
        return validationStatus == null ? researchUnitRepository.count() : researchUnitRepository.countByValidationStatus(validationStatus);
    }

    private long totalResearchers(ValidationStatus validationStatus) {
        return validationStatus == null ? researcherRepository.count() : researcherRepository.countByValidationStatus(validationStatus);
    }

    private long activeResearchers(ValidationStatus validationStatus) {
        return validationStatus == null ? researcherRepository.countByActiveTrue() : researcherRepository.countByActiveTrueAndValidationStatus(validationStatus);
    }

    private long totalPublications(ValidationStatus validationStatus) {
        return validationStatus == null ? publicationRepository.count() : publicationRepository.countByValidationStatus(validationStatus);
    }

    private int safeLimit(Integer requestedLimit, int defaultValue) {
        if (requestedLimit == null) {
            return defaultValue;
        }
        return Math.min(Math.max(requestedLimit, 1), MAX_LIMIT);
    }

    private int safeWindowYears(Integer requestedWindowYears) {
        if (requestedWindowYears == null) {
            return DEFAULT_TREND_WINDOW_YEARS;
        }
        return Math.min(Math.max(requestedWindowYears, 1), MAX_WINDOW_YEARS);
    }

    private List<NamedCountResponse> namedCountRows(List<Object[]> rows, boolean hasId) {
        return rows.stream()
            .map(row -> {
                Long id = hasId ? (Long) row[0] : null;
                String name = String.valueOf(hasId ? row[1] : row[0]);
                long count = (Long) (hasId ? row[2] : row[1]);
                return new NamedCountResponse(id, name, count);
            })
            .toList();
    }

    private Map<Long, TopicCount> topicCountById(List<Object[]> rows) {
        return rows.stream()
            .map(this::toTopicCount)
            .collect(Collectors.toMap(
                TopicCount::id,
                Function.identity(),
                (first, second) -> first
            ));
    }

    private TopicCount toTopicCount(Object[] row) {
        Long topicId = ((Number) row[0]).longValue();
        String topicName = (String) row[1];
        long publicationCount = ((Number) row[2]).longValue();
        return new TopicCount(topicId, topicName, publicationCount);
    }

    private TopicTrendResponse toTopicTrend(TopicCount recent, Map<Long, TopicCount> previousCounts) {
        long previousCount = previousCounts.containsKey(recent.id()) ? previousCounts.get(recent.id()).count() : 0L;
        long delta = recent.count() - previousCount;
        double growthRate = previousCount == 0L ? (recent.count() > 0 ? 1.0 : 0.0) : ((double) delta / previousCount);
        return new TopicTrendResponse(
            recent.id(),
            recent.name(),
            recent.count(),
            previousCount,
            delta,
            round(growthRate)
        );
    }

    private CollaborationPairResponse toCollaborationPair(Object[] row) {
        return new CollaborationPairResponse(
            ((Number) row[0]).longValue(),
            String.valueOf(row[1]),
            ((Number) row[2]).longValue(),
            String.valueOf(row[3]),
            ((Number) row[4]).longValue()
        );
    }

    private Map<Long, List<String>> topicNamesByPublicationId(Collection<Long> publicationIds) {
        if (publicationIds.isEmpty()) {
            return Map.of();
        }
        List<PublicationTopicEntity> links = publicationTopicRepository.findByPublicationIdIn(publicationIds);
        Map<Long, TopicEntity> topicsById = topicRepository.findAllById(links.stream().map(PublicationTopicEntity::getTopicId).toList())
            .stream()
            .collect(Collectors.toMap(TopicEntity::getId, Function.identity()));

        return links.stream()
            .filter(link -> topicsById.containsKey(link.getTopicId()))
            .collect(Collectors.groupingBy(
                PublicationTopicEntity::getPublicationId,
                Collectors.mapping(link -> topicsById.get(link.getTopicId()).getName(), Collectors.toList())
            ));
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private record TopicCount(Long id, String name, long count) {
    }
}
