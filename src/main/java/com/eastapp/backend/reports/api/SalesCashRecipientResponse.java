package com.eastapp.backend.reports.api;

import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;

import java.util.UUID;

public record SalesCashRecipientResponse(
        UUID userId,
        String employeeId,
        String fullName,
        SystemRole role
) {
    public static SalesCashRecipientResponse from(UserAccount user) {
        return new SalesCashRecipientResponse(
                user.getId(),
                user.getEmployeeId(),
                user.getFullName(),
                user.getRole().getSystemKey()
        );
    }
}
