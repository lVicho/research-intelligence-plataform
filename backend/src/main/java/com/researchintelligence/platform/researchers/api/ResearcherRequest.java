package com.researchintelligence.platform.researchers.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResearcherRequest(
    @NotBlank @Size(max = 255) String fullName,
    @Size(max = 255) String displayName,
    @Email @Size(max = 320) String email,
    @Pattern(regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{3}[0-9X]$", message = "must be a valid ORCID iD") String orcid,
    Boolean active
) {
}
