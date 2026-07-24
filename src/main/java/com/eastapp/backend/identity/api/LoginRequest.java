package com.eastapp.backend.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Size(max = 32)
        String companyCode,

        @NotBlank
        @Size(max = 32)
        String employeeId,

        @NotBlank
        @Size(max = 24)
        String phoneE164,

        @NotBlank
        @Size(max = 128)
        String password
) {
}
