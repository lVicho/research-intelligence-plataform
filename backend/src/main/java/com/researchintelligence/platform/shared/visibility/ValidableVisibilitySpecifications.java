package com.researchintelligence.platform.shared.visibility;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

public final class ValidableVisibilitySpecifications {

    private static final String DEFAULT_VALIDATION_STATUS_ATTRIBUTE = "validationStatus";

    private ValidableVisibilitySpecifications() {
    }

    @FunctionalInterface
    public interface OwnerPredicateBuilder<T> {
        Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder, Long researcherId);
    }

    public static <T> Specification<T> publicValidated() {
        return publicValidated(DEFAULT_VALIDATION_STATUS_ATTRIBUTE);
    }

    public static <T> Specification<T> publicValidated(String validationStatusAttribute) {
        return (root, query, criteriaBuilder) -> publicValidatedPredicate(root, criteriaBuilder, validationStatusAttribute);
    }

    public static <T> Specification<T> visibleTo(
        VisibilityScope scope,
        Long linkedResearcherId,
        OwnerPredicateBuilder<T> ownerPredicateBuilder
    ) {
        return visibleTo(scope, linkedResearcherId, ownerPredicateBuilder, DEFAULT_VALIDATION_STATUS_ATTRIBUTE);
    }

    public static <T> Specification<T> visibleTo(
        VisibilityScope scope,
        Long linkedResearcherId,
        OwnerPredicateBuilder<T> ownerPredicateBuilder,
        String validationStatusAttribute
    ) {
        return (root, query, criteriaBuilder) -> {
            VisibilityScope effectiveScope = scope == null ? VisibilityScope.PUBLIC_VALIDATED : scope;
            return switch (effectiveScope) {
                case ADMIN_ALL -> criteriaBuilder.conjunction();
                case MY_DATA -> {
                    Predicate publicValidated = publicValidatedPredicate(root, criteriaBuilder, validationStatusAttribute);
                    if (linkedResearcherId == null || ownerPredicateBuilder == null) {
                        yield publicValidated;
                    }
                    yield criteriaBuilder.or(publicValidated, ownerPredicateBuilder.toPredicate(root, query, criteriaBuilder, linkedResearcherId));
                }
                case PUBLIC_VALIDATED -> publicValidatedPredicate(root, criteriaBuilder, validationStatusAttribute);
            };
        };
    }

    private static <T> Predicate publicValidatedPredicate(
        Root<T> root,
        CriteriaBuilder criteriaBuilder,
        String validationStatusAttribute
    ) {
        return criteriaBuilder.equal(root.<ValidationStatus>get(validationStatusAttribute), ValidationStatus.VALIDATED);
    }
}
