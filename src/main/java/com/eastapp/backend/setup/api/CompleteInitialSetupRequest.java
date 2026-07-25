package com.eastapp.backend.setup.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompleteInitialSetupRequest(
        @NotBlank
        @Pattern(regexp = "(?i)^[A-HJ-NP-Z2-9]{10}$")
        String setupCode,

        @NotBlank
        @Size(max = 120)
        String businessName,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,31}$")
        String companyCode,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{1,3}$")
        String employeeIdPrefix,

        @NotBlank
        @Size(max = 120)
        String fullName,

        @NotBlank
        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$")
        String phoneE164,

        @NotBlank
        @Size(min = 4, max = 72)
        String password
) {
}
