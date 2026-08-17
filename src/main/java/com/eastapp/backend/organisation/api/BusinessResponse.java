package com.eastapp.backend.organisation.api;

import com.eastapp.backend.organisation.Tenant;

import java.util.UUID;

public record BusinessResponse(
        UUID id,
        String companyCode,
        String businessName,
        String employeeIdPrefix,
        String googlePlaceId,
        String googlePlaceName,
        String formattedAddress,
        double latitude,
        double longitude,
        String googleMapsUri
) {
    public static BusinessResponse from(Tenant tenant) {
        return new BusinessResponse(
                tenant.getId(),
                tenant.getCompanyCode(),
                tenant.getBusinessName(),
                tenant.getEmployeeIdPrefix(),
                tenant.getGooglePlaceId(),
                tenant.getGooglePlaceName(),
                tenant.getFormattedAddress(),
                tenant.getLatitude(),
                tenant.getLongitude(),
                tenant.getGoogleMapsUri()
        );
    }
}
