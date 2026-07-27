package com.eastapp.backend.organisation.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
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
        @Size(max = 255)
        String googlePlaceId,

        @Min(20)
        @Max(1000)
        int geofenceRadiusMeters
) {
}
