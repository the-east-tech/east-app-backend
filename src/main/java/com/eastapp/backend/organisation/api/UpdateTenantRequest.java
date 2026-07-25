package com.eastapp.backend.organisation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @NotBlank
        @Size(max = 120)
        String businessName,
        boolean active
) {
}
