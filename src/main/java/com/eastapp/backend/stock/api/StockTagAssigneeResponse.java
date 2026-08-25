package com.eastapp.backend.stock.api;

import com.eastapp.backend.people.SystemRole;

import java.util.UUID;

public record StockTagAssigneeResponse(
        UUID userId,
        String fullName,
        String employeeId,
        SystemRole role
) {
}
