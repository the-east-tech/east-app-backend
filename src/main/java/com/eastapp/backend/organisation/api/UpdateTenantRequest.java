package com.eastapp.backend.organisation.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @NotBlank
        @Size(max = 120)
        String businessName,
        boolean active,

        @NotBlank
        @Size(max = 255)
        String googlePlaceId,

        @Min(20)
        @Max(1000)
        int geofenceRadiusMeters
) {
}
