package com.eastapp.backend.organisation.api;

import com.eastapp.backend.organisation.Tenant;

import java.util.UUID;

public record BusinessResponse(
        UUID id,
        String companyCode,
        String businessName,
        String employeeIdPrefix
) {
    public static BusinessResponse from(Tenant tenant) {
        return new BusinessResponse(
                tenant.getId(),
                tenant.getCompanyCode(),
                tenant.getBusinessName(),
                tenant.getEmployeeIdPrefix()
        );
    }
}
