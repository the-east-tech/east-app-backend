package com.eastapp.backend.setup.api;

public record CompleteInitialSetupResponse(
        String companyCode,
        String businessName,
        String employeeId
) {
}
