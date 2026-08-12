package com.eastapp.backend.people.api;

import com.eastapp.backend.people.Role;

import java.util.UUID;

public record RoleResponse(
        UUID id,
        String systemKey,
        String name,
        boolean active,
        long assignedUsers
) {
    public static RoleResponse from(Role role, long assignedUsers) {
        return new RoleResponse(
                role.getId(),
                role.getSystemKey() == null ? null : role.getSystemKey().name(),
                role.getName(),
                role.isActive(),
                assignedUsers
        );
    }
}
