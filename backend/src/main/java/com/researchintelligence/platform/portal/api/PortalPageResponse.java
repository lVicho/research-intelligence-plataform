package com.researchintelligence.platform.portal.api;

import com.researchintelligence.platform.shared.api.PageResponse;
import com.researchintelligence.platform.shared.visibility.VisibilityScope;
import java.util.List;

public record PortalPageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last,
    VisibilityScope visibilityScope,
    boolean validationFilterApplied
) {
    public static <T> PortalPageResponse<T> from(PageResponse<T> page) {
        return new PortalPageResponse<>(
            page.content(),
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages(),
            page.last(),
            VisibilityScope.PUBLIC_VALIDATED,
            true
        );
    }
}
