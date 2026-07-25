package com.eastapp.backend.auth.security;

import com.eastapp.backend.people.SystemRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
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
    public Collection<? extends GrantedAuthority> authorities() {
        if (systemRole == null) {
            return List.of(new SimpleGrantedAuthority("ROLE_CUSTOM"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + systemRole.name()));
    }

    public boolean isHead() {
        return systemRole == SystemRole.HEAD;
    }

    public boolean isManager() {
        return systemRole == SystemRole.MANAGER;
    }
}
