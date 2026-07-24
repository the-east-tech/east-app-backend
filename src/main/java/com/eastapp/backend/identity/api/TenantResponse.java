package com.eastapp.backend.identity.api;

import com.eastapp.backend.identity.Tenant;

import java.util.UUID;

public record TenantResponse(
        UUID id,
        String companyCode,
        String name
) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getCompanyCode(),
                tenant.getName()
        );
    }
}
