package com.researchintelligence.platform.shared.api;

import java.time.Instant;
import java.util.List;

public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<ValidationError> validationErrors
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }

    public static ApiError withValidationErrors(
        int status,
        String error,
        String message,
        String path,
        List<ValidationError> validationErrors
    ) {
        return new ApiError(Instant.now(), status, error, message, path, validationErrors);
    }
}
