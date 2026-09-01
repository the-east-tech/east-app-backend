package com.eastapp.backend.tasks.api;

import com.eastapp.backend.people.SystemRole;

import java.util.UUID;

public record TaskPersonResponse(
        UUID userId,
        String fullName,
        String employeeId,
        SystemRole role
) {
}
