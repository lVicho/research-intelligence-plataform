package com.researchintelligence.platform.publications.persistence;

import com.researchintelligence.platform.publications.domain.PublicationStatus;
import com.researchintelligence.platform.publications.domain.PublicationType;
import com.researchintelligence.platform.researchers.persistence.ResearcherAffiliationEntity;
import com.researchintelligence.platform.researchers.persistence.ResearcherEntity;
import com.researchintelligence.platform.researchunits.persistence.ResearchUnitEntity;
import com.researchintelligence.platform.shared.visibility.ValidableVisibilitySpecifications;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class PublicationSpecifications {

    private PublicationSpecifications() {
    }

    public static Specification<PublicationEntity> hasId(Long id) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("id"), id);
    }

    public static Specification<PublicationEntity> hasIdIn(List<Long> ids) {
        return (root, query, criteriaBuilder) -> ids == null || ids.isEmpty()
            ? criteriaBuilder.disjunction()
            : root.get("id").in(ids);
    }

    public static Specification<PublicationEntity> visibleTo(VisibilityScope scope, Long linkedResearcherId) {
        return ValidableVisibilitySpecifications.visibleTo(scope, linkedResearcherId, PublicationSpecifications::authoredByResearcher);
    }

    public static Specification<PublicationEntity> matches(
        String text,
        String textPattern,
        Integer yearFrom,
        Integer yearTo,
        PublicationType type,
        PublicationStatus status,
        Long researchUnitId,
        Long researcherId,
        String topic,
        String topicPattern
    ) {
        return matches(
            text,
            textPattern,
            yearFrom,
            yearTo,
            type,
            status,
            researchUnitId,
            researcherId,
            topic,
            topicPattern,
            null
        );
    }

    public static Specification<PublicationEntity> matches(
        String text,
        String textPattern,
        Integer yearFrom,
        Integer yearTo,
        PublicationType type,
        PublicationStatus status,
        Long researchUnitId,
        Long researcherId,
        String topic,
        String topicPattern,
        ValidationStatus relationshipValidationStatus
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (text != null) {
                predicates.add(textMatches(root, criteriaBuilder, textPattern));
            }
            if (yearFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("publicationYear"), yearFrom));
            }
            if (yearTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("publicationYear"), yearTo));
            }
            if (type != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (researcherId != null) {
                predicates.add(authoredByResearcher(root, query, criteriaBuilder, researcherId, relationshipValidationStatus));
            }
            if (researchUnitId != null) {
                predicates.add(hasCurrentAffiliationInResearchUnit(root, query, criteriaBuilder, researchUnitId, relationshipValidationStatus));
            }
            if (topic != null) {
                predicates.add(hasTopic(root, query, criteriaBuilder, topic, topicPattern));
            }
            return predicates.isEmpty() ? criteriaBuilder.conjunction() : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate textMatches(Root<PublicationEntity> root, CriteriaBuilder criteriaBuilder, String textPattern) {
        return criteriaBuilder.or(
            criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), textPattern),
            criteriaBuilder.like(lowerCoalesced(root, criteriaBuilder, "abstractText"), textPattern),
            criteriaBuilder.like(lowerCoalesced(root, criteriaBuilder, "publicSummary"), textPattern),
            criteriaBuilder.like(lowerCoalesced(root, criteriaBuilder, "source"), textPattern),
            criteriaBuilder.like(lowerCoalesced(root, criteriaBuilder, "sourceDetail"), textPattern),
            criteriaBuilder.like(lowerCoalesced(root, criteriaBuilder, "doi"), textPattern),
            criteriaBuilder.like(lowerCoalesced(root, criteriaBuilder, "isbn"), textPattern),
            criteriaBuilder.like(lowerCoalesced(root, criteriaBuilder, "issn"), textPattern),
            criteriaBuilder.like(lowerCoalesced(root, criteriaBuilder, "languageCode"), textPattern)
        );
    }

    private static Expression<String> lowerCoalesced(
        Root<PublicationEntity> root,
        CriteriaBuilder criteriaBuilder,
        String attribute
    ) {
        return criteriaBuilder.lower(criteriaBuilder.coalesce(root.<String>get(attribute), ""));
    }

    private static Predicate authoredByResearcher(
        Root<PublicationEntity> root,
        CriteriaQuery<?> query,
        CriteriaBuilder criteriaBuilder,
        Long researcherId
    ) {
        return authoredByResearcher(root, query, criteriaBuilder, researcherId, null);
    }

    private static Predicate authoredByResearcher(
        Root<PublicationEntity> root,
        CriteriaQuery<?> query,
        CriteriaBuilder criteriaBuilder,
        Long researcherId,
        ValidationStatus relationshipValidationStatus
    ) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<PublicationAuthorEntity> author = subquery.from(PublicationAuthorEntity.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(author.get("publicationId"), root.get("id")));
        predicates.add(criteriaBuilder.equal(author.get("researcherId"), researcherId));
        if (relationshipValidationStatus != null) {
            Root<ResearcherEntity> researcher = subquery.from(ResearcherEntity.class);
            predicates.add(criteriaBuilder.equal(researcher.get("id"), author.get("researcherId")));
            predicates.add(criteriaBuilder.equal(researcher.get("validationStatus"), relationshipValidationStatus));
        }
        subquery.select(author.get("id"));
        subquery.where(predicates.toArray(new Predicate[0]));
        return criteriaBuilder.exists(subquery);
    }

    private static Predicate hasCurrentAffiliationInResearchUnit(
        Root<PublicationEntity> root,
        CriteriaQuery<?> query,
        CriteriaBuilder criteriaBuilder,
        Long researchUnitId
    ) {
        return hasCurrentAffiliationInResearchUnit(root, query, criteriaBuilder, researchUnitId, null);
    }

    private static Predicate hasCurrentAffiliationInResearchUnit(
        Root<PublicationEntity> root,
        CriteriaQuery<?> query,
        CriteriaBuilder criteriaBuilder,
        Long researchUnitId,
        ValidationStatus relationshipValidationStatus
    ) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<PublicationAuthorEntity> author = subquery.from(PublicationAuthorEntity.class);
        Root<ResearcherAffiliationEntity> affiliation = subquery.from(ResearcherAffiliationEntity.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(author.get("publicationId"), root.get("id")));
        predicates.add(criteriaBuilder.isNotNull(author.get("researcherId")));
        predicates.add(criteriaBuilder.equal(affiliation.get("researcherId"), author.get("researcherId")));
        predicates.add(criteriaBuilder.equal(affiliation.get("researchUnitId"), researchUnitId));
        predicates.add(criteriaBuilder.or(
            criteriaBuilder.isNull(affiliation.get("endDate")),
            criteriaBuilder.greaterThanOrEqualTo(affiliation.get("endDate"), LocalDate.now())
        ));
        if (relationshipValidationStatus != null) {
            Root<ResearcherEntity> researcher = subquery.from(ResearcherEntity.class);
            Root<ResearchUnitEntity> researchUnit = subquery.from(ResearchUnitEntity.class);
            predicates.add(criteriaBuilder.equal(affiliation.get("validationStatus"), relationshipValidationStatus));
            predicates.add(criteriaBuilder.equal(researcher.get("id"), author.get("researcherId")));
            predicates.add(criteriaBuilder.equal(researcher.get("validationStatus"), relationshipValidationStatus));
            predicates.add(criteriaBuilder.equal(researchUnit.get("id"), affiliation.get("researchUnitId")));
            predicates.add(criteriaBuilder.equal(researchUnit.get("validationStatus"), relationshipValidationStatus));
        }
        subquery.select(author.get("id"));
        subquery.where(predicates.toArray(new Predicate[0]));
        return criteriaBuilder.exists(subquery);
    }

    private static Predicate hasTopic(
        Root<PublicationEntity> root,
        CriteriaQuery<?> query,
        CriteriaBuilder criteriaBuilder,
        String topic,
        String topicPattern
    ) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<PublicationTopicEntity> publicationTopic = subquery.from(PublicationTopicEntity.class);
        Root<TopicEntity> topicEntity = subquery.from(TopicEntity.class);
        subquery.select(publicationTopic.get("publicationId"));
        subquery.where(
            criteriaBuilder.equal(publicationTopic.get("publicationId"), root.get("id")),
            criteriaBuilder.equal(topicEntity.get("id"), publicationTopic.get("topicId")),
            criteriaBuilder.or(
                criteriaBuilder.equal(criteriaBuilder.lower(topicEntity.get("normalizedName")), topic),
                criteriaBuilder.like(criteriaBuilder.lower(topicEntity.get("name")), topicPattern)
            )
        );
        return criteriaBuilder.exists(subquery);
    }
}
