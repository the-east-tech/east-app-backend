package com.eastapp.backend.organisation.api;

import com.eastapp.backend.organisation.Tenant;

import java.util.UUID;

public record TenantResponse(
        UUID id,
        String companyCode,
        String businessName,
        String employeeIdPrefix,
        boolean active
) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getCompanyCode(),
                tenant.getBusinessName(),
                tenant.getEmployeeIdPrefix(),
                tenant.isActive()
        );
    }
}
