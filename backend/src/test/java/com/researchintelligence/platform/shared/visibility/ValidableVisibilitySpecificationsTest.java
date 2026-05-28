package com.researchintelligence.platform.shared.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchintelligence.platform.validation.domain.ValidationStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ValidableVisibilitySpecificationsTest {

    @Mock
    private Root<Object> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder criteriaBuilder;

    @Mock
    private Path<ValidationStatus> validationStatusPath;

    @Mock
    private Predicate validatedPredicate;

    @Mock
    private Predicate ownerPredicate;

    @Mock
    private Predicate combinedPredicate;

    @Mock
    private Predicate allPredicate;

    @Test
    void publicScopeRestrictsToValidatedRecordsOnly() {
        when(root.<ValidationStatus>get("validationStatus")).thenReturn(validationStatusPath);
        when(criteriaBuilder.equal(validationStatusPath, ValidationStatus.VALIDATED)).thenReturn(validatedPredicate);

        Specification<Object> specification = ValidableVisibilitySpecifications.visibleTo(
            VisibilityScope.PUBLIC_VALIDATED,
            null,
            unusedOwnerPredicate()
        );

        assertSame(validatedPredicate, specification.toPredicate(root, query, criteriaBuilder));
    }

    @Test
    void researcherScopeCombinesValidatedRecordsWithOwnedRecords() {
        AtomicReference<Long> ownerResearcherId = new AtomicReference<>();
        when(root.<ValidationStatus>get("validationStatus")).thenReturn(validationStatusPath);
        when(criteriaBuilder.equal(validationStatusPath, ValidationStatus.VALIDATED)).thenReturn(validatedPredicate);
        when(criteriaBuilder.or(validatedPredicate, ownerPredicate)).thenReturn(combinedPredicate);

        Specification<Object> specification = ValidableVisibilitySpecifications.visibleTo(
            VisibilityScope.MY_DATA,
            42L,
            (root, query, criteriaBuilder, researcherId) -> {
                ownerResearcherId.set(researcherId);
                return ownerPredicate;
            }
        );

        assertSame(combinedPredicate, specification.toPredicate(root, query, criteriaBuilder));
        assertEquals(42L, ownerResearcherId.get());
    }

    @Test
    void adminScopeAllowsAllRecords() {
        when(criteriaBuilder.conjunction()).thenReturn(allPredicate);

        Specification<Object> specification = ValidableVisibilitySpecifications.visibleTo(
            VisibilityScope.ADMIN_ALL,
            null,
            unusedOwnerPredicate()
        );

        assertSame(allPredicate, specification.toPredicate(root, query, criteriaBuilder));
        verifyNoInteractions(root);
    }

    private ValidableVisibilitySpecifications.OwnerPredicateBuilder<Object> unusedOwnerPredicate() {
        return (root, query, criteriaBuilder, researcherId) -> {
            throw new AssertionError("Owner predicate should not be used.");
        };
    }
}
