package com.eastapp.backend.people.api;

import com.eastapp.backend.people.Role;

import java.util.UUID;

public record UserRoleResponse(
        UUID id,
        String systemKey,
        String name,
        boolean active
) {
    public static UserRoleResponse from(Role role) {
        return new UserRoleResponse(
                role.getId(),
                role.getSystemKey() == null ? null : role.getSystemKey().name(),
                role.getName(),
                role.isActive()
        );
    }
}
