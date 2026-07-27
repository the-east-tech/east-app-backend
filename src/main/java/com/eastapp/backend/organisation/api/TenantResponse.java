package com.eastapp.backend.organisation.api;

import com.eastapp.backend.organisation.Tenant;

import java.util.UUID;

public record TenantResponse(
        UUID id,
        String companyCode,
        String businessName,
        String employeeIdPrefix,
        boolean active,
        String googlePlaceId,
        String googlePlaceName,
        String formattedAddress,
        double latitude,
        double longitude,
        String googleMapsUri
) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getCompanyCode(),
                tenant.getBusinessName(),
                tenant.getEmployeeIdPrefix(),
                tenant.isActive(),
                tenant.getGooglePlaceId(),
                tenant.getGooglePlaceName(),
                tenant.getFormattedAddress(),
                tenant.getLatitude(),
                tenant.getLongitude(),
                tenant.getGoogleMapsUri()
        );
    }
}
