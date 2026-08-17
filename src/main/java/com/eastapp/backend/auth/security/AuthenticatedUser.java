package com.eastapp.backend.auth.security;

import com.eastapp.backend.people.SystemRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AuthenticatedUser(
        UUID sessionId,
        UUID userId,
        UUID tenantId,
        UUID roleId,
        String employeeId,
        String fullName,
        String tenantCode,
        String tenantName,
        SystemRole systemRole
) {
    public AuthenticatedUser {
        Objects.requireNonNull(systemRole, "systemRole must not be null");
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + systemRole.name()));
    }

    public boolean isOwner() {
        return systemRole == SystemRole.OWNER;
    }

    /** Retains the existing management meaning: Owner or Head. */
    public boolean isHead() {
        return systemRole == SystemRole.OWNER || systemRole == SystemRole.HEAD;
    }

    public boolean isManager() {
        return systemRole == SystemRole.MANAGER;
    }
}
