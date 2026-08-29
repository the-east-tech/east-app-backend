package com.eastapp.backend.auth.security;

import com.eastapp.backend.auth.permission.RolePermissionPolicy;
import com.eastapp.backend.auth.permission.SystemPermission;
import com.eastapp.backend.people.SystemRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
        SystemRole systemRole,
        Set<SystemPermission> permissions
) {
    public AuthenticatedUser {
        Objects.requireNonNull(systemRole, "systemRole must not be null");
        permissions = Set.copyOf(
                Objects.requireNonNull(permissions, "permissions must not be null")
        );
    }

    public AuthenticatedUser(
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
        this(
                sessionId,
                userId,
                tenantId,
                roleId,
                employeeId,
                fullName,
                tenantCode,
                tenantName,
                systemRole,
                RolePermissionPolicy.grantedTo(systemRole)
        );
    }

    public Collection<? extends GrantedAuthority> authorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + systemRole.name()));
        permissions.stream()
                .sorted()
                .map(permission -> new SimpleGrantedAuthority(permission.authority()))
                .forEach(authorities::add);
        return List.copyOf(authorities);
    }

    public boolean hasPermission(SystemPermission permission) {
        return permission != null && permissions.contains(permission);
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
