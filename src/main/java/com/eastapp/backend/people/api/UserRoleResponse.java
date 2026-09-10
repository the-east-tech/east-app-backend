package com.eastapp.backend.people.api;

import com.eastapp.backend.people.Role;
import com.eastapp.backend.people.SystemRole;

import java.util.UUID;

public record UserRoleResponse(
        UUID id,
        String systemKey,
        String name,
        boolean active
) {
    public static UserRoleResponse from(Role role) {
        if (role.getSystemKey() == SystemRole.ADMIN) {
            return new UserRoleResponse(
                    role.getId(),
                    SystemRole.OWNER.name(),
                    "Owner",
                    role.isActive()
            );
        }
        return new UserRoleResponse(
                role.getId(),
                role.getSystemKey().name(),
                role.getName(),
                role.isActive()
        );
    }
}
